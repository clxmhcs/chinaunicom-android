package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.network.UnicomAPIClient
import com.clxmhcs.chinaunicom.core.network.UnicomPasswordLoginResult
import com.clxmhcs.chinaunicom.core.network.UnicomSMSLoginResult
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

fun interface QuotaCredentialValidator {
    fun fetchQuota(credentials: AccountCredentials): QuotaFetchResult
}

class UnicomQuotaCredentialValidator(
    private val apiClient: UnicomAPIClient = UnicomAPIClient(),
) : QuotaCredentialValidator {
    override fun fetchQuota(credentials: AccountCredentials): QuotaFetchResult =
        apiClient.fetchQuota(credentials)
}

data class ValidatedLoginAccountSeed(
    val accountID: UUID,
    val mobile: String,
    val quota: QuotaFetchResult,
)

sealed class LoginAccountLifecycleException(message: String) : Exception(message) {
    data object InvalidMobile : LoginAccountLifecycleException("请输入正确的 11 位联通手机号")
    data object MissingCredentials : LoginAccountLifecycleException("账号缺少可用登录凭据")
}

/**
 * M5-D transaction boundary joining login credentials, M4 quota validation and the M5-A
 * secure credential store without taking ownership of M6 account-list persistence.
 *
 * Source-equivalent ordering for creation is deliberate:
 * 1. validate credentials through the real quota path;
 * 2. prefer M4 renewed credentials when session activation changes Cookie/token_online;
 * 3. save credentials under a newly allocated account UUID;
 * 4. invoke the caller's account-metadata commit;
 * 5. if metadata commit fails, delete the newly saved credentials before rethrowing.
 *
 * The returned seed intentionally strips QuotaFetchResult.updatedCredentials so credentials do
 * not escape into account metadata or ordinary persistence. M6 must continue to use this class
 * or CredentialStore for credential access rather than serializing credentials with accounts.
 */
class LoginAccountLifecycle(
    private val validator: QuotaCredentialValidator,
    private val credentialStore: CredentialStore,
    private val accountIDProvider: () -> UUID = UUID::randomUUID,
) {
    fun createValidatedSMSAccount(
        mobile: String,
        loginResult: UnicomSMSLoginResult,
        commitAccountMetadata: (ValidatedLoginAccountSeed) -> Unit,
    ): ValidatedLoginAccountSeed = createValidatedAccount(
        mobile = mobile,
        credentials = loginResult.credentials,
        commitAccountMetadata = commitAccountMetadata,
    )

    fun createValidatedPasswordAccount(
        mobile: String,
        loginResult: UnicomPasswordLoginResult,
        commitAccountMetadata: (ValidatedLoginAccountSeed) -> Unit,
    ): ValidatedLoginAccountSeed = createValidatedAccount(
        mobile = mobile,
        credentials = loginResult.credentials,
        commitAccountMetadata = commitAccountMetadata,
    )

    fun createValidatedAccount(
        mobile: String,
        credentials: AccountCredentials,
        commitAccountMetadata: (ValidatedLoginAccountSeed) -> Unit,
    ): ValidatedLoginAccountSeed {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != 11) throw LoginAccountLifecycleException.InvalidMobile

        val result = validator.fetchQuota(credentials)
        val credentialsToSave = result.updatedCredentials ?: credentials
        val accountID = accountIDProvider()
        credentialStore.save(accountID, credentialsToSave)

        val seed = ValidatedLoginAccountSeed(
            accountID = accountID,
            mobile = normalizedMobile,
            quota = result.copy(updatedCredentials = null),
        )

        try {
            commitAccountMetadata(seed)
        } catch (error: Throwable) {
            runCatching { credentialStore.delete(accountID) }
            throw error
        }
        return seed
    }

    fun restoreCredentials(accountID: UUID): AccountCredentials =
        credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials

    fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult {
        val credentials = restoreCredentials(accountID)
        val result = validator.fetchQuota(credentials)
        result.updatedCredentials?.let { renewed ->
            credentialStore.save(accountID, renewed)
        }
        return result.copy(updatedCredentials = null)
    }

    /**
     * M5-side half of account deletion. The caller owns M6 metadata removal. If that commit fails,
     * previously stored credentials are restored, matching the rollback intent of the iOS AppStore.
     */
    fun deleteAccount(
        accountID: UUID,
        commitAccountMetadataDeletion: () -> Unit,
    ) {
        val previousCredentials = credentialStore.read(accountID)
        credentialStore.delete(accountID)
        try {
            commitAccountMetadataDeletion()
        } catch (error: Throwable) {
            if (previousCredentials != null) {
                runCatching { credentialStore.save(accountID, previousCredentials) }
            }
            throw error
        }
    }

    private fun normalizeMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == 13 && digits.startsWith("86")) digits.drop(2) else digits
    }
}
