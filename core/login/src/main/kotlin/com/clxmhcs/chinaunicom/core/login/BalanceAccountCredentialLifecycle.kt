package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BalanceFetchResult
import com.clxmhcs.chinaunicom.core.network.UnicomAPIClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

fun interface BalanceCredentialValidator {
    fun fetchBalance(credentials: AccountCredentials): BalanceFetchResult
}

class UnicomBalanceCredentialValidator(
    private val apiClient: UnicomAPIClient = UnicomAPIClient(),
) : BalanceCredentialValidator {
    override fun fetchBalance(credentials: AccountCredentials): BalanceFetchResult =
        apiClient.fetchBalance(credentials)
}

/**
 * Secure credential boundary for M6 balance refresh.
 *
 * Balance business state never receives raw Cookie/appID/token_online. Credentials are restored
 * from the M5 Keystore store, renewed credentials returned by M4 are saved immediately, and the
 * outward result has updatedCredentials stripped before it reaches ordinary M6 persistence.
 */
class BalanceAccountCredentialLifecycle(
    private val validator: BalanceCredentialValidator,
    private val credentialStore: CredentialStore,
) {
    fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    fun refreshValidatedBalance(accountID: UUID): BalanceFetchResult {
        val credentials = credentialStore.read(accountID)
            ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchBalance(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
