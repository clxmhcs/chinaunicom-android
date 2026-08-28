package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class WidgetSnapshotUnit(val rawValue: String) {
    GIGABYTE("gigabyte"),
    MINUTE("minute"),
}

data class WidgetQuotaSnapshotItem(
    val titleTop: String,
    val titleBottom: String = "",
    val remaining: Double,
    val total: Double,
    val used: Double,
    val unit: WidgetSnapshotUnit,
)

data class WidgetQuotaSnapshot(
    val accountID: UUID?,
    val mobile: String,
    val displayName: String,
    val packageName: String,
    val todayUsageGB: Double,
    val balanceYuan: Double?,
    val updatedAt: Instant,
    val items: List<WidgetQuotaSnapshotItem>,
)

data class WidgetDualDashboardItem(
    val id: String,
    val title: String,
    val kind: WidgetDualSlotKind,
    val remaining: Double?,
    val total: Double?,
    val used: Double?,
    val isUnlimited: Boolean,
) {
    val usedFraction: Double?
        get() {
            if (kind == WidgetDualSlotKind.INTEGRAL || isUnlimited) return null
            val totalValue = total?.takeIf { it.isFinite() && it > 0 } ?: return null
            val usedValue = used?.takeIf(Double::isFinite) ?: return null
            return min(max(usedValue / totalValue, 0.0), 1.0)
        }
}

data class WidgetDualAccountSnapshot(
    val accountID: UUID,
    val mobileSuffix: String,
    val todayUsageGB: Double,
    val balanceYuan: Double?,
    val updatedAt: Instant,
    val items: List<WidgetDualDashboardItem>,
)

data class WidgetDualSnapshot(
    val left: WidgetDualAccountSnapshot?,
    val right: WidgetDualAccountSnapshot?,
    val generatedAt: Instant,
)
