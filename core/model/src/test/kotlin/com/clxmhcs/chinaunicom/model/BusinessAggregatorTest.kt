package com.clxmhcs.chinaunicom.model

import com.clxmhcs.chinaunicom.core.model.CarryForwardScope
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.ShareScope
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BusinessAggregatorTest {

    @Test
    fun aggregatePreservesAuthoritativeAccountWithoutLossyProjection() {
        val packageValue = FlowPackage(
            id = "flow-1",
            originalName = "测试共享结转流量",
            totalMB = 1024.5,
            usedMB = 123.25,
            remainingMB = 901.25,
            detectedQuotaType = QuotaType.LIMITED,
            detectedCategory = PackageCategory.GENERAL,
            isShared = true,
            shareScope = ShareScope.SHARED,
            carryForwardScope = CarryForwardScope.INCLUDED,
            currentMonthTotalMB = 800.25,
            carryForwardTotalMB = 224.25,
            endDateText = "2026-09-30",
            rawType = "mock-type",
            rawCode = "mock-code",
        )
        val updatedAt = Instant.parse("2026-08-22T00:00:00Z")
        val account = UnicomAccount(
            displayName = "测试账户",
            mobile = "18600009025",
            packageName = "测试套餐",
            packages = listOf(packageValue),
            balanceYuan = 12.34,
            lastUpdatedAt = updatedAt,
        )

        val overview = BusinessAggregator.aggregate(account)

        assertSame(account, overview.accounts.single())
        assertSame(packageValue, overview.accounts.single().packages.single())
        assertEquals(1024.5, overview.accounts.single().packages.single().totalMB!!, 0.0)
        assertEquals(901.25, overview.accounts.single().packages.single().remainingMB!!, 0.0)
        assertEquals(ShareScope.SHARED, overview.accounts.single().packages.single().shareScope)
        assertEquals(CarryForwardScope.INCLUDED, overview.accounts.single().packages.single().carryForwardScope)
        assertEquals(updatedAt.toEpochMilli(), overview.updatedAt)
    }

    @Test
    fun aggregateAccountsProducesOneOverviewContainingAllDomainAccounts() {
        val first = UnicomAccount(displayName = "A", mobile = "13000000001")
        val second = UnicomAccount(displayName = "B", mobile = "13000000002")

        val overview = BusinessAggregator.aggregateAccounts(listOf(first, second), updatedAt = 123L)

        assertEquals(listOf(first, second), overview.accounts)
        assertEquals(123L, overview.updatedAt)
    }
}
