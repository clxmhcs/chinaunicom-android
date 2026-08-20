package com.clxmhcs.chinaunicom.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

internal val parserJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

internal fun parseJson(data: ByteArray): JsonElement =
    parserJson.parseToJsonElement(data.toString(Charsets.UTF_8))

internal fun JsonElement?.textValue(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive === JsonNull) return null
    return primitive.content
}

internal fun JsonElement?.nonEmptyText(): String? =
    textValue()?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonElement?.doubleValue(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    primitive.doubleOrNull?.let { return it }
    return primitive.content.replace(",", "").toDoubleOrNull()
}

internal fun JsonElement?.boolValue(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    primitive.doubleOrNull?.let { return it != 0.0 }
    return when (primitive.content.trim().lowercase()) {
        "1", "true", "yes", "y", "shared" -> true
        "0", "false", "no", "n", "unshared" -> false
        else -> null
    }
}

internal fun JsonElement?.objects(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject.firstString(keys: List<String>): String? =
    keys.asSequence().mapNotNull { this[it].textValue() }.firstOrNull { it.isNotEmpty() }

internal fun JsonObject.firstBool(keys: List<String>): Boolean? =
    keys.asSequence().mapNotNull { this[it].boolValue() }.firstOrNull()

internal fun JsonObject.hasAnyValue(keys: List<String>): Boolean = keys.any { key ->
    val value = this[key] ?: return@any false
    value.textValue()?.trim()?.isNotEmpty() ?: true
}

internal fun recursiveString(element: JsonElement, keys: Set<String>): String? = when (element) {
    is JsonObject -> {
        element.entries.firstNotNullOfOrNull { (key, value) ->
            if (key in keys) value.textValue()?.takeIf { it.isNotEmpty() } else null
        } ?: element.values.firstNotNullOfOrNull { recursiveString(it, keys) }
    }
    is JsonArray -> element.firstNotNullOfOrNull { recursiveString(it, keys) }
    else -> null
}

internal fun capacityMB(value: JsonElement?, numericFallback: Boolean): Double? {
    value ?: return null
    val primitiveNumber = value.doubleValue()
    val source = value.nonEmptyText()
    if (source == null) return if (numericFallback) primitiveNumber else null
    val match = Regex(
        "([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|G|MB|M)(?![A-Za-z])",
        RegexOption.IGNORE_CASE,
    ).find(source) ?: return if (numericFallback) primitiveNumber else null
    val numeric = match.groupValues[1].toDoubleOrNull() ?: return if (numericFallback) primitiveNumber else null
    return when (match.groupValues[2].uppercase()) {
        "TB" -> numeric * 1024 * 1024
        "GB", "G" -> numeric * 1024
        "MB", "M" -> numeric
        else -> null
    }
}

internal fun fnv1a64(value: String): String {
    var hash = 1469598103934665603uL
    value.encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16)
}

internal fun normalizedResourceName(value: String): String =
    value.filterNot(Char::isWhitespace)
        .replace("（", "(")
        .replace("）", ")")
        .lowercase()

internal fun normalizedType(value: String?): String = value?.trim()?.lowercase().orEmpty()
