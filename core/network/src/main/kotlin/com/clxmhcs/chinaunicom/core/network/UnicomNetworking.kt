package com.clxmhcs.chinaunicom.core.network

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val networkJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

data class UnicomCookieMutation(val name: String, val value: String?)

data class UnicomRequest(
    val url: String,
    val body: ByteArray = byteArrayOf(),
    val headers: Map<String, String> = emptyMap(),
)

data class UnicomRawResponse(
    val statusCode: Int,
    val body: ByteArray,
    val headers: Map<String, List<String>> = emptyMap(),
)

data class UnicomHTTPResponse(
    val data: ByteArray,
    val cookieMutations: List<UnicomCookieMutation>,
)

sealed class UnicomAPIException(message: String) : Exception(message) {
    data object InvalidResponse : UnicomAPIException("invalidResponse")
    data class HttpStatus(val statusCode: Int) : UnicomAPIException("httpStatus:$statusCode")
    data class Server(val serverMessage: String) : UnicomAPIException(serverMessage)
    data object SessionExpired : UnicomAPIException("sessionExpired")
    data object MissingCookie : UnicomAPIException("missingCookie")
    data object MissingCredentials : UnicomAPIException("missingCredentials")
    data object NoPackages : UnicomAPIException("noPackages")
}

fun interface UnicomTransport {
    fun post(request: UnicomRequest): UnicomRawResponse
}

class OkHttpUnicomTransport(timeoutMillis: Long = 16_000L) : UnicomTransport {
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        val builder = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(null))
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        client.newCall(builder.build()).execute().use { response ->
            val headers = response.headers.names().associateWith { name -> response.headers.values(name) }
            return UnicomRawResponse(
                statusCode = response.code,
                body = response.body?.bytes() ?: byteArrayOf(),
                headers = headers,
            )
        }
    }
}

class UnicomHTTPClient(
    private val transport: UnicomTransport = OkHttpUnicomTransport(),
    private val retryDelayMillis: Long = 1_000L,
) {
    fun post(
        url: String,
        body: ByteArray = byteArrayOf(),
        headers: Map<String, String> = emptyMap(),
    ): UnicomHTTPResponse {
        var attempt = 0
        while (true) {
            try {
                return performPost(url, body, headers)
            } catch (error: Exception) {
                if (attempt >= 1 || !shouldRetry(error)) throw error
                attempt += 1
                if (retryDelayMillis > 0) Thread.sleep(retryDelayMillis)
            }
        }
    }

    private fun performPost(
        url: String,
        body: ByteArray,
        headers: Map<String, String>,
    ): UnicomHTTPResponse {
        val response = transport.post(UnicomRequest(url, body, headers))
        if (response.statusCode !in 200..299) {
            throw UnicomAPIException.HttpStatus(response.statusCode)
        }
        return UnicomHTTPResponse(
            data = response.body,
            cookieMutations = UnicomCookieCodec.mutations(response.headers),
        )
    }

    private fun shouldRetry(error: Exception): Boolean = when (error) {
        is UnicomAPIException.HttpStatus -> error.statusCode in 500..599
        is IOException -> true
        else -> false
    }
}

object UnicomResponseStatus {
    val successCodes: Set<String> = setOf("0", "0000", "200", "success")
    val expiredCodes: Set<String> = setOf("9998", "999998", "999999", "0500")

    fun topLevelCode(data: ByteArray, keys: List<String> = listOf("code", "rsp_code", "status")): String? {
        val root = runCatching { networkJson.parseToJsonElement(data.toString(Charsets.UTF_8)) }.getOrNull()
            as? JsonObject ?: return null
        return keys.asSequence()
            .mapNotNull { key -> root[key].stringValue()?.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    fun isSuccess(code: String?): Boolean = code?.lowercase() in successCodes

    fun isExpired(code: String?): Boolean = code in expiredCodes

    fun responseLooksExpired(data: ByteArray): Boolean {
        val parsed = runCatching { networkJson.parseToJsonElement(data.toString(Charsets.UTF_8)) }.getOrNull()
        if (parsed != null) {
            val objectValue = parsed as? JsonObject ?: return false
            val code = listOf("code", "rsp_code", "status")
                .asSequence()
                .mapNotNull { objectValue[it].stringValue()?.trim() }
                .firstOrNull { it.isNotEmpty() }
            return isExpired(code)
        }

        val trimmed = data.toString(Charsets.UTF_8).trim()
        if (trimmed in expiredCodes) return true
        val text = trimmed.lowercase()
        return listOf("cookie无效", "cookie 无效", "未登录", "重新登录", "登录失效").any(text::contains)
    }
}

object UnicomCookieCodec {
    private data class CookiePair(val key: String, val name: String, val value: String)

    private val attributeNames = setOf(
        "domain", "path", "expires", "max-age", "samesite",
        "secure", "httponly", "priority", "partitioned",
    )

    private val combinedSetCookieSeparator = Regex(",(?=\\s*[!#$%&'*+\\-.^_`|~0-9A-Za-z]+=)")

    fun normalize(source: String): String = cookiePairs(source)
        .joinToString("; ") { "${it.name}=${it.value}" }

    fun applying(mutations: List<UnicomCookieMutation>, source: String): String {
        val existing = cookiePairs(source)
        val order = existing.mapTo(mutableListOf()) { it.key }
        val values = existing.associate { it.key to (it.name to it.value) }.toMutableMap()

        mutations.forEach { mutation ->
            val key = mutation.name.lowercase()
            if (key.isEmpty()) return@forEach
            val value = mutation.value
            if (value.isNullOrEmpty()) {
                values.remove(key)
                order.removeAll { it == key }
            } else {
                if (key !in values) order += key
                values[key] = mutation.name to value
            }
        }
        return order.mapNotNull { key -> values[key]?.let { "${it.first}=${it.second}" } }.joinToString("; ")
    }

    fun value(name: String, source: String): String? {
        val target = name.lowercase()
        return cookiePairs(source).firstOrNull { it.key == target }?.value
    }

    fun mutations(headers: Map<String, List<String>>): List<UnicomCookieMutation> {
        val values = headers.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
        return mutationsFromSetCookieHeaders(values)
    }

    fun mutationsFromSetCookieHeaders(headerValues: List<String>): List<UnicomCookieMutation> {
        val records = headerValues.flatMap { value -> combinedSetCookieSeparator.split(value) }
        val order = mutableListOf<String>()
        val mutations = mutableMapOf<String, UnicomCookieMutation>()

        records.forEach { record ->
            val attributes = record.split(';')
            val pair = attributes.firstOrNull() ?: return@forEach
            val separator = pair.indexOf('=')
            if (separator < 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim().trim('"')
            val key = name.lowercase()
            if (name.isEmpty()) return@forEach

            val deletesCookie = value.isEmpty() || attributes.drop(1).any { attribute ->
                val normalized = attribute.trim().lowercase()
                when {
                    normalized.startsWith("max-age=") -> normalized.removePrefix("max-age=").toIntOrNull()?.let { it <= 0 } ?: false
                    normalized.startsWith("expires=thu, 01 jan 1970") -> true
                    normalized.startsWith("expires=thu, 01-jan-1970") -> true
                    else -> false
                }
            }
            if (key !in mutations) order += key
            mutations[key] = UnicomCookieMutation(name, if (deletesCookie) null else value)
        }
        return order.mapNotNull(mutations::get)
    }

    private fun cookiePairs(source: String): List<CookiePair> {
        val normalized = source.trim()
            .replace("\r", "\n")
            .removePrefixIgnoreCase("Cookie:")
            .removePrefixIgnoreCase("Set-Cookie:")
        val order = mutableListOf<String>()
        val values = mutableMapOf<String, CookiePair>()

        normalized.split(Regex("[;\\n]")).forEach { rawPart ->
            val part = rawPart.trim()
            val separator = part.indexOf('=')
            if (separator < 0) return@forEach
            val name = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim().trim('"')
            val key = name.lowercase()
            if (name.isEmpty() || value.isEmpty() || key in attributeNames) return@forEach
            if (key !in values) order += key
            values[key] = CookiePair(key, name, value)
        }
        return order.mapNotNull(values::get)
    }
}

fun unicomFormEncoded(values: Map<String, String>): ByteArray =
    unicomFormEncoded(values.toList().sortedBy { it.first })

fun unicomFormEncoded(values: List<Pair<String, String>>): ByteArray = values.joinToString("&") { (key, value) ->
    "${percentEncode(key)}=${percentEncode(value)}"
}.toByteArray(Charsets.UTF_8)

private fun percentEncode(value: String): String {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val hex = "0123456789ABCDEF"
    return buildString(bytes.size * 3) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            val allowed = unsigned in 'A'.code..'Z'.code ||
                unsigned in 'a'.code..'z'.code ||
                unsigned in '0'.code..'9'.code ||
                unsigned == '-'.code || unsigned == '.'.code || unsigned == '_'.code || unsigned == '~'.code
            if (allowed) append(unsigned.toChar()) else {
                append('%')
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0F])
            }
        }
    }
}

internal fun parseNetworkJson(data: ByteArray): JsonElement =
    try {
        networkJson.parseToJsonElement(data.toString(Charsets.UTF_8))
    } catch (_: Exception) {
        throw UnicomAPIException.InvalidResponse
    }

internal fun JsonElement?.stringValue(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive === JsonNull) return null
    return primitive.content
}

internal fun JsonElement?.objectValue(): JsonObject? = this as? JsonObject
internal fun JsonElement?.objectList(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

internal fun recursiveString(element: JsonElement, keys: Set<String>): String? = when (element) {
    is JsonObject -> element.entries.firstNotNullOfOrNull { (key, value) ->
        if (key in keys) value.stringValue()?.takeIf { it.isNotEmpty() } else null
    } ?: element.values.firstNotNullOfOrNull { recursiveString(it, keys) }
    is JsonArray -> element.firstNotNullOfOrNull { recursiveString(it, keys) }
    else -> null
}

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this
