package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.GiftRecordsFetchResult
import com.clxmhcs.chinaunicom.core.model.RebateContractsFetchResult
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import com.clxmhcs.chinaunicom.core.network.RebateAndGiftNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomRebateAndGiftClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

interface RebateAndGiftCredentialValidator {
    fun fetchContracts(credentials: AccountCredentials, scope: RebateQueryScope): RebateContractsFetchResult
    fun fetchGiftRecords(credentials: AccountCredentials): GiftRecordsFetchResult
}

class UnicomRebateAndGiftCredentialValidator(
    private val client: RebateAndGiftNetworkClient = UnicomRebateAndGiftClient(),
) : RebateAndGiftCredentialValidator {
    override fun fetchContracts(
        credentials: AccountCredentials,
        scope: RebateQueryScope,
    ): RebateContractsFetchResult = client.fetchContracts(credentials, scope)

    override fun fetchGiftRecords(credentials: AccountCredentials): GiftRecordsFetchResult =
        client.fetchGiftRecords(credentials)
}

interface RebateAndGiftRequestLifecycle {
    fun hasCredentials(accountID: UUID): Boolean
    fun fetchContractsValidated(accountID: UUID, scope: RebateQueryScope): RebateContractsFetchResult
    fun fetchGiftRecordsValidated(accountID: UUID): GiftRecordsFetchResult
}

/** M5 CredentialStore remains the only credential authority for M9-F carrier requests. */
class RebateAndGiftAccountCredentialLifecycle(
    private val validator: RebateAndGiftCredentialValidator,
    private val credentialStore: CredentialStore,
) : RebateAndGiftRequestLifecycle {
    override fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    override fun fetchContractsValidated(
        accountID: UUID,
        scope: RebateQueryScope,
    ): RebateContractsFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchContracts(credentials, scope)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }

    override fun fetchGiftRecordsValidated(accountID: UUID): GiftRecordsFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchGiftRecords(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
