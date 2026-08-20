package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal class RemainingUnlimitedFlowResponseNormalizer {
    private data class CandidateIndex(
        val feePolicyIDs: MutableSet<String> = mutableSetOf(),
        val normalizedNames: MutableSet<String> = mutableSetOf(),
    )

    fun normalize(snapshot: RemainingQuerySnapshot, responseData: ByteArray): RemainingQuerySnapshot {
        val root = runCatching { parseNetworkJson(responseData) as? JsonObject }.getOrNull() ?: return snapshot
        val payload = payloadRoot(root)
        val speedLimitMB = summarySpeedLimitMB(payload) ?: summarySpeedLimitMB(root) ?: return snapshot
        val candidates = unlimitedCandidates(payload)
        if (candidates.feePolicyIDs.isEmpty() && candidates.normalizedNames.isEmpty()) return snapshot

        return snapshot.copy(
            flowPackages = snapshot.flowPackages.map { packageValue ->
                if (packageValue.category != RemainingFlowCategory.GENERAL ||
                    hasFiniteQuotaEvidence(packageValue) ||
                    !matches(packageValue, candidates)
                ) {
                    packageValue
                } else {
                    packageValue.copy(
                        totalMB = null,
                        remainingMB = null,
                        isUnlimited = true,
                        speedLimitMB = packageValue.speedLimitMB ?: speedLimitMB,
                    )
                }
            },
        )
    }

    private fun payloadRoot(root: JsonObject): JsonObject {
        val data = root["data"] as? JsonObject ?: return root
        return if (listOf("resources", "unshared", "shareData", "flowSumList", "summary").any(data::containsKey)) data else root
    }

    private fun unlimitedCandidates(value: JsonElement): CandidateIndex {
        val index = CandidateIndex()
        fun scan(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if (hasUnlimitedSignature(element)) {
                        element["feePolicyId"].stringValue().trimmedOrNull()?.let(index.feePolicyIDs::add)
                        resourceName(element)?.let(::normalizedName)?.let(index.normalizedNames::add)
                    }
                    element.values.forEach(::scan)
                }
                is JsonArray -> element.forEach(::scan)
                else -> Unit
            }
        }
        scan(value)
        return index
    }

    private fun hasUnlimitedSignature(item: JsonObject): Boolean {
        val total = item.number("total") ?: return false
        val used = item.number("use") ?: return false
        val remaining = item.number("remain") ?: return false
        if (item["flowType"].stringValue() != "1" || item.flag("limited") != true || abs(total) > 0.01 || used <= 0 || remaining >= 0) {
            return false
        }
        return abs(remaining + used) <= max(1.0, used * 0.01)
    }

    private fun summarySpeedLimitMB(value: JsonElement): Double? = when (value) {
        is JsonObject -> {
            val hasLimitContext = value.containsKey("limitValue") &&
                (value.containsKey("limitSum") || value.containsKey("limitSpeed") || value.containsKey("speedState") || value.containsKey("fengdingstate"))
            if (hasLimitContext) speedLimitValueMB(value["limitValue"]) ?: value.values.firstNotNullOfOrNull(::summarySpeedLimitMB)
            else value.values.firstNotNullOfOrNull(::summarySpeedLimitMB)
        }
        is JsonArray -> value.firstNotNullOfOrNull(::summarySpeedLimitMB)
        else -> null
    }

    private fun speedLimitValueMB(value: JsonElement?): Double? {
        val text = value.stringValue().trimmedOrNull() ?: return null
        capacityMB(text)?.let { return normalizeThresholdMB(it) }
        val numeric = text.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return normalizeThresholdMB(if (numeric <= 1024) numeric * 1024 else numeric)
    }

    private fun capacityMB(source: String): Double? {
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|G|MB|M)(?![A-Za-z])", RegexOption.IGNORE_CASE).find(source) ?: return null
        val numeric = match.groupValues[1].toDoubleOrNull() ?: return null
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
            val wholeGB = max(1.0, round(value / 1024)) * 1024
            if (abs(wholeGB - value) / max(value, 1.0) <= 0.12) return wholeGB
        }
        return value
    }

    private fun matches(packageValue: RemainingFlowPackage, candidates: CandidateIndex): Boolean {
        if (packageValue.feePolicyID?.let(candidates.feePolicyIDs::contains) == true) return true
        return normalizedName(packageValue.name) in candidates.normalizedNames
    }

    private fun hasFiniteQuotaEvidence(packageValue: RemainingFlowPackage): Boolean = (packageValue.totalMB ?: 0.0) > 0

    private fun resourceName(item: JsonObject): String? = listOf(
        "feePolicyName", "addUpItemName", "resourceName", "packageName", "productName", "name", "title",
    ).asSequence().mapNotNull { item[it].stringValue().trimmedOrNull() }.firstOrNull()

    private fun normalizedName(value: String): String = value.trim()
        .replace(" ", "")
        .replace("（", "(")
        .replace("）", ")")
        .lowercase()

    private fun JsonObject.number(key: String): Double? = this[key].stringValue()?.replace(",", "")?.toDoubleOrNull()

    private fun JsonObject.flag(key: String): Boolean? = when (this[key].stringValue()?.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }
}
