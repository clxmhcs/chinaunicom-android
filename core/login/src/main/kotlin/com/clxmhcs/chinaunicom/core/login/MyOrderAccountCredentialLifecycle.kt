package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyOrderFetchResult
import com.clxmhcs.chinaunicom.core.network.MyOrderNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomMyOrderClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

fun interface MyOrderCredentialValidator {
    fun fetch(
        mobile: String,
        page: Int,
        pageSize: Int,
        credentials: AccountCredentials,
    ): MyOrderFetchResult
}

class UnicomMyOrderCredentialValidator(
    private val client: MyOrderNetworkClient = UnicomMyOrderClient(),
) : MyOrderCredentialValidator {
    override fun fetch(
        mobile: String,
        page: Int,
        pageSize: Int,
        credentials: AccountCredentials,
    ): MyOrderFetchResult = client.fetch(mobile, page, pageSize, credentials)
}

interface MyOrderRequestLifecycle {
    fun fetchValidated(
        accountID: UUID,
        mobile: String,
        page: Int,
        pageSize: Int,
    ): MyOrderFetchResult
}

/**
 * M5-owned secure credential boundary for M9-A My Order requests.
 *
 * Raw Cookie/appID/token_online never enter ordinary M9 state. Renewed credentials are committed
 * immediately to the existing CredentialStore, then stripped from the result returned to data state.
 */
class MyOrderAccountCredentialLifecycle(
    private val validator: MyOrderCredentialValidator,
    private val credentialStore: CredentialStore,
) : MyOrderRequestLifecycle {
    override fun fetchValidated(
        accountID: UUID,
        mobile: String,
        page: Int,
        pageSize: Int,
    ): MyOrderFetchResult {
        val credentials = credentialStore.read(accountID)
            ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetch(mobile, page, pageSize, credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
