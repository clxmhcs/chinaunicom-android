package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.refresh.AndroidDailyUsageBaselineStore
import com.clxmhcs.chinaunicom.data.refresh.DailyUsageBaselineStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/** Source-derived one traffic calculation path shared by both Android Widget layouts. */
class WidgetDailyUsageCalculator(
    private val baselineStore: DailyUsageBaselineStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun todayUsedMB(
        accountID: UUID,
        packages: List<FlowPackage>,
        displayPreferences: List<PackageDisplayPreference> = emptyList(),
        summaryGroups: List<FlowSummaryGroup>? = null,
        at: Instant,
    ): Double? {
        val date = at.atZone(zoneId).toLocalDate()
        val dateKey = date.toString()
        val baseline = baselineStore.load(accountID, dateKey) ?: return null
        val cached = baselineStore.loadTodayUsageMB(accountID, dateKey)
        if (at.isBefore(baseline.capturedAt.minusSeconds(TIMESTAMP_TOLERANCE_SECONDS))) return cached

        val currentPackages = scopedPackages(packages, displayPreferences, summaryGroups)
        val baselinePackages = scopedPackages(baseline.packages, displayPreferences, summaryGroups)
        val calculated = resilientDeltaMB(currentPackages, baselinePackages, date) ?: return cached
        return baselineStore.recordTodayUsageMB(accountID, dateKey, calculated)
    }

    private fun resilientDeltaMB(
        currentPackages: List<FlowPackage>,
        baselinePackages: List<FlowPackage>,
        date: LocalDate,
    ): Double? {
        val current = packageMap(currentPackages)
        val baseline = packageMap(baselinePackages)
        if (current.isEmpty() && baseline.isEmpty()) return 0.0
        if (current.isEmpty() || baseline.isEmpty()) return null

        val sharedKeys = current.keys intersect baseline.keys
        val monthStart = date.dayOfMonth == 1
        if (sharedKeys.isEmpty()) {
            if (!monthStart) return null
            return max(0.0, current.values.sumOf(::finiteUsedMB))
        }

        var total = 0.0
        var sawMonthStartRollback = false
        for (key in sharedKeys) {
            val currentUsed = finiteUsedMB(current.getValue(key))
            val baselineUsed = finiteUsedMB(baseline.getValue(key))
            if (currentUsed + COMPARISON_TOLERANCE_MB >= baselineUsed) {
                total += max(0.0, currentUsed - baselineUsed)
            } else {
                if (!monthStart) return null
                total += currentUsed
                sawMonthStartRollback = true
            }
        }

        if (monthStart && sawMonthStartRollback) {
            for (key in current.keys - baseline.keys) total += finiteUsedMB(current.getValue(key))
        }
        return total.takeIf(Double::isFinite)?.let { max(0.0, it) }
    }

    private fun scopedPackages(
        packages: List<FlowPackage>,
        displayPreferences: List<PackageDisplayPreference>,
        summaryGroups: List<FlowSummaryGroup>?,
    ): List<FlowPackage> {
        val flowPackages = packages.filter { it.remainingMB != null || it.totalMB != null || it.usedMB != null }
        val account = UnicomAccount(
            displayName = "",
            mobile = "",
            packages = flowPackages,
            displayPreferences = displayPreferences,
            summaryGroups = summaryGroups,
        )
        val groups = account.visibleSummaryGroups
        if (groups.isNotEmpty()) {
            val selectedIDs = groups.flatMap { it.packageKeys }.toSet()
            val selected = account.visibleDetailPackages.filter { it.id in selectedIDs }
            if (selected.isNotEmpty()) return selected
        }
        val preferred = flowPackages.filter(::isPrimaryTrafficPackage)
        return preferred.ifEmpty { flowPackages }
    }

    private fun packageMap(packages: List<FlowPackage>): Map<String, FlowPackage> = buildMap {
        packages.forEach { item ->
            val key = packageIdentity(item)
            val existing = get(key)
            if (existing == null || finiteUsedMB(item) > finiteUsedMB(existing)) put(key, item)
        }
    }

    private fun packageIdentity(item: FlowPackage): String {
        val id = normalizedToken(item.id)
        if (id.isNotEmpty()) return "id:$id"
        val rawCode = normalizedToken(item.rawCode)
        val rawType = normalizedToken(item.rawType)
        if (rawCode.isNotEmpty()) return "raw:$rawType|$rawCode"
        return "name:${normalizedToken(item.originalName)}|$rawType"
    }

    private fun finiteUsedMB(item: FlowPackage): Double = item.usedMB?.takeIf(Double::isFinite)?.let { max(0.0, it) } ?: 0.0
    private fun normalizedToken(value: String?): String = value.orEmpty().trim().lowercase(Locale.ROOT)

    private fun isPrimaryTrafficPackage(item: FlowPackage): Boolean {
        if (item.detectedCategory == PackageCategory.DIRECTED) return false
        val name = item.originalName
        return listOf("云盘", "定向", "免流", "专属", "权益").none { name.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val TIMESTAMP_TOLERANCE_SECONDS = 2L
        private const val COMPARISON_TOLERANCE_MB = 0.0001

        fun android(context: android.content.Context): WidgetDailyUsageCalculator = WidgetDailyUsageCalculator(
            AndroidDailyUsageBaselineStore(context.applicationContext),
        )
    }
}
