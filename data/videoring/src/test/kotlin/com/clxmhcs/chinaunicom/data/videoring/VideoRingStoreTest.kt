package com.clxmhcs.chinaunicom.data.videoring

import com.clxmhcs.chinaunicom.core.login.VideoRingRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VideoRingMember
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
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
    fun everyEntryRestoresCacheThenRefreshesAndPersistsSelectedAccount() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val cache = FakeCache(mutableMapOf(account.id to VideoRingCacheRecord(memberState("18600001234", false), Instant.parse("2026-08-27T00:00:00Z"))))
        val lifecycle = FakeLifecycle(account.id, memberState("18600001234", true))
        val store = DefaultVideoRingStore(
            lifecycle = lifecycle,
            cache = cache,
            policyProvider = { VideoRingStoreRefreshPolicy(VideoRingEntryMode.EVERY_ENTRY, 60) },
            now = { Instant.parse("2026-08-27T01:00:00Z") },
        )

        store.load(account)

        assertEquals(1, lifecycle.fetchCount)
        assertFalse(store.state.value.loading)
        assertEquals(account.id, store.state.value.accountID)
        assertTrue(store.state.value.memberState?.members?.first()?.isMember == true)
        assertEquals(Instant.parse("2026-08-27T01:00:00Z"), store.state.value.lastRefreshTime)
        assertFalse(store.state.value.restoredFromCache)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun unexpiredCacheSkipsNetworkWhenConfigured() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val cache = FakeCache(mutableMapOf(account.id to VideoRingCacheRecord(memberState("18600001234", true), Instant.parse("2026-08-27T00:30:00Z"))))
        val lifecycle = FakeLifecycle(account.id, memberState("18600001234", false))
        val store = DefaultVideoRingStore(
            lifecycle = lifecycle,
            cache = cache,
            policyProvider = { VideoRingStoreRefreshPolicy(VideoRingEntryMode.REFRESH_WHEN_EXPIRED, 60) },
            now = { Instant.parse("2026-08-27T01:00:00Z") },
        )

        store.load(account)

        assertEquals(0, lifecycle.fetchCount)
        assertTrue(store.state.value.restoredFromCache)
        assertTrue(store.state.value.memberState?.members?.first()?.isMember == true)
    }

    @Test
    fun manualOnlyWithoutCacheDoesNotQueryUntilRefresh() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val lifecycle = FakeLifecycle(account.id, memberState("18600001234", true))
        val store = DefaultVideoRingStore(
            lifecycle = lifecycle,
            cache = FakeCache(),
            policyProvider = { VideoRingStoreRefreshPolicy(VideoRingEntryMode.MANUAL_ONLY, 60) },
        )

        store.load(account)
        assertEquals(0, lifecycle.fetchCount)
        assertNotNull(store.state.value.errorMessage)

        store.refresh(account)
        assertEquals(1, lifecycle.fetchCount)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun mismatchedPhoneIsRejectedEvenIfLifecycleMisbehaves() = runBlocking {
        val account = UnicomAccount(displayName = "A", mobile = "18600001234")
        val lifecycle = FakeLifecycle(account.id, memberState("18500005678", true))
        val store = DefaultVideoRingStore(lifecycle, FakeCache())

        store.load(account)

        assertNotNull(store.state.value.errorMessage)
        assertNull(store.state.value.memberState)
    }

    private fun memberState(phone: String, opened: Boolean) = VideoRingMemberState(
        phoneNumber = phone,
        members = listOf(VideoRingMember("15", "铂金会员", "15", opened)),
    )

    private class FakeLifecycle(private val accountID: UUID, private val result: VideoRingMemberState) : VideoRingRequestLifecycle {
        var fetchCount = 0
        override fun hasCredentials(accountID: UUID): Boolean = accountID == this.accountID
        override fun fetchValidated(accountID: UUID, expectedPhoneNumber: String): VideoRingMemberFetchResult {
            fetchCount += 1
            return VideoRingMemberFetchResult(result)
        }
    }

    private class FakeCache(val values: MutableMap<UUID, VideoRingCacheRecord> = mutableMapOf()) : VideoRingDiskCache {
        override fun load(accountID: UUID): VideoRingCacheRecord? = values[accountID]
        override fun save(accountID: UUID, record: VideoRingCacheRecord) { values[accountID] = record }
    }
}
