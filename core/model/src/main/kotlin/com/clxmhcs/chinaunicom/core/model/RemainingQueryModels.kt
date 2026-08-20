package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import java.util.UUID
import kotlin.math.max

enum class RemainingMemberRole(val rawValue: String) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    UNKNOWN("unknown"),
}

enum class RemainingFlowCategory(val rawValue: String) {
    GENERAL("general"),
    EXCLUSIVE("exclusive"),
    OTHER("other"),
    UNKNOWN("unknown"),
}

data class RemainingMember(
    val maskedNumber: String,
    val secretNumber: String?,
    val role: RemainingMemberRole,
    val isCurrentLogin: Boolean?,
) {
    val id: String get() = "$maskedNumber|${role.rawValue}"
}

data class RemainingMemberUsage(
    val maskedNumber: String,
    val role: RemainingMemberRole,
    val usedValue: Double,
    val isCurrentLogin: Boolean?,
) {
    val id: String get() = "$maskedNumber|${role.rawValue}"
}

data class RemainingFlowSummary(
    val category: RemainingFlowCategory,
    val remainingMB: Double,
    val usedMB: Double,
) {
    val id: RemainingFlowCategory get() = category
}

data class RemainingFlowPackage(
    val id: String,
    val name: String,
    val category: RemainingFlowCategory?,
    val totalMB: Double?,
    val usedMB: Double?,
    val remainingMB: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsage>,
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
    val isUnlimited: Boolean?,
    val speedLimitMB: Double?,
) {
    val safeUsedMB: Double get() = max(0.0, usedMB ?: 0.0)
    val safeRemainingMB: Double get() = max(0.0, remainingMB ?: 0.0)
    val resolvedIsUnlimited: Boolean get() = isUnlimited == true
}

data class RemainingVoicePackage(
    val id: String,
    val name: String,
    val totalMinutes: Double?,
    val usedMinutes: Double?,
    val remainingMinutes: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsage>,
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
) {
    val safeUsedMinutes: Double get() = max(0.0, usedMinutes ?: 0.0)
    val safeRemainingMinutes: Double get() = max(0.0, remainingMinutes ?: 0.0)
}

data class RemainingVoiceSnapshot(
    val remainingMinutes: Double?,
    val usedMinutes: Double?,
    val packages: List<RemainingVoicePackage>,
    val unsharedPackages: List<RemainingVoicePackage>,
)

data class RemainingSMSPackage(
    val id: String,
    val name: String,
    val totalCount: Double?,
    val usedCount: Double?,
    val remainingCount: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsage>,
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
)

data class RemainingSMSSnapshot(
    val remainingCount: Double?,
    val usedCount: Double?,
    val packages: List<RemainingSMSPackage>,
    val unsharedPackages: List<RemainingSMSPackage>,
)

data class RemainingQuerySnapshot(
    val updatedAt: Instant,
    val members: List<RemainingMember>,
    val flowSummaries: List<RemainingFlowSummary>,
    val flowPackages: List<RemainingFlowPackage>,
    val sharedFlowMemberTotals: List<RemainingMemberUsage>,
    val voice: RemainingVoiceSnapshot,
    val sms: RemainingSMSSnapshot,
)

data class DailyUsageBaseline(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val accountID: UUID,
    val dateKey: String,
    val capturedAt: Instant,
    val packages: List<FlowPackage>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
