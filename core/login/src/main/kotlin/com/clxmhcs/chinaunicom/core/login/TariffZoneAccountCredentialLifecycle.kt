package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneIndexFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneProductReference
import com.clxmhcs.chinaunicom.core.model.TariffZoneReferencesFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.network.TariffZoneNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomTariffZoneClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

interface TariffZoneCredentialValidator {
    fun fetchIndex(credentials: AccountCredentials): TariffZoneIndexFetchResult

    fun fetchProductReferences(
        credentials: AccountCredentials,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult

    fun fetchDetails(
        credentials: AccountCredentials,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult
}

class UnicomTariffZoneCredentialValidator(
    private val client: TariffZoneNetworkClient = UnicomTariffZoneClient(),
) : TariffZoneCredentialValidator {
    override fun fetchIndex(credentials: AccountCredentials): TariffZoneIndexFetchResult =
        client.fetchIndex(credentials)

    override fun fetchProductReferences(
        credentials: AccountCredentials,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult = client.fetchProductReferences(
        credentials,
        scope,
        firstLevel,
        secondLevel,
        region,
    )

    override fun fetchDetails(
        credentials: AccountCredentials,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult = client.fetchDetails(credentials, references, page, region)
}

interface TariffZoneRequestLifecycle {
    fun hasCredentials(accountID: UUID): Boolean
    fun fetchIndexValidated(accountID: UUID): TariffZoneIndexFetchResult

    fun fetchProductReferencesValidated(
        accountID: UUID,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult

    fun fetchDetailsValidated(
        accountID: UUID,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult
}

/** M5 CredentialStore remains the only credential authority for M9-G carrier requests. */
class TariffZoneAccountCredentialLifecycle(
    private val validator: TariffZoneCredentialValidator,
    private val credentialStore: CredentialStore,
) : TariffZoneRequestLifecycle {
    override fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    override fun fetchIndexValidated(accountID: UUID): TariffZoneIndexFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchIndex(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }

    override fun fetchProductReferencesValidated(
        accountID: UUID,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchProductReferences(credentials, scope, firstLevel, secondLevel, region)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }

    override fun fetchDetailsValidated(
        accountID: UUID,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchDetails(credentials, references, page, region)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
