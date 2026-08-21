package com.clxmhcs.chinaunicom.core.refresh

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshCoordinatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val account = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun automaticRefreshConsumesFreshCacheButManualRefreshBypassesIt() {
        val coordinator = RefreshCoordinator<String>(intervalMinutes = 60, zoneId = zone)
        val now = Instant.parse("2026-08-21T10:00:00Z")
        val lease = (coordinator.request(account, RefreshSource.AUTOMATIC, now) as RefreshDecision.Granted).lease
        assertTrue(coordinator.complete(lease, "cached", now))

        assertTrue(coordinator.request(account, RefreshSource.AUTOMATIC, now.plusSeconds(60)) is RefreshDecision.Cached)
        assertTrue(coordinator.request(account, RefreshSource.MANUAL, now.plusSeconds(60)) is RefreshDecision.Granted)
    }

    @Test
    fun onlyLeaseOwnerCanCommitAndFailureKeepsOldCache() {
        val coordinator = RefreshCoordinator<String>(zoneId = zone)
        val now = Instant.parse("2026-08-21T10:00:00Z")
        val first = (coordinator.request(account, RefreshSource.AUTOMATIC, now) as RefreshDecision.Granted).lease
        assertTrue(coordinator.complete(first, "old", now))
        val second = (coordinator.request(account, RefreshSource.MANUAL, now.plusSeconds(1)) as RefreshDecision.Granted).lease

        assertFalse(coordinator.complete(first, "wrong", now.plusSeconds(2)))
        assertTrue(coordinator.fail(second))
        assertEquals("old", coordinator.latest(account)?.value)
    }

    @Test
    fun newLocalDayExpiresAutomaticCacheEvenInsideInterval() {
        val coordinator = RefreshCoordinator<String>(intervalMinutes = 120, zoneId = zone)
        val beforeMidnight = Instant.parse("2026-08-21T15:59:00Z")
        val lease = (coordinator.request(account, RefreshSource.AUTOMATIC, beforeMidnight) as RefreshDecision.Granted).lease
        coordinator.complete(lease, "old", beforeMidnight)

        val afterMidnight = Instant.parse("2026-08-21T16:01:00Z")
        assertTrue(coordinator.request(account, RefreshSource.AUTOMATIC, afterMidnight) is RefreshDecision.Granted)
    }
}
