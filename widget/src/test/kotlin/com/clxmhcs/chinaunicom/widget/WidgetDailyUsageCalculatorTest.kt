package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.DailyUsageBaseline
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.data.refresh.DailyUsageBaselineStore
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDailyUsageCalculatorTest {
    private val accountID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun computesDeltaFromMidnightBaseline() {
        val store = FakeBaselineStore()
        store.baseline = DailyUsageBaseline(
            accountID = accountID,
            dateKey = "2026-08-28",
            capturedAt = Instant.parse("2026-08-27T16:00:00Z"),
            packages = listOf(flow("a", 100.0)),
        )
        val calculator = WidgetDailyUsageCalculator(store, zone)
        val value = calculator.todayUsedMB(
            accountID = accountID,
            packages = listOf(flow("a", 145.0)),
            at = Instant.parse("2026-08-28T04:00:00Z"),
        )
        assertEquals(45.0, value ?: -1.0, 0.0001)
    }

    @Test
    fun ordinaryDayCounterRollbackKeepsLastValidValue() {
        val store = FakeBaselineStore(cached = 23.0)
        store.baseline = DailyUsageBaseline(
            accountID = accountID,
            dateKey = "2026-08-28",
            capturedAt = Instant.parse("2026-08-27T16:00:00Z"),
            packages = listOf(flow("a", 100.0)),
        )
        val calculator = WidgetDailyUsageCalculator(store, zone)
        val value = calculator.todayUsedMB(
            accountID = accountID,
            packages = listOf(flow("a", 90.0)),
            at = Instant.parse("2026-08-28T04:00:00Z"),
        )
        assertEquals(23.0, value ?: -1.0, 0.0001)
    }

    private fun flow(id: String, used: Double) = FlowPackage(
        id = id,
        originalName = "通用流量-$id",
        totalMB = 1000.0,
        usedMB = used,
        remainingMB = 1000.0 - used,
        detectedQuotaType = QuotaType.LIMITED,
        detectedCategory = PackageCategory.GENERAL,
        isShared = false,
    )

    private class FakeBaselineStore(
        var baseline: DailyUsageBaseline? = null,
        var cached: Double? = null,
    ) : DailyUsageBaselineStore {
        override fun load(accountID: UUID, dateKey: String): DailyUsageBaseline? = baseline
        override fun save(value: DailyUsageBaseline): Boolean { baseline = value; return true }
        override fun delete(accountID: UUID, dateKey: String): Boolean { baseline = null; return true }
        override fun deleteAccount(accountID: UUID): Boolean { baseline = null; return true }
        override fun loadTodayUsageMB(accountID: UUID, dateKey: String): Double? = cached
        override fun recordTodayUsageMB(accountID: UUID, dateKey: String, usedMB: Double): Double {
            cached = maxOf(cached ?: 0.0, usedMB)
            return cached ?: usedMB
        }
    }
}
