package com.clxmhcs.chinaunicom.data.tariffzone

import com.clxmhcs.chinaunicom.core.login.TariffZoneRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetail
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneFirstLevel
import com.clxmhcs.chinaunicom.core.model.TariffZoneIndex
import com.clxmhcs.chinaunicom.core.model.TariffZoneIndexFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneProductReference
import com.clxmhcs.chinaunicom.core.model.TariffZoneReferencesFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.model.TariffZoneSecondLevel
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TariffZoneStoreTest {
    @Test
    fun loadDefaultsToLocalDetectedRegionAndFiveItemBatchThenLoadsMore() = runBlocking {
        val account = account()
        val region = TariffZoneRegion("011", "110", "北京", "北京")
        val references = (1..7).map { TariffZoneProductReference("P$it", "资费$it") }
        val lifecycle = FakeLifecycle(
            region = region,
            levels = listOf(TariffZoneFirstLevel("1", "套餐", listOf(TariffZoneSecondLevel("1001", "移网")))),
            references = { _, _, _ -> references },
        )
        val store = DefaultTariffZoneStore(lifecycle, now = { Instant.parse("2026-08-27T12:00:00Z") })

        store.load(account)

        val first = store.state.value
        assertEquals(TariffZoneScope.LOCAL, first.scope)
        assertEquals(region, first.selectedRegion)
        assertEquals("1", first.selectedFirstLevelID)
        assertEquals("1001", first.selectedSecondLevelID)
        assertEquals(5, first.details.size)
        assertTrue(first.hasMore)
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5"), lifecycle.detailBatches.first())

        store.loadMore(account)

        val second = store.state.value
        assertEquals(7, second.details.size)
        assertFalse(second.hasMore)
        assertEquals(listOf("P6", "P7"), lifecycle.detailBatches.last())
    }

    @Test
    fun searchBuildsAllCategoryIndexAndMatchesNameOrPlanID() = runBlocking {
        val account = account()
        val region = TariffZoneRegion("011", "110", "北京", "北京")
        val levels = listOf(
            TariffZoneFirstLevel("1", "套餐", listOf(TariffZoneSecondLevel("1001", "移网"))),
            TariffZoneFirstLevel("2", "加装包", listOf(TariffZoneSecondLevel("2001", "流量包"))),
        )
        val lifecycle = FakeLifecycle(
            region = region,
            levels = levels,
            references = { first, second, _ ->
                when (first to second) {
                    "1" to "1001" -> listOf(TariffZoneProductReference("PLAN-A", "冰激凌套餐"))
                    "2" to "2001" -> listOf(TariffZoneProductReference("FLOW-88", "暑期流量包"))
                    else -> emptyList()
                }
            },
        )
        val store = DefaultTariffZoneStore(lifecycle)
        store.load(account)

        store.search(account, "FLOW-88")
        assertEquals(1, store.state.value.searchResults.size)
        assertEquals("暑期流量包", store.state.value.searchResults.single().name)

        store.search(account, "冰激凌")
        assertEquals(1, store.state.value.searchResults.size)
        assertEquals("PLAN-A", store.state.value.searchResults.single().reference.id)
    }

    @Test
    fun missingCredentialFailsWithoutCarrierRequest() = runBlocking {
        val account = account()
        val lifecycle = FakeLifecycle(hasCredentialsValue = false)
        val store = DefaultTariffZoneStore(lifecycle)

        store.load(account)

        assertEquals("当前号码缺少可用凭据", store.state.value.errorMessage)
        assertEquals(0, lifecycle.indexCalls)
    }

    private fun account(): UnicomAccount = UnicomAccount(
        id = UUID.fromString("00000000-0000-0000-0000-000000000100"),
        displayName = "测试号码",
        mobile = "18600001234",
    )

    private class FakeLifecycle(
        private val hasCredentialsValue: Boolean = true,
        private val region: TariffZoneRegion = TariffZoneRegion("011", "110", "北京", "北京"),
        private val levels: List<TariffZoneFirstLevel> = TariffZoneFirstLevel.FALLBACK,
        private val references: (String, String, TariffZoneScope) -> List<TariffZoneProductReference> = { _, _, _ -> emptyList() },
    ) : TariffZoneRequestLifecycle {
        var indexCalls = 0
        val detailBatches = mutableListOf<List<String>>()

        override fun hasCredentials(accountID: UUID): Boolean = hasCredentialsValue

        override fun fetchIndexValidated(accountID: UUID): TariffZoneIndexFetchResult {
            indexCalls += 1
            return TariffZoneIndexFetchResult(
                index = TariffZoneIndex(
                    regions = listOf(region),
                    levels = levels,
                    userProvinceCode = region.provinceCode,
                    userCityCode = region.cityCode,
                ),
                updatedCredentials = null,
            )
        }

        override fun fetchProductReferencesValidated(
            accountID: UUID,
            scope: TariffZoneScope,
            firstLevel: String,
            secondLevel: String,
            region: TariffZoneRegion,
        ): TariffZoneReferencesFetchResult = TariffZoneReferencesFetchResult(
            references = references(firstLevel, secondLevel, scope),
            updatedCredentials = null,
        )

        override fun fetchDetailsValidated(
            accountID: UUID,
            references: List<TariffZoneProductReference>,
            page: Int,
            region: TariffZoneRegion,
        ): TariffZoneDetailsFetchResult {
            detailBatches += references.map { it.id }
            return TariffZoneDetailsFetchResult(
                details = references.map { reference -> detail(reference) },
                timeText = "2026/08/27 20:00",
                updatedCredentials = null,
            )
        }

        private fun detail(reference: TariffZoneProductReference) = TariffZoneDetail(
            id = reference.id,
            reportNo = reference.id,
            name = reference.name,
            codeType = "",
            feesStandard = "",
            feeUnit = "",
            otherFees = "",
            extraFees = "",
            minute = "",
            commonData = "",
            dataUnit = "",
            sms = "",
            orientTraffic = "",
            orientTrafficUnit = "",
            iptv = "",
            broadBand = "",
            equityCoupon = "",
            serviceContent = "",
            useScope = "",
            validPeriod = "",
            onlinePeriod = "",
            saleChnl = "",
            unsubscribe = "",
            startDate = "",
            endDate = "",
            contractDuty = "",
            otherDesc = "",
        )
    }
}
