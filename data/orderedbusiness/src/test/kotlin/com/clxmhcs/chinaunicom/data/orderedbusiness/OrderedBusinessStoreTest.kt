package com.clxmhcs.chinaunicom.data.orderedbusiness

import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessItem
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSection
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.settings.CachedBusinessEntryMode
import com.clxmhcs.chinaunicom.data.settings.OrderedBusinessRefreshPolicy
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedBusinessStoreTest {
    private val now = Instant.parse("2026-08-25T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val first = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val second = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val orphan = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun jsonCodecRoundTripsSourceSnapshotShape() {
        val codec = OrderedBusinessSnapshotJsonCodec()
        val input = mapOf(first to snapshot("cached", now.minusSeconds(60)))

        assertEquals(input, codec.decode(codec.encode(input)))
    }

    @Test
    fun cachePreferredLoadsExistingSnapshotWithoutNetwork() = runBlocking {
        val cached = snapshot("cached", now.minusSeconds(24 * 3600))
        val cache = MemoryOrderedBusinessCache(mutableMapOf(first to cached))
        val client = FakeOrderedBusinessRefreshClient()
        val store = store(client, cache, OrderedBusinessRefreshPolicy())

        store.loadCachedOrRefreshIfMissing(account(first, 0))

        assertEquals(cached, store.snapshot(first))
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun refreshWhenExpiredQueriesAfterValidityWindow() = runBlocking {
        val old = snapshot("old", now.minusSeconds(13 * 3600))
        val fresh = snapshot("fresh", now)
        val cache = MemoryOrderedBusinessCache(mutableMapOf(first to old))
        val client = FakeOrderedBusinessRefreshClient().apply { responses[first] = fresh }
        val policy = OrderedBusinessRefreshPolicy(
            entryMode = CachedBusinessEntryMode.REFRESH_WHEN_EXPIRED,
            cacheValidityHours = 12,
        )
        val store = store(client, cache, policy)

        store.loadCachedOrRefreshIfMissing(account(first, 0))

        assertEquals(listOf(first), client.calls)
        assertEquals(fresh, store.snapshot(first))
        assertEquals(OrderedBusinessRefreshState.Idle, store.refreshState(first))
    }

    @Test
    fun networkFailureRetainsPreviousSuccessfulSnapshot() = runBlocking {
        val old = snapshot("old", now.minusSeconds(60))
        val cache = MemoryOrderedBusinessCache(mutableMapOf(first to old))
        val client = FakeOrderedBusinessRefreshClient().apply { failures += first }
        val store = store(client, cache, OrderedBusinessRefreshPolicy())

        store.refresh(first)

        assertEquals(old, store.snapshot(first))
        val state = store.refreshState(first)
        assertTrue(state is OrderedBusinessRefreshState.Failed)
    }

    @Test
    fun successfulNetworkResultSurvivesCacheWriteFailureAsWarning() = runBlocking {
        val fresh = snapshot("fresh", now)
        val cache = MemoryOrderedBusinessCache().apply { failSave = true }
        val client = FakeOrderedBusinessRefreshClient().apply { responses[first] = fresh }
        val store = store(client, cache, OrderedBusinessRefreshPolicy())

        store.refresh(first)

        assertEquals(fresh, store.snapshot(first))
        assertTrue(store.refreshState(first) is OrderedBusinessRefreshState.Warning)
    }

    @Test
    fun reconcileRemovesOrphansRefreshesInOrderAndUsesConfiguredGap() = runBlocking {
        val cache = MemoryOrderedBusinessCache(
            mutableMapOf(
                first to snapshot("old-first", now.minusSeconds(10)),
                orphan to snapshot("orphan", now.minusSeconds(10)),
            ),
        )
        val client = FakeOrderedBusinessRefreshClient().apply {
            responses[first] = snapshot("new-first", now)
            responses[second] = snapshot("new-second", now)
        }
        val sleeps = mutableListOf<Long>()
        val store = store(
            client,
            cache,
            OrderedBusinessRefreshPolicy(
                entryMode = CachedBusinessEntryMode.EVERY_ENTRY,
                refreshAllAccountGapSeconds = 1,
            ),
            sleeper = { sleeps += it },
        )

        store.reconcileAccounts(listOf(first, second))

        assertEquals(listOf(first, second), client.calls)
        assertEquals(listOf(1_000L), sleeps)
        assertTrue(orphan !in store.state.value.snapshots)
        assertEquals(setOf(first, second), store.state.value.snapshots.keys)
    }

    private fun store(
        client: FakeOrderedBusinessRefreshClient,
        cache: MemoryOrderedBusinessCache,
        policy: OrderedBusinessRefreshPolicy,
        sleeper: suspend (Long) -> Unit = {},
    ) = DefaultOrderedBusinessStore(
        client = client,
        cache = cache,
        policyProvider = OrderedBusinessPolicyProvider { policy },
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
        sleeper = sleeper,
    )

    private fun account(id: UUID, order: Int) = UnicomAccount(
        id = id,
        displayName = "A$order",
        mobile = "1380013800$order",
        sortOrder = order,
    )

    private fun snapshot(title: String, at: Instant) = OrderedBusinessSnapshot(
        title = title,
        queryTime = "2026-08-25 20:00:00",
        fetchedAt = at,
        sections = listOf(
            OrderedBusinessSection(
                id = "主套餐",
                title = "主套餐",
                icon = "simcard.fill",
                items = listOf(
                    OrderedBusinessItem("p1", "套餐", null, "99", "2026-01-01", null),
                ),
            ),
        ),
    )
}

private class FakeOrderedBusinessRefreshClient : OrderedBusinessRefreshClient {
    val calls = mutableListOf<UUID>()
    val responses = mutableMapOf<UUID, OrderedBusinessSnapshot>()
    val failures = mutableSetOf<UUID>()

    override suspend fun fetch(accountID: UUID): OrderedBusinessFetchResult {
        calls += accountID
        if (accountID in failures) error("ordered business failed")
        return OrderedBusinessFetchResult(
            responses[accountID] ?: error("No response for $accountID"),
            updatedCredentials = null,
        )
    }
}

private class MemoryOrderedBusinessCache(
    var values: MutableMap<UUID, OrderedBusinessSnapshot> = mutableMapOf(),
) : OrderedBusinessDiskCache {
    var failSave = false
    var saveCount = 0

    override fun load(): Map<UUID, OrderedBusinessSnapshot> = values.toMap()

    override fun save(snapshots: Map<UUID, OrderedBusinessSnapshot>) {
        saveCount += 1
        if (failSave) throw IOException("disk full")
        values = snapshots.toMutableMap()
    }
}
