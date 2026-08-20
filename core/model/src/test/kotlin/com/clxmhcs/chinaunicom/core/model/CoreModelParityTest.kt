package com.clxmhcs.chinaunicom.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelParityTest {
    @Test
    fun flowPackageMatchesIosFractionRules() {
        val limited = FlowPackage(
            id = "flow-1",
            originalName = "国内流量",
            totalMB = 100.0,
            usedMB = null,
            remainingMB = 25.0,
            detectedQuotaType = QuotaType.LIMITED,
            detectedCategory = PackageCategory.GENERAL,
            isShared = false,
        )
        assertEquals(0.75, limited.usedFraction ?: -1.0, 0.000001)

        val unlimited = limited.copy(detectedQuotaType = QuotaType.UNLIMITED, usedMB = 20_000.0)
        assertNull(unlimited.usedFraction)
        assertTrue((unlimited.detailDisplayFraction(QuotaType.UNLIMITED) ?: 0.0) in 0.0..1.0)
    }

    @Test
    fun accountSummaryGroupingPreservesIosKeywordRules() {
        val domestic = flow("a", "国内通用流量", 100.0, 20.0, 80.0)
        val province = flow("b", "省内流量", 50.0, 10.0, 40.0)
        val directed = flow("c", "视频定向免流", 30.0, 5.0, 25.0)
        val account = UnicomAccount(displayName = "A", mobile = "13000000000", packages = listOf(domestic, province, directed))

        assertEquals(listOf("国内流量", "省内流量", "定向流量"), account.automaticSummaryGroups.map { it.name })
        assertEquals("a", account.primaryPackage?.id)
    }

    @Test
    fun forcedResourceKindMatchesIosCrossConversion() {
        val flow = flow("same", "套餐A", 120.0, 20.0, 100.0)
        val preference = PackageDisplayPreference(packageKey = "same", resourceKindOverride = ResourceDisplayKind.VOICE)
        val account = UnicomAccount(
            displayName = "A",
            mobile = "13000000000",
            packages = listOf(flow),
            displayPreferences = listOf(preference),
        )

        assertTrue(account.sortedPackages.isEmpty())
        assertEquals(1, account.resolvedVoicePackages.size)
        assertEquals(120.0, account.resolvedVoicePackages.single().totalMinutes ?: -1.0, 0.000001)
    }

    @Test
    fun billAndIntegralComputedFieldsMatchIosModels() {
        val month = BillMonth(year = "2026", month = "8")
        assertEquals("202608", month.key)
        assertEquals("8月", month.title)
        assertEquals("2026", month.subtitle)

        val integralMonth = IntegralMonthSummary("2026年08月", 1, 2, 3)
        assertEquals("202608", integralMonth.yearMonth)
        assertEquals("2-3-202608", IntegralDetailQuery.month(integralMonth, "3", "新增")?.cacheKey)
    }

    @Test
    fun orderClassificationMatchesIosSourceCodeRules() {
        val member = MyOrderMember(
            id = "m1",
            goodsName = " 5G 套餐 ",
            price = "订购",
            integral = "10|30|20",
            goodsID = null,
            productPicture = null,
            tradeTags = emptyList(),
        )
        val order = order(sourceCode = "D15", members = listOf(member))
        assertEquals(MyOrderKind.VOICE_AND_DATA, order.kind)
        assertEquals("订购", order.operationType)
        assertNull(order.displayPrice)
        assertEquals(30, order.displayPoints)
        assertEquals("5G 套餐", order.primaryTitle)
    }

    @Test
    fun defaultsAndSafeValuesMatchIosModels() {
        assertEquals(AppSettings(), AppSettings())
        val remaining = RemainingFlowPackage(
            id = "r1",
            name = "流量",
            category = null,
            totalMB = null,
            usedMB = -1.0,
            remainingMB = -2.0,
            isShared = false,
            memberUsages = emptyList(),
            endDateText = null,
            feePolicyID = null,
            rawType = null,
            rawCode = null,
            isUnlimited = null,
            speedLimitMB = null,
        )
        assertEquals(0.0, remaining.safeUsedMB, 0.0)
        assertEquals(0.0, remaining.safeRemainingMB, 0.0)
        assertFalse(remaining.resolvedIsUnlimited)
    }

    private fun flow(id: String, name: String, total: Double, used: Double, remaining: Double) = FlowPackage(
        id = id,
        originalName = name,
        totalMB = total,
        usedMB = used,
        remainingMB = remaining,
        detectedQuotaType = QuotaType.LIMITED,
        detectedCategory = PackageCategory.GENERAL,
        isShared = false,
    )

    private fun order(sourceCode: String?, members: List<MyOrderMember>) = MyOrder(
        id = "o1",
        orderID = "order-1",
        encodedOrderID = null,
        sourceCode = sourceCode,
        sourceName = null,
        statusCode = null,
        statusName = "已完成",
        nodeCode = null,
        nodeName = null,
        createdAtText = "2026-08-20",
        channelName = null,
        phoneNumber = "13012345678",
        maskedContactNumber = null,
        accountNumber = null,
        address = null,
        goodsName = null,
        tradeType = null,
        sceneType = null,
        originNodeName = null,
        members = members,
        actions = emptyList(),
        tradeTags = emptyList(),
    )
}
