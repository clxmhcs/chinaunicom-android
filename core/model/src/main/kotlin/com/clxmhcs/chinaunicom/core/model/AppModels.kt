package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class UnicomAccount(
    val id: UUID = UUID.randomUUID(),
    val displayName: String,
    val mobile: String,
    val packageName: String = "",
    val packages: List<FlowPackage> = emptyList(),
    val voicePackages: List<VoicePackage>? = emptyList(),
    val remainingQuerySnapshot: RemainingQuerySnapshot? = null,
    val balanceYuan: Double? = null,
    val balanceUpdatedAt: Instant? = null,
    val unavailableBalanceDetail: UnavailableBalanceDetail? = null,
    val displayPreferences: List<PackageDisplayPreference> = emptyList(),
    val summaryGroups: List<FlowSummaryGroup>? = null,
    val voiceSummaryGroups: List<VoiceSummaryGroup>? = null,
    val quotaResourceStatus: QuotaResourceStatus? = null,
    val lastUpdatedAt: Instant? = null,
    val lastErrorMessage: String? = null,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    val resolvedVoicePackages: List<VoicePackage>
        get() {
            val forcedPackages = deduplicatedVoicePackages(flowPackagesForcedToVoice)
            val forcedKeys = forcedPackages.map(::voicePackageDeduplicationKey).toSet()
            val automaticPackages = deduplicatedVoicePackages(baseVoicePackages)
                .filterNot { forcedKeys.contains(voicePackageDeduplicationKey(it)) }

            return (forcedPackages + automaticPackages).sortedWith(
                compareBy<VoicePackage> { it.isUnlimited }
                    .thenByDescending { it.totalMinutes ?: -1.0 }
                    .thenBy { it.originalName },
            )
        }

    val ambiguousResourceGroups: List<AmbiguousResourceGroup>
        get() {
            val flowGroups = packages.groupBy { ambiguousResourceNameKey(it.originalName) }
            val voiceGroups = (voicePackages ?: emptyList()).groupBy { ambiguousResourceNameKey(it.originalName) }

            return flowGroups.mapNotNull { (key, flowPackages) ->
                val voices = voiceGroups[key]
                if (key.isEmpty() || voices.isNullOrEmpty()) return@mapNotNull null
                AmbiguousResourceGroup(
                    nameKey = key,
                    displayName = ambiguousResourceDisplayName(
                        flowPackages.firstOrNull()?.originalName
                            ?: voices.firstOrNull()?.originalName
                            ?: key,
                    ),
                    flowPackages = flowPackages,
                    voicePackages = voices,
                )
            }.sortedBy { it.displayName }
        }

    val sortedPackages: List<FlowPackage>
        get() {
            val preferences = indexedPreferences
            val flowPackages = packages.filter {
                (preferences[it.id] ?: PackageDisplayPreference(packageKey = it.id)).resourceKindOverride != ResourceDisplayKind.VOICE
            } + voicePackagesForcedToFlow

            return flowPackages.sortedWith { lhs, rhs ->
                val lp = preference(lhs, preferences)
                val rp = preference(rhs, preferences)
                if (lp.sortOrder == rp.sortOrder) lhs.originalName.compareTo(rhs.originalName)
                else lp.sortOrder.compareTo(rp.sortOrder)
            }
        }

    val automaticSummaryGroups: List<FlowSummaryGroup>
        get() {
            val preferences = indexedPreferences
            val candidates = sortedPackages.filter {
                preference(it, preferences).placement != DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }
            val markedKeywords = listOf(
                "国内", "全国", "校区", "省内", "小区", "本地", "畅游", "校园",
                "定向", "专属", "免流", "畅视", "云盘",
            )

            val domestic = candidates.filter { packageValue ->
                val name = packageValue.originalName
                val hasExplicitDomesticMarker = containsAny(listOf("国内", "全国"), name) &&
                    !containsAny(listOf("省内", "小区", "校区", "校园", "畅游"), name)
                val hasNoKnownMarker = !containsAny(markedKeywords, name)
                hasExplicitDomesticMarker || hasNoKnownMarker
            }.map { it.id }
            val province = candidates.filter { containsAny(listOf("省内", "畅游"), it.originalName) }.map { it.id }
            val community = candidates.filter { containsAny(listOf("小区", "本地"), it.originalName) }.map { it.id }
            val campusArea = candidates.filter { containsAny(listOf("校区"), it.originalName) }.map { it.id }
            val campus = candidates.filter { containsAny(listOf("校园"), it.originalName) }.map { it.id }
            val directed = candidates.filter {
                containsAny(listOf("定向", "专属", "免流", "畅视", "云盘"), it.originalName)
            }.map { it.id }
            val matched = (domestic + province + community + campusArea + campus + directed).toSet()
            val other = candidates.filterNot { matched.contains(it.id) }.map { it.id }

            val groups = mutableListOf<FlowSummaryGroup>()
            fun append(id: String, name: String, keys: List<String>) {
                if (keys.isNotEmpty()) {
                    groups += FlowSummaryGroup(id = id, name = name, packageKeys = keys, sortOrder = groups.size)
                }
            }
            append("auto.domestic", "国内流量", domestic)
            append("auto.province", "省内流量", province)
            append("auto.community", "小区流量", community)
            append("auto.campusArea", "校区流量", campusArea)
            append("auto.campus", "校园流量", campus)
            append("auto.directed", "定向流量", directed)
            append("auto.other", "其他流量", other)
            if (groups.isEmpty() && candidates.isNotEmpty()) {
                groups += FlowSummaryGroup(
                    id = "auto.all",
                    name = "全部流量",
                    packageKeys = candidates.map { it.id },
                    sortOrder = 0,
                )
            }
            return groups
        }

    val configuredSummaryGroups: List<FlowSummaryGroup>
        get() = (summaryGroups ?: automaticSummaryGroups).sortedWith(
            compareBy<FlowSummaryGroup> { it.sortOrder }.thenBy { it.name },
        )

    val visibleSummaryGroups: List<FlowSummaryGroup>
        get() = configuredSummaryGroups.filter { it.isVisibleOnHome && summary(it).packageCount > 0 }

    val primaryPackage: FlowPackage?
        get() {
            val preferences = indexedPreferences
            sortedPackages.firstOrNull {
                preference(it, preferences).placement == DisplayPlacement.PRIMARY &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }?.let { return it }

            return sortedPackages.firstOrNull {
                preference(it, preferences).placement != DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE &&
                    quotaType(it, preferences) == QuotaType.LIMITED &&
                    category(it, preferences) == PackageCategory.GENERAL
            } ?: sortedPackages.firstOrNull {
                preference(it, preferences).placement != DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }
        }

    val secondaryPackages: List<FlowPackage>
        get() {
            val preferences = indexedPreferences
            val sorted = sortedPackages
            val explicitlySelected = sorted.filter {
                preference(it, preferences).placement == DisplayPlacement.SECONDARY &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }
            if (explicitlySelected.isNotEmpty()) return explicitlySelected.take(2)
            val primary = primaryPackage ?: return emptyList()
            return sorted.filter {
                it.id != primary.id &&
                    preference(it, preferences).placement != DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }.take(2)
        }

    val visibleDetailPackages: List<FlowPackage>
        get() {
            val preferences = indexedPreferences
            return sortedPackages.filter {
                preference(it, preferences).placement != DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }
        }

    val hiddenPackages: List<FlowPackage>
        get() {
            val preferences = indexedPreferences
            return sortedPackages.filter {
                preference(it, preferences).placement == DisplayPlacement.HIDDEN &&
                    resourceKind(it, preferences) != ResourceDisplayKind.VOICE
            }
        }

    val visibleVoicePackages: List<VoicePackage>
        get() {
            val preferences = indexedPreferences
            return resolvedVoicePackages.filter {
                (preferences[it.id] ?: PackageDisplayPreference(packageKey = it.id)).placement != DisplayPlacement.HIDDEN
            }
        }

    val hiddenVoicePackages: List<VoicePackage>
        get() {
            val preferences = indexedPreferences
            return resolvedVoicePackages.filter {
                (preferences[it.id] ?: PackageDisplayPreference(packageKey = it.id)).placement == DisplayPlacement.HIDDEN
            }
        }

    fun preference(packageValue: FlowPackage): PackageDisplayPreference =
        displayPreferences.firstOrNull { it.packageKey == packageValue.id }
            ?: PackageDisplayPreference(packageKey = packageValue.id)

    fun displayName(packageValue: FlowPackage): String =
        preference(packageValue).alias.trimmedOrNull() ?: packageValue.originalName

    fun quotaType(packageValue: FlowPackage): QuotaType {
        val override = preference(packageValue).quotaTypeOverride
        return if (override == QuotaType.AUTOMATIC) packageValue.detectedQuotaType else override
    }

    fun category(packageValue: FlowPackage): PackageCategory {
        val override = preference(packageValue).categoryOverride
        return if (override == PackageCategory.AUTOMATIC) packageValue.detectedCategory else override
    }

    fun summary(group: FlowSummaryGroup): FlowSummary {
        val packageIndex = indexedPackages
        val preferenceIndex = indexedPreferences
        val selected = group.packageKeys.mapNotNull(packageIndex::get).filter {
            resourceKind(it, preferenceIndex) != ResourceDisplayKind.VOICE
        }
        val isUnlimited = selected.any { quotaType(it, preferenceIndex) == QuotaType.UNLIMITED }
        val used = selected.sumOf { it.safeUsedMB }
        val remaining = selected.sumOf { it.safeRemainingMB }
        val limited = selected.filter { quotaType(it, preferenceIndex) != QuotaType.UNLIMITED }
        val totals = limited.mapNotNull { packageValue ->
            packageValue.totalMB?.takeIf { it > 0 }
                ?: if (packageValue.usedMB != null && packageValue.remainingMB != null) {
                    max(0.0, packageValue.usedMB) + max(0.0, packageValue.remainingMB)
                } else null
        }
        val total = totals.takeIf { it.isNotEmpty() }?.sum()

        return FlowSummary(
            id = group.id,
            name = group.name.takeUnless { it.trim().isEmpty() } ?: "未命名分类",
            usedMB = used,
            totalMB = if (isUnlimited) null else total,
            remainingMB = if (isUnlimited) null else remaining,
            isUnlimited = isUnlimited,
            packageCount = selected.size,
        )
    }

    fun groupNamesContaining(packageID: String): List<String> =
        configuredSummaryGroups.filter { it.packageKeys.contains(packageID) }.map { it.name }

    private val baseVoicePackages: List<VoicePackage>
        get() {
            val preferences = indexedPreferences
            return (voicePackages ?: emptyList()).filter {
                (preferences[it.id] ?: PackageDisplayPreference(packageKey = it.id)).resourceKindOverride != ResourceDisplayKind.FLOW
            }
        }

    private val flowPackagesForcedToVoice: List<VoicePackage>
        get() {
            val preferences = indexedPreferences
            return packages.mapNotNull { packageValue ->
                val configured = preferences[packageValue.id] ?: PackageDisplayPreference(packageKey = packageValue.id)
                if (configured.resourceKindOverride != ResourceDisplayKind.VOICE) return@mapNotNull null
                VoicePackage(
                    id = packageValue.id,
                    originalName = packageValue.originalName,
                    totalMinutes = if (packageValue.detectedQuotaType == QuotaType.UNLIMITED) null else packageValue.totalMB,
                    usedMinutes = packageValue.usedMB,
                    remainingMinutes = if (packageValue.detectedQuotaType == QuotaType.UNLIMITED) null else packageValue.remainingMB,
                    isUnlimited = packageValue.detectedQuotaType == QuotaType.UNLIMITED,
                    isShared = packageValue.isShared,
                    endDateText = packageValue.endDateText,
                    rawType = packageValue.rawType,
                    rawCode = packageValue.rawCode,
                )
            }
        }

    private val voicePackagesForcedToFlow: List<FlowPackage>
        get() {
            val preferences = indexedPreferences
            return (voicePackages ?: emptyList()).mapNotNull { packageValue ->
                val configured = preferences[packageValue.id] ?: PackageDisplayPreference(packageKey = packageValue.id)
                if (configured.resourceKindOverride != ResourceDisplayKind.FLOW) return@mapNotNull null
                FlowPackage(
                    id = packageValue.id,
                    originalName = packageValue.originalName,
                    totalMB = if (packageValue.isUnlimited) null else packageValue.totalMinutes,
                    usedMB = packageValue.usedMinutes,
                    remainingMB = if (packageValue.isUnlimited) null else packageValue.remainingMinutes,
                    detectedQuotaType = if (packageValue.isUnlimited) QuotaType.UNLIMITED else QuotaType.LIMITED,
                    detectedCategory = PackageCategory.GENERAL,
                    isShared = packageValue.isShared,
                    endDateText = packageValue.endDateText,
                    rawType = packageValue.rawType,
                    rawCode = packageValue.rawCode,
                )
            }
        }

    private val indexedPreferences: Map<String, PackageDisplayPreference>
        get() = buildMap {
            displayPreferences.forEach { preference -> putIfAbsent(preference.packageKey, preference) }
        }

    private val indexedPackages: Map<String, FlowPackage>
        get() = buildMap {
            sortedPackages.forEach { packageValue -> putIfAbsent(packageValue.id, packageValue) }
        }

    private fun deduplicatedVoicePackages(packages: List<VoicePackage>): List<VoicePackage> {
        val indexByResource = mutableMapOf<String, Int>()
        val selected = mutableListOf<VoicePackage>()
        packages.forEach { packageValue ->
            val key = voicePackageDeduplicationKey(packageValue)
            if (key.isEmpty()) return@forEach
            val index = indexByResource[key]
            if (index != null) selected[index] = preferredVoicePackage(selected[index], packageValue)
            else {
                indexByResource[key] = selected.size
                selected += packageValue
            }
        }
        return selected
    }

    private fun voicePackageDeduplicationKey(packageValue: VoicePackage): String = listOf(
        normalizedVoicePackageName(packageValue.originalName),
        resourceValueSignature(packageValue.totalMinutes, packageValue.usedMinutes, packageValue.remainingMinutes),
        packageValue.rawCode.orEmpty(),
        packageValue.rawType.orEmpty(),
    ).joinToString("|")

    private fun normalizedVoicePackageName(value: String): String =
        value.trim().filterNot(Char::isWhitespace).lowercase(Locale.ROOT)

    private fun resourceValueSignature(total: Double?, used: Double?, remaining: Double?): String =
        listOf(total, used, remaining).joinToString("/") { value ->
            value.finiteOrNull()?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
        }

    private fun preferredVoicePackage(lhs: VoicePackage, rhs: VoicePackage): VoicePackage {
        val lhsScore = voicePackageQualityScore(lhs)
        val rhsScore = voicePackageQualityScore(rhs)
        if (lhsScore != rhsScore) return if (lhsScore > rhsScore) lhs else rhs

        val lhsTotal = lhs.totalMinutes ?: -1.0
        val rhsTotal = rhs.totalMinutes ?: -1.0
        if (abs(lhsTotal - rhsTotal) > 0.01) return if (lhsTotal > rhsTotal) lhs else rhs

        val lhsRemaining = lhs.remainingMinutes ?: -1.0
        val rhsRemaining = rhs.remainingMinutes ?: -1.0
        if (abs(lhsRemaining - rhsRemaining) > 0.01) return if (lhsRemaining > rhsRemaining) lhs else rhs

        return lhs
    }

    private fun voicePackageQualityScore(packageValue: VoicePackage): Int {
        var score = 0
        if (packageValue.totalMinutes != null) score += 4
        if (packageValue.usedMinutes != null) score += 2
        if (packageValue.remainingMinutes != null) score += 2
        if (!packageValue.rawCode.isNullOrEmpty()) score += 2
        if (!packageValue.rawType.isNullOrEmpty()) score += 1
        if (!packageValue.endDateText.isNullOrEmpty()) score += 1
        return score
    }

    private fun ambiguousResourceNameKey(value: String): String =
        ambiguousResourceDisplayName(value)
            .replace(Regex("\\([^)]*\\)"), "")
            .filterNot(Char::isWhitespace)
            .lowercase(Locale.ROOT)

    private fun ambiguousResourceDisplayName(value: String): String =
        value.replace("（", "(")
            .replace("）", ")")
            .replace(Regex("\\(语音\\)"), "")
            .trim()

    private fun preference(
        packageValue: FlowPackage,
        index: Map<String, PackageDisplayPreference>,
    ): PackageDisplayPreference = index[packageValue.id] ?: PackageDisplayPreference(packageKey = packageValue.id)

    private fun resourceKind(
        packageValue: FlowPackage,
        index: Map<String, PackageDisplayPreference>,
    ): ResourceDisplayKind = preference(packageValue, index).resourceKindOverride ?: ResourceDisplayKind.AUTOMATIC

    private fun quotaType(
        packageValue: FlowPackage,
        index: Map<String, PackageDisplayPreference>,
    ): QuotaType {
        val override = preference(packageValue, index).quotaTypeOverride
        return if (override == QuotaType.AUTOMATIC) packageValue.detectedQuotaType else override
    }

    private fun category(
        packageValue: FlowPackage,
        index: Map<String, PackageDisplayPreference>,
    ): PackageCategory {
        val override = preference(packageValue, index).categoryOverride
        return if (override == PackageCategory.AUTOMATIC) packageValue.detectedCategory else override
    }

    private fun containsAny(keywords: List<String>, name: String): Boolean =
        keywords.any { name.contains(it, ignoreCase = true) }
}

enum class QuotaResourceStatus(val rawValue: String) {
    AVAILABLE("available"),
    NOT_SUBSCRIBED("notSubscribed"),
}

enum class ShareScope(val rawValue: String, val title: String?) {
    SHARED("shared", "共享"),
    UNSHARED("unshared", "非共享"),
    UNKNOWN("unknown", null),
}

enum class CarryForwardScope(val rawValue: String, val title: String?) {
    CARRY_FORWARD("carryForward", "结转"),
    INCLUDED("included", "含结转"),
    NONE("none", null),
    UNKNOWN("unknown", null),
}

data class FlowPackage(
    val id: String,
    val originalName: String,
    val totalMB: Double?,
    val usedMB: Double?,
    val remainingMB: Double?,
    val detectedQuotaType: QuotaType,
    val detectedCategory: PackageCategory,
    val isShared: Boolean,
    val shareScope: ShareScope? = null,
    val carryForwardScope: CarryForwardScope? = null,
    val currentMonthTotalMB: Double? = null,
    val carryForwardTotalMB: Double? = null,
    val endDateText: String? = null,
    val rawType: String? = null,
    val rawCode: String? = null,
) {
    val resolvedShareScope: ShareScope get() = shareScope ?: if (isShared) ShareScope.SHARED else ShareScope.UNKNOWN
    val resolvedCarryForwardScope: CarryForwardScope get() = carryForwardScope ?: CarryForwardScope.UNKNOWN
    val safeUsedMB: Double get() = max(0.0, usedMB ?: 0.0)
    val safeRemainingMB: Double get() = max(0.0, remainingMB ?: 0.0)

    val usedFraction: Double?
        get() {
            val total = totalMB ?: return null
            if (total <= 0 || detectedQuotaType == QuotaType.UNLIMITED) return null
            val used = min(max(0.0, usedMB ?: max(0.0, total - (remainingMB ?: total))), total)
            return used / total
        }

    fun detailDisplayFraction(quotaType: QuotaType): Double? {
        if (quotaType == QuotaType.UNLIMITED) {
            val stepMB = 100 * 1024.0
            val displayedTotalMB = max(stepMB, ceil(safeUsedMB / stepMB) * stepMB)
            return min(max(safeUsedMB / displayedTotalMB, 0.0), 1.0)
        }
        return usedFraction
    }
}

data class VoicePackage(
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
    val safeUsedMinutes: Double get() = max(0.0, usedMinutes ?: 0.0)
    val safeRemainingMinutes: Double get() = max(0.0, remainingMinutes ?: 0.0)

    val usedFraction: Double?
        get() {
            val total = totalMinutes ?: return null
            if (isUnlimited || total <= 0) return null
            val used = min(max(0.0, usedMinutes ?: max(0.0, total - (remainingMinutes ?: total))), total)
            return used / total
        }
}

data class PackageDisplayPreference(
    val packageKey: String,
    val alias: String? = null,
    val resourceKindOverride: ResourceDisplayKind? = null,
    val quotaTypeOverride: QuotaType = QuotaType.AUTOMATIC,
    val categoryOverride: PackageCategory = PackageCategory.AUTOMATIC,
    val placement: DisplayPlacement = DisplayPlacement.DETAIL_ONLY,
    val includeInSummary: Boolean = true,
    val sortOrder: Int = 0,
) {
    val id: String get() = packageKey
}

enum class ResourceDisplayKind(val rawValue: String, val title: String) {
    AUTOMATIC("automatic", "自动识别"),
    FLOW("flow", "流量"),
    VOICE("voice", "语音"),
}

data class AmbiguousResourceGroup(
    val nameKey: String,
    val displayName: String,
    val flowPackages: List<FlowPackage>,
    val voicePackages: List<VoicePackage>,
) {
    val id: String get() = nameKey
}

data class FlowSummaryGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageKeys: List<String> = emptyList(),
    val isVisibleOnHome: Boolean = true,
    val sortOrder: Int = 0,
)

data class VoicePackageIdentityHint(
    val originalName: String,
    val rawType: String?,
    val rawCode: String?,
    val isShared: Boolean,
    val isUnlimited: Boolean,
    val totalMinutes: Double?,
)

data class VoiceSummaryGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageKeys: List<String> = emptyList(),
    val packageIdentityHints: Map<String, VoicePackageIdentityHint>? = null,
    val sortOrder: Int = 0,
)

data class FlowSummary(
    val id: String,
    val name: String,
    val usedMB: Double,
    val totalMB: Double?,
    val remainingMB: Double?,
    val isUnlimited: Boolean,
    val packageCount: Int,
) {
    val usedFraction: Double?
        get() {
            val total = totalMB ?: return null
            if (isUnlimited || total <= 0) return null
            return min(max(usedMB / total, 0.0), 1.0)
        }

    val isExhausted: Boolean
        get() {
            if (isUnlimited) return false
            remainingMB?.let { return it <= 0.0001 }
            totalMB?.takeIf { it > 0 }?.let { return usedMB >= it - 0.0001 }
            return (usedFraction ?: 0.0) >= 0.999999
        }
}

enum class QuotaType(val rawValue: String, val title: String) {
    AUTOMATIC("automatic", "自动识别"),
    LIMITED("limited", "有限流量"),
    UNLIMITED("unlimited", "不限量"),
}

enum class PackageCategory(val rawValue: String, val title: String) {
    AUTOMATIC("automatic", "自动识别"),
    GENERAL("general", "通用"),
    DIRECTED("directed", "定向免流"),
    OTHER("other", "其他"),
}

enum class DisplayPlacement(val rawValue: String, val title: String) {
    PRIMARY("primary", "首页主显示"),
    SECONDARY("secondary", "首页辅助"),
    DETAIL_ONLY("detailOnly", "仅详情"),
    HIDDEN("hidden", "隐藏"),
}

data class AccountCredentials(
    val cookie: String,
    val appID: String?,
    val tokenOnline: String?,
)

data class QuotaFetchResult(
    val packageName: String,
    val packages: List<FlowPackage>,
    val voicePackages: List<VoicePackage>,
    val remainingQuerySnapshot: RemainingQuerySnapshot? = null,
    val balanceYuan: Double?,
    val unavailableBalanceDetail: UnavailableBalanceDetail?,
    val quotaResourceStatus: QuotaResourceStatus,
    val updatedCredentials: AccountCredentials?,
)

data class BalanceFetchResult(
    val balanceYuan: Double?,
    val unavailableBalanceDetail: UnavailableBalanceDetail?,
    val updatedCredentials: AccountCredentials?,
)

data class UnavailableBalanceDetail(
    val currentBalance: String?,
    val unavailableLimitFee: String?,
    val frozenFee: String?,
    val totalUnavailable: String?,
    val limitItems: List<UnavailableLimitItem>,
    val frozenItems: List<FrozenBalanceItem>,
) {
    val displayUnavailableLimitFee: String get() = normalizedMoney(unavailableLimitFee)
    val displayFrozenFee: String get() = normalizedMoney(frozenFee)
    val displayTotalUnavailable: String get() = normalizedMoney(totalUnavailable)

    private fun normalizedMoney(value: String?): String = value.trimmedOrNull() ?: "0.00"
}

data class UnavailableLimitItem(
    val depositName: String?,
    val unavailableLimitFee: String?,
    val belongSerialNumber: String?,
    val endCycle: String?,
    val depositInfo: String?,
    val userStyle: String?,
) {
    val id: String
        get() = listOf(depositName, belongSerialNumber, unavailableLimitFee, endCycle)
            .mapNotNull { it.trimmedOrNull() }
            .joinToString("|")
}

data class FrozenBalanceItem(
    val actionName: String?,
    val serialNumber: String?,
    val actionMoney: String?,
    val usedMoney: String?,
    val leftMoney: String?,
    val actionDepart: String?,
    val startCycle: String?,
    val endCycle: String?,
) {
    val id: String
        get() = listOf(actionName, serialNumber, actionMoney, leftMoney, actionDepart, startCycle, endCycle)
            .mapNotNull { it.trimmedOrNull() }
            .joinToString("|")
}

enum class BalanceRefreshState {
    IDLE,
    LOADING,
    FAILED,
}

sealed interface RefreshState {
    data object Idle : RefreshState
    data object Loading : RefreshState
    data object Succeeded : RefreshState
    data class Failed(val message: String) : RefreshState
}

enum class DisplayUnit(val rawValue: String, val title: String) {
    AUTOMATIC("automatic", "自动"),
    MEGABYTES("megabytes", "MB"),
    GIGABYTES("gigabytes", "GB"),
}

data class AppSettings(
    val autoRefreshOnLaunch: Boolean = true,
    val hideMobileMiddleDigits: Boolean = true,
    val hideBroadbandMiddleDigits: Boolean = false,
    val displayUnit: DisplayUnit = DisplayUnit.AUTOMATIC,
)
