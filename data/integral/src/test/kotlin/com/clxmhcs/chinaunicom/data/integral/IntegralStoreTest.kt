package com.clxmhcs.chinaunicom.data.integral

import com.clxmhcs.chinaunicom.core.login.IntegralRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshCycleMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshPolicy
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegralStoreTest {
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val account = UnicomAccount(accountID, "主卡", "13800138000")
    private val now = Instant.parse("2026-08-26T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val detailQuery = IntegralDetailQuery("1", "3", null, "奖励积分")

    @Test
    fun validCurrentCycleCacheLoadsWithoutNetwork() = runBlocking {
        val policyProvider = MutableIntegralPolicyProvider(IntegralRefreshPolicy())
        val cachePolicy = IntegralCachePolicy(policyProvider)
        val record = IntegralCacheRecord(snapshot(now.minusSeconds(3600)), emptyMap(), cachePolicy.refreshCycleKey(now))
        val cache = MemoryIntegralCache(mutableMapOf(accountID to record))
        val lifecycle = FakeIntegralLifecycle()
        val store = store(lifecycle, cache, cachePolicy)

        store.loadIfNeeded(account)

        assertTrue(store.state.value.loadState is IntegralLoadState.Loaded)
        assertEquals(1000, store.state.value.snapshot?.totalAvailable)
        assertEquals(0, lifecycle.overviewCalls)
    }

    @Test
    fun noCacheManualOnlyRequiresExplicitRefresh() = runBlocking {
        val policy = IntegralRefreshPolicy(
            automaticRefreshEnabled = true,
            cycleMode = IntegralRefreshCycleMode.MANUAL_ONLY,
            checkOnEntry = true,
        )
        val cachePolicy = IntegralCachePolicy(MutableIntegralPolicyProvider(policy))
        val lifecycle = FakeIntegralLifecycle()
        val store = store(lifecycle, MemoryIntegralCache(), cachePolicy)

        store.loadIfNeeded(account)

        assertTrue(store.state.value.loadState is IntegralLoadState.ManualRequired)
        assertNull(store.state.value.snapshot)
        assertEquals(0, lifecycle.overviewCalls)
    }

    @Test
    fun overviewRefreshClearsOldDetailsAndPersistsCurrentCycle() = runBlocking {
        val policyProvider = MutableIntegralPolicyProvider(IntegralRefreshPolicy())
        val cachePolicy = IntegralCachePolicy(policyProvider)
        val oldRecord = IntegralCacheRecord(
            snapshot = snapshot(now.minusSeconds(40L * 24 * 3600)),
            details = mapOf(detailQuery.cacheKey to listOf(detail("old"))),
            refreshCycleKey = "202607",
        )
        val cache = MemoryIntegralCache(mutableMapOf(accountID to oldRecord))
        val lifecycle = FakeIntegralLifecycle(overview = snapshot(now, total = 2222))
        val store = store(lifecycle, cache, cachePolicy)

        store.loadIfNeeded(account)

        assertEquals(1, lifecycle.overviewCalls)
        assertEquals(2222, store.state.value.snapshot?.totalAvailable)
        assertNull(store.details(detailQuery))
        val saved = cache.records[accountID]
        assertNotNull(saved)
        assertTrue(saved!!.details.isEmpty())
        assertEquals(cachePolicy.refreshCycleKey(now), saved.refreshCycleKey)
    }

    @Test
    fun overviewDiskWriteFailureRetainsPreviousSuccessfulSnapshot() = runBlocking {
        val cachePolicy = IntegralCachePolicy(MutableIntegralPolicyProvider(IntegralRefreshPolicy()))
        val old = IntegralCacheRecord(snapshot(now.minusSeconds(40L * 24 * 3600), total = 1000), emptyMap(), "202607")
        val cache = MemoryIntegralCache(mutableMapOf(accountID to old), failSave = true)
        val lifecycle = FakeIntegralLifecycle(overview = snapshot(now, total = 9999))
        val store = store(lifecycle, cache, cachePolicy)

        store.loadIfNeeded(account)

        assertEquals(1000, store.state.value.snapshot?.totalAvailable)
        assertTrue(store.state.value.loadState is IntegralLoadState.Loaded)
        assertFalse(store.state.value.isRefreshing)
        assertNotNull(store.state.value.errorMessage)
    }

    @Test
    fun detailCacheSuppressesNetworkAndForcedRefreshUpdatesMemoryEvenIfDiskWriteFails() = runBlocking {
        val cachePolicy = IntegralCachePolicy(MutableIntegralPolicyProvider(IntegralRefreshPolicy()))
        val record = IntegralCacheRecord(
            snapshot = snapshot(now.minusSeconds(60)),
            details = mapOf(detailQuery.cacheKey to listOf(detail("cached"))),
            refreshCycleKey = cachePolicy.refreshCycleKey(now),
        )
        val cache = MemoryIntegralCache(mutableMapOf(accountID to record))
        val lifecycle = FakeIntegralLifecycle(details = listOf(detail("fresh")))
        val store = store(lifecycle, cache, cachePolicy)
        store.loadIfNeeded(account)

        store.loadDetails(detailQuery, account, force = false)
        assertEquals(0, lifecycle.detailCalls)
        assertEquals("cached", store.details(detailQuery)?.single()?.scoreValue)

        cache.failSave = true
        store.loadDetails(detailQuery, account, force = true)
        assertEquals(1, lifecycle.detailCalls)
        assertEquals("fresh", store.details(detailQuery)?.single()?.scoreValue)
        assertNotNull(store.state.value.errorMessage)
        assertNull(store.state.value.loadingDetailKey)
    }

    @Test
    fun cachePolicyPreservesMonthlyBoundaryFixedIntervalAndClockRollback() {
        val policyProvider = MutableIntegralPolicyProvider(IntegralRefreshPolicy())
        val cachePolicy = IntegralCachePolicy(policyProvider)
        val beforeBoundary = Instant.parse("2026-08-01T23:59:59Z") // 2026-08-02 07:59:59 Asia/Shanghai
        val atBoundary = Instant.parse("2026-08-02T00:00:00Z")
        assertEquals("202607", cachePolicy.refreshCycleKey(beforeBoundary))
        assertEquals("202608", cachePolicy.refreshCycleKey(atBoundary))

        policyProvider.policy = IntegralRefreshPolicy(
            cycleMode = IntegralRefreshCycleMode.FIXED_INTERVAL,
            fixedIntervalHours = 24,
        )
        val record = IntegralCacheRecord(snapshot(now), emptyMap(), "fixed-24h")
        assertFalse(cachePolicy.needsAutomaticRefresh(record, now.plusSeconds(23 * 3600)))
        assertTrue(cachePolicy.needsAutomaticRefresh(record, now.plusSeconds(24 * 3600)))
        assertTrue(cachePolicy.needsAutomaticRefresh(record, now.minusSeconds(1)))
    }

    private fun store(
        lifecycle: FakeIntegralLifecycle,
        cache: MemoryIntegralCache,
        policy: IntegralCachePolicy,
    ) = DefaultIntegralStore(
        lifecycle = lifecycle,
        cache = cache,
        cachePolicy = policy,
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun snapshot(at: Instant, total: Int = 1000) = IntegralSnapshot(
        totalAvailable = total,
        communication = 200,
        reward = 300,
        directional = null,
        expiredAndExpiringReward = 0,
        expiringThisMonth = 10,
        expiringCommunication = 0,
        expiringReward = 10,
        expirationDay = null,
        couponCount = 0,
        provinceCode = "11",
        packageID = "pkg",
        isUnicom = "1",
        months = emptyList(),
        fetchedAt = at,
        parserVersion = IntegralSnapshot.CURRENT_PARSER_VERSION,
    )

    private fun detail(value: String) = IntegralDetailItem(
        typeChar = "3",
        scoreType = "1",
        title = "奖励积分",
        scoreValue = value,
        createTime = null,
        returnTime = null,
        endTime = null,
        orderTime = null,
        channelName = null,
        expireTime = null,
        expireTag = null,
    )
}

private class MutableIntegralPolicyProvider(var policy: IntegralRefreshPolicy) : IntegralRefreshPolicyProvider {
    override fun current(): IntegralRefreshPolicy = policy
}

private class MemoryIntegralCache(
    val records: MutableMap<UUID, IntegralCacheRecord> = mutableMapOf(),
    var failSave: Boolean = false,
) : IntegralDiskCache {
    override fun load(accountID: UUID): IntegralCacheRecord? = records[accountID]
    override fun snapshots(accountIDs: Collection<UUID>): Map<UUID, IntegralSnapshot> =
        records.filterKeys { it in accountIDs }.mapValues { it.value.snapshot }

    override fun save(record: IntegralCacheRecord, accountID: UUID) {
        if (failSave) throw IOException("disk-full")
        records[accountID] = record
    }

    override fun clear() { records.clear() }
}

private class FakeIntegralLifecycle(
    private val overview: IntegralSnapshot = IntegralSnapshot(
        totalAvailable = 1000,
        communication = 200,
        reward = 300,
        directional = null,
        expiredAndExpiringReward = 0,
        expiringThisMonth = 10,
        expiringCommunication = 0,
        expiringReward = 10,
        expirationDay = null,
        couponCount = 0,
        provinceCode = null,
        packageID = null,
        isUnicom = null,
        months = emptyList(),
        fetchedAt = Instant.parse("2026-08-26T03:00:00Z"),
        parserVersion = IntegralSnapshot.CURRENT_PARSER_VERSION,
    ),
    private val details: List<IntegralDetailItem> = emptyList(),
) : IntegralRequestLifecycle {
    var overviewCalls = 0
    var detailCalls = 0

    override fun hasCredentials(accountID: UUID): Boolean = true

    override fun fetchOverviewValidated(
        accountID: UUID,
        mobile: String,
        fetchedAt: Instant,
    ): IntegralFetchResult {
        overviewCalls += 1
        return IntegralFetchResult(overview.copy(fetchedAt = fetchedAt), null)
    }

    override fun fetchDetailsValidated(
        accountID: UUID,
        mobile: String,
        query: IntegralDetailQuery,
    ): IntegralDetailsFetchResult {
        detailCalls += 1
        return IntegralDetailsFetchResult(details, null)
    }
}
