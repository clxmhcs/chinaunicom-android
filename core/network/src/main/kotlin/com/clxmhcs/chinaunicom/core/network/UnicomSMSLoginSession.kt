package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val loginJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

data class UnicomLoginDeviceIdentity(
    val deviceCode: String,
    val uniqueIdentifier: String,
    val deviceID: String,
    val appID: String,
    val deviceModel: String,
    val deviceOS: String,
)

interface UnicomLoginDeviceIdentityStore {
    fun identity(): UnicomLoginDeviceIdentity
    fun cityCookie(): String
    fun updateCityCookie(value: String)
}

data class UnicomSMSLoginResult(
    val credentials: AccountCredentials,
    val invalidAt: String?,
)

data class UnicomLoginCaptchaChallenge(
    val title: String,
    val message: String,
    val url: String,
    val bridgePayload: Map<String, String>,
)

sealed interface UnicomSMSSendOutcome {
    data class CodeSent(val message: String) : UnicomSMSSendOutcome
    data class CaptchaRequired(val challenge: UnicomLoginCaptchaChallenge) : UnicomSMSSendOutcome
}

sealed class UnicomSMSLoginException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidMobile : UnicomSMSLoginException("请输入正确的 11 位联通手机号")
    data object InvalidCode : UnicomSMSLoginException("请输入收到的 6 位短信验证码")
    data object InvalidResponse : UnicomSMSLoginException("联通短信验证码登录接口返回了无法识别的数据")
    data object InvalidCaptchaURL : UnicomSMSLoginException("联通返回的短信安全验证地址无效")
    data object MissingCookie : UnicomSMSLoginException("验证码登录成功，但联通未返回可用 Cookie")
    data object MissingTokenOnline : UnicomSMSLoginException("验证码登录成功，但联通未返回 token_online")
    class Server(val serverMessage: String) : UnicomSMSLoginException(serverMessage)
    class Network(cause: Throwable) : UnicomSMSLoginException("smsLoginNetworkFailed", cause)
}

/**
 * Login transport equivalent to the iOS ephemeral URLSession used by SMS/password login.
 * It intentionally owns no CookieJar: Set-Cookie mutations are returned to the session and
 * accumulated explicitly so production and unit-test behavior share the same state machine.
 */
class OkHttpUnicomLoginTransport(
    requestTimeoutMillis: Long = 25_000L,
    resourceTimeoutMillis: Long = 35_000L,
) : UnicomTransport {
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(resourceTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        val builder = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(null))
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        client.newCall(builder.build()).execute().use { response ->
            val headerMap = response.headers.names().associateWith { name -> response.headers.values(name) }
            val rawBody = response.body?.bytes() ?: byteArrayOf()
            val contentEncoding = response.header("Content-Encoding").orEmpty()
            val body = if (contentEncoding.equals("gzip", ignoreCase = true) && rawBody.isNotEmpty()) {
                runCatching {
                    GZIPInputStream(ByteArrayInputStream(rawBody)).use { it.readBytes() }
                }.getOrDefault(rawBody)
            } else {
                rawBody
            }
            return UnicomRawResponse(
                statusCode = response.code,
                body = body,
                headers = headerMap,
            )
        }
    }
}

class UnicomSMSLoginSession(
    private val identityStore: UnicomLoginDeviceIdentityStore,
    private val transport: UnicomTransport = OkHttpUnicomLoginTransport(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val random: Random = Random.Default,
    private val localIPv4Provider: () -> String? = ::defaultLocalIPv4Address,
) {
    private val identity = identityStore.identity()
    private val pvSessionID = sessionTimeFormatter.format(localDateTime()) + identity.deviceCode
    private var accumulatedCookie = ""
    private var prepared = false

    fun captchaSystemInfo(): Map<String, String> = mapOf(
        "devicedId" to identity.deviceCode,
        "deviceCode" to identity.deviceCode,
        "deviceId" to identity.deviceID,
        "deviceModel" to identity.deviceModel,
        "deviceBrand" to DEVICE_BRAND,
        "deviceOS" to identity.deviceOS,
        "appVersion" to VERSION,
        "clientVersion" to VERSION,
    )

    fun currentCookieHeader(): String = normalizedCookie()

    fun sendCode(
        mobile: String,
        resultToken: String? = null,
        preferredAppID: String? = null,
    ): UnicomSMSSendOutcome {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != 11) throw UnicomSMSLoginException.InvalidMobile

        val requestAppID = preferredAppID.trimmedOrNull() ?: identity.appID
        val encryptedMobile = encryptLoginValue(normalizedMobile)
        if (resultToken == null && !prepared) {
            prepareLoginSession(encryptedMobile)
        }

        val fields = linkedMapOf(
            "loginCodeLen" to "6",
            "voipToken" to "citc-default-token-do-not-push",
            "deviceBrand" to DEVICE_BRAND,
            "simOperator" to SIM_OPERATOR,
            "deviceId" to identity.deviceID,
            "netWay" to "4G",
            "provinceCode" to provinceCode(),
            "deviceCode" to identity.deviceCode,
            "deviceOS" to identity.deviceOS,
            "uniqueIdentifier" to identity.uniqueIdentifier,
            "version" to VERSION,
            "pip" to localIPv4Provider().orEmpty(),
            "isFirstInstall" to "0",
            "remark4" to "",
            "simCount" to "1",
            "mobile" to encryptedMobile,
            "appId" to requestAppID,
            "cityCode" to cityCode(),
            "reqtime" to requestTimeFormatter.format(localDateTime()),
            "deviceModel" to identity.deviceModel,
        )
        if (!resultToken.isNullOrEmpty()) fields["resultToken"] = resultToken

        val objectValue = postForm(SEND_CODE_URL, fields)
        val code = recursiveString(objectValue, setOf("rsp_code", "code", "status")).orEmpty()
        val type = recursiveString(objectValue, setOf("type", "verifyType")).orEmpty()
        val message = recursiveString(objectValue, setOf("rsp_desc", "dsc", "desc", "message"))
            ?: "验证码已发送，请注意查收。"

        if (code == "ECS99998" && type == "10") {
            return UnicomSMSSendOutcome.CaptchaRequired(captchaChallenge(objectValue, message))
        }
        if (code != "0000" && code != "0") throw UnicomSMSLoginException.Server(message)
        return UnicomSMSSendOutcome.CodeSent(message)
    }

    fun login(
        mobile: String,
        code: String,
        preferredAppID: String? = null,
    ): UnicomSMSLoginResult {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != 11) throw UnicomSMSLoginException.InvalidMobile
        val normalizedCode = code.filter(Char::isDigit)
        if (normalizedCode.length != 6) throw UnicomSMSLoginException.InvalidCode

        val requestAppID = preferredAppID.trimmedOrNull() ?: identity.appID
        val encryptedMobile = encryptLoginValue(normalizedMobile)
        if (!prepared) prepareLoginSession(encryptedMobile)

        val fields = linkedMapOf(
            "voipToken" to "citc-default-token-do-not-push",
            "loginStyle" to "0",
            "deviceBrand" to DEVICE_BRAND,
            "deviceId" to identity.deviceID,
            "simOperator" to SIM_OPERATOR,
            "netWay" to "4G",
            "voiceoff_flag" to "1",
            "deviceCode" to identity.deviceCode,
            "deviceOS" to identity.deviceOS,
            "uniqueIdentifier" to identity.uniqueIdentifier,
            "latitude" to "",
            "version" to VERSION,
            "yw_code" to "",
            "pip" to localIPv4Provider().orEmpty(),
            "isFirstInstall" to "0",
            "remark4" to "",
            "keyVersion" to KEY_VERSION,
            "longitude" to "",
            "simCount" to "1",
            "mobile" to encryptedMobile,
            "appId" to requestAppID,
            "deviceModel" to identity.deviceModel,
            "reqtime" to requestTimeFormatter.format(localDateTime()),
            "password" to encryptLoginValue(normalizedCode),
        )

        val objectValue = postForm(LOGIN_URL, fields)
        val responseCode = recursiveString(objectValue, setOf("code", "rsp_code", "status")).orEmpty()
        val message = recursiveString(objectValue, setOf("dsc", "rsp_desc", "desc", "message"))
            ?: "联通短信验证码登录失败（code: ${responseCode.ifEmpty { "未知" }}）"
        if (responseCode != "0" && responseCode != "0000") {
            throw UnicomSMSLoginException.Server(message)
        }

        val appID = recursiveString(objectValue, setOf("appId", "appID")).trimmedOrNull() ?: requestAppID
        val tokenOnline = recursiveString(objectValue, setOf("token_online", "tokenOnline")).trimmedOrNull()
            ?: throw UnicomSMSLoginException.MissingTokenOnline
        val cookie = normalizedCookie()
        if (cookie.isEmpty()) throw UnicomSMSLoginException.MissingCookie
        val invalidAt = recursiveString(objectValue, setOf("invalidat", "invalidAt"))

        // Source ordering is deliberate: the returned credential Cookie is snapshotted before
        // the list-derived city Cookie is updated for subsequent login sessions.
        updateCityCookie(objectValue)

        return UnicomSMSLoginResult(
            credentials = AccountCredentials(
                cookie = cookie,
                appID = appID,
                tokenOnline = tokenOnline,
            ),
            invalidAt = invalidAt,
        )
    }

    private fun prepareLoginSession(encryptedMobile: String) {
        seedBaseCookies()
        val milliseconds = clock.instant().toEpochMilli().toString()
        val jsonBody = linkedMapOf(
            "mobile" to percentEncodedFormValue(encryptedMobile),
            "seq" to "__NSDictionaryM_${milliseconds}_${random.nextInt(1_000_000, 10_000_000)}",
            "sign" to "",
            "provinceCode" to provinceCode(),
            "timestamp" to milliseconds,
            "appVersion" to VERSION,
            "version" to SWITCH_VERSION,
            "deviceCode" to identity.deviceCode,
        )
        val body = loginJson.encodeToString(jsonBody).toByteArray(Charsets.UTF_8)
        val request = UnicomRequest(
            url = SWITCH_URL,
            body = body,
            headers = officialHeaders("application/json"),
        )
        runCatching { transport.post(request) }
            .onSuccess(::captureCookies)
        prepared = true
    }

    private fun postForm(url: String, fields: Map<String, String>): JsonElement {
        val response = try {
            transport.post(
                UnicomRequest(
                    url = url,
                    body = unicomFormEncoded(fields),
                    headers = officialHeaders("application/x-www-form-urlencoded"),
                ),
            )
        } catch (error: UnicomSMSLoginException) {
            throw error
        } catch (error: Exception) {
            throw UnicomSMSLoginException.Network(error)
        }
        captureCookies(response)
        return parseLoginResponse(response.body)
    }

    private fun parseLoginResponse(data: ByteArray): JsonElement {
        val parsed = try {
            loginJson.parseToJsonElement(data.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw UnicomSMSLoginException.InvalidResponse
        }
        if (parsed !is JsonObject && parsed !is JsonArray) throw UnicomSMSLoginException.InvalidResponse
        return parsed
    }

    private fun officialHeaders(contentType: String): Map<String, String> = buildMap {
        put("Content-Type", contentType)
        put("User-Agent", officialUserAgent())
        put("Accept", "*/*")
        put("Accept-Language", "zh-Hans-CN;q=1.0")
        put("Accept-Encoding", "gzip;q=1.0, compress;q=0.5")
        val cookie = normalizedCookie()
        if (cookie.isNotEmpty()) put("Cookie", cookie)
    }

    private fun seedBaseCookies() {
        setCookie("PvSessionId", pvSessionID)
        setCookie("c_version", VERSION)
        setCookie("channel", CHANNEL)
        setCookie("devicedId", identity.deviceCode)
        setCookie("city", cityCookie())
    }

    private fun setCookie(name: String, value: String) {
        accumulatedCookie = UnicomCookieCodec.applying(
            listOf(UnicomCookieMutation(name, value)),
            accumulatedCookie,
        )
    }

    private fun captureCookies(response: UnicomRawResponse) {
        val mutations = UnicomCookieCodec.mutations(response.headers)
        if (mutations.isNotEmpty()) {
            accumulatedCookie = UnicomCookieCodec.applying(mutations, accumulatedCookie)
        }
    }

    private fun captchaChallenge(objectValue: JsonElement, fallbackMessage: String): UnicomLoginCaptchaChallenge {
        val urlText = recursiveString(objectValue, setOf("url"))
            ?: throw UnicomSMSLoginException.InvalidCaptchaURL
        val normalizedURL = urlText.replace("\\/", "/")
        val uri = runCatching { URI(normalizedURL) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw UnicomSMSLoginException.InvalidCaptchaURL
        }
        val payload = challengePayload(objectValue).toMutableMap().apply { put("channel", "smssms") }
        val title = payload["mainTitle"].trimmedOrNull() ?: "安全验证"
        val detail = payload["mainDesc"].trimmedOrNull()
            ?: recursiveString(objectValue, setOf("dsc"))
            ?: fallbackMessage
        return UnicomLoginCaptchaChallenge(
            title = title,
            message = detail,
            url = normalizedURL,
            bridgePayload = payload,
        )
    }

    private fun challengePayload(objectValue: JsonElement): Map<String, String> {
        val dictionary = objectValue as? JsonObject ?: return emptyMap()
        val keys = setOf(
            "type", "curNum", "desmobile", "doubleConfirm", "mainDesc", "mainTitle",
            "userType", "url", "menuurl", "mobile", "filename", "dsc", "code",
        )
        return keys.mapNotNull { key ->
            val primitive = dictionary[key] as? JsonPrimitive ?: return@mapNotNull null
            key to primitive.content
        }.toMap()
    }

    private fun updateCityCookie(objectValue: JsonElement) {
        val dictionary = objectValue as? JsonObject ?: return
        val list = dictionary["list"] as? JsonArray ?: return
        val first = list.firstOrNull() as? JsonObject ?: return
        val province = (first["proCode"] as? JsonPrimitive)?.content.trimmedOrNull() ?: return
        val city = (first["cityCode"] as? JsonPrimitive)?.content.trimmedOrNull() ?: return
        val value = "$province|$city"
        identityStore.updateCityCookie(value)
        setCookie("city", value)
    }

    private fun normalizeMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == 13 && digits.startsWith("86")) digits.drop(2) else digits
    }

    private fun provinceCode(): String = cityCookie().substringBefore('|').ifBlank { "017" }

    private fun cityCode(): String {
        val parts = cityCookie().split('|')
        return parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: "170"
    }

    private fun cityCookie(): String = identityStore.cityCookie().trimmedOrNull() ?: DEFAULT_CITY_COOKIE

    private fun normalizedCookie(): String = UnicomCookieCodec.normalize(accumulatedCookie)

    private fun encryptLoginValue(value: String): String = try {
        UnicomLoginRSAEncryptor.encrypt(value)
    } catch (error: UnicomLoginEncryptionException) {
        throw UnicomSMSLoginException.Network(error)
    }

    private fun officialUserAgent(): String =
        "ChinaUnicom4.x/12.14 (com.chinaunicom.mobilebusiness; build:13; iOS ${identity.deviceOS}) " +
            "Alamofire/4.7.3 unicom{version:$VERSION}"

    private fun localDateTime(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), clock.zone)

    companion object {
        const val SEND_CODE_URL = "https://loginxx.10010.com/mobileService/sendRadomNum.htm"
        const val LOGIN_URL = "https://loginxx.10010.com/mobileService/radomLogin.htm"
        const val SWITCH_URL = "https://loginxx.10010.com/login-web/v1/switch/getSwitch"
        const val VERSION = "iphone_c@12.1400"
        const val KEY_VERSION = "2"
        const val CHANNEL = "GGPD"
        const val SWITCH_VERSION = "237"
        const val DEFAULT_CITY_COOKIE = "017|170"
        const val DEVICE_BRAND = "iPhone"
        const val SIM_OPERATOR = "--,--,65535,65535,--@--,--,65535,65535,--"

        private val requestTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val sessionTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

private fun percentEncodedFormValue(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val allowed = unsigned in 'A'.code..'Z'.code ||
            unsigned in 'a'.code..'z'.code ||
            unsigned in '0'.code..'9'.code ||
            unsigned == '-'.code || unsigned == '.'.code || unsigned == '_'.code || unsigned == '~'.code
        if (allowed) append(unsigned.toChar()) else append("%%%02X".format(unsigned))
    }
}

private fun defaultLocalIPv4Address(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()
