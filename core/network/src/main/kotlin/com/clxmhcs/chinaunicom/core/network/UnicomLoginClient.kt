package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.nio.charset.StandardCharsets
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Network-only login state for one user-initiated login attempt. It is intentionally not a
 * singleton: callers must create a fresh instance per attempt and persist only the returned
 * [AccountCredentials] through M5-A after a successful result.
 */
class UnicomLoginClient(
    private val identity: UnicomLoginIdentity,
    private val http: UnicomHTTPClient = UnicomHTTPClient(),
    private val requestTime: () -> String = { requestTimeFormatter.format(LocalDateTime.now()) },
    private val currentMillis: () -> Long = System::currentTimeMillis,
) {
    private var accumulatedCookie = ""
    private var prepared = false
    private var cityCookie = identity.cityCookie

    fun sendSmsCode(
        mobile: String,
        captchaResultToken: String? = null,
        preferredAppId: String? = null,
    ): UnicomSmsCodeSendOutcome = synchronized(lock) {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != MOBILE_LENGTH) throw UnicomLoginException.InvalidMobile
        val appId = preferredAppId.trimmedOrNull() ?: identity.appId
        val encryptedMobile = UnicomLoginCrypto.encrypt(normalizedMobile)
        if (captchaResultToken.trimmedOrNull() == null && !prepared) {
            prepare(encryptedMobile)
        }

        val fields = linkedMapOf(
            "loginCodeLen" to "6",
            "voipToken" to "citc-default-token-do-not-push",
            "deviceBrand" to identity.deviceBrand,
            "simOperator" to SIM_OPERATOR,
            "deviceId" to identity.deviceId,
            "netWay" to identity.networkType,
            "provinceCode" to provinceCode(),
            "deviceCode" to identity.deviceCode,
            "deviceOS" to identity.deviceOs,
            "uniqueIdentifier" to identity.uniqueIdentifier,
            "version" to APP_VERSION,
            "pip" to "",
            "isFirstInstall" to "0",
            "remark4" to "",
            "simCount" to "1",
            "mobile" to encryptedMobile,
            "appId" to appId,
            "cityCode" to cityCode(),
            "reqtime" to requestTime(),
            "deviceModel" to identity.deviceModel,
        )
        captchaResultToken.trimmedOrNull()?.let { fields["resultToken"] = it }

        val response = postForm(SEND_SMS_URL, fields)
        val code = response.string("rsp_code", "code", "status").orEmpty()
        val type = response.string("type", "verifyType").orEmpty()
        if (code == SMS_CAPTCHA_CODE && type == CAPTCHA_TYPE) {
            return@synchronized UnicomSmsCodeSendOutcome.CaptchaRequired(response.captchaChallenge())
        }
        if (code !in setOf("0", "0000")) throw UnicomLoginException.ServerRejected(code)
        UnicomSmsCodeSendOutcome.CodeSent
    }

    fun loginWithSms(
        mobile: String,
        code: String,
        preferredAppId: String? = null,
    ): UnicomLoginResult = synchronized(lock) {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != MOBILE_LENGTH) throw UnicomLoginException.InvalidMobile
        val normalizedCode = code.filter(Char::isDigit)
        if (normalizedCode.length != SMS_CODE_LENGTH) throw UnicomLoginException.InvalidSmsCode
        val appId = preferredAppId.trimmedOrNull() ?: identity.appId
        val encryptedMobile = UnicomLoginCrypto.encrypt(normalizedMobile)
        if (!prepared) prepare(encryptedMobile)

        val response = postForm(
            SMS_LOGIN_URL,
            linkedMapOf(
                "voipToken" to "citc-default-token-do-not-push",
                "loginStyle" to "0",
                "deviceBrand" to identity.deviceBrand,
                "deviceId" to identity.deviceId,
                "simOperator" to SIM_OPERATOR,
                "netWay" to identity.networkType,
                "voiceoff_flag" to "1",
                "deviceCode" to identity.deviceCode,
                "deviceOS" to identity.deviceOs,
                "uniqueIdentifier" to identity.uniqueIdentifier,
                "latitude" to "",
                "version" to APP_VERSION,
                "yw_code" to "",
                "pip" to "",
                "isFirstInstall" to "0",
                "remark4" to "",
                "keyVersion" to KEY_VERSION,
                "longitude" to "",
                "simCount" to "1",
                "mobile" to encryptedMobile,
                "appId" to appId,
                "deviceModel" to identity.deviceModel,
                "reqtime" to requestTime(),
                "password" to UnicomLoginCrypto.encrypt(normalizedCode),
            ),
        )
        val responseCode = response.string("code", "rsp_code", "status").orEmpty()
        if (responseCode !in setOf("0", "0000")) throw UnicomLoginException.ServerRejected(responseCode)
        completeLogin(response, appId)
    }

    fun loginWithPassword(
        mobile: String,
        password: String,
        captchaResultToken: String? = null,
        preferredAppId: String? = null,
    ): UnicomPasswordLoginOutcome = synchronized(lock) {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != MOBILE_LENGTH) throw UnicomLoginException.InvalidMobile
        val normalizedPassword = password.trim()
        if (normalizedPassword.isEmpty()) throw UnicomLoginException.MissingPassword
        val appId = preferredAppId.trimmedOrNull()?.takeIf(::isValidAppId) ?: identity.appId
        val encryptedMobile = UnicomLoginCrypto.encrypt(normalizedMobile)
        if (captchaResultToken.trimmedOrNull() == null && !prepared) prepare(encryptedMobile)

        val fields = linkedMapOf(
            "voipToken" to "citc-default-token-do-not-push",
            "deviceBrand" to identity.deviceBrand,
            "deviceId" to identity.deviceId,
            "simOperator" to SIM_OPERATOR,
            "netWay" to identity.networkType,
            "deviceCode" to identity.deviceCode,
            "uniqueIdentifier" to identity.uniqueIdentifier,
            "deviceOS" to identity.deviceOs,
            "latitude" to "",
            "version" to APP_VERSION,
            "pip" to "",
            "isFirstInstall" to "0",
            "remark4" to "",
            "keyVersion" to KEY_VERSION,
            "longitude" to "",
            "simCount" to "1",
            "mobile" to encryptedMobile,
            "isRemberPwd" to "false",
            "appId" to appId,
            "reqtime" to requestTime(),
            "deviceModel" to identity.deviceModel,
            "password" to UnicomLoginCrypto.encrypt(normalizedPassword),
        )
        captchaResultToken.trimmedOrNull()?.let { fields["resultToken"] = it }

        val response = postForm(PASSWORD_LOGIN_URL, fields)
        val code = response.string("code", "rsp_code", "status").orEmpty()
        val type = response.string("type", "verifyType").orEmpty()
        if (code == PASSWORD_CAPTCHA_CODE && type == CAPTCHA_TYPE) {
            if (response.string("mobile").trimmedOrNull() == null) throw UnicomLoginException.InvalidCaptcha
            return@synchronized UnicomPasswordLoginOutcome.CaptchaRequired(response.captchaChallenge())
        }
        if (!UnicomResponseStatus.isSuccess(code)) throw UnicomLoginException.ServerRejected(code)
        UnicomPasswordLoginOutcome.Success(completeLogin(response, appId))
    }

    private fun prepare(encryptedMobile: String) {
        seedBaseCookies()
        val timestamp = currentMillis().toString()
        val body = buildJsonObject {
            put("mobile", encryptedMobile)
            put("seq", "__NSDictionaryM_${timestamp}_${identity.deviceCode.take(8)}")
            put("sign", "")
            put("provinceCode", provinceCode())
            put("timestamp", timestamp)
            put("appVersion", APP_VERSION)
            put("version", SWITCH_VERSION)
            put("deviceCode", identity.deviceCode)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            post(SWITCH_URL, body, "application/json")
        } catch (_: UnicomLoginException.TransportUnavailable) {
            // iOS treats switch as a best-effort bootstrap; the actual login request remains authoritative.
        } finally {
            prepared = true
            body.fill(0)
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): JsonElement =
        post(url, unicomFormEncoded(fields), "application/x-www-form-urlencoded")

    private fun post(url: String, body: ByteArray, contentType: String): JsonElement = try {
        val response = http.post(url, body, headers(contentType))
        accumulatedCookie = UnicomCookieCodec.applying(response.cookieMutations, accumulatedCookie)
        loginJson.parseToJsonElement(response.data.toString(StandardCharsets.UTF_8))
    } catch (_: UnicomLoginException) {
        throw UnicomLoginException.TransportUnavailable
    } catch (_: Exception) {
        throw UnicomLoginException.TransportUnavailable
    }

    private fun completeLogin(response: JsonElement, fallbackAppId: String): UnicomLoginResult {
        updateCityCookie(response)
        val cookie = UnicomCookieCodec.normalize(accumulatedCookie)
        if (cookie.isEmpty()) throw UnicomLoginException.MissingCookie
        val tokenOnline = response.string("token_online", "tokenOnline").trimmedOrNull()
            ?: throw UnicomLoginException.MissingTokenOnline
        return UnicomLoginResult(
            credentials = AccountCredentials(
                cookie = cookie,
                appID = response.string("appId", "appID").trimmedOrNull() ?: fallbackAppId,
                tokenOnline = tokenOnline,
            ),
            invalidAt = response.string("invalidat", "invalidAt").trimmedOrNull(),
        )
    }

    private fun headers(contentType: String): Map<String, String> = buildMap {
        put("Content-Type", contentType)
        put("User-Agent", identity.userAgent)
        put("Accept", "*/*")
        put("Accept-Language", "zh-Hans-CN;q=1.0")
        UnicomCookieCodec.normalize(accumulatedCookie).trimmedOrNull()?.let { put("Cookie", it) }
    }

    private fun seedBaseCookies() {
        val base = listOf(
            UnicomCookieMutation("PvSessionId", currentMillis().toString() + identity.deviceCode),
            UnicomCookieMutation("c_version", APP_VERSION),
            UnicomCookieMutation("channel", CHANNEL),
            UnicomCookieMutation("devicedId", identity.deviceCode),
            UnicomCookieMutation("city", cityCookie),
        )
        accumulatedCookie = UnicomCookieCodec.applying(base, accumulatedCookie)
    }

    private fun updateCityCookie(response: JsonElement) {
        val root = response as? JsonObject ?: return
        val first = root["list"] as? JsonArray ?: return
        val item = first.firstOrNull() as? JsonObject ?: return
        val province = item["proCode"].stringValue().trimmedOrNull() ?: return
        val city = item["cityCode"].stringValue().trimmedOrNull() ?: return
        cityCookie = "$province|$city"
        accumulatedCookie = UnicomCookieCodec.applying(listOf(UnicomCookieMutation("city", cityCookie)), accumulatedCookie)
    }

    private fun provinceCode(): String = cityCookie.substringBefore('|').ifBlank { DEFAULT_PROVINCE }

    private fun cityCode(): String = cityCookie.substringAfter('|', DEFAULT_CITY).ifBlank { DEFAULT_CITY }

    private fun JsonElement.captchaChallenge(): UnicomCaptchaChallenge {
        val url = string("url").trimmedOrNull()?.replace("\\/", "/")
            ?: throw UnicomLoginException.InvalidCaptcha
        if (!isTrustedCaptchaUrl(url)) throw UnicomLoginException.InvalidCaptcha
        return UnicomCaptchaChallenge(
            url = url,
            title = string("mainTitle").trimmedOrNull() ?: "安全验证",
            message = string("mainDesc", "dsc", "desc", "message").trimmedOrNull() ?: "请完成安全验证",
        )
    }

    private fun isTrustedCaptchaUrl(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme.equals("https", ignoreCase = true) &&
            (host == CAPTCHA_ROOT_DOMAIN || host.endsWith(".$CAPTCHA_ROOT_DOMAIN"))
    } catch (_: Exception) {
        false
    }

    private fun JsonElement.string(vararg keys: String): String? {
        when (this) {
            is JsonObject -> {
                keys.forEach { key -> this[key].stringValue().trimmedOrNull()?.let { return it } }
                values.forEach { value -> value.string(*keys)?.let { return it } }
            }
            is JsonArray -> forEach { value -> value.string(*keys)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.content

    private fun normalizeMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == 13 && digits.startsWith("86")) digits.drop(2) else digits
    }

    private fun isValidAppId(value: String): Boolean = value.length == APP_ID_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.ifEmpty { null }

    companion object {
        private const val SEND_SMS_URL = "https://loginxx.10010.com/mobileService/sendRadomNum.htm"
        private const val SMS_LOGIN_URL = "https://loginxx.10010.com/mobileService/radomLogin.htm"
        private const val PASSWORD_LOGIN_URL = "https://loginxx.10010.com/mobileService/login.htm"
        private const val SWITCH_URL = "https://loginxx.10010.com/login-web/v1/switch/getSwitch"
        private const val APP_VERSION = "iphone_c@12.1400"
        private const val KEY_VERSION = "2"
        private const val SWITCH_VERSION = "237"
        private const val CHANNEL = "GGPD"
        private const val SIM_OPERATOR = "--,--,65535,65535,--@--,--,65535,65535,--"
        private const val MOBILE_LENGTH = 11
        private const val SMS_CODE_LENGTH = 6
        private const val APP_ID_LENGTH = 192
        private const val DEFAULT_PROVINCE = "017"
        private const val DEFAULT_CITY = "170"
        private const val CAPTCHA_TYPE = "10"
        private const val SMS_CAPTCHA_CODE = "ECS99998"
        private const val PASSWORD_CAPTCHA_CODE = "ECS99999"
        private const val CAPTCHA_ROOT_DOMAIN = "10010.com"
        private val lock = Any()
        private val loginJson = Json { isLenient = true; ignoreUnknownKeys = true }
        private val requestTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

data class UnicomLoginIdentity(
    val deviceCode: String,
    val uniqueIdentifier: String,
    val deviceId: String,
    val appId: String,
    val deviceModel: String,
    val deviceOs: String,
    val deviceBrand: String = "Android",
    val networkType: String = "wifi",
    val cityCookie: String = "017|170",
    val userAgent: String = "ChinaUnicom4.x/12.14 (com.chinaunicom.mobilebusiness; Android) unicom{version:iphone_c@12.1400}",
)

data class UnicomLoginResult(
    val credentials: AccountCredentials,
    val invalidAt: String?,
)

data class UnicomCaptchaChallenge(
    val url: String,
    val title: String,
    val message: String,
)

sealed interface UnicomSmsCodeSendOutcome {
    data object CodeSent : UnicomSmsCodeSendOutcome
    data class CaptchaRequired(val challenge: UnicomCaptchaChallenge) : UnicomSmsCodeSendOutcome
}

sealed interface UnicomPasswordLoginOutcome {
    data class Success(val result: UnicomLoginResult) : UnicomPasswordLoginOutcome
    data class CaptchaRequired(val challenge: UnicomCaptchaChallenge) : UnicomPasswordLoginOutcome
}

sealed class UnicomLoginException(message: String) : Exception(message) {
    data object InvalidMobile : UnicomLoginException("invalidMobile")
    data object InvalidSmsCode : UnicomLoginException("invalidSmsCode")
    data object MissingPassword : UnicomLoginException("missingPassword")
    data object InvalidCaptcha : UnicomLoginException("invalidCaptcha")
    data object MissingCookie : UnicomLoginException("missingCookie")
    data object MissingTokenOnline : UnicomLoginException("missingTokenOnline")
    data object TransportUnavailable : UnicomLoginException("loginTransportUnavailable")
    data class ServerRejected(val code: String) : UnicomLoginException("loginServerRejected:$code")
}
