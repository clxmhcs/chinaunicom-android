package com.clxmhcs.chinaunicom.core.parser

import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowPackageDisplayFormatterTest {

    @Test
    fun limitedQuotaUsesIndependentRemainingAndIosFlowFormatter() {
        val packageValue = flowPackage(
            total = 2048.0,
            used = 1024.0,
            remaining = 777.25,
        )
        val account = UnicomAccount(
            displayName = "测试账户",
            mobile = "13000000000",
            packages = listOf(packageValue),
        )

        val display = flowPackageDisplayText(account, packageValue, DisplayUnit.AUTOMATIC)

        assertEquals("测试流量", display.title)
        assertEquals("剩余 777.25 MB", display.remainingText)
        assertEquals("已用 1 GB / 总量 2 GB", display.detailText)
        assertEquals(0.5, display.progress!!, 0.0)
    }

    @Test
    fun nilAndNegativeValuesDoNotCollapseIntoInventedQuota() {
        val packageValue = flowPackage(
            total = null,
            used = -5.0,
            remaining = null,
        )
        val account = UnicomAccount(
            displayName = "测试账户",
            mobile = "13000000000",
            packages = listOf(packageValue),
        )

        val display = flowPackageDisplayText(account, packageValue)

        assertEquals("剩余 --", display.remainingText)
        assertEquals("已用 0 MB / 总量 --", display.detailText)
        assertNull(display.progress)
    }

    @Test
    fun unlimitedQuotaNeverInventsRemainingOrTotal() {
        val packageValue = flowPackage(
            total = null,
            used = 1536.0,
            remaining = null,
            quotaType = QuotaType.UNLIMITED,
        )
        val account = UnicomAccount(
            displayName = "测试账户",
            mobile = "13000000000",
            packages = listOf(packageValue),
        )

        val display = flowPackageDisplayText(account, packageValue)

        assertEquals("不限量", display.remainingText)
        assertEquals("已用 1.50 GB", display.detailText)
        assertNull(display.progress)
    }

    @Test
    fun accountDisplayPreferenceRemainsAuthoritativeForTitleAndQuotaOverride() {
        val packageValue = flowPackage(
            total = 1024.0,
            used = 512.0,
            remaining = 512.0,
        )
        val account = UnicomAccount(
            displayName = "测试账户",
            mobile = "13000000000",
            packages = listOf(packageValue),
            displayPreferences = listOf(
                PackageDisplayPreference(
                    packageKey = packageValue.id,
                    alias = "自定义名称",
                    quotaTypeOverride = QuotaType.UNLIMITED,
                ),
            ),
        )

        val display = flowPackageDisplayText(account, packageValue)

        assertEquals("自定义名称", display.title)
        assertEquals("不限量", display.remainingText)
        assertEquals("已用 512 MB", display.detailText)
        assertNull(display.progress)
    }

    @Test
    fun automaticUnitUsesRawMbThresholdBeforeRounding() {
        val formatter = FlowFormatter(DisplayUnit.AUTOMATIC)

        assertEquals("1024 MB", formatter.string(1023.999))
        assertEquals("1 GB", formatter.string(1024.0))
        assertEquals("--", formatter.string(Double.NaN))
        assertEquals("--", formatter.string(Double.POSITIVE_INFINITY))
    }

    private fun flowPackage(
        total: Double?,
        used: Double?,
        remaining: Double?,
        quotaType: QuotaType = QuotaType.LIMITED,
    ): FlowPackage = FlowPackage(
        id = "flow-1",
        originalName = "测试流量",
        totalMB = total,
        usedMB = used,
        remainingMB = remaining,
        detectedQuotaType = quotaType,
        detectedCategory = PackageCategory.GENERAL,
        isShared = false,
    )
}
