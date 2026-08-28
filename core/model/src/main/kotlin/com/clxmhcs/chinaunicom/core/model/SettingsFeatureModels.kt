package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import java.util.UUID

enum class PhoneCarrier(val rawValue: String, val displayName: String) {
    CHINA_UNICOM("chinaUnicom", "联通"),
    CHINA_MOBILE("chinaMobile", "移动"),
    CHINA_TELECOM("chinaTelecom", "电信"),
    CHINA_BROADNET("chinaBroadnet", "广电"),
    OTHER("other", "其它"),
    UNKNOWN("unknown", "未知"),
}

enum class PhoneCarrierCorrection(val rawValue: String, val displayName: String) {
    AUTOMATIC("automatic", "自动识别"),
    CHINA_UNICOM("chinaUnicom", "联通"),
    CHINA_MOBILE("chinaMobile", "移动"),
    CHINA_TELECOM("chinaTelecom", "电信"),
    CHINA_BROADNET("chinaBroadnet", "广电"),
    OTHER("other", "其它");

    val carrier: PhoneCarrier?
        get() = when (this) {
            AUTOMATIC -> null
            CHINA_UNICOM -> PhoneCarrier.CHINA_UNICOM
            CHINA_MOBILE -> PhoneCarrier.CHINA_MOBILE
            CHINA_TELECOM -> PhoneCarrier.CHINA_TELECOM
            CHINA_BROADNET -> PhoneCarrier.CHINA_BROADNET
            OTHER -> PhoneCarrier.OTHER
        }
}

data class PhoneSegmentAttributionRecord(
    val prefix: String,
    val location: String? = null,
    val carrier: PhoneCarrier,
    val updatedAt: Instant,
)

data class PhoneSegmentUpdateResult(
    val updatedCount: Int,
    val failedCount: Int,
) {
    val totalCount: Int get() = updatedCount + failedCount
}

enum class WidgetQuotaResourceKind(val rawValue: String, val title: String) {
    FLOW("flow", "流量"),
    VOICE("voice", "语音"),
}

data class WidgetQuotaSlotConfiguration(
    val id: String,
    val title: String,
    val kind: WidgetQuotaResourceKind,
    val packageIDs: List<String> = emptyList(),
    val isVisible: Boolean = true,
) {
    val displayTitle: String get() = title.trim().ifEmpty { kind.title }

    fun normalized(): WidgetQuotaSlotConfiguration = copy(
        id = id.trim().ifEmpty { UUID.randomUUID().toString() },
        title = title.trim(),
        packageIDs = packageIDs.map(String::trim).filter(String::isNotEmpty).distinct().sorted(),
    )

    companion object {
        val defaultSlots: List<WidgetQuotaSlotConfiguration> = listOf(
            WidgetQuotaSlotConfiguration("domestic-flow", "国内流量", WidgetQuotaResourceKind.FLOW),
            WidgetQuotaSlotConfiguration("province-flow", "省内流量", WidgetQuotaResourceKind.FLOW),
            WidgetQuotaSlotConfiguration("cell-flow", "小区流量", WidgetQuotaResourceKind.FLOW),
            WidgetQuotaSlotConfiguration("campus-flow", "校区流量", WidgetQuotaResourceKind.FLOW),
            WidgetQuotaSlotConfiguration("domestic-voice", "国内语音", WidgetQuotaResourceKind.VOICE),
            WidgetQuotaSlotConfiguration("family-voice", "一家亲语音", WidgetQuotaResourceKind.VOICE),
        )
    }
}

data class WidgetDisplayConfiguration(
    val selectedAccountID: UUID? = null,
    val showsTodayUsage: Boolean = true,
    val showsBalance: Boolean = true,
    val automaticRefreshEnabled: Boolean = true,
    val automaticRefreshMinutes: List<Int> = DEFAULT_AUTOMATIC_REFRESH_MINUTES,
    val slots: List<WidgetQuotaSlotConfiguration> = WidgetQuotaSlotConfiguration.defaultSlots,
) {
    fun normalized(): WidgetDisplayConfiguration {
        val times = automaticRefreshMinutes.filter { it in 0 until MINUTES_PER_DAY }.distinct().sorted()
        return copy(
            automaticRefreshMinutes = times.ifEmpty { DEFAULT_AUTOMATIC_REFRESH_MINUTES },
            slots = slots.ifEmpty { WidgetQuotaSlotConfiguration.defaultSlots }.map { it.normalized() },
        )
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val DEFAULT_AUTOMATIC_REFRESH_MINUTES: List<Int> = listOf(8 * 60, 11 * 60, 14 * 60, 17 * 60)
    }
}

enum class WidgetDualSide(val rawValue: String) { LEFT("left"), RIGHT("right") }

enum class WidgetDualSlotKind(val rawValue: String, val title: String) {
    FLOW("flow", "流量"),
    VOICE("voice", "语音"),
    INTEGRAL("integral", "可用积分"),
}

data class WidgetDualSlotConfiguration(
    val id: String,
    val title: String,
    val kind: WidgetDualSlotKind,
    val flowSummaryGroupID: String? = null,
    val voiceSummaryGroupID: String? = null,
    val packageIDs: List<String> = emptyList(),
    val isVisible: Boolean = true,
) {
    val displayTitle: String get() = title.trim().ifEmpty { kind.title }

    fun normalized(fallbackID: String): WidgetDualSlotConfiguration {
        val flowGroup = flowSummaryGroupID?.trim()?.takeIf(String::isNotEmpty)?.takeIf { kind == WidgetDualSlotKind.FLOW }
        val voiceGroup = voiceSummaryGroupID?.trim()?.takeIf(String::isNotEmpty)?.takeIf { kind == WidgetDualSlotKind.VOICE }
        val packages = if (kind == WidgetDualSlotKind.INTEGRAL) emptyList() else packageIDs
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
        return copy(
            id = id.trim().ifEmpty { fallbackID },
            title = if (kind == WidgetDualSlotKind.INTEGRAL) WidgetDualSlotKind.INTEGRAL.title else title.trim(),
            flowSummaryGroupID = flowGroup,
            voiceSummaryGroupID = voiceGroup,
            packageIDs = packages,
        )
    }

    companion object {
        const val SLOT_COUNT = 6

        fun defaultSlots(side: WidgetDualSide): List<WidgetDualSlotConfiguration> = when (side) {
            WidgetDualSide.LEFT -> listOf(
                WidgetDualSlotConfiguration("left-domestic-flow", "国内流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("left-province-flow", "省内流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("left-cell-flow", "小区流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("left-campus-flow", "校区流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("left-domestic-voice", "国内语音", WidgetDualSlotKind.VOICE),
                WidgetDualSlotConfiguration("left-slot-6", "", WidgetDualSlotKind.FLOW, isVisible = false),
            )
            WidgetDualSide.RIGHT -> listOf(
                WidgetDualSlotConfiguration("right-domestic-flow", "国内流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("right-slot-2", "", WidgetDualSlotKind.FLOW, isVisible = false),
                WidgetDualSlotConfiguration("right-cell-flow", "小区流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("right-campus-flow", "校区流量", WidgetDualSlotKind.FLOW),
                WidgetDualSlotConfiguration("right-domestic-voice", "国内语音", WidgetDualSlotKind.VOICE),
                WidgetDualSlotConfiguration("right-family-voice", "一家亲语音", WidgetDualSlotKind.VOICE),
            )
        }
    }
}

data class WidgetDualDisplayConfiguration(
    val leftAccountID: UUID? = null,
    val rightAccountID: UUID? = null,
    val leftSlots: List<WidgetDualSlotConfiguration> = WidgetDualSlotConfiguration.defaultSlots(WidgetDualSide.LEFT),
    val rightSlots: List<WidgetDualSlotConfiguration> = WidgetDualSlotConfiguration.defaultSlots(WidgetDualSide.RIGHT),
    val sourceBindingVersion: Int = CURRENT_SOURCE_BINDING_VERSION,
) {
    fun normalized(): WidgetDualDisplayConfiguration {
        val right = rightAccountID?.takeUnless { it == leftAccountID }
        return copy(
            rightAccountID = right,
            leftSlots = normalizeSlots(leftSlots, WidgetDualSide.LEFT),
            rightSlots = normalizeSlots(rightSlots, WidgetDualSide.RIGHT),
            sourceBindingVersion = sourceBindingVersion.coerceAtLeast(0),
        )
    }

    fun accountID(side: WidgetDualSide): UUID? = if (side == WidgetDualSide.LEFT) leftAccountID else rightAccountID
    fun slots(side: WidgetDualSide): List<WidgetDualSlotConfiguration> = if (side == WidgetDualSide.LEFT) leftSlots else rightSlots

    fun withAccount(side: WidgetDualSide, accountID: UUID?): WidgetDualDisplayConfiguration = when (side) {
        WidgetDualSide.LEFT -> copy(leftAccountID = accountID, rightAccountID = rightAccountID?.takeUnless { it == accountID }).normalized()
        WidgetDualSide.RIGHT -> copy(rightAccountID = accountID, leftAccountID = leftAccountID?.takeUnless { it == accountID }).normalized()
    }

    fun withSlots(side: WidgetDualSide, slots: List<WidgetDualSlotConfiguration>): WidgetDualDisplayConfiguration = when (side) {
        WidgetDualSide.LEFT -> copy(leftSlots = slots).normalized()
        WidgetDualSide.RIGHT -> copy(rightSlots = slots).normalized()
    }

    companion object {
        const val CURRENT_SOURCE_BINDING_VERSION = 2

        private fun normalizeSlots(
            source: List<WidgetDualSlotConfiguration>,
            side: WidgetDualSide,
        ): List<WidgetDualSlotConfiguration> {
            val defaults = WidgetDualSlotConfiguration.defaultSlots(side)
            val result = source.take(WidgetDualSlotConfiguration.SLOT_COUNT).toMutableList()
            while (result.size < WidgetDualSlotConfiguration.SLOT_COUNT) result += defaults[result.size]
            return result.mapIndexed { index, item -> item.normalized(defaults[index].id) }
        }
    }
}

enum class ShortcutNotificationSlot(val rawValue: String, val title: String) {
    NONE("none", "未绑定"),
    A("A", "A"),
    B("B", "B"),
    C("C", "C"),
    D("D", "D"),
}

data class ShortcutNotificationTemplateSettings(
    val notifyTraffic: Boolean = true,
    val notifyVoice: Boolean = true,
    val notifyBalance: Boolean = false,
    val notifyOnFailure: Boolean = false,
    val titleTemplate: String = "[套餐名称](已用[主流量.已用])",
    val subtitleTemplate: String = "在线:[在线时长]、用[本次用量];今用[今日用量]",
    val bodyTemplate: String = "国内余;省内余;小区余\n校区余;校园余;流量共余。（总流量[总流量]）。\n语音余",
)

data class ShortcutNotificationProfile(
    val accountID: UUID,
    val slot: ShortcutNotificationSlot = ShortcutNotificationSlot.NONE,
    val settings: ShortcutNotificationTemplateSettings = ShortcutNotificationTemplateSettings(),
    val updatedAt: Instant = Instant.EPOCH,
)
