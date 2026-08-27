package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoRingAccountCredentialLifecycleTest {
    @Test
    fun renewedCredentialsArePersistedAndStrippedFromBusinessResult() {
        val id = UUID.randomUUID()
        val old = AccountCredentials("old=1", "app", "token")
        val renewed = AccountCredentials("new=1", "app", "token2")
        val store = FakeCredentialStore(mutableMapOf(id to old))
        val lifecycle = VideoRingAccountCredentialLifecycle(
            validator = object : VideoRingCredentialValidator {
                override fun fetchMemberState(credentials: AccountCredentials, expectedPhoneNumber: String) =
                    VideoRingMemberFetchResult(
                        VideoRingMemberState(expectedPhoneNumber, emptyList()),
                        renewed,
                    )
            },
            credentialStore = store,
        )

        val result = lifecycle.fetchValidated(id, "18600001234")

        assertEquals(renewed, store.read(id))
        assertNull(result.updatedCredentials)
        assertEquals("18600001234", result.state.phoneNumber)
    }

    private class FakeCredentialStore(
        private val values: MutableMap<UUID, AccountCredentials>,
    ) : CredentialStore {
        override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
        override fun read(accountID: UUID): AccountCredentials? = values[accountID]
        override fun delete(accountID: UUID) { values.remove(accountID) }
        override fun deleteAll() { values.clear() }
    }
}
