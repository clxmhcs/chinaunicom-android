package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.FlowSummary
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import com.clxmhcs.chinaunicom.core.model.WidgetQuotaResourceKind
import com.clxmhcs.chinaunicom.core.model.WidgetQuotaSlotConfiguration
import java.util.Locale
import kotlin.math.max

/** Target-neutral source-derived mapping from account models to Widget snapshot items. */
object WidgetSnapshotBuilder {
    fun makeSingleItems(
        account: UnicomAccount,
        configuration: WidgetDisplayConfiguration,
    ): List<WidgetQuotaSnapshotItem> {
        val configured = configuration.slots.filter { it.isVisible }.take(6).map { makeSingleItem(it, account) }
        return configured.ifEmpty { WidgetQuotaSlotConfiguration.defaultSlots.map { makeSingleItem(it, account) } }
    }

    fun makeDualItems(
        account: UnicomAccount,
        slots: List<WidgetDualSlotConfiguration>,
    ): List<WidgetDualDashboardItem> = slots.take(WidgetDualSlotConfiguration.SLOT_COUNT).map { slot ->
        if (!slot.isVisible) emptyDualItem(slot) else makeDualItem(slot, account)
    }

    fun maskedMobile(mobile: String): String {
        val digits = mobile.filter(Char::isDigit)
        return if (digits.length >= 7) digits.take(3) + "****" + digits.takeLast(4) else mobile
    }

    fun mobileSuffix(mobile: String): String {
        val digits = mobile.filter(Char::isDigit)
        return if (digits.length >= 4) digits.takeLast(4) else mobile
    }

    private fun makeSingleItem(slot: WidgetQuotaSlotConfiguration, account: UnicomAccount): WidgetQuotaSnapshotItem =
        when (slot.kind) {
            WidgetQuotaResourceKind.FLOW -> {
                matchingSummary(slot.displayTitle, account)?.let { return makeSingleFlowItem(slot.displayTitle, it) }
                if (slot.packageIDs.isNotEmpty()) {
                    makeSingleFlowItem(slot.displayTitle, account.packages.filter { it.id in slot.packageIDs })
                } else {
                    makeAutomaticSingleFlowItem(slot, account)
                }
            }
            WidgetQuotaResourceKind.VOICE -> {
                val packages = account.resolvedVoicePackages
                if (slot.packageIDs.isNotEmpty()) {
                    makeSingleVoiceItem(slot.displayTitle, packages.filter { it.id in slot.packageIDs })
                } else {
                    makeAutomaticSingleVoiceItem(slot, packages)
                }
            }
        }

    private fun makeAutomaticSingleFlowItem(
        slot: WidgetQuotaSlotConfiguration,
        account: UnicomAccount,
    ): WidgetQuotaSnapshotItem {
        val packages = when (slot.id) {
            "domestic-flow" -> account.packages.filter { isDefaultDomesticSingleFlowPackageName(it.originalName) }
            "province-flow" -> account.packages.filter { matchesSingle(it.originalName, listOf("省内", "畅游")) }
            "cell-flow" -> account.packages.filter { matchesSingle(it.originalName, listOf("小区", "本地")) }
            "campus-flow" -> account.packages.filter { matchesSingle(it.originalName, listOf("校区")) }
            else -> account.packages.filter { matchesSingle(it.originalName, listOf(slot.displayTitle)) }
        }
        return makeSingleFlowItem(slot.displayTitle, packages)
    }

    private fun makeAutomaticSingleVoiceItem(
        slot: WidgetQuotaSlotConfiguration,
        packages: List<VoicePackage>,
    ): WidgetQuotaSnapshotItem {
        val selected = when (slot.id) {
            "domestic-voice" -> packages.filter { matchesSingle(it.originalName, listOf("国内"), listOf("一家亲")) }
            "family-voice" -> packages.filter { matchesSingle(it.originalName, listOf("一家亲")) }
            else -> packages.filter { matchesSingle(it.originalName, listOf(slot.displayTitle)) }
        }
        return makeSingleVoiceItem(slot.displayTitle, selected)
    }

    private fun makeSingleFlowItem(title: String, summary: FlowSummary) = WidgetQuotaSnapshotItem(
        titleTop = title,
        remaining = max(0.0, (summary.remainingMB ?: 0.0) / 1024.0),
        total = max(0.0, (summary.totalMB ?: 0.0) / 1024.0),
        used = max(0.0, summary.usedMB / 1024.0),
        unit = WidgetSnapshotUnit.GIGABYTE,
    )

    private fun makeSingleFlowItem(title: String, packages: List<FlowPackage>): WidgetQuotaSnapshotItem {
        val values = packages.map(::resolvedFlowValues)
        return WidgetQuotaSnapshotItem(
            titleTop = title,
            remaining = max(0.0, values.sumOf { it.remaining } / 1024.0),
            total = max(0.0, values.sumOf { it.total } / 1024.0),
            used = max(0.0, values.sumOf { it.used } / 1024.0),
            unit = WidgetSnapshotUnit.GIGABYTE,
        )
    }

    private fun makeSingleVoiceItem(title: String, packages: List<VoicePackage>): WidgetQuotaSnapshotItem {
        val values = packages.map(::resolvedVoiceValues)
        return WidgetQuotaSnapshotItem(
            titleTop = title,
            remaining = max(0.0, values.sumOf { it.remaining }),
            total = max(0.0, values.sumOf { it.total }),
            used = max(0.0, values.sumOf { it.used }),
            unit = WidgetSnapshotUnit.MINUTE,
        )
    }

    private fun makeDualItem(slot: WidgetDualSlotConfiguration, account: UnicomAccount): WidgetDualDashboardItem =
        when (slot.kind) {
            WidgetDualSlotKind.INTEGRAL -> emptyDualItem(slot)
            WidgetDualSlotKind.VOICE -> {
                val packages = account.resolvedVoicePackages
                when {
                    slot.packageIDs.isNotEmpty() -> makeDualVoiceItem(slot, packages.filter { it.id in slot.packageIDs })
                    slot.voiceSummaryGroupID != null -> {
                        val group = account.voiceSummaryGroups.orEmpty().firstOrNull { it.id == slot.voiceSummaryGroupID }
                        if (group == null) emptyDualItem(slot)
                        else makeDualVoiceItem(slot, packages.filter { it.id in group.packageKeys.toSet() })
                    }
                    else -> emptyDualItem(slot)
                }
            }
            WidgetDualSlotKind.FLOW -> {
                when {
                    slot.packageIDs.isNotEmpty() -> makeDualFlowItem(slot, account.packages.filter { it.id in slot.packageIDs })
                    slot.flowSummaryGroupID != null -> {
                        val group = account.configuredSummaryGroups.firstOrNull { it.id == slot.flowSummaryGroupID }
                        val summary = group?.let(account::summary)
                        if (summary == null || summary.packageCount <= 0) emptyDualItem(slot) else makeDualFlowItem(slot, summary)
                    }
                    else -> emptyDualItem(slot)
                }
            }
        }

    private fun makeDualFlowItem(slot: WidgetDualSlotConfiguration, summary: FlowSummary) = WidgetDualDashboardItem(
        id = slot.id,
        title = slot.displayTitle,
        kind = WidgetDualSlotKind.FLOW,
        remaining = summary.remainingMB?.let { max(0.0, it / 1024.0) },
        total = summary.totalMB?.let { max(0.0, it / 1024.0) },
        used = max(0.0, summary.usedMB / 1024.0),
        isUnlimited = summary.isUnlimited,
    )

    private fun makeDualFlowItem(slot: WidgetDualSlotConfiguration, packages: List<FlowPackage>): WidgetDualDashboardItem {
        if (packages.isEmpty() || packages.none { it.totalMB != null || it.remainingMB != null || it.usedMB != null }) return emptyDualItem(slot)
        val values = packages.map(::resolvedFlowValues)
        return WidgetDualDashboardItem(
            id = slot.id,
            title = slot.displayTitle,
            kind = WidgetDualSlotKind.FLOW,
            remaining = max(0.0, values.sumOf { it.remaining } / 1024.0),
            total = max(0.0, values.sumOf { it.total } / 1024.0),
            used = max(0.0, values.sumOf { it.used } / 1024.0),
            isUnlimited = packages.any { it.detectedQuotaType == QuotaType.UNLIMITED },
        )
    }

    private fun makeDualVoiceItem(slot: WidgetDualSlotConfiguration, packages: List<VoicePackage>): WidgetDualDashboardItem {
        if (packages.isEmpty() || packages.none { it.totalMinutes != null || it.remainingMinutes != null || it.usedMinutes != null }) return emptyDualItem(slot)
        val values = packages.map(::resolvedVoiceValues)
        return WidgetDualDashboardItem(
            id = slot.id,
            title = slot.displayTitle,
            kind = WidgetDualSlotKind.VOICE,
            remaining = max(0.0, values.sumOf { it.remaining }),
            total = max(0.0, values.sumOf { it.total }),
            used = max(0.0, values.sumOf { it.used }),
            isUnlimited = packages.any { it.isUnlimited },
        )
    }

    private fun emptyDualItem(slot: WidgetDualSlotConfiguration) = WidgetDualDashboardItem(
        id = slot.id,
        title = slot.displayTitle,
        kind = slot.kind,
        remaining = null,
        total = null,
        used = null,
        isUnlimited = false,
    )

    private fun matchingSummary(title: String, account: UnicomAccount): FlowSummary? {
        val normalized = normalizedTitle(title)
        val group = account.configuredSummaryGroups.firstOrNull {
            normalizedTitle(it.name) == normalized && account.summary(it).packageCount > 0
        } ?: return null
        return account.summary(group)
    }

    private fun isDefaultDomesticSingleFlowPackageName(value: String): Boolean {
        val normalized = normalizedFlowName(value)
        val explicit = listOf("国内", "全国").any(normalized::contains) &&
            listOf("省内", "小区", "校区", "校园", "畅游").none(normalized::contains)
        val noKnownMarker = KNOWN_FLOW_KEYWORDS.none(normalized::contains)
        return explicit || noKnownMarker
    }

    private fun matchesSingle(value: String, include: List<String>, exclude: List<String> = emptyList()): Boolean {
        val normalized = normalizedFlowName(value)
        return (include.isEmpty() || include.any { normalized.contains(it, ignoreCase = true) }) &&
            exclude.none { normalized.contains(it, ignoreCase = true) }
    }

    private fun normalizedFlowName(value: String): String = value.replace('（', '(').replace('）', ')').lowercase(Locale.ROOT)
    private fun normalizedTitle(value: String): String = value.trim().replace(" ", "").replace("　", "").replace('（', '(').replace('）', ')').lowercase(Locale.ROOT)

    private data class Resolved(val total: Double, val remaining: Double, val used: Double)

    private fun resolvedFlowValues(item: FlowPackage): Resolved {
        val rawTotal = item.totalMB
        val rawUsed = item.usedMB
        val rawRemaining = item.remainingMB
        val used = max(0.0, rawUsed ?: 0.0)
        val remaining = max(0.0, rawRemaining ?: 0.0)
        val total = when {
            rawTotal != null && rawTotal > 0.0 -> rawTotal
            rawUsed != null || rawRemaining != null -> used + remaining
            else -> 0.0
        }
        val resolvedUsed = rawUsed?.let { max(0.0, it) }
            ?: rawRemaining?.let { max(0.0, total - max(0.0, it)) }
            ?: 0.0
        val resolvedRemaining = rawRemaining?.let { max(0.0, it) }
            ?: rawUsed?.let { max(0.0, total - max(0.0, it)) }
            ?: 0.0
        return Resolved(max(0.0, total), resolvedRemaining, resolvedUsed)
    }

    private fun resolvedVoiceValues(item: VoicePackage): Resolved {
        val rawTotal = item.totalMinutes
        val rawUsed = item.usedMinutes
        val rawRemaining = item.remainingMinutes
        val used = max(0.0, rawUsed ?: 0.0)
        val remaining = max(0.0, rawRemaining ?: 0.0)
        val total = when {
            rawTotal != null && rawTotal > 0.0 -> rawTotal
            rawUsed != null || rawRemaining != null -> used + remaining
            else -> 0.0
        }
        val resolvedUsed = rawUsed?.let { max(0.0, it) }
            ?: rawRemaining?.let { max(0.0, total - max(0.0, it)) }
            ?: 0.0
        val resolvedRemaining = rawRemaining?.let { max(0.0, it) }
            ?: rawUsed?.let { max(0.0, total - max(0.0, it)) }
            ?: 0.0
        return Resolved(max(0.0, total), resolvedRemaining, resolvedUsed)
    }

    private val KNOWN_FLOW_KEYWORDS = listOf("国内", "全国", "校区", "省内", "小区", "本地", "畅游", "校园", "定向", "专属", "免流", "畅视", "云盘")
}
