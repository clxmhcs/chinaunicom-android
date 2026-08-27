package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.network.UnicomVideoRingClient
import com.clxmhcs.chinaunicom.core.network.VideoRingNetworkClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

interface VideoRingCredentialValidator {
    fun fetchMemberState(credentials: AccountCredentials, expectedPhoneNumber: String): VideoRingMemberFetchResult
}

class UnicomVideoRingCredentialValidator(
    private val client: VideoRingNetworkClient = UnicomVideoRingClient(),
) : VideoRingCredentialValidator {
    override fun fetchMemberState(
        credentials: AccountCredentials,
        expectedPhoneNumber: String,
    ): VideoRingMemberFetchResult = client.fetchMemberState(credentials, expectedPhoneNumber)
}

interface VideoRingRequestLifecycle {
    fun hasCredentials(accountID: UUID): Boolean
    fun fetchValidated(accountID: UUID, expectedPhoneNumber: String): VideoRingMemberFetchResult
}

/** M5 CredentialStore remains the only credential authority for M9-H. */
class VideoRingAccountCredentialLifecycle(
    private val validator: VideoRingCredentialValidator,
    private val credentialStore: CredentialStore,
) : VideoRingRequestLifecycle {
    override fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    override fun fetchValidated(accountID: UUID, expectedPhoneNumber: String): VideoRingMemberFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchMemberState(credentials, expectedPhoneNumber)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
