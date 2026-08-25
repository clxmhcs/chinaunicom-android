package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.network.OrderedBusinessNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomOrderedBusinessClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

fun interface OrderedBusinessCredentialValidator {
    fun fetch(credentials: AccountCredentials): OrderedBusinessFetchResult
}

class UnicomOrderedBusinessCredentialValidator(
    private val client: OrderedBusinessNetworkClient = UnicomOrderedBusinessClient(),
) : OrderedBusinessCredentialValidator {
    override fun fetch(credentials: AccountCredentials): OrderedBusinessFetchResult = client.fetch(credentials)
}

/**
 * M5-owned secure credential boundary for M8 ordered-business refresh.
 *
 * Raw Cookie/appID/token_online never enter ordinary M8 cache/store state. Renewed credentials are
 * committed to the existing CredentialStore immediately and stripped before the business result is
 * returned to the M8 store.
 */
class OrderedBusinessAccountCredentialLifecycle(
    private val validator: OrderedBusinessCredentialValidator,
    private val credentialStore: CredentialStore,
) {
    fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    fun refreshValidated(accountID: UUID): OrderedBusinessFetchResult {
        val credentials = credentialStore.read(accountID)
            ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetch(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
