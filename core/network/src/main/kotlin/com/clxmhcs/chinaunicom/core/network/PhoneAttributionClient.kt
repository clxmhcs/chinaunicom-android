package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.PhoneCarrier
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class PhoneAttributionLookupResult(
    val location: String?,
    val carrier: PhoneCarrier,
)

sealed interface PhoneCarrierSegmentFetchResult {
    data class Matched(val carrier: PhoneCarrier) : PhoneCarrierSegmentFetchResult
    data object NoMatch : PhoneCarrierSegmentFetchResult
    data object Failed : PhoneCarrierSegmentFetchResult
}

interface PhoneAttributionClient {
    suspend fun fetchCarrierSegment(prefix: String): PhoneCarrierSegmentFetchResult
    suspend fun fetchAttribution(prefix: String): PhoneAttributionLookupResult?
}

class UnicomPhoneAttributionClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PhoneAttributionClient {

    override suspend fun fetchCarrierSegment(prefix: String): PhoneCarrierSegmentFetchResult {
        if (prefix.length != 3 || prefix.any { !it.isDigit() }) return PhoneCarrierSegmentFetchResult.NoMatch
        val url = "https://zj.v.api.aa1.cn/api/phone/2024/".toHttpUrl().newBuilder()
            .addQueryParameter("num", prefix)
            .build()
        val body = execute(url.toString()) ?: return PhoneCarrierSegmentFetchResult.Failed
        val root = parseObject(body) ?: return PhoneCarrierSegmentFetchResult.NoMatch
        val carriers = carriersIn(root).filter { it != PhoneCarrier.UNKNOWN }.toSet()
        val carrier = if (carriers.size == 1) carriers.first() else null
        return carrier?.let(PhoneCarrierSegmentFetchResult::Matched) ?: PhoneCarrierSegmentFetchResult.NoMatch
    }

    override suspend fun fetchAttribution(prefix: String): PhoneAttributionLookupResult? {
        if (prefix.length < 7 || prefix.any { !it.isDigit() }) return null
        val url = "https://cx.shouji.360.cn/phonearea.php".toHttpUrl().newBuilder()
            .addQueryParameter("number", prefix)
            .build()
        val body = execute(url.toString()) ?: return null
        val root = parseObject(body) ?: return null
        val data = root["data"] as? JsonObject ?: return null
        val location = normalizedLocation(data["city"]) ?: normalizedLocation(data["province"])
        val carrier = carrierIn(data) ?: PhoneCarrier.UNKNOWN
        return if (location == null && carrier == PhoneCarrier.UNKNOWN) null else PhoneAttributionLookupResult(location, carrier)
    }

    private fun execute(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string()
        }
    }.getOrNull()

    private fun parseObject(raw: String): JsonObject? {
        val text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) return null
        return runCatching { json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun normalizedLocation(element: JsonElement?): String? {
        var value = (element as? JsonPrimitive)?.content?.trim().orEmpty()
        if (value.isEmpty() || value == "未知") return null
        for (suffix in listOf("市", "省", "壮族自治区", "回族自治区", "维吾尔自治区", "自治区")) {
            if (value.endsWith(suffix)) {
                value = value.dropLast(suffix.length).trim()
                break
            }
        }
        return value.takeIf(String::isNotEmpty)
    }

    private fun carriersIn(element: JsonElement): List<PhoneCarrier> = when (element) {
        is JsonPrimitive -> listOfNotNull(carrierFromText(element.content))
        is JsonArray -> element.flatMap(::carriersIn)
        is JsonObject -> {
            val direct = CARRIER_KEYS.mapNotNull { key -> element[key]?.let(::carrierIn) }
            if (direct.isNotEmpty()) direct else element.values.flatMap(::carriersIn)
        }
        else -> emptyList()
    }

    private fun carrierIn(element: JsonElement): PhoneCarrier? = carriersIn(element).firstOrNull()

    private fun carrierFromText(text: String): PhoneCarrier? = when {
        text.contains("联通") -> PhoneCarrier.CHINA_UNICOM
        text.contains("移动") -> PhoneCarrier.CHINA_MOBILE
        text.contains("电信") -> PhoneCarrier.CHINA_TELECOM
        text.contains("广电") || text.contains("广播电视") -> PhoneCarrier.CHINA_BROADNET
        else -> null
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"
        private val CARRIER_KEYS = listOf(
            "sp", "isp", "carrier", "company", "operator", "supplier", "serviceProvider", "service_provider",
        )
    }
}
