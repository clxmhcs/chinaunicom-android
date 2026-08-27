package com.clxmhcs.chinaunicom.data.videoring

import com.clxmhcs.chinaunicom.core.login.VideoRingRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRingStoreTest {
    @Test
    fun loadPublishesSelectedAccountOnlyAndNoCredentialState() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val lifecycle = FakeLifecycle(
            account.id,
            VideoRingMemberFetchResult(
                VideoRingMemberState("18600001234", emptyList(), emptyList(), isEnabled = true),
            ),
        )
        val store = DefaultVideoRingStore(lifecycle, now = { Instant.parse("2026-08-27T00:00:00Z") })

        store.load(account)

        assertFalse(store.state.value.loading)
        assertEquals(account.id, store.state.value.accountID)
        assertEquals("18600001234", store.state.value.memberState?.phoneNumber)
        assertTrue(store.state.value.memberState?.isEnabled == true)
        assertEquals(Instant.parse("2026-08-27T00:00:00Z"), store.state.value.lastRefreshTime)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun mismatchedPhoneIsRejectedEvenIfNetworkLifecycleMisbehaves() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val lifecycle = FakeLifecycle(
            account.id,
            VideoRingMemberFetchResult(
                VideoRingMemberState("18500005678", emptyList(), emptyList()),
            ),
        )
        val store = DefaultVideoRingStore(lifecycle)

        store.load(account)

        assertNotNull(store.state.value.errorMessage)
        assertNull(store.state.value.memberState)
    }

    private class FakeLifecycle(
        private val accountID: UUID,
        private val result: VideoRingMemberFetchResult,
    ) : VideoRingRequestLifecycle {
        override fun hasCredentials(accountID: UUID): Boolean = accountID == this.accountID
        override fun fetchValidated(accountID: UUID, expectedPhoneNumber: String): VideoRingMemberFetchResult = result
    }
}
