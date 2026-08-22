package com.clxmhcs.chinaunicom.core.storage

import com.clxmhcs.chinaunicom.core.model.CarryForwardScope
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.FrozenBalanceItem
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingFlowSummary
import com.clxmhcs.chinaunicom.core.model.RemainingMember
import com.clxmhcs.chinaunicom.core.model.RemainingMemberRole
import com.clxmhcs.chinaunicom.core.model.RemainingMemberUsage
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingSMSPackage
import com.clxmhcs.chinaunicom.core.model.RemainingSMSSnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingVoicePackage
import com.clxmhcs.chinaunicom.core.model.RemainingVoiceSnapshot
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.ShareScope
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnavailableLimitItem
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.VoicePackageIdentityHint
import com.clxmhcs.chinaunicom.core.model.VoiceSummaryGroup
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for ordinary account metadata only.
 *
 * Credentials are deliberately absent from every DTO. Cookie/appID/token_online remain owned by
 * M5's Keystore-backed CredentialStore and cannot be serialized through this codec.
 */
class AccountMetadataJsonCodec(
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
) {
    fun encode(accounts: List<UnicomAccount>): ByteArray =
        json.encodeToString(ListSerializer(AccountDto.serializer()), accounts.map(AccountDto::fromDomain))
            .toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): List<UnicomAccount> =
        json.decodeFromString(ListSerializer(AccountDto.serializer()), bytes.toString(StandardCharsets.UTF_8))
            .map(AccountDto::toDomain)
}

@Serializable
private data class AccountDto(
    val id: String,
    val displayName: String,
    val mobile: String,
    val packageName: String = "",
    val packages: List<FlowPackageDto> = emptyList(),
    val voicePackages: List<VoicePackageDto>? = emptyList(),
    val remainingQuerySnapshot: RemainingQuerySnapshotDto? = null,
    val balanceYuan: Double? = null,
    val balanceUpdatedAt: String? = null,
    val unavailableBalanceDetail: UnavailableBalanceDetailDto? = null,
    val displayPreferences: List<PackageDisplayPreferenceDto> = emptyList(),
    val summaryGroups: List<FlowSummaryGroupDto>? = null,
    val voiceSummaryGroups: List<VoiceSummaryGroupDto>? = null,
    val quotaResourceStatus: String? = null,
    val lastUpdatedAt: String? = null,
    val lastErrorMessage: String? = null,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun toDomain(): UnicomAccount = UnicomAccount(
        id = UUID.fromString(id),
        displayName = displayName,
        mobile = mobile,
        packageName = packageName,
        packages = packages.map(FlowPackageDto::toDomain),
        voicePackages = voicePackages?.map(VoicePackageDto::toDomain),
        remainingQuerySnapshot = remainingQuerySnapshot?.toDomain(),
        balanceYuan = balanceYuan,
        balanceUpdatedAt = balanceUpdatedAt?.let(Instant::parse),
        unavailableBalanceDetail = unavailableBalanceDetail?.toDomain(),
        displayPreferences = displayPreferences.map(PackageDisplayPreferenceDto::toDomain),
        summaryGroups = summaryGroups?.map(FlowSummaryGroupDto::toDomain),
        voiceSummaryGroups = voiceSummaryGroups?.map(VoiceSummaryGroupDto::toDomain),
        quotaResourceStatus = quotaResourceStatus?.let(::quotaResourceStatusFromRaw),
        lastUpdatedAt = lastUpdatedAt?.let(Instant::parse),
        lastErrorMessage = lastErrorMessage,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
    )

    companion object {
        fun fromDomain(value: UnicomAccount): AccountDto = AccountDto(
            id = value.id.toString(),
            displayName = value.displayName,
            mobile = value.mobile,
            packageName = value.packageName,
            packages = value.packages.map(FlowPackageDto::fromDomain),
            voicePackages = value.voicePackages?.map(VoicePackageDto::fromDomain),
            remainingQuerySnapshot = value.remainingQuerySnapshot?.let(RemainingQuerySnapshotDto::fromDomain),
            balanceYuan = value.balanceYuan,
            balanceUpdatedAt = value.balanceUpdatedAt?.toString(),
            unavailableBalanceDetail = value.unavailableBalanceDetail?.let(UnavailableBalanceDetailDto::fromDomain),
            displayPreferences = value.displayPreferences.map(PackageDisplayPreferenceDto::fromDomain),
            summaryGroups = value.summaryGroups?.map(FlowSummaryGroupDto::fromDomain),
            voiceSummaryGroups = value.voiceSummaryGroups?.map(VoiceSummaryGroupDto::fromDomain),
            quotaResourceStatus = value.quotaResourceStatus?.rawValue,
            lastUpdatedAt = value.lastUpdatedAt?.toString(),
            lastErrorMessage = value.lastErrorMessage,
            isEnabled = value.isEnabled,
            sortOrder = value.sortOrder,
        )
    }
}

@Serializable
private data class FlowPackageDto(
    val id: String,
    val originalName: String,
    val totalMB: Double?,
    val usedMB: Double?,
    val remainingMB: Double?,
    val detectedQuotaType: String,
    val detectedCategory: String,
    val isShared: Boolean,
    val shareScope: String? = null,
    val carryForwardScope: String? = null,
    val currentMonthTotalMB: Double? = null,
    val carryForwardTotalMB: Double? = null,
    val endDateText: String? = null,
    val rawType: String? = null,
    val rawCode: String? = null,
) {
    fun toDomain() = FlowPackage(
        id = id,
        originalName = originalName,
        totalMB = totalMB,
        usedMB = usedMB,
        remainingMB = remainingMB,
        detectedQuotaType = quotaTypeFromRaw(detectedQuotaType),
        detectedCategory = packageCategoryFromRaw(detectedCategory),
        isShared = isShared,
        shareScope = shareScope?.let(::shareScopeFromRaw),
        carryForwardScope = carryForwardScope?.let(::carryForwardScopeFromRaw),
        currentMonthTotalMB = currentMonthTotalMB,
        carryForwardTotalMB = carryForwardTotalMB,
        endDateText = endDateText,
        rawType = rawType,
        rawCode = rawCode,
    )

    companion object {
        fun fromDomain(value: FlowPackage) = FlowPackageDto(
            value.id,
            value.originalName,
            value.totalMB,
            value.usedMB,
            value.remainingMB,
            value.detectedQuotaType.rawValue,
            value.detectedCategory.rawValue,
            value.isShared,
            value.shareScope?.rawValue,
            value.carryForwardScope?.rawValue,
            value.currentMonthTotalMB,
            value.carryForwardTotalMB,
            value.endDateText,
            value.rawType,
            value.rawCode,
        )
    }
}

@Serializable
private data class VoicePackageDto(
    val id: String,
    val originalName: String,
    val totalMinutes: Double?,
    val usedMinutes: Double?,
    val remainingMinutes: Double?,
    val isUnlimited: Boolean,
    val isShared: Boolean,
    val endDateText: String? = null,
    val rawType: String? = null,
    val rawCode: String? = null,
) {
    fun toDomain() = VoicePackage(id, originalName, totalMinutes, usedMinutes, remainingMinutes, isUnlimited, isShared, endDateText, rawType, rawCode)

    companion object {
        fun fromDomain(value: VoicePackage) = VoicePackageDto(
            value.id, value.originalName, value.totalMinutes, value.usedMinutes, value.remainingMinutes,
            value.isUnlimited, value.isShared, value.endDateText, value.rawType, value.rawCode,
        )
    }
}

@Serializable
private data class PackageDisplayPreferenceDto(
    val packageKey: String,
    val alias: String? = null,
    val resourceKindOverride: String? = null,
    val quotaTypeOverride: String = QuotaType.AUTOMATIC.rawValue,
    val categoryOverride: String = PackageCategory.AUTOMATIC.rawValue,
    val placement: String = DisplayPlacement.DETAIL_ONLY.rawValue,
    val includeInSummary: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun toDomain() = PackageDisplayPreference(
        packageKey = packageKey,
        alias = alias,
        resourceKindOverride = resourceKindOverride?.let(::resourceDisplayKindFromRaw),
        quotaTypeOverride = quotaTypeFromRaw(quotaTypeOverride),
        categoryOverride = packageCategoryFromRaw(categoryOverride),
        placement = displayPlacementFromRaw(placement),
        includeInSummary = includeInSummary,
        sortOrder = sortOrder,
    )

    companion object {
        fun fromDomain(value: PackageDisplayPreference) = PackageDisplayPreferenceDto(
            value.packageKey,
            value.alias,
            value.resourceKindOverride?.rawValue,
            value.quotaTypeOverride.rawValue,
            value.categoryOverride.rawValue,
            value.placement.rawValue,
            value.includeInSummary,
            value.sortOrder,
        )
    }
}

@Serializable
private data class FlowSummaryGroupDto(
    val id: String,
    val name: String,
    val packageKeys: List<String> = emptyList(),
    val isVisibleOnHome: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun toDomain() = FlowSummaryGroup(id, name, packageKeys, isVisibleOnHome, sortOrder)

    companion object {
        fun fromDomain(value: FlowSummaryGroup) = FlowSummaryGroupDto(value.id, value.name, value.packageKeys, value.isVisibleOnHome, value.sortOrder)
    }
}

@Serializable
private data class VoicePackageIdentityHintDto(
    val originalName: String,
    val rawType: String?,
    val rawCode: String?,
    val isShared: Boolean,
    val isUnlimited: Boolean,
    val totalMinutes: Double?,
) {
    fun toDomain() = VoicePackageIdentityHint(originalName, rawType, rawCode, isShared, isUnlimited, totalMinutes)

    companion object {
        fun fromDomain(value: VoicePackageIdentityHint) = VoicePackageIdentityHintDto(
            value.originalName, value.rawType, value.rawCode, value.isShared, value.isUnlimited, value.totalMinutes,
        )
    }
}

@Serializable
private data class VoiceSummaryGroupDto(
    val id: String,
    val name: String,
    val packageKeys: List<String> = emptyList(),
    val packageIdentityHints: Map<String, VoicePackageIdentityHintDto>? = null,
    val sortOrder: Int = 0,
) {
    fun toDomain() = VoiceSummaryGroup(
        id,
        name,
        packageKeys,
        packageIdentityHints?.mapValues { it.value.toDomain() },
        sortOrder,
    )

    companion object {
        fun fromDomain(value: VoiceSummaryGroup) = VoiceSummaryGroupDto(
            value.id,
            value.name,
            value.packageKeys,
            value.packageIdentityHints?.mapValues { VoicePackageIdentityHintDto.fromDomain(it.value) },
            value.sortOrder,
        )
    }
}

@Serializable
private data class UnavailableBalanceDetailDto(
    val currentBalance: String?,
    val unavailableLimitFee: String?,
    val frozenFee: String?,
    val totalUnavailable: String?,
    val limitItems: List<UnavailableLimitItemDto> = emptyList(),
    val frozenItems: List<FrozenBalanceItemDto> = emptyList(),
) {
    fun toDomain() = UnavailableBalanceDetail(
        currentBalance,
        unavailableLimitFee,
        frozenFee,
        totalUnavailable,
        limitItems.map(UnavailableLimitItemDto::toDomain),
        frozenItems.map(FrozenBalanceItemDto::toDomain),
    )

    companion object {
        fun fromDomain(value: UnavailableBalanceDetail) = UnavailableBalanceDetailDto(
            value.currentBalance,
            value.unavailableLimitFee,
            value.frozenFee,
            value.totalUnavailable,
            value.limitItems.map(UnavailableLimitItemDto::fromDomain),
            value.frozenItems.map(FrozenBalanceItemDto::fromDomain),
        )
    }
}

@Serializable
private data class UnavailableLimitItemDto(
    val depositName: String?,
    val unavailableLimitFee: String?,
    val belongSerialNumber: String?,
    val endCycle: String?,
    val depositInfo: String?,
    val userStyle: String?,
) {
    fun toDomain() = UnavailableLimitItem(depositName, unavailableLimitFee, belongSerialNumber, endCycle, depositInfo, userStyle)

    companion object {
        fun fromDomain(value: UnavailableLimitItem) = UnavailableLimitItemDto(
            value.depositName, value.unavailableLimitFee, value.belongSerialNumber, value.endCycle, value.depositInfo, value.userStyle,
        )
    }
}

@Serializable
private data class FrozenBalanceItemDto(
    val actionName: String?,
    val serialNumber: String?,
    val actionMoney: String?,
    val usedMoney: String?,
    val leftMoney: String?,
    val actionDepart: String?,
    val startCycle: String?,
    val endCycle: String?,
) {
    fun toDomain() = FrozenBalanceItem(actionName, serialNumber, actionMoney, usedMoney, leftMoney, actionDepart, startCycle, endCycle)

    companion object {
        fun fromDomain(value: FrozenBalanceItem) = FrozenBalanceItemDto(
            value.actionName, value.serialNumber, value.actionMoney, value.usedMoney, value.leftMoney,
            value.actionDepart, value.startCycle, value.endCycle,
        )
    }
}

@Serializable
private data class RemainingQuerySnapshotDto(
    val updatedAt: String,
    val members: List<RemainingMemberDto> = emptyList(),
    val flowSummaries: List<RemainingFlowSummaryDto> = emptyList(),
    val flowPackages: List<RemainingFlowPackageDto> = emptyList(),
    val sharedFlowMemberTotals: List<RemainingMemberUsageDto> = emptyList(),
    val voice: RemainingVoiceSnapshotDto,
    val sms: RemainingSMSSnapshotDto,
) {
    fun toDomain() = RemainingQuerySnapshot(
        Instant.parse(updatedAt),
        members.map(RemainingMemberDto::toDomain),
        flowSummaries.map(RemainingFlowSummaryDto::toDomain),
        flowPackages.map(RemainingFlowPackageDto::toDomain),
        sharedFlowMemberTotals.map(RemainingMemberUsageDto::toDomain),
        voice.toDomain(),
        sms.toDomain(),
    )

    companion object {
        fun fromDomain(value: RemainingQuerySnapshot) = RemainingQuerySnapshotDto(
            value.updatedAt.toString(),
            value.members.map(RemainingMemberDto::fromDomain),
            value.flowSummaries.map(RemainingFlowSummaryDto::fromDomain),
            value.flowPackages.map(RemainingFlowPackageDto::fromDomain),
            value.sharedFlowMemberTotals.map(RemainingMemberUsageDto::fromDomain),
            RemainingVoiceSnapshotDto.fromDomain(value.voice),
            RemainingSMSSnapshotDto.fromDomain(value.sms),
        )
    }
}

@Serializable
private data class RemainingMemberDto(
    val maskedNumber: String,
    val secretNumber: String?,
    val role: String,
    val isCurrentLogin: Boolean?,
) {
    fun toDomain() = RemainingMember(maskedNumber, secretNumber, remainingMemberRoleFromRaw(role), isCurrentLogin)

    companion object {
        fun fromDomain(value: RemainingMember) = RemainingMemberDto(value.maskedNumber, value.secretNumber, value.role.rawValue, value.isCurrentLogin)
    }
}

@Serializable
private data class RemainingMemberUsageDto(
    val maskedNumber: String,
    val role: String,
    val usedValue: Double,
    val isCurrentLogin: Boolean?,
) {
    fun toDomain() = RemainingMemberUsage(maskedNumber, remainingMemberRoleFromRaw(role), usedValue, isCurrentLogin)

    companion object {
        fun fromDomain(value: RemainingMemberUsage) = RemainingMemberUsageDto(value.maskedNumber, value.role.rawValue, value.usedValue, value.isCurrentLogin)
    }
}

@Serializable
private data class RemainingFlowSummaryDto(
    val category: String,
    val remainingMB: Double,
    val usedMB: Double,
) {
    fun toDomain() = RemainingFlowSummary(remainingFlowCategoryFromRaw(category), remainingMB, usedMB)

    companion object {
        fun fromDomain(value: RemainingFlowSummary) = RemainingFlowSummaryDto(value.category.rawValue, value.remainingMB, value.usedMB)
    }
}

@Serializable
private data class RemainingFlowPackageDto(
    val id: String,
    val name: String,
    val category: String?,
    val totalMB: Double?,
    val usedMB: Double?,
    val remainingMB: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsageDto> = emptyList(),
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
    val isUnlimited: Boolean?,
    val speedLimitMB: Double?,
) {
    fun toDomain() = RemainingFlowPackage(
        id, name, category?.let(::remainingFlowCategoryFromRaw), totalMB, usedMB, remainingMB,
        isShared, memberUsages.map(RemainingMemberUsageDto::toDomain), endDateText, feePolicyID,
        rawType, rawCode, isUnlimited, speedLimitMB,
    )

    companion object {
        fun fromDomain(value: RemainingFlowPackage) = RemainingFlowPackageDto(
            value.id, value.name, value.category?.rawValue, value.totalMB, value.usedMB, value.remainingMB,
            value.isShared, value.memberUsages.map(RemainingMemberUsageDto::fromDomain), value.endDateText,
            value.feePolicyID, value.rawType, value.rawCode, value.isUnlimited, value.speedLimitMB,
        )
    }
}

@Serializable
private data class RemainingVoicePackageDto(
    val id: String,
    val name: String,
    val totalMinutes: Double?,
    val usedMinutes: Double?,
    val remainingMinutes: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsageDto> = emptyList(),
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
) {
    fun toDomain() = RemainingVoicePackage(
        id, name, totalMinutes, usedMinutes, remainingMinutes, isShared,
        memberUsages.map(RemainingMemberUsageDto::toDomain), endDateText, feePolicyID, rawType, rawCode,
    )

    companion object {
        fun fromDomain(value: RemainingVoicePackage) = RemainingVoicePackageDto(
            value.id, value.name, value.totalMinutes, value.usedMinutes, value.remainingMinutes, value.isShared,
            value.memberUsages.map(RemainingMemberUsageDto::fromDomain), value.endDateText, value.feePolicyID,
            value.rawType, value.rawCode,
        )
    }
}

@Serializable
private data class RemainingVoiceSnapshotDto(
    val remainingMinutes: Double?,
    val usedMinutes: Double?,
    val packages: List<RemainingVoicePackageDto> = emptyList(),
    val unsharedPackages: List<RemainingVoicePackageDto> = emptyList(),
) {
    fun toDomain() = RemainingVoiceSnapshot(
        remainingMinutes,
        usedMinutes,
        packages.map(RemainingVoicePackageDto::toDomain),
        unsharedPackages.map(RemainingVoicePackageDto::toDomain),
    )

    companion object {
        fun fromDomain(value: RemainingVoiceSnapshot) = RemainingVoiceSnapshotDto(
            value.remainingMinutes,
            value.usedMinutes,
            value.packages.map(RemainingVoicePackageDto::fromDomain),
            value.unsharedPackages.map(RemainingVoicePackageDto::fromDomain),
        )
    }
}

@Serializable
private data class RemainingSMSPackageDto(
    val id: String,
    val name: String,
    val totalCount: Double?,
    val usedCount: Double?,
    val remainingCount: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsageDto> = emptyList(),
    val endDateText: String?,
    val feePolicyID: String?,
    val rawType: String?,
    val rawCode: String?,
) {
    fun toDomain() = RemainingSMSPackage(
        id, name, totalCount, usedCount, remainingCount, isShared,
        memberUsages.map(RemainingMemberUsageDto::toDomain), endDateText, feePolicyID, rawType, rawCode,
    )

    companion object {
        fun fromDomain(value: RemainingSMSPackage) = RemainingSMSPackageDto(
            value.id, value.name, value.totalCount, value.usedCount, value.remainingCount, value.isShared,
            value.memberUsages.map(RemainingMemberUsageDto::fromDomain), value.endDateText, value.feePolicyID,
            value.rawType, value.rawCode,
        )
    }
}

@Serializable
private data class RemainingSMSSnapshotDto(
    val remainingCount: Double?,
    val usedCount: Double?,
    val packages: List<RemainingSMSPackageDto> = emptyList(),
    val unsharedPackages: List<RemainingSMSPackageDto> = emptyList(),
) {
    fun toDomain() = RemainingSMSSnapshot(
        remainingCount,
        usedCount,
        packages.map(RemainingSMSPackageDto::toDomain),
        unsharedPackages.map(RemainingSMSPackageDto::toDomain),
    )

    companion object {
        fun fromDomain(value: RemainingSMSSnapshot) = RemainingSMSSnapshotDto(
            value.remainingCount,
            value.usedCount,
            value.packages.map(RemainingSMSPackageDto::fromDomain),
            value.unsharedPackages.map(RemainingSMSPackageDto::fromDomain),
        )
    }
}

private fun quotaResourceStatusFromRaw(raw: String): QuotaResourceStatus =
    QuotaResourceStatus.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown quotaResourceStatus: $raw")

private fun quotaTypeFromRaw(raw: String): QuotaType =
    QuotaType.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown quotaType: $raw")

private fun packageCategoryFromRaw(raw: String): PackageCategory =
    PackageCategory.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown packageCategory: $raw")

private fun shareScopeFromRaw(raw: String): ShareScope =
    ShareScope.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown shareScope: $raw")

private fun carryForwardScopeFromRaw(raw: String): CarryForwardScope =
    CarryForwardScope.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown carryForwardScope: $raw")

private fun resourceDisplayKindFromRaw(raw: String): ResourceDisplayKind =
    ResourceDisplayKind.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown resourceDisplayKind: $raw")

private fun displayPlacementFromRaw(raw: String): DisplayPlacement =
    DisplayPlacement.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown displayPlacement: $raw")

private fun remainingMemberRoleFromRaw(raw: String): RemainingMemberRole =
    RemainingMemberRole.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown remainingMemberRole: $raw")

private fun remainingFlowCategoryFromRaw(raw: String): RemainingFlowCategory =
    RemainingFlowCategory.entries.firstOrNull { it.rawValue == raw } ?: error("Unknown remainingFlowCategory: $raw")
