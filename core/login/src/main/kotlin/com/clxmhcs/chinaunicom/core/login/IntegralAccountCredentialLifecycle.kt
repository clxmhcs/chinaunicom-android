package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralFetchResult
import com.clxmhcs.chinaunicom.core.network.IntegralNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomIntegralClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Instant
import java.util.UUID

interface IntegralCredentialValidator {
    fun fetchOverview(
        credentials: AccountCredentials,
        mobile: String,
        fetchedAt: Instant,
    ): IntegralFetchResult

    fun fetchDetails(
        query: IntegralDetailQuery,
        credentials: AccountCredentials,
        mobile: String,
    ): IntegralDetailsFetchResult
}

class UnicomIntegralCredentialValidator(
    private val client: IntegralNetworkClient = UnicomIntegralClient(),
) : IntegralCredentialValidator {
    override fun fetchOverview(
        credentials: AccountCredentials,
        mobile: String,
        fetchedAt: Instant,
    ): IntegralFetchResult = client.fetchOverview(credentials, mobile, fetchedAt)

    override fun fetchDetails(
        query: IntegralDetailQuery,
        credentials: AccountCredentials,
        mobile: String,
    ): IntegralDetailsFetchResult = client.fetchDetails(query, credentials, mobile)
}

interface IntegralRequestLifecycle {
    fun hasCredentials(accountID: UUID): Boolean
    fun fetchOverviewValidated(accountID: UUID, mobile: String, fetchedAt: Instant): IntegralFetchResult
    fun fetchDetailsValidated(accountID: UUID, mobile: String, query: IntegralDetailQuery): IntegralDetailsFetchResult
}

/** M5 credential authority bridge for M8-D integral requests. */
class IntegralAccountCredentialLifecycle(
    private val validator: IntegralCredentialValidator,
    private val credentialStore: CredentialStore,
) : IntegralRequestLifecycle {
    override fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    override fun fetchOverviewValidated(
        accountID: UUID,
        mobile: String,
        fetchedAt: Instant,
    ): IntegralFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchOverview(credentials, mobile, fetchedAt)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }

    override fun fetchDetailsValidated(
        accountID: UUID,
        mobile: String,
        query: IntegralDetailQuery,
    ): IntegralDetailsFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchDetails(query, credentials, mobile)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
