package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val passwordLoginJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

data class UnicomPasswordLoginResult(
    val credentials: AccountCredentials,
    val invalidAt: String?,
)

sealed interface UnicomPasswordLoginOutcome {
    data class Success(val result: UnicomPasswordLoginResult) : UnicomPasswordLoginOutcome
    data class CaptchaRequired(val challenge: UnicomLoginCaptchaChallenge) : UnicomPasswordLoginOutcome
}

sealed class UnicomPasswordLoginException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidMobile : UnicomPasswordLoginException("请输入正确的 11 位联通手机号")
    data object MissingPassword : UnicomPasswordLoginException("请输入联通 App 登录密码或登录专用密码")
    data object InvalidPublicKey : UnicomPasswordLoginException("联通登录公钥格式无效")
    data object PlaintextTooLong : UnicomPasswordLoginException("登录密码内容过长，无法完成 RSA 加密")
    class EncryptionFailed(detail: String, cause: Throwable? = null) :
        UnicomPasswordLoginException("登录信息加密失败：$detail", cause)
    data object InvalidResponse : UnicomPasswordLoginException("联通密码登录接口返回了无法识别的数据")
    data object InvalidCaptchaURL : UnicomPasswordLoginException("联通返回的安全验证地址无效")
    data object MissingCaptchaMobile : UnicomPasswordLoginException("联通未返回图片验证所需的账号参数")
    data object MissingCookie : UnicomPasswordLoginException("密码验证成功，但联通未返回可用 Cookie")
    data object MissingTokenOnline : UnicomPasswordLoginException("密码验证成功，但联通未返回 token_online")
    class PasswordRejected(val serverMessage: String) : UnicomPasswordLoginException(
        "$serverMessage\n\n请确认输入的是中国联通 App 登录密码/登录专用密码；如果仍失败，说明当前账号可能不再支持传统服务密码直接登录。",
    )
    class SmsVerificationRequired(val serverMessage: String) : UnicomPasswordLoginException(
        "$serverMessage\n\n当前账号本次登录需要短信验证码验证，请优先使用验证码登录。",
    )
    class Server(val serverMessage: String) : UnicomPasswordLoginException(serverMessage)
    class Network(cause: Throwable) : UnicomPasswordLoginException("passwordLoginNetworkFailed", cause)
}

/**
 * Source-derived China Unicom password login session.
 *
 * This intentionally reuses the M5-B transport/RSA/device-identity/Cookie contracts while
 * preserving the password-login-specific request fields, result classification and captcha code.
 * Password plaintext is transient and is never persisted by this layer.
 */
class UnicomPasswordLoginSession(
    private val identityStore: UnicomLoginDeviceIdentityStore,
    private val transport: UnicomTransport = OkHttpUnicomLoginTransport(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val random: Random = Random.Default,
    private val localIPv4Provider: () -> String? = ::defaultPasswordLoginIPv4Address,
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

    fun login(
        mobile: String,
        password: String,
        resultToken: String? = null,
        preferredAppID: String? = null,
    ): UnicomPasswordLoginOutcome {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != 11) throw UnicomPasswordLoginException.InvalidMobile

        val normalizedPassword = password.trim()
        if (normalizedPassword.isEmpty()) throw UnicomPasswordLoginException.MissingPassword

        val requestAppID = validPreferredAppID(preferredAppID) ?: identity.appID
        val encryptedMobile = encryptLoginValue(normalizedMobile)
        val encryptedPassword = encryptLoginValue(normalizedPassword)

        if (resultToken == null && !prepared) {
            prepareLoginSession(encryptedMobile)
        }

        val fields = linkedMapOf(
            "voipToken" to "citc-default-token-do-not-push",
            "deviceBrand" to DEVICE_BRAND,
            "deviceId" to identity.deviceID,
            "simOperator" to SIM_OPERATOR,
            "netWay" to "wifi",
            "deviceCode" to identity.deviceCode,
            "uniqueIdentifier" to identity.uniqueIdentifier,
            "deviceOS" to identity.deviceOS,
            "latitude" to "",
            "version" to VERSION,
            "pip" to localIPv4Provider().orEmpty(),
            "isFirstInstall" to "0",
            "remark4" to "",
            "keyVersion" to KEY_VERSION,
            "longitude" to "",
            "simCount" to "1",
            "mobile" to encryptedMobile,
            "isRemberPwd" to "false",
            "appId" to requestAppID,
            "reqtime" to requestTimeFormatter.format(localDateTime()),
            "deviceModel" to identity.deviceModel,
            "password" to encryptedPassword,
        )
        if (!resultToken.isNullOrEmpty()) fields["resultToken"] = resultToken

        val objectValue = postForm(LOGIN_URL, fields)
        val responseCode = recursiveString(objectValue, setOf("code", "rsp_code", "status")).orEmpty()
        val type = recursiveString(objectValue, setOf("type", "verifyType")).orEmpty()

        if (responseCode == CAPTCHA_CODE && type == CAPTCHA_TYPE) {
            return UnicomPasswordLoginOutcome.CaptchaRequired(captchaChallenge(objectValue))
        }

        if (!UnicomResponseStatus.isSuccess(responseCode)) {
            val message = recursiveString(objectValue, setOf("dsc", "rsp_desc", "desc", "message"))
                ?: "联通密码登录失败（code: ${responseCode.ifEmpty { "未知" }}）"
            when {
                message.contains("ECS11721") || message.contains("密码错误") ->
                    throw UnicomPasswordLoginException.PasswordRejected(message)
                message.contains("短信验证码") ->
                    throw UnicomPasswordLoginException.SmsVerificationRequired(message)
                else -> throw UnicomPasswordLoginException.Server(message)
            }
        }

        // Password-login source ordering differs from SMS login: update city before snapshotting
        // the final credential Cookie, so the returned Cookie carries the response-derived city.
        updateCityCookie(objectValue)

        val cookie = normalizedCookie()
        if (cookie.isEmpty()) throw UnicomPasswordLoginException.MissingCookie
        val tokenOnline = recursiveString(objectValue, setOf("token_online", "tokenOnline")).trimmedOrNull()
            ?: throw UnicomPasswordLoginException.MissingTokenOnline
        val returnedAppID = recursiveString(objectValue, setOf("appId", "appID")).trimmedOrNull()
        val invalidAt = recursiveString(objectValue, setOf("invalidat", "invalidAt"))

        return UnicomPasswordLoginOutcome.Success(
            UnicomPasswordLoginResult(
                credentials = AccountCredentials(
                    cookie = cookie,
                    appID = returnedAppID ?: requestAppID,
                    tokenOnline = tokenOnline,
                ),
                invalidAt = invalidAt,
            ),
        )
    }

    private fun prepareLoginSession(encryptedMobile: String) {
        seedBaseCookies()
        val milliseconds = clock.instant().toEpochMilli().toString()
        val jsonBody = linkedMapOf(
            "mobile" to percentEncodedPasswordLoginValue(encryptedMobile),
            "seq" to "__NSDictionaryM_${milliseconds}_${random.nextInt(1_000_000, 10_000_000)}",
            "sign" to "",
            "provinceCode" to provinceCode(),
            "timestamp" to milliseconds,
            "appVersion" to VERSION,
            "version" to SWITCH_VERSION,
            "deviceCode" to identity.deviceCode,
        )
        val request = UnicomRequest(
            url = SWITCH_URL,
            body = passwordLoginJson.encodeToString(jsonBody).toByteArray(Charsets.UTF_8),
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
        } catch (error: UnicomPasswordLoginException) {
            throw error
        } catch (error: Exception) {
            throw UnicomPasswordLoginException.Network(error)
        }
        captureCookies(response)
        return parseResponse(response.body)
    }

    private fun parseResponse(data: ByteArray): JsonElement {
        val parsed = try {
            passwordLoginJson.parseToJsonElement(data.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw UnicomPasswordLoginException.InvalidResponse
        }
        if (parsed !is JsonObject && parsed !is JsonArray) throw UnicomPasswordLoginException.InvalidResponse
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

    private fun captchaChallenge(objectValue: JsonElement): UnicomLoginCaptchaChallenge {
        val urlText = recursiveString(objectValue, setOf("url"))
            ?: throw UnicomPasswordLoginException.InvalidCaptchaURL
        val normalizedURL = urlText.replace("\\/", "/")
        val uri = runCatching { URI(normalizedURL) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank()) throw UnicomPasswordLoginException.InvalidCaptchaURL

        val payload = challengePayload(objectValue)
        val riskMobile = payload["mobile"].trimmedOrNull()
            ?: throw UnicomPasswordLoginException.MissingCaptchaMobile
        val title = payload["mainTitle"].trimmedOrNull() ?: "身份验证"
        val detail = payload["mainDesc"].trimmedOrNull()
            ?: recursiveString(objectValue, setOf("dsc"))
            ?: "请完成安全验证"

        return UnicomLoginCaptchaChallenge(
            title = title,
            message = detail,
            url = normalizedURL,
            bridgePayload = payload + ("mobile" to riskMobile),
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

    private fun validPreferredAppID(value: String?): String? {
        val appID = value.trimmedOrNull() ?: return null
        if (appID.length != 192) return null
        return appID.takeIf { candidate -> candidate.all { it in '0'..'9' || it in 'a'..'f' } }
    }

    private fun provinceCode(): String = cityCookie().substringBefore('|').ifBlank { "017" }

    private fun cityCookie(): String = identityStore.cityCookie().trimmedOrNull() ?: DEFAULT_CITY_COOKIE

    private fun normalizedCookie(): String = UnicomCookieCodec.normalize(accumulatedCookie)

    private fun encryptLoginValue(value: String): String = try {
        UnicomLoginRSAEncryptor.encrypt(value)
    } catch (_: UnicomLoginEncryptionException.InvalidPublicKey) {
        throw UnicomPasswordLoginException.InvalidPublicKey
    } catch (_: UnicomLoginEncryptionException.PlaintextTooLong) {
        throw UnicomPasswordLoginException.PlaintextTooLong
    } catch (error: UnicomLoginEncryptionException.EncryptionFailed) {
        val detail = error.cause?.message?.trimmedOrNull() ?: "设备不支持 RSA PKCS#1 加密"
        throw UnicomPasswordLoginException.EncryptionFailed(detail, error)
    }

    private fun officialUserAgent(): String =
        "ChinaUnicom4.x/12.14 (com.chinaunicom.mobilebusiness; build:13; iOS ${identity.deviceOS}) " +
            "Alamofire/4.7.3 unicom{version:$VERSION}"

    private fun localDateTime(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), clock.zone)

    companion object {
        const val LOGIN_URL = "https://loginxx.10010.com/mobileService/login.htm"
        const val SWITCH_URL = "https://loginxx.10010.com/login-web/v1/switch/getSwitch"
        const val VERSION = "iphone_c@12.1400"
        const val KEY_VERSION = "2"
        const val CHANNEL = "GGPD"
        const val SWITCH_VERSION = "237"
        const val DEFAULT_CITY_COOKIE = "017|170"
        const val DEVICE_BRAND = "iPhone"
        const val SIM_OPERATOR = "--,--,65535,65535,--@--,--,65535,65535,--"
        const val CAPTCHA_CODE = "ECS99999"
        const val CAPTCHA_TYPE = "10"

        private val requestTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val sessionTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

private fun percentEncodedPasswordLoginValue(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val allowed = unsigned in 'A'.code..'Z'.code ||
            unsigned in 'a'.code..'z'.code ||
            unsigned in '0'.code..'9'.code ||
            unsigned == '-'.code || unsigned == '.'.code || unsigned == '_'.code || unsigned == '~'.code
        if (allowed) append(unsigned.toChar()) else append("%%%02X".format(unsigned))
    }
}

private fun defaultPasswordLoginIPv4Address(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()
