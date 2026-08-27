package com.clxmhcs.chinaunicom.data.broadbandaccount

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.network.UnicomAPIClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Clock
import java.time.Instant
import java.util.UUID

fun interface BroadbandCredentialValidator {
    fun validate(credentials: AccountCredentials): AccountCredentials
}

class UnicomBroadbandCredentialValidator(
    private val api: UnicomAPIClient = UnicomAPIClient(),
) : BroadbandCredentialValidator {
    override fun validate(credentials: AccountCredentials): AccountCredentials {
        val result = api.fetchQuota(credentials)
        return result.updatedCredentials ?: credentials
    }
}

data class BroadbandAccountDraft(
    val serviceNumber: String,
    val displayName: String = "",
    val idCardLastSix: String,
    val locationName: String = "",
    val provinceCode: String = "",
    val cityCode: String = "",
    val areaCode: String = "",
)

/**
 * Source-equivalent transactional boundary for independent broadband accounts.
 * Validation occurs before persistence; credential and ordinary metadata writes are rolled back as a unit.
 */
class BroadbandAccountLifecycle(
    private val repository: BroadbandAccountRepository,
    private val credentialStore: CredentialStore,
    private val validator: BroadbandCredentialValidator = UnicomBroadbandCredentialValidator(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun loadAccounts(): List<BroadbandAccountInfo> = repository.loadAccounts()

    fun validateAndSave(
        draft: BroadbandAccountDraft,
        enteredCredentials: AccountCredentials,
    ): BroadbandAccountInfo {
        val normalizedServiceNumber = draft.serviceNumber.trim()
        val normalizedIDCardLastSix = draft.idCardLastSix.filter(Char::isDigit).take(6)
        val normalizedCookie = enteredCredentials.cookie.trim()
        require(normalizedServiceNumber.isNotEmpty()) { "broadbandServiceNumberRequired" }
        require(normalizedIDCardLastSix.length == 6) { "idCardLastSixMustContainSixDigits" }
        require(normalizedCookie.isNotEmpty()) { "broadbandCookieRequired" }

        val normalizedCredentials = AccountCredentials(
            cookie = normalizedCookie,
            appID = enteredCredentials.appID?.trim()?.takeIf(String::isNotEmpty),
            tokenOnline = enteredCredentials.tokenOnline?.trim()?.takeIf(String::isNotEmpty),
        )
        val validatedCredentials = validator.validate(normalizedCredentials)
        val existing = repository.loadAccounts().firstOrNull { it.serviceNumber == normalizedServiceNumber }
        val now = Instant.now(clock)
        val record = BroadbandAccountInfo(
            id = existing?.id ?: UUID.randomUUID(),
            serviceNumber = normalizedServiceNumber,
            displayName = draft.displayName.trim(),
            idCardLastSix = normalizedIDCardLastSix,
            locationName = draft.locationName.trim(),
            provinceCode = draft.provinceCode.trim(),
            cityCode = draft.cityCode.trim(),
            areaCode = draft.areaCode.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        val previousCredentials = credentialStore.read(record.id)
        credentialStore.save(record.id, validatedCredentials)
        try {
            repository.upsert(record)
        } catch (error: Throwable) {
            if (previousCredentials == null) {
                credentialStore.delete(record.id)
            } else {
                credentialStore.save(record.id, previousCredentials)
            }
            throw error
        }
        return record
    }

    fun validateAndUpdateCredentials(
        accountID: UUID,
        enteredCredentials: AccountCredentials,
    ): BroadbandAccountInfo {
        val account = repository.loadAccounts().firstOrNull { it.id == accountID }
            ?: error("broadbandAccountNotFound")
        val normalized = AccountCredentials(
            cookie = enteredCredentials.cookie.trim().also { require(it.isNotEmpty()) { "broadbandCookieRequired" } },
            appID = enteredCredentials.appID?.trim()?.takeIf(String::isNotEmpty),
            tokenOnline = enteredCredentials.tokenOnline?.trim()?.takeIf(String::isNotEmpty),
        )
        val validated = validator.validate(normalized)
        val previousCredentials = credentialStore.read(accountID)
        credentialStore.save(accountID, validated)
        val updated = account.copy(updatedAt = Instant.now(clock))
        try {
            repository.upsert(updated)
        } catch (error: Throwable) {
            if (previousCredentials == null) credentialStore.delete(accountID)
            else credentialStore.save(accountID, previousCredentials)
            throw error
        }
        return updated
    }

    fun remove(accountID: UUID) {
        repository.remove(accountID)
        credentialStore.delete(accountID)
    }

    fun clear() {
        val ids = repository.loadAccounts().map { it.id }
        repository.clear()
        ids.forEach(credentialStore::delete)
    }
}
