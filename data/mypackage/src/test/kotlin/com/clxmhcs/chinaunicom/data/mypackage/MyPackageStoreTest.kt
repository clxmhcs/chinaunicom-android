package com.clxmhcs.chinaunicom.data.mypackage

import com.clxmhcs.chinaunicom.core.model.MyPackageFetchResult
import com.clxmhcs.chinaunicom.core.model.MyPackageSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.settings.MyPackageRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.PageEntryRefreshMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MyPackageStoreTest {
    @Test
    fun everyEntryQueriesEveryTimeAndKeepsAccountScopedCache() = runBlocking {
        val account = account()
        var calls = 0
        val cache = FakeCache()
        val store = DefaultMyPackageStore(
            client = MyPackageRefreshClient {
                calls += 1
                MyPackageFetchResult(snapshot("套餐$calls"), null)
            },
            cache = cache,
            policyProvider = MyPackagePolicyProvider { MyPackageRefreshPolicy(PageEntryRefreshMode.EVERY_ENTRY, 30) },
            clock = fixedClock(),
        )

        store.load(account)
        store.load(account)

        assertEquals(2, calls)
        assertEquals("套餐2", store.state.value.snapshot?.productName)
        assertEquals(2, cache.saveCount)
        assertNotNull(cache.records[account.id])
    }

    @Test
    fun manualOnlyRestoresCacheWithoutNetwork() = runBlocking {
        val account = account()
        val cache = FakeCache().apply {
            records[account.id] = MyPackageCacheRecord(snapshot = snapshot("缓存套餐"), fetchedAt = Instant.parse("2026-08-27T00:00:00Z"))
        }
        var calls = 0
        val store = DefaultMyPackageStore(
            client = MyPackageRefreshClient {
                calls += 1
                MyPackageFetchResult(snapshot("网络套餐"), null)
            },
            cache = cache,
            policyProvider = MyPackagePolicyProvider { MyPackageRefreshPolicy(PageEntryRefreshMode.MANUAL_ONLY, 30) },
            clock = fixedClock(),
        )

        store.load(account)

        assertEquals(0, calls)
        assertEquals("缓存套餐", store.state.value.snapshot?.productName)
        assertEquals(MyPackageRefreshState.Idle, store.state.value.refreshState)
    }

    @Test
    fun cacheCodecRoundTripsSnapshotAndTimestamp() {
        val id = UUID.randomUUID()
        val record = MyPackageCacheRecord(snapshot = snapshot("测试套餐"), fetchedAt = Instant.parse("2026-08-27T00:00:00Z"))
        val codec = MyPackageCacheJsonCodec()

        val decoded = codec.decode(codec.encode(mapOf(id to record)))

        assertEquals("测试套餐", decoded[id]?.snapshot?.productName)
        assertEquals(record.fetchedAt, decoded[id]?.fetchedAt)
        assertEquals(true, decoded[id]?.isCompatible)
    }

    private class FakeCache : MyPackageDiskCache {
        val records = mutableMapOf<UUID, MyPackageCacheRecord>()
        var saveCount = 0
        override fun load(accountID: UUID): MyPackageCacheRecord? = records[accountID]
        override fun save(record: MyPackageCacheRecord, accountID: UUID) {
            saveCount += 1
            records[accountID] = record
        }
    }

    private fun account() = UnicomAccount(
        id = UUID.randomUUID(),
        displayName = "联通号码",
        mobile = "18600000000",
    )

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-08-27T00:10:00Z"), ZoneOffset.UTC)

    private fun snapshot(name: String) = MyPackageSnapshot(
        productName = name,
        productStartDate = "",
        packageResourceType = "1",
        monthFee = "38",
        packageDescription = "",
        businessRules = "",
        monthFeeDescription = "",
        contractTips = "",
        cannotCancelPrompt = "",
        promotionURL = null,
        promotionImageURL = null,
        promotionText = "",
        activities = emptyList(),
        mobileRules = emptyList(),
        broadbandResources = emptyList(),
        broadbandTips = "",
        memberGroups = emptyList(),
        isPrettyNumber = false,
    )
}
