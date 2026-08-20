package com.clxmhcs.chinaunicom.core.parser

import com.clxmhcs.chinaunicom.core.model.CarryForwardScope
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.ShareScope
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.max

private val capacityWithUnitRegex = Regex("(-?[0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|G|MB|M|KB|K|B)", RegexOption.IGNORE_CASE)
private val capacityNumberRegex = Regex("(-?[0-9]+(?:\\.[0-9]+)?)")
private val durationWithUnitRegex = Regex("(-?[0-9]+(?:\\.[0-9]+)?)\\s*(小时|钟头|HOURS?|HRS?|HR|H|分钟|MINUTES?|MINS?|MIN|秒|SECONDS?|SECS?|SEC|S)", RegexOption.IGNORE_CASE)
private val ambiguousMinuteWithMRegex = Regex("(-?[0-9]+(?:\\.[0-9]+)?)\\s*M(?!B)", RegexOption.IGNORE_CASE)

data class QuotaParseResult(
    val packageName: String,
    val packages: List<FlowPackage>,
    val voicePackages: List<VoicePackage>,
    val quotaResourceStatus: QuotaResourceStatus,
)

sealed class QuotaParserException(message: String) : Exception(message) {
    data object SessionExpired : QuotaParserException("sessionExpired")
    data class Server(val serverMessage: String) : QuotaParserException(serverMessage)
    data object NoPackages : QuotaParserException("noPackages")
}

class QuotaParser {
    private enum class ResourceKind { FLOW, VOICE, SMS, UNKNOWN }

    private data class Candidate(
        val dictionary: JsonObject,
        val path: List<String>,
        val inheritedKind: ResourceKind?,
    )

    private data class ParsedCandidate(
        val packageValue: FlowPackage,
        val qualityScore: Int,
        val flowConfidence: Int,
        val sourcePath: String,
    )

    private data class ParsedVoiceCandidate(
        val packageValue: VoicePackage,
        val qualityScore: Int,
        val voiceConfidence: Int,
        val sourcePath: String,
    )

    fun parse(data: ByteArray): QuotaParseResult {
        val root = parseJson(data)
        val responseCode = topLevelCode(root)
        if (isExpired(responseCode)) throw QuotaParserException.SessionExpired

        val packageName = recursiveString(root, setOf("productname", "packageName")).orEmpty()
        val candidates = mutableListOf<Candidate>()
        collectPackageDictionaries(root, emptyList(), null, candidates)

        val flowCandidates = candidates.mapNotNull(::parseFlowCandidate)
        val voiceCandidates = candidates.mapNotNull(::parseVoiceCandidate)
        val packages = deduplicateFlow(flowCandidates)
        val voicePackages = deduplicateVoice(voiceCandidates)

        if (packages.isEmpty() && voicePackages.isNotEmpty()) {
            return QuotaParseResult(packageName, packages, voicePackages, QuotaResourceStatus.NOT_SUBSCRIBED)
        }

        if (packages.isEmpty() && voicePackages.isEmpty()) {
            if (looksLikeNoSubscribedQuotaResources(root, packageName, responseCode, candidates)) {
                return QuotaParseResult(packageName, emptyList(), emptyList(), QuotaResourceStatus.NOT_SUBSCRIBED)
            }
            val description = recursiveString(root, setOf("desc", "rsp_desc", "message"))
            if (!description.isNullOrEmpty()) throw QuotaParserException.Server(description)
            throw QuotaParserException.NoPackages
        }

        return QuotaParseResult(packageName, packages, voicePackages, QuotaResourceStatus.AVAILABLE)
    }

    private fun topLevelCode(root: JsonElement): String? {
        val objectValue = root as? JsonObject ?: return null
        return listOf("code", "rsp_code", "status")
            .asSequence()
            .mapNotNull { objectValue[it].textValue()?.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun isSuccess(code: String?): Boolean = code?.lowercase() in setOf("0", "0000", "200", "success")
    private fun isExpired(code: String?): Boolean = code in setOf("9998", "999998", "999999", "0500")

    private fun looksLikeNoSubscribedQuotaResources(
        root: JsonElement,
        packageName: String,
        responseCode: String?,
        rawCandidates: List<Candidate>,
    ): Boolean {
        if (rawCandidates.isNotEmpty()) return false
        if (packageName.trim().isNotEmpty()) return true
        return isSuccess(responseCode) && containsQuotaResponseContainer(root)
    }

    private fun containsQuotaResponseContainer(element: JsonElement): Boolean = when (element) {
        is JsonObject -> {
            val keys = setOf(
                "resources", "resourceList", "flowList", "flowInfo", "voiceList",
                "packageList", "feePolicyList", "addupList", "leftList", "dataList",
            )
            element.keys.any { it in keys } || element.values.any(::containsQuotaResponseContainer)
        }
        is JsonArray -> element.any(::containsQuotaResponseContainer)
        else -> false
    }

    private fun parseFlowCandidate(candidate: Candidate): ParsedCandidate? {
        val dictionary = candidate.dictionary
        val name = dictionary.firstString(resourceNameKeys)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawType = dictionary.firstString(resourceTypeKeys)
        val sourcePath = candidate.path.joinToString(".")
        val kind = resourceKind(name, rawType, sourcePath, dictionary, candidate.inheritedKind)
        if (kind != ResourceKind.FLOW && !hasStrongFlowEvidence(name, rawType, sourcePath, dictionary)) return null

        val total = capacityMB(
            dictionary,
            listOf("totalTxt", "xexceedvalueTxt", "totalFlowTxt"),
            listOf("total", "xexceedvalue", "totalFlow"),
            listOf("totalUnit", "xexceedvalueUnit", "flowUnit", "resourceUnit", "unit"),
        )
        val remaining = capacityMB(
            dictionary,
            listOf("remainTxt", "remainFlowTxt", "leftTxt"),
            listOf("remain", "remainFlow", "left"),
            listOf("remainUnit", "leftUnit", "flowUnit", "resourceUnit", "unit"),
        )
        var used = capacityMB(
            dictionary,
            listOf("useTxt", "usedTxt", "useFlowTxt"),
            listOf("use", "used", "useFlow"),
            listOf("useUnit", "usedUnit", "flowUnit", "resourceUnit", "unit"),
        )
        if (used == null && total != null && remaining != null) used = max(0.0, total - remaining)
        if (total == null && remaining == null && used == null) return null

        val explicitUnlimited = dictionary.firstBool(listOf("unlimited", "isUnlimited")) == true
        val unlimitedByName = name.contains("不限", ignoreCase = true) || name.contains("无限", ignoreCase = true)
        val quotaType = if (explicitUnlimited || unlimitedByName || (total ?: 0.0) < 0) QuotaType.UNLIMITED else QuotaType.LIMITED

        val loweredType = rawType?.lowercase().orEmpty()
        val freeFlow = dictionary.firstBool(listOf("freeFlow", "isFreeFlow")) == true
        val category = if (
            freeFlow || loweredType.contains("mlresources") ||
            listOf("免流", "定向", "专享", "畅视", "云盘").any(name::contains)
        ) PackageCategory.DIRECTED else PackageCategory.GENERAL

        val feePolicyID = dictionary.firstString(listOf("feePolicyId"))
        val itemCode = dictionary.firstString(listOf("addupItemCode", "itemCode"))
        val rawID = dictionary.firstString(resourceIDKeys)
        val endDate = dictionary.firstString(endDateKeys)
        val scope = shareScope(dictionary, sourcePath, name)
        val carryForward = carryForwardInfo(dictionary, total)
        val fallbackID = fnv1a64(
            listOf(normalizedResourceName(name), normalizedType(rawType), itemCode.orEmpty(), endDate.orEmpty()).joinToString("|"),
        )

        val packageValue = FlowPackage(
            id = rawID?.takeIf { it.isNotEmpty() } ?: fallbackID,
            originalName = name,
            totalMB = if (quotaType == QuotaType.UNLIMITED) null else nonNegative(total),
            usedMB = nonNegative(used),
            remainingMB = if (quotaType == QuotaType.UNLIMITED) null else nonNegative(remaining),
            detectedQuotaType = quotaType,
            detectedCategory = category,
            isShared = scope == ShareScope.SHARED,
            shareScope = scope,
            carryForwardScope = carryForward.scope,
            currentMonthTotalMB = carryForward.currentMonthTotalMB,
            carryForwardTotalMB = carryForward.carryForwardTotalMB,
            endDateText = endDate,
            rawType = rawType,
            rawCode = itemCode ?: feePolicyID,
        )
        return ParsedCandidate(
            packageValue,
            flowQualityScore(packageValue),
            flowConfidence(name, rawType, sourcePath, dictionary),
            sourcePath,
        )
    }

    private fun parseVoiceCandidate(candidate: Candidate): ParsedVoiceCandidate? {
        val dictionary = candidate.dictionary
        val name = dictionary.firstString(resourceNameKeys)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawType = dictionary.firstString(resourceTypeKeys)
        val sourcePath = candidate.path.joinToString(".")
        val kind = resourceKind(name, rawType, sourcePath, dictionary, candidate.inheritedKind)
        if (kind != ResourceKind.VOICE && !hasStrongVoiceEvidence(name, rawType, sourcePath, dictionary)) return null

        val total = durationMinutes(
            dictionary,
            listOf("totalTxt", "xexceedvalueTxt", "totalVoiceTxt", "voiceTotalTxt"),
            listOf("total", "xexceedvalue", "totalVoice", "voiceTotal"),
            listOf("totalUnit", "xexceedvalueUnit", "voiceUnit", "resourceUnit", "unit"),
        )
        val remaining = durationMinutes(
            dictionary,
            listOf("remainTxt", "leftTxt", "remainVoiceTxt", "voiceRemainTxt"),
            listOf("remain", "left", "remainVoice", "voiceRemain"),
            listOf("remainUnit", "leftUnit", "voiceUnit", "resourceUnit", "unit"),
        )
        var used = durationMinutes(
            dictionary,
            listOf("useTxt", "usedTxt", "useVoiceTxt", "voiceUsedTxt"),
            listOf("use", "used", "useVoice", "voiceUsed"),
            listOf("useUnit", "usedUnit", "voiceUnit", "resourceUnit", "unit"),
        )
        if (used == null && total != null && remaining != null) used = max(0.0, total - remaining)
        if (total == null && remaining == null && used == null) return null

        val explicitUnlimited = dictionary.firstBool(listOf("unlimited", "isUnlimited")) == true
        val unlimitedByName = name.contains("不限", ignoreCase = true) || name.contains("无限", ignoreCase = true)
        val isUnlimited = explicitUnlimited || unlimitedByName || (total ?: 0.0) < 0

        val feePolicyID = dictionary.firstString(listOf("feePolicyId"))
        val itemCode = dictionary.firstString(listOf("addupItemCode", "itemCode"))
        val rawID = dictionary.firstString(resourceIDKeys)
        val endDate = dictionary.firstString(endDateKeys)
        val fallbackID = fnv1a64(
            listOf("voice", normalizedResourceName(name), normalizedType(rawType), itemCode.orEmpty(), endDate.orEmpty()).joinToString("|"),
        )
        val identifier = "voice:" + (rawID?.takeIf { it.isNotEmpty() } ?: fallbackID)
        val packageValue = VoicePackage(
            id = identifier,
            originalName = name,
            totalMinutes = if (isUnlimited) null else nonNegative(total),
            usedMinutes = nonNegative(used),
            remainingMinutes = if (isUnlimited) null else nonNegative(remaining),
            isUnlimited = isUnlimited,
            isShared = shareScope(dictionary, sourcePath, name) == ShareScope.SHARED,
            endDateText = endDate,
            rawType = rawType,
            rawCode = itemCode ?: feePolicyID,
        )
        return ParsedVoiceCandidate(
            packageValue,
            voiceQualityScore(packageValue),
            voiceConfidence(name, rawType, sourcePath, dictionary),
            sourcePath,
        )
    }

    private fun deduplicateFlow(input: List<ParsedCandidate>): List<FlowPackage> {
        val indexByResource = mutableMapOf<String, Int>()
        val selected = mutableListOf<ParsedCandidate>()
        input.forEach { candidate ->
            val key = flowDeduplicationKey(candidate)
            if (key.isEmpty()) return@forEach
            val index = indexByResource[key]
            if (index != null) selected[index] = preferredFlow(selected[index], candidate)
            else {
                indexByResource[key] = selected.size
                selected += candidate
            }
        }
        val usedIDs = mutableSetOf<String>()
        return selected.map { candidate ->
            var packageValue = candidate.packageValue
            if (packageValue.id in usedIDs) {
                packageValue = packageValue.copy(
                    id = packageValue.id + "-" + fnv1a64(flowDeduplicationKey(candidate) + "|" + candidate.sourcePath),
                )
            }
            usedIDs += packageValue.id
            packageValue
        }
    }

    private fun flowDeduplicationKey(candidate: ParsedCandidate): String {
        val p = candidate.packageValue
        val signature = listOf(
            roundedSignature(p.totalMB), roundedSignature(p.usedMB), roundedSignature(p.remainingMB),
            p.rawCode.orEmpty(), normalizedType(p.rawType), p.resolvedShareScope.rawValue, p.resolvedCarryForwardScope.rawValue,
        ).joinToString("|")
        return normalizedResourceName(p.originalName) + "|" + signature
    }

    private fun deduplicateVoice(input: List<ParsedVoiceCandidate>): List<VoicePackage> {
        val indexByResource = mutableMapOf<String, Int>()
        val selected = mutableListOf<ParsedVoiceCandidate>()
        input.forEach { candidate ->
            val key = voiceDeduplicationKey(candidate)
            if (key.isEmpty()) return@forEach
            val index = indexByResource[key]
            if (index != null) selected[index] = preferredVoice(selected[index], candidate)
            else {
                indexByResource[key] = selected.size
                selected += candidate
            }
        }
        val usedIDs = mutableSetOf<String>()
        return selected.map { candidate ->
            var packageValue = candidate.packageValue
            if (packageValue.id in usedIDs) {
                packageValue = packageValue.copy(
                    id = packageValue.id + "-" + fnv1a64(voiceDeduplicationKey(candidate) + "|" + candidate.sourcePath),
                )
            }
            usedIDs += packageValue.id
            packageValue
        }
    }

    private fun voiceDeduplicationKey(candidate: ParsedVoiceCandidate): String {
        val p = candidate.packageValue
        val signature = listOf(
            roundedSignature(p.totalMinutes), roundedSignature(p.usedMinutes), roundedSignature(p.remainingMinutes),
            p.rawCode.orEmpty(), normalizedType(p.rawType),
        ).joinToString("|")
        return normalizedResourceName(p.originalName) + "|" + signature
    }

    private fun preferredFlow(lhs: ParsedCandidate, rhs: ParsedCandidate): ParsedCandidate {
        if (lhs.flowConfidence != rhs.flowConfidence) return if (lhs.flowConfidence > rhs.flowConfidence) lhs else rhs
        val lhsTotal = lhs.packageValue.totalMB ?: -1.0
        val rhsTotal = rhs.packageValue.totalMB ?: -1.0
        if (abs(lhsTotal - rhsTotal) > 0.01) return if (lhsTotal > rhsTotal) lhs else rhs
        val lhsRemaining = lhs.packageValue.remainingMB ?: -1.0
        val rhsRemaining = rhs.packageValue.remainingMB ?: -1.0
        if (abs(lhsRemaining - rhsRemaining) > 0.01) return if (lhsRemaining > rhsRemaining) lhs else rhs
        if (lhs.qualityScore != rhs.qualityScore) return if (lhs.qualityScore > rhs.qualityScore) lhs else rhs
        return lhs
    }

    private fun preferredVoice(lhs: ParsedVoiceCandidate, rhs: ParsedVoiceCandidate): ParsedVoiceCandidate {
        if (lhs.voiceConfidence != rhs.voiceConfidence) return if (lhs.voiceConfidence > rhs.voiceConfidence) lhs else rhs
        if (lhs.qualityScore != rhs.qualityScore) return if (lhs.qualityScore > rhs.qualityScore) lhs else rhs
        val lhsTotal = lhs.packageValue.totalMinutes ?: -1.0
        val rhsTotal = rhs.packageValue.totalMinutes ?: -1.0
        if (abs(lhsTotal - rhsTotal) > 0.01) return if (lhsTotal > rhsTotal) lhs else rhs
        val lhsRemaining = lhs.packageValue.remainingMinutes ?: -1.0
        val rhsRemaining = rhs.packageValue.remainingMinutes ?: -1.0
        if (abs(lhsRemaining - rhsRemaining) > 0.01) return if (lhsRemaining > rhsRemaining) lhs else rhs
        return lhs
    }

    private fun collectPackageDictionaries(
        element: JsonElement,
        path: List<String>,
        inheritedKind: ResourceKind?,
        result: MutableList<Candidate>,
    ) {
        when (element) {
            is JsonObject -> {
                val currentInheritedKind = containerKind(element, path) ?: inheritedKind
                if (isPackageDictionary(element)) result += Candidate(element, path, currentInheritedKind)
                element.forEach { (key, value) ->
                    collectPackageDictionaries(value, path + key, currentInheritedKind, result)
                }
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                collectPackageDictionaries(value, path + index.toString(), inheritedKind, result)
            }
            else -> Unit
        }
    }

    private fun isPackageDictionary(dictionary: JsonObject): Boolean {
        val hasName = resourceNameKeys.any(dictionary::containsKey)
        val quotaKeys = listOf(
            "total", "remain", "left", "use", "used", "xexceedvalue", "remainFlow", "totalFlow",
            "totalTxt", "remainTxt", "leftTxt", "useTxt", "usedTxt", "xexceedvalueTxt", "unlimited",
            "totalVoice", "voiceTotal", "remainVoice", "voiceRemain", "useVoice", "voiceUsed",
        )
        return hasName && quotaKeys.any(dictionary::containsKey)
    }

    private fun capacityMB(dictionary: JsonObject, textKeys: List<String>, numberKeys: List<String>, unitKeys: List<String>): Double? {
        textKeys.forEach { key -> dictionary[key].textValue()?.let(::parseCapacityText)?.let { return it } }
        val explicitUnit = unitKeys.firstNotNullOfOrNull { dictionary[it].textValue() }
        numberKeys.forEach { key -> dictionary[key].doubleValue()?.let { return convertToMB(it, explicitUnit) } }
        return null
    }

    private fun durationMinutes(dictionary: JsonObject, textKeys: List<String>, numberKeys: List<String>, unitKeys: List<String>): Double? {
        val explicitUnit = unitKeys.firstNotNullOfOrNull { dictionary[it].textValue() }
        textKeys.forEach { key -> dictionary[key].textValue()?.let { parseDurationText(it, explicitUnit) }?.let { return it } }
        numberKeys.forEach { key -> dictionary[key].doubleValue()?.let { return convertToMinutes(it, explicitUnit) } }
        return null
    }

    private fun parseCapacityText(text: String): Double? {
        val normalized = text.replace(",", "").uppercase()
        capacityWithUnitRegex.find(normalized)?.let { match ->
            return convertToMB(match.groupValues[1].toDouble(), match.groupValues[2])
        }
        return capacityNumberRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun parseDurationText(text: String, fallbackUnit: String?): Double? {
        val normalized = text.replace(",", "").uppercase()
        durationWithUnitRegex.find(normalized)?.let { match ->
            return convertToMinutes(match.groupValues[1].toDouble(), match.groupValues[2])
        }
        ambiguousMinuteWithMRegex.find(normalized)?.let { return it.groupValues[1].toDoubleOrNull() }
        val value = capacityNumberRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return convertToMinutes(value, fallbackUnit)
    }

    private fun convertToMB(value: Double, unit: String?): Double = when (unit?.trim()?.uppercase() ?: "MB") {
        "TB", "T" -> value * 1024 * 1024
        "GB", "G" -> value * 1024
        "KB", "K" -> value / 1024
        "B", "BYTE", "BYTES" -> value / 1024 / 1024
        else -> value
    }

    private fun convertToMinutes(value: Double, unit: String?): Double {
        val normalized = unit?.trim()?.uppercase() ?: "分钟"
        return when {
            normalized.contains("小时") || normalized.contains("钟头") || normalized in setOf("H", "HR", "HRS", "HOUR", "HOURS") -> value * 60
            normalized.contains("秒") || normalized in setOf("S", "SEC", "SECS", "SECOND", "SECONDS") -> value / 60
            else -> value
        }
    }

    private fun resourceKind(
        name: String,
        rawType: String?,
        path: String,
        dictionary: JsonObject,
        inheritedKind: ResourceKind?,
    ): ResourceKind {
        explicitResourceKind(rawType, dictionary)?.let { return it }
        inheritedKind?.let { return it }
        val combined = classificationText(name, rawType, path, dictionary)
        val unitText = allUnitText(dictionary)
        if (listOf("sms", "message", "短信", "条数", "短信条", "条").any(combined::contains) ||
            listOf("条", "条数").any(unitText::contains)
        ) return ResourceKind.SMS
        if (hasStrongVoiceEvidence(name, rawType, path, dictionary)) return ResourceKind.VOICE
        if (hasStrongFlowEvidence(name, rawType, path, dictionary)) return ResourceKind.FLOW
        return ResourceKind.UNKNOWN
    }

    private fun containerKind(dictionary: JsonObject, path: List<String>): ResourceKind? {
        val type = dictionary.firstString(listOf("type"))?.trim()?.lowercase().orEmpty()
        val text = type + "|" + path.joinToString(".").lowercase()
        return when {
            text.contains("smslist") || text.contains("sms") || text.contains("短信") -> ResourceKind.SMS
            text.contains("voice") || text.contains("unsharedvoicelist") || text.contains("语音") -> ResourceKind.VOICE
            text.contains("flow") || text.contains("unsharedflowlist") || text.contains("流量") -> ResourceKind.FLOW
            else -> null
        }
    }

    private fun explicitResourceKind(rawType: String?, dictionary: JsonObject): ResourceKind? {
        val normalizedRawType = rawType?.trim()?.lowercase().orEmpty()
        val resourceType = dictionary.firstString(listOf("resourceType", "resourcesType", "resourceTypeCode"))?.trim()?.lowercase() ?: normalizedRawType
        val addUpItemCode = dictionary.firstString(listOf("addupItemCode", "itemCode"))?.trim()
        val addUpItemName = dictionary.firstString(listOf("addUpItemName"))?.trim()?.lowercase().orEmpty()
        if (resourceType in setOf("02", "voice", "voiceresources", "unsharedvoicelist") || addUpItemCode == "40000") return ResourceKind.VOICE
        if (resourceType in setOf("01", "flow", "flowresources", "mlresources", "unsharedflowlist") ||
            dictionary.hasAnyValue(listOf("flowType")) || addUpItemCode in setOf("40008", "40025", "40026")
        ) return ResourceKind.FLOW
        if (resourceType in setOf("03", "sms", "smslist") || addUpItemName.contains("短信") || addUpItemName.contains("条")) return ResourceKind.SMS
        if (listOf("语音", "通话", "分钟").any(addUpItemName::contains)) return ResourceKind.VOICE
        if (listOf("流量", "gb", "mb").any(addUpItemName::contains)) return ResourceKind.FLOW
        return null
    }

    private fun classificationText(name: String, rawType: String?, path: String, dictionary: JsonObject): String {
        val parts = mutableListOf(name, rawType.orEmpty(), path)
        listOf(
            "addUpItemName", "resourceName", "resourceTypeName", "unit", "totalUnit", "remainUnit", "useUnit",
            "totalTxt", "remainTxt", "useTxt",
        ).forEach { parts += dictionary.firstString(listOf(it)).orEmpty() }
        return parts.joinToString("|").lowercase()
    }

    private fun hasStrongFlowEvidence(name: String, rawType: String?, path: String, dictionary: JsonObject): Boolean {
        val text = classificationText(name, rawType, path, dictionary)
        if (dictionary.hasAnyValue(listOf("flowType"))) return true
        if (dictionary.hasAnyValue(listOf(
                "totalFlow", "remainFlow", "useFlow", "usedFlow", "totalFlowTxt", "remainFlowTxt", "useFlowTxt", "usedFlowTxt",
            ))) return true
        if (dictionary.firstString(listOf("addupItemCode", "itemCode")) in setOf("40008", "40025", "40026")) return true
        return listOf("流量", "flow", "data", "上网", "gb", "mb", "kb", "tb").any(text::contains)
    }

    private fun hasStrongVoiceEvidence(name: String, rawType: String?, path: String, dictionary: JsonObject): Boolean {
        val text = classificationText(name, rawType, path, dictionary)
        if (dictionary.hasAnyValue(listOf(
                "totalVoice", "voiceTotal", "remainVoice", "voiceRemain", "useVoice", "voiceUsed", "totalVoiceTxt", "voiceTotalTxt",
                "remainVoiceTxt", "voiceRemainTxt", "useVoiceTxt", "voiceUsedTxt",
            ))) return true
        if (dictionary.firstString(listOf("addupItemCode", "itemCode")) == "40000") return true
        return listOf("语音", "通话", "分钟", "voice", "call", "minute", "minutes", "一家亲", "跨域群组").any(text::contains)
    }

    @Suppress("unused")
    private fun looksLikeAmbiguousDurationCandidate(dictionary: JsonObject): Boolean {
        val unitText = allUnitText(dictionary)
        if (unitText.split("|").map(String::trim).contains("m")) return true
        return listOf("totalTxt", "remainTxt", "leftTxt", "useTxt", "usedTxt", "xexceedvalueTxt")
            .mapNotNull { dictionary[it].textValue() }
            .any { text -> durationWithUnitRegex.containsMatchIn(text.uppercase()) || ambiguousMinuteWithMRegex.containsMatchIn(text.uppercase()) }
    }

    private fun flowConfidence(name: String, rawType: String?, path: String, dictionary: JsonObject): Int {
        val combined = classificationText(name, rawType, path, dictionary)
        var score = 0
        if (listOf("flow", "data", "流量", "上网").any(combined::contains)) score += 8
        val units = allUnitText(dictionary)
        if (listOf("gb", "mb", "kb", "tb").any(units::contains)) score += 6
        if (listOf("totalTxt", "remainTxt", "useTxt", "usedTxt", "xexceedvalueTxt").any(dictionary::containsKey)) score += 3
        return score
    }

    private fun voiceConfidence(name: String, rawType: String?, path: String, dictionary: JsonObject): Int {
        val combined = classificationText(name, rawType, path, dictionary)
        var score = 0
        if (listOf("voice", "speech", "call", "语音", "通话", "主叫", "一家亲", "跨域群组").any(combined::contains)) score += 10
        val units = allUnitText(dictionary)
        if (listOf("分钟", "小时", "minute", "min", "hour", "hr").any(units::contains)) score += 8
        if (listOf("totalVoice", "voiceTotal", "remainVoice", "voiceRemain", "useVoice", "voiceUsed").any(dictionary::containsKey)) score += 5
        return score
    }

    private fun allUnitText(dictionary: JsonObject): String = listOf(
        "unit", "totalUnit", "useUnit", "usedUnit", "remainUnit", "leftUnit", "resourceUnit", "voiceUnit", "flowUnit",
        "totalTxt", "remainTxt", "useTxt", "usedTxt",
    ).mapNotNull { dictionary[it].textValue() }.joinToString("|").lowercase()

    private fun flowQualityScore(packageValue: FlowPackage): Int {
        var score = 0
        if (packageValue.totalMB != null) score += 4
        if (packageValue.usedMB != null) score += 2
        if (packageValue.remainingMB != null) score += 2
        if (!packageValue.rawCode.isNullOrEmpty()) score += 2
        if (!packageValue.rawType.isNullOrEmpty()) score += 1
        if (!packageValue.endDateText.isNullOrEmpty()) score += 1
        return score
    }

    private fun roundedSignature(value: Double?): String = if (value?.isFinite() == true) "%.4f".format(java.util.Locale.US, value) else ""

    private fun voiceQualityScore(packageValue: VoicePackage): Int {
        var score = 0
        if (packageValue.totalMinutes != null) score += 4
        if (packageValue.usedMinutes != null) score += 2
        if (packageValue.remainingMinutes != null) score += 2
        if (!packageValue.rawCode.isNullOrEmpty()) score += 2
        if (!packageValue.rawType.isNullOrEmpty()) score += 1
        if (!packageValue.endDateText.isNullOrEmpty()) score += 1
        return score
    }

    private fun shareScope(dictionary: JsonObject, path: String, name: String): ShareScope {
        dictionary.firstString(listOf("typemark"))?.trim()?.let {
            if (it == "0") return ShareScope.SHARED
            if (it == "1") return ShareScope.UNSHARED
        }
        val normalizedPath = path.lowercase()
        if (normalizedPath.contains("unshared")) return ShareScope.UNSHARED
        if (normalizedPath.contains("sharedata") || normalizedPath.contains("resources")) return ShareScope.SHARED
        if (name.contains("非共享", ignoreCase = true)) return ShareScope.UNSHARED
        if (name.contains("共享", ignoreCase = true)) return ShareScope.SHARED
        dictionary.firstBool(listOf("shared", "isShared", "shareFlag", "isShare", "canShare"))?.let {
            return if (it) ShareScope.SHARED else ShareScope.UNSHARED
        }
        return ShareScope.UNKNOWN
    }

    private data class CarryForwardInfo(
        val scope: CarryForwardScope,
        val currentMonthTotalMB: Double?,
        val carryForwardTotalMB: Double?,
    )

    private fun carryForwardInfo(dictionary: JsonObject, totalMB: Double?): CarryForwardInfo {
        if (dictionary.firstString(listOf("resourceSource"))?.trim() == "1") {
            return CarryForwardInfo(CarryForwardScope.CARRY_FORWARD, null, nonNegative(totalMB))
        }
        val mergeFlag = dictionary.firstString(listOf("mergeFlag"))?.trim()
        val beforeTotal = capacityMB(
            dictionary,
            listOf("beforeTotalTxt"),
            listOf("beforeTotal"),
            listOf("beforeTotalUnit", "totalUnit", "flowUnit", "resourceUnit", "unit"),
        )
        if (mergeFlag != "1" || beforeTotal == null || totalMB == null || totalMB <= beforeTotal + 0.01) {
            return CarryForwardInfo(CarryForwardScope.NONE, null, null)
        }
        return CarryForwardInfo(CarryForwardScope.INCLUDED, nonNegative(beforeTotal), nonNegative(totalMB - beforeTotal))
    }

    private fun nonNegative(value: Double?): Double? = value?.let { max(0.0, it) }

    private val resourceNameKeys = listOf(
        "feePolicyName", "addUpItemName", "packageName", "policyName", "name", "resourceName", "elementName", "offerName", "productName",
    )
    private val resourceTypeKeys = listOf("resourceType", "type", "resourcesType", "resourceName", "resourceTypeName", "resourceTypeCode")
    private val resourceIDKeys = listOf("feePolicyId", "addupItemCode", "itemCode", "itemId", "resourceId", "id", "elementId", "offerId", "productId")
    private val endDateKeys = listOf("endDate", "endXsbDate", "expireDate", "validDate", "invalidDate")
}
