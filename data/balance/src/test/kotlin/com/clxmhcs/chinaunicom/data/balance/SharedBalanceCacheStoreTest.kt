package com.clxmhcs.chinaunicom.data.balance

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedBalanceCacheStoreTest {
    private val first = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val second = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val zone = ZoneId.of("Asia/Taipei")
    private val storage = MemorySharedBalanceStorage()
    private val store = SharedBalanceCacheStore(storage, zone)

    @Test
    fun automaticUsesFreshSameDayCacheAndNewDayExpiresIt() {
        val at = Instant.parse("2026-08-25T01:00:00Z")
        store.replaceScopes(listOf(SharedBalanceScope.account(first)), at)
        val token = (store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, at) as SharedBalanceRefreshClaim.Granted).token
        store.completeRefresh(token, 88.66, first, at)

        val fresh = store.beginAutomaticRefresh(first, SharedBalanceRefreshSource.APP_AUTOMATIC, at.plusSeconds(59 * 60))
        assertTrue(fresh is SharedBalanceRefreshClaim.Cached)

        val nextDay = Instant.parse("2026-08-25T16:05:00Z")
        val expired = store.beginAutomaticRefresh(first, SharedBalanceRefreshSource.APP_AUTOMATIC, nextDay)
        assertTrue(expired is SharedBalanceRefreshClaim.Granted)
    }

    @Test
    fun onlyOneLeaseIsGrantedAndForcedRefreshAlsoRespectsInflight() {
        val now = Instant.parse("2026-08-25T02:00:00Z")
        val firstClaim = store.beginAutomaticRefresh(first, SharedBalanceRefreshSource.APP_AUTOMATIC, now)
        assertTrue(firstClaim is SharedBalanceRefreshClaim.Granted)
        assertTrue(store.beginAutomaticRefresh(first, SharedBalanceRefreshSource.WIDGET_AUTOMATIC, now) is SharedBalanceRefreshClaim.InFlight)
        assertTrue(store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, now) is SharedBalanceRefreshClaim.InFlight)

        val token = (firstClaim as SharedBalanceRefreshClaim.Granted).token
        store.failRefresh(token)
        assertTrue(store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, now) is SharedBalanceRefreshClaim.Granted)
    }

    @Test
    fun failedRefreshKeepsLastSuccessfulEntry() {
        val now = Instant.parse("2026-08-25T02:00:00Z")
        val successful = (store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, now) as SharedBalanceRefreshClaim.Granted).token
        store.completeRefresh(successful, 10.0, first, now)
        val later = now.plusSeconds(4000)
        val failed = (store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, later) as SharedBalanceRefreshClaim.Granted).token
        store.failRefresh(failed)
        assertEquals(10.0, store.latestEntry(first)?.balanceYuan ?: -1.0, 0.0)
    }

    @Test
    fun scopeChangeInvalidatesCacheAndLease() {
        val now = Instant.parse("2026-08-25T02:00:00Z")
        val group = SharedBalanceScope("group:g", listOf(first, second), first)
        store.replaceScopes(listOf(group), now)
        val token = (store.beginForcedRefresh(first, SharedBalanceRefreshSource.APP_MANUAL, now) as SharedBalanceRefreshClaim.Granted).token
        store.completeRefresh(token, 20.0, first, now)
        assertEquals(20.0, store.latestEntry(second)?.balanceYuan ?: -1.0, 0.0)

        store.replaceScopes(listOf(SharedBalanceScope.account(first), SharedBalanceScope.account(second)), now.plusSeconds(1))
        assertNull(store.latestEntry(first))
        assertNull(store.latestEntry(second))
    }

    @Test
    fun leaseDurationAndRefreshIntervalAreClamped() {
        store.setRefreshIntervalMinutes(99_999)
        assertEquals(24 * 60, store.refreshIntervalMinutes())
        val now = Instant.parse("2026-08-25T02:00:00Z")
        val token = (store.beginForcedRefresh(
            first,
            SharedBalanceRefreshSource.APP_MANUAL,
            now,
            Duration.ofSeconds(1),
        ) as SharedBalanceRefreshClaim.Granted).token
        assertEquals(15, Duration.between(token.startedAt, token.expiresAt).seconds)
    }
}

private class MemorySharedBalanceStorage(
    var state: SharedBalancePersistedState = SharedBalancePersistedState(),
) : SharedBalanceStateStorage {
    override fun <T> transaction(
        block: (SharedBalancePersistedState) -> SharedBalanceTransaction<T>,
    ): T? {
        val result = block(state)
        state = result.state
        return result.value
    }
}
