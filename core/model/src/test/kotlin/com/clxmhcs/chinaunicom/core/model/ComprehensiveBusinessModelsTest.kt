package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComprehensiveBusinessModelsTest {
    @Test
    fun orderedBusinessTotalCountMatchesSourceDerivedSections() {
        val snapshot = OrderedBusinessSnapshot(
            title = "套餐",
            queryTime = "2026-08-25 19:00:00",
            fetchedAt = Instant.parse("2026-08-25T11:00:00Z"),
            sections = listOf(
                OrderedBusinessSection("main", "主套餐", "simcard.fill", listOf(item("1"))),
                OrderedBusinessSection("other", "其他已订产品", "shippingbox.fill", listOf(item("2"), item("3"))),
            ),
        )

        assertEquals(3, snapshot.totalCount)
    }

    @Test
    fun billMonthKeyTitleAndFlattenedItemsMatchIosModelSemantics() {
        val month = BillMonth(year = "2026", month = "8")
        assertEquals("202608", month.key)
        assertEquals("8月", month.title)
        assertEquals("2026", month.subtitle)

        val first = BillItem("a", "基础费", null, "10", "1", "9")
        val second = BillItem("b", "增值费", "X", "5", "0", "5")
        val bill = UserBill(
            id = "u",
            mobile = "13000000000",
            virtualUserTag = null,
            payable = "14",
            sections = listOf(BillItemSection("s", "费用", listOf(first, second))),
            totalPrice = null,
            totalDiscount = null,
            totalRealFee = null,
            totalAdjustAfter = null,
            totalAcctDiscnt = null,
            totalLateFee = null,
            allRebates = null,
            realPayFeeP = null,
        )
        assertEquals(listOf(first, second), bill.allItems)
        assertEquals(PhoneBillSnapshot.CURRENT_PARSER_VERSION, 4)
    }

    @Test
    fun integralMonthAndQueriesPreserveIosCacheKeys() {
        val month = IntegralMonthSummary("2026年08月", addScore = 10, consumedScore = 3, expiredScore = 1)
        assertEquals("202608", month.yearMonth)

        val query = IntegralDetailQuery.month(month, typeChar = "1", title = "新增积分")!!
        assertEquals("2-1-202608", query.cacheKey)
        assertEquals("2026年08月 · 新增积分", query.title)

        assertNull(IntegralSection.AVAILABLE.detailQuery)
        assertEquals("0-3-all", IntegralSection.COMMUNICATION.detailQuery!!.cacheKey)
        assertEquals("1-3-all", IntegralSection.REWARD.detailQuery!!.cacheKey)
        assertEquals("2-2-all", IntegralSection.EXPIRING.detailQuery!!.cacheKey)
    }

    @Test
    fun integralDetailStableIdentityIncludesAllSourceFields() {
        val item = IntegralDetailItem(
            typeChar = "3",
            scoreType = "0",
            title = "通信积分",
            scoreValue = "+10",
            createTime = "2026-08-01",
            returnTime = null,
            endTime = null,
            orderTime = null,
            channelName = "APP",
            expireTime = null,
            expireTag = null,
        )

        assertEquals("3|0|通信积分|+10|2026-08-01||||APP||", item.id)
    }

    private fun item(id: String) = OrderedBusinessItem(
        id = id,
        name = "业务$id",
        subtitle = null,
        fee = null,
        startDate = null,
        endDate = null,
    )
}
