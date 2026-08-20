package com.clxmhcs.chinaunicom.core.parser

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
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class RemainingQueryParser {
    private enum class ResourceKind { FLOW, VOICE, SMS, UNKNOWN }

    private data class FlowQuotaMetadata(val isUnlimited: Boolean, val speedLimitMB: Double?)

    private data class UnlimitedFlowMetadataIndex(
        val feePolicyIDs: MutableSet<String> = mutableSetOf(),
        val normalizedNames: MutableSet<String> = mutableSetOf(),
        val speedLimitByFeePolicyID: MutableMap<String, Double> = mutableMapOf(),
        val speedLimitByName: MutableMap<String, Double> = mutableMapOf(),
        var globalSpeedLimitMB: Double? = null,
    )

    fun parse(data: ByteArray, updatedAt: Instant = Instant.now()): RemainingQuerySnapshot {
        val root = parseJson(data) as? JsonObject ?: throw RemainingQueryParserException.InvalidRoot
        val payload = payloadRoot(root)

        val sharedTotals = memberUsages(payload["shareData"].asObject()?.get("viceCardList"))
        val members = mergeMembers(parseMembers(payload["viceCardLits"]), sharedTotals)
        val summaries = parseSummaries(payload["flowSumList"])
        val unlimitedIndex = unlimitedFlowMetadataIndex(payload, root)

        val flow = mutableListOf<RemainingFlowPackage>()
        val voice = mutableListOf<RemainingVoicePackage>()
        val sms = mutableListOf<RemainingSMSPackage>()
        val unsharedVoice = mutableListOf<RemainingVoicePackage>()
        val unsharedSMS = mutableListOf<RemainingSMSPackage>()
        var voiceRemaining: Double? = null
        var voiceUsed: Double? = null
        var smsRemaining: Double? = null
        var smsUsed: Double? = null

        payload["resources"].objects().forEach { resource ->
            when (resourceKind(resource["type"])) {
                ResourceKind.FLOW -> flow += flowPackages(resource["details"], true, unlimitedIndex)
                ResourceKind.VOICE -> {
                    voiceRemaining = resource["remainResource"].doubleValue() ?: voiceRemaining
                    voiceUsed = resource["userResource"].doubleValue() ?: voiceUsed
                    voice += voicePackages(resource["details"], true)
                }
                ResourceKind.SMS -> {
                    smsRemaining = resource["remainResource"].doubleValue() ?: smsRemaining
                    smsUsed = resource["userResource"].doubleValue() ?: smsUsed
                    sms += smsPackages(resource["details"], true)
                }
                ResourceKind.UNKNOWN -> Unit
            }
        }

        payload["shareData"].asObject()?.let { shareData ->
            flow += flowPackages(shareData["details"], true, unlimitedIndex)
        }

        payload["unshared"].objects().forEach { resource ->
            when (resourceKind(resource["type"])) {
                ResourceKind.FLOW -> flow += flowPackages(resource["details"], false, unlimitedIndex)
                ResourceKind.VOICE -> unsharedVoice += voicePackages(resource["details"], false)
                ResourceKind.SMS -> unsharedSMS += smsPackages(resource["details"], false)
                ResourceKind.UNKNOWN -> Unit
            }
        }

        return RemainingQuerySnapshot(
            updatedAt = updatedAt,
            members = members,
            flowSummaries = summaries,
            flowPackages = dedupeFlow(flow),
            sharedFlowMemberTotals = dedupeUsages(sharedTotals),
            voice = RemainingVoiceSnapshot(
                remainingMinutes = nonNegative(voiceRemaining),
                usedMinutes = nonNegative(voiceUsed),
                packages = dedupeVoice(voice),
                unsharedPackages = dedupeVoice(unsharedVoice),
            ),
            sms = RemainingSMSSnapshot(
                remainingCount = nonNegative(smsRemaining),
                usedCount = nonNegative(smsUsed),
                packages = dedupeSMS(sms),
                unsharedPackages = dedupeSMS(unsharedSMS),
            ),
        )
    }

    private fun payloadRoot(root: JsonObject): JsonObject {
        val data = root["data"] as? JsonObject ?: return root
        return if (listOf("resources", "unshared", "shareData", "viceCardLits", "flowSumList").any(data::containsKey)) data else root
    }

    private fun resourceKind(value: JsonElement?): ResourceKind {
        val type = value.textValue()?.lowercase().orEmpty()
        return when {
            type == "flow" || type.contains("flowlist") -> ResourceKind.FLOW
            type == "voice" || type.contains("voicelist") -> ResourceKind.VOICE
            type == "sms" || type.contains("smslist") -> ResourceKind.SMS
            else -> ResourceKind.UNKNOWN
        }
    }

    private fun parseMembers(value: JsonElement?): List<RemainingMember> = value.objects().mapNotNull { item ->
        val number = item["number"].nonEmptyText() ?: return@mapNotNull null
        RemainingMember(
            maskedNumber = number,
            secretNumber = item["secretNumber"].nonEmptyText(),
            role = roleFromNumberFlag(item["numberFlag"]),
            isCurrentLogin = null,
        )
    }

    private fun mergeMembers(base: List<RemainingMember>, usages: List<RemainingMemberUsage>): List<RemainingMember> {
        val usageByNumber = dedupeUsages(usages).associateBy { it.maskedNumber }
        val seen = mutableSetOf<String>()
        return base.mapNotNull { member ->
            if (!seen.add(member.maskedNumber)) return@mapNotNull null
            val usage = usageByNumber[member.maskedNumber] ?: return@mapNotNull member
            RemainingMember(
                maskedNumber = member.maskedNumber,
                secretNumber = member.secretNumber,
                role = if (member.role == RemainingMemberRole.UNKNOWN) usage.role else member.role,
                isCurrentLogin = usage.isCurrentLogin,
            )
        }
    }

    private fun parseSummaries(value: JsonElement?): List<RemainingFlowSummary> {
        val byCategory = mutableMapOf<RemainingFlowCategory, RemainingFlowSummary>()
        value.objects().forEach { item ->
            val category = flowCategory(item["flowtype"])
            if (category == null || category == RemainingFlowCategory.UNKNOWN) return@forEach
            byCategory[category] = RemainingFlowSummary(
                category = category,
                remainingMB = nonNegative(item["xcanusevalue"].doubleValue()) ?: 0.0,
                usedMB = nonNegative(item["xusedvalue"].doubleValue()) ?: 0.0,
            )
        }
        return listOf(RemainingFlowCategory.GENERAL, RemainingFlowCategory.EXCLUSIVE, RemainingFlowCategory.OTHER).mapNotNull(byCategory::get)
    }

    private fun flowPackages(
        value: JsonElement?,
        shared: Boolean,
        unlimitedIndex: UnlimitedFlowMetadataIndex,
    ): List<RemainingFlowPackage> = value.objects().mapNotNull { item ->
        val name = packageName(item) ?: return@mapNotNull null
        val values = quotaValues(item)
        if (values.total == null && values.used == null && values.remaining == null) return@mapNotNull null
        val feeID = item["feePolicyId"].nonEmptyText()
        val rawType = item["resourceType"].nonEmptyText()
        val rawCode = item["addupItemCode"].nonEmptyText()
        val metadata = flowQuotaMetadata(item, name, feeID, values, unlimitedIndex)
        RemainingFlowPackage(
            id = packageID("flow", shared, item, name),
            name = name,
            category = flowCategory(item["flowType"]),
            totalMB = if (metadata.isUnlimited) null else values.total,
            usedMB = values.used,
            remainingMB = if (metadata.isUnlimited) null else values.remaining,
            isShared = shared,
            memberUsages = memberUsages(item["viceCardlist"]),
            endDateText = item["endDate"].nonEmptyText(),
            feePolicyID = feeID,
            rawType = rawType,
            rawCode = rawCode,
            isUnlimited = metadata.isUnlimited,
            speedLimitMB = metadata.speedLimitMB,
        )
    }

    private fun voicePackages(value: JsonElement?, shared: Boolean): List<RemainingVoicePackage> = value.objects().mapNotNull { item ->
        val name = packageName(item) ?: return@mapNotNull null
        val values = quotaValues(item)
        if (values.total == null && values.used == null && values.remaining == null) return@mapNotNull null
        RemainingVoicePackage(
            id = packageID("voice", shared, item, name),
            name = name,
            totalMinutes = values.total,
            usedMinutes = values.used,
            remainingMinutes = values.remaining,
            isShared = shared,
            memberUsages = memberUsages(item["viceCardlist"]),
            endDateText = item["endDate"].nonEmptyText(),
            feePolicyID = item["feePolicyId"].nonEmptyText(),
            rawType = item["resourceType"].nonEmptyText(),
            rawCode = item["addupItemCode"].nonEmptyText(),
        )
    }

    private fun smsPackages(value: JsonElement?, shared: Boolean): List<RemainingSMSPackage> = value.objects().mapNotNull { item ->
        val name = packageName(item) ?: return@mapNotNull null
        val values = quotaValues(item)
        if (values.total == null && values.used == null && values.remaining == null) return@mapNotNull null
        RemainingSMSPackage(
            id = packageID("sms", shared, item, name),
            name = name,
            totalCount = values.total,
            usedCount = values.used,
            remainingCount = values.remaining,
            isShared = shared,
            memberUsages = memberUsages(item["viceCardlist"]),
            endDateText = item["endDate"].nonEmptyText(),
            feePolicyID = item["feePolicyId"].nonEmptyText(),
            rawType = item["resourceType"].nonEmptyText(),
            rawCode = item["addupItemCode"].nonEmptyText(),
        )
    }

    private data class QuotaValues(val total: Double?, val used: Double?, val remaining: Double?)

    private fun flowQuotaMetadata(
        item: JsonObject,
        name: String,
        feePolicyID: String?,
        values: QuotaValues,
        unlimitedIndex: UnlimitedFlowMetadataIndex,
    ): FlowQuotaMetadata {
        val explicitUnlimited = listOf("unlimited", "isUnlimited", "unlimitFlag", "unlimitedFlag").any { item[it].boolValue() == true }
        val unlimitedByName = name.contains("不限", ignoreCase = true) || name.contains("无限", ignoreCase = true)
        val normalizedName = normalizedPackageName(name)
        val unlimitedByFeeID = feePolicyID?.let(unlimitedIndex.feePolicyIDs::contains) ?: false
        val unlimitedByIndexedName = normalizedName in unlimitedIndex.normalizedNames
        val unlimitedByRawTotal = (item["total"].doubleValue() ?: 0.0) < 0
        val hasExplicitFiniteQuota = item["limited"].boolValue() == false && (values.total ?: 0.0) > 0 && (values.remaining ?: 0.0) >= 0
        val directUnlimited = explicitUnlimited || unlimitedByName || unlimitedByRawTotal
        val indexedUnlimited = unlimitedByFeeID || unlimitedByIndexedName
        val isUnlimited = directUnlimited || (!hasExplicitFiniteQuota && indexedUnlimited)
        if (!isUnlimited) return FlowQuotaMetadata(false, null)

        val directLimit = speedLimitMB(item)
        val inferredLimit = inferSpeedLimitMB(values.used, item)
        val indexedLimit = feePolicyID?.let { unlimitedIndex.speedLimitByFeePolicyID[it] }
            ?: unlimitedIndex.speedLimitByName[normalizedName]
        return FlowQuotaMetadata(true, directLimit ?: inferredLimit ?: indexedLimit ?: unlimitedIndex.globalSpeedLimitMB)
    }

    private fun unlimitedFlowMetadataIndex(payload: JsonObject, root: JsonObject): UnlimitedFlowMetadataIndex {
        val index = UnlimitedFlowMetadataIndex()
        index.feePolicyIDs += unlimitedFeePolicyIDs(payload["unlimitExclusiveFeeIdSet"] ?: root["unlimitExclusiveFeeIdSet"])
        index.globalSpeedLimitMB = globalUnlimitedSpeedLimitMB(payload)

        fun scan(value: JsonElement?) {
            when (value) {
                is JsonObject -> {
                    val name = resourceName(value)
                    val normalizedName = name?.let(::normalizedPackageName)
                    val feeID = value["feePolicyId"].nonEmptyText()
                    val explicitUnlimited = listOf("unlimited", "isUnlimited", "unlimitFlag", "unlimitedFlag").any { value[it].boolValue() == true }
                    val nameUnlimited = name?.let { it.contains("不限", true) || it.contains("无限", true) } ?: false
                    val rawTotalUnlimited = (value["total"].doubleValue() ?: 0.0) < 0
                    val looksUnlimited = explicitUnlimited || nameUnlimited || rawTotalUnlimited
                    val limit = speedLimitMB(value) ?: inferSpeedLimitMB(nonNegative(value["use"].doubleValue()), value)
                    if (feeID != null && limit != null) index.speedLimitByFeePolicyID[feeID] = limit
                    if (normalizedName != null && limit != null) index.speedLimitByName[normalizedName] = limit
                    if (looksUnlimited) {
                        if (feeID != null) index.feePolicyIDs += feeID
                        if (normalizedName != null) index.normalizedNames += normalizedName
                    }
                    value.values.forEach(::scan)
                }
                is JsonArray -> value.forEach(::scan)
                else -> Unit
            }
        }
        scan(payload)
        return index
    }

    private fun unlimitedFeePolicyIDs(value: JsonElement?): Set<String> {
        val result = mutableSetOf<String>()
        fun collect(element: JsonElement?) {
            element.nonEmptyText()?.let {
                result += it
                return
            }
            when (element) {
                is JsonObject -> {
                    listOf("feePolicyId", "feePolicyID", "id", "code").forEach { key -> element[key].nonEmptyText()?.let(result::add) }
                    element.values.forEach(::collect)
                }
                is JsonArray -> element.forEach(::collect)
                else -> Unit
            }
        }
        collect(value)
        return result
    }

    private fun speedLimitMB(item: JsonObject): Double? {
        val preferredKeys = listOf(
            "speedLimit", "speedLimitValue", "speedLimitFlow", "speedLimitDesc", "speedLimitText",
            "limitValue", "limitFlow", "limitDesc", "limitText", "capValue", "capFlow", "capDesc", "capText", "capMark",
            "xexceedvalue", "xexceedvalueTxt",
        )
        preferredKeys.forEach { key ->
            item[key]?.let { capacityMB(it, numericFallback = key == "xexceedvalue") }?.let { if (it > 128) return normalizeThresholdMB(it) }
        }
        item.forEach { (key, value) ->
            val lower = key.lowercase()
            if (lower in setOf("limited", "unlimited", "isunlimited")) return@forEach
            if (!(lower.contains("speed") || lower.contains("limit") || lower.contains("cap") || lower.contains("exceed"))) return@forEach
            capacityMB(value, false)?.let { if (it > 128) return normalizeThresholdMB(it) }
        }
        item.values.forEach { value ->
            val text = value.nonEmptyText() ?: return@forEach
            if (!text.contains("限速")) return@forEach
            capacityMB(value, false)?.let { if (it > 128) return normalizeThresholdMB(it) }
        }
        return null
    }

    private fun inferSpeedLimitMB(usedMB: Double?, item: JsonObject): Double? {
        if (usedMB == null || usedMB <= 0) return null
        listOf("fKusedPercent", "usedPercent", "zKusedPercent").forEach { key ->
            val percent = item[key].doubleValue() ?: return@forEach
            if (percent <= 0 || percent > 100) return@forEach
            val threshold = usedMB / (percent / 100)
            if (threshold > 128) return normalizeThresholdMB(threshold)
        }
        return null
    }

    private fun globalUnlimitedSpeedLimitMB(payload: JsonObject): Double? {
        val candidates = mutableListOf<Double>()
        fun scan(value: JsonElement?) {
            when (value) {
                is JsonObject -> value.forEach { (key, child) ->
                    val lower = key.lowercase()
                    if (lower !in setOf("limited", "unlimited", "isunlimited") &&
                        (lower.contains("speed") || lower.contains("limit") || lower.contains("cap"))
                    ) capacityMB(child, false)?.let { if (it > 128) candidates += normalizeThresholdMB(it) }
                    scan(child)
                }
                is JsonArray -> value.forEach(::scan)
                else -> value.nonEmptyText()?.takeIf { it.contains("限速") }?.let {
                    capacityMB(value, false)?.let { parsed -> if (parsed > 128) candidates += normalizeThresholdMB(parsed) }
                }
            }
        }
        scan(payload)
        return candidates.minOrNull()
    }

    private fun capacityMB(value: JsonElement, numericFallback: Boolean): Double? {
        val primitiveNumber = value.doubleValue()
        val source = value.nonEmptyText()
        if (source == null) return if (numericFallback) primitiveNumber else null
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|G|MB|M)(?![A-Za-z])", RegexOption.IGNORE_CASE).find(source)
            ?: return if (numericFallback) primitiveNumber else null
        val numeric = match.groupValues[1].toDoubleOrNull() ?: return if (numericFallback) primitiveNumber else null
        return when (match.groupValues[2].uppercase()) {
            "TB" -> numeric * 1024 * 1024
            "GB", "G" -> numeric * 1024
            "MB", "M" -> numeric
            else -> null
        }
    }

    private fun normalizeThresholdMB(value: Double): Double {
        if (!value.isFinite() || value <= 0) return value
        if (value >= 1024) {
            val roundedGB = max(1.0, round(value / 1024))
            val wholeGB = roundedGB * 1024
            if (abs(wholeGB - value) / max(value, 1.0) <= 0.12) return wholeGB
        }
        return value
    }

    private fun memberUsages(value: JsonElement?): List<RemainingMemberUsage> = value.objects().mapNotNull { item ->
        val maskedNumber = item["usernumber"].nonEmptyText() ?: return@mapNotNull null
        RemainingMemberUsage(
            maskedNumber = maskedNumber,
            role = roleFromViceCardFlag(item["viceCardflag"]),
            usedValue = nonNegative(item["use"].doubleValue()) ?: 0.0,
            isCurrentLogin = item["currentLoginFlag"].boolValue(),
        )
    }

    private fun dedupeUsages(input: List<RemainingMemberUsage>): List<RemainingMemberUsage> {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, RemainingMemberUsage>()
        input.forEach { usage ->
            val old = map[usage.maskedNumber]
            if (old != null) {
                map[usage.maskedNumber] = RemainingMemberUsage(
                    maskedNumber = old.maskedNumber,
                    role = if (old.role == RemainingMemberRole.UNKNOWN) usage.role else old.role,
                    usedValue = max(old.usedValue, usage.usedValue),
                    isCurrentLogin = if (old.isCurrentLogin == true || usage.isCurrentLogin == true) true else old.isCurrentLogin ?: usage.isCurrentLogin,
                )
            } else {
                order += usage.maskedNumber
                map[usage.maskedNumber] = usage
            }
        }
        return order.mapNotNull(map::get)
    }

    private fun dedupeFlow(input: List<RemainingFlowPackage>): List<RemainingFlowPackage> {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, RemainingFlowPackage>()
        input.forEach { item ->
            val old = map[item.id]
            if (old != null) {
                val isUnlimited = old.resolvedIsUnlimited || item.resolvedIsUnlimited
                map[item.id] = RemainingFlowPackage(
                    id = old.id,
                    name = if (old.name.length >= item.name.length) old.name else item.name,
                    category = preferredCategory(old.category, item.category),
                    totalMB = if (isUnlimited) null else old.totalMB ?: item.totalMB,
                    usedMB = old.usedMB ?: item.usedMB,
                    remainingMB = if (isUnlimited) null else old.remainingMB ?: item.remainingMB,
                    isShared = old.isShared || item.isShared,
                    memberUsages = dedupeUsages(old.memberUsages + item.memberUsages),
                    endDateText = old.endDateText ?: item.endDateText,
                    feePolicyID = old.feePolicyID ?: item.feePolicyID,
                    rawType = old.rawType ?: item.rawType,
                    rawCode = old.rawCode ?: item.rawCode,
                    isUnlimited = isUnlimited,
                    speedLimitMB = old.speedLimitMB ?: item.speedLimitMB,
                )
            } else {
                order += item.id
                map[item.id] = item
            }
        }
        return order.mapNotNull(map::get)
    }

    private fun dedupeVoice(input: List<RemainingVoicePackage>): List<RemainingVoicePackage> {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, RemainingVoicePackage>()
        input.forEach { item ->
            val old = map[item.id]
            if (old != null) {
                map[item.id] = RemainingVoicePackage(
                    id = old.id,
                    name = if (old.name.length >= item.name.length) old.name else item.name,
                    totalMinutes = old.totalMinutes ?: item.totalMinutes,
                    usedMinutes = old.usedMinutes ?: item.usedMinutes,
                    remainingMinutes = old.remainingMinutes ?: item.remainingMinutes,
                    isShared = old.isShared || item.isShared,
                    memberUsages = dedupeUsages(old.memberUsages + item.memberUsages),
                    endDateText = old.endDateText ?: item.endDateText,
                    feePolicyID = old.feePolicyID ?: item.feePolicyID,
                    rawType = old.rawType ?: item.rawType,
                    rawCode = old.rawCode ?: item.rawCode,
                )
            } else {
                order += item.id
                map[item.id] = item
            }
        }
        return order.mapNotNull(map::get)
    }

    private fun dedupeSMS(input: List<RemainingSMSPackage>): List<RemainingSMSPackage> {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, RemainingSMSPackage>()
        input.forEach { item ->
            val old = map[item.id]
            if (old != null) {
                map[item.id] = RemainingSMSPackage(
                    id = old.id,
                    name = if (old.name.length >= item.name.length) old.name else item.name,
                    totalCount = old.totalCount ?: item.totalCount,
                    usedCount = old.usedCount ?: item.usedCount,
                    remainingCount = old.remainingCount ?: item.remainingCount,
                    isShared = old.isShared || item.isShared,
                    memberUsages = dedupeUsages(old.memberUsages + item.memberUsages),
                    endDateText = old.endDateText ?: item.endDateText,
                    feePolicyID = old.feePolicyID ?: item.feePolicyID,
                    rawType = old.rawType ?: item.rawType,
                    rawCode = old.rawCode ?: item.rawCode,
                )
            } else {
                order += item.id
                map[item.id] = item
            }
        }
        return order.mapNotNull(map::get)
    }

    private fun quotaValues(item: JsonObject): QuotaValues {
        val total = nonNegative(item["total"].doubleValue())
        val remaining = nonNegative(item["remain"].doubleValue())
        val explicitUsed = nonNegative(item["use"].doubleValue())
        val used = explicitUsed ?: if (total != null && remaining != null) max(0.0, total - remaining) else null
        return QuotaValues(total, used, remaining)
    }

    private fun packageID(kind: String, shared: Boolean, item: JsonObject, name: String): String {
        val scope = if (shared) "shared" else "unshared"
        item["feePolicyId"].nonEmptyText()?.let { return "$kind:$scope:fee:$it" }
        val signature = listOf(
            item["resourceType"].nonEmptyText().orEmpty(),
            item["addupItemCode"].nonEmptyText().orEmpty(),
            name.lowercase(),
            item["total"].textValue().orEmpty(),
            item["endDate"].nonEmptyText().orEmpty(),
        ).joinToString("|")
        return "$kind:$scope:fallback:${fnv1a64(signature)}"
    }

    private fun packageName(item: JsonObject): String? = item["feePolicyName"].nonEmptyText() ?: item["addUpItemName"].nonEmptyText()

    private fun resourceName(item: JsonObject): String? =
        listOf("feePolicyName", "addUpItemName", "resourceName", "packageName", "productName", "name", "title")
            .firstNotNullOfOrNull { item[it].nonEmptyText() }

    private fun normalizedPackageName(value: String): String = value.trim().replace(" ", "").replace("（", "(").replace("）", ")").lowercase()

    private fun flowCategory(value: JsonElement?): RemainingFlowCategory? = when (value.textValue()) {
        "1" -> RemainingFlowCategory.GENERAL
        "2" -> RemainingFlowCategory.EXCLUSIVE
        "3" -> RemainingFlowCategory.OTHER
        null -> null
        else -> RemainingFlowCategory.UNKNOWN
    }

    private fun preferredCategory(lhs: RemainingFlowCategory?, rhs: RemainingFlowCategory?): RemainingFlowCategory? =
        lhs?.takeIf { it != RemainingFlowCategory.UNKNOWN } ?: rhs

    private fun roleFromNumberFlag(value: JsonElement?): RemainingMemberRole = when (value.textValue()) {
        "0" -> RemainingMemberRole.PRIMARY
        "1" -> RemainingMemberRole.SECONDARY
        else -> RemainingMemberRole.UNKNOWN
    }

    private fun roleFromViceCardFlag(value: JsonElement?): RemainingMemberRole = when (value.textValue()) {
        "1" -> RemainingMemberRole.PRIMARY
        "0" -> RemainingMemberRole.SECONDARY
        else -> RemainingMemberRole.UNKNOWN
    }

    private fun nonNegative(value: Double?): Double? = value?.let { max(0.0, it) }
}

sealed class RemainingQueryParserException(message: String) : Exception(message) {
    data object InvalidRoot : RemainingQueryParserException("invalidRoot")
}
