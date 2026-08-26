package com.clxmhcs.chinaunicom.data.comprehensive

import com.clxmhcs.chinaunicom.core.model.IntegralMonthSummary
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.data.integral.IntegralCacheRecord
import com.clxmhcs.chinaunicom.data.integral.IntegralDiskCache
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComprehensiveBusinessStoreTest {
    private val first = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val second = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun rootProjectsOnlyRequestedCachedIntegralPoints() = runBlocking {
        val cache = MemoryIntegralCache(
            mutableMapOf(
                first to record(points = 1234),
                second to record(points = 5678),
            ),
        )
        val store = DefaultComprehensiveBusinessStore(cache, Dispatchers.Unconfined)

        store.loadCachedPoints(listOf(first))

        assertEquals(1234, store.points(first))
        assertNull(store.points(second))
        assertEquals(mapOf(first to 1234), store.state.value.pointsByAccountID)
    }

    @Test
    fun reloadingWithDifferentAccountSetDropsOrphanPoints() = runBlocking {
        val cache = MemoryIntegralCache(
            mutableMapOf(
                first to record(points = 1234),
                second to record(points = 5678),
            ),
        )
        val store = DefaultComprehensiveBusinessStore(cache, Dispatchers.Unconfined)

        store.loadCachedPoints(listOf(first, second))
        store.loadCachedPoints(listOf(second))

        assertNull(store.points(first))
        assertEquals(5678, store.points(second))
        assertEquals(setOf(second), store.state.value.pointsByAccountID.keys)
    }

    private fun record(points: Int) = IntegralCacheRecord(
        snapshot = IntegralSnapshot(
            totalAvailable = points,
            communication = 100,
            reward = 200,
            directional = null,
            expiredAndExpiringReward = 0,
            expiringThisMonth = 0,
            expiringCommunication = 0,
            expiringReward = 0,
            expirationDay = null,
            couponCount = 0,
            provinceCode = null,
            packageID = null,
            isUnicom = null,
            months = listOf(IntegralMonthSummary("2026-08", 1, 2, 3)),
            fetchedAt = Instant.parse("2026-08-26T00:00:00Z"),
            parserVersion = IntegralSnapshot.CURRENT_PARSER_VERSION,
        ),
        details = emptyMap(),
        refreshCycleKey = "monthly-202608",
    )
}

private class MemoryIntegralCache(
    private val values: MutableMap<UUID, IntegralCacheRecord> = mutableMapOf(),
) : IntegralDiskCache {
    override fun load(accountID: UUID): IntegralCacheRecord? = values[accountID]
    override fun snapshots(accountIDs: Collection<UUID>) = values.filterKeys(accountIDs.toSet()::contains).mapValues { it.value.snapshot }
    override fun save(record: IntegralCacheRecord, accountID: UUID) { values[accountID] = record }
    override fun clear() { values.clear() }
}
