package com.clxmhcs.chinaunicom.core.network

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.Base64
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomPasswordLoginSessionTest {
    private val identity = UnicomLoginDeviceIdentity(
        deviceCode = "550E8400-E29B-41D4-A716-446655440000",
        uniqueIdentifier = "iosa" + "a".repeat(32),
        deviceID = "b".repeat(64),
        appID = "c".repeat(192),
        deviceModel = "Pixel-Test",
        deviceOS = "14.1",
    )
    private val clock = Clock.fixed(Instant.parse("2026-08-22T01:00:00Z"), ZoneOffset.UTC)

    @Test
    fun firstPasswordLoginRunsSwitchAndPostsSourceDefinedForm() {
        val validPreferredAppID = "d".repeat(192)
        val transport = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}", setCookies = listOf("bootstrap=one; Path=/")))
            enqueue(
                passwordResponse(
                    """
                        {
                          "code":"0",
                          "appId":"$validPreferredAppID",
                          "token_online":"token-result"
                        }
                    """.trimIndent(),
                    setCookies = listOf("session=active; Path=/"),
                ),
            )
        }
        val passwordSession = session(transport, PasswordFakeIdentityStore(identity))

        val outcome = passwordSession.login(
            mobile = "+86 138-0013-8000",
            password = "  service-pass-123  ",
            preferredAppID = validPreferredAppID,
        )

        assertEquals(2, transport.requests.size)
        val switchRequest = transport.requests[0]
        assertEquals(UnicomPasswordLoginSession.SWITCH_URL, switchRequest.url)
        assertEquals("application/json", switchRequest.headers["Content-Type"])
        assertTrue(switchRequest.headers.getValue("User-Agent").contains("unicom{version:iphone_c@12.1400}"))
        val switchCookies = passwordCookieMap(switchRequest.headers.getValue("Cookie"))
        assertEquals("iphone_c@12.1400", switchCookies["c_version"])
        assertEquals("GGPD", switchCookies["channel"])
        assertEquals(identity.deviceCode, switchCookies["devicedId"])
        assertEquals("017|170", switchCookies["city"])
        assertTrue(switchCookies.getValue("PvSessionId").startsWith("20260822010000"))

        val switchJSON = Json.parseToJsonElement(switchRequest.body.toString(Charsets.UTF_8)).jsonObject
        assertEquals("237", switchJSON.getValue("version").jsonPrimitive.content)
        assertEquals("iphone_c@12.1400", switchJSON.getValue("appVersion").jsonPrimitive.content)
        assertEquals("017", switchJSON.getValue("provinceCode").jsonPrimitive.content)
        assertEquals("1787360400000", switchJSON.getValue("timestamp").jsonPrimitive.content)
        assertTrue(switchJSON.getValue("seq").jsonPrimitive.content.startsWith("__NSDictionaryM_1787360400000_"))
        assertTrue(switchJSON.getValue("mobile").jsonPrimitive.content.endsWith("%3D"))

        val loginRequest = transport.requests[1]
        assertEquals(UnicomPasswordLoginSession.LOGIN_URL, loginRequest.url)
        val fields = passwordFormFields(loginRequest.body)
        assertEquals("wifi", fields["netWay"])
        assertEquals("false", fields["isRemberPwd"])
        assertEquals("2", fields["keyVersion"])
        assertEquals(validPreferredAppID, fields["appId"])
        assertEquals("192.0.2.10", fields["pip"])
        assertEquals(128, Base64.getDecoder().decode(fields.getValue("mobile")).size)
        assertEquals(128, Base64.getDecoder().decode(fields.getValue("password")).size)
        assertFalse(fields.getValue("password").contains("service-pass-123"))
        assertEquals("one", passwordCookieMap(loginRequest.headers.getValue("Cookie"))["bootstrap"])

        assertTrue(outcome is UnicomPasswordLoginOutcome.Success)
        val result = (outcome as UnicomPasswordLoginOutcome.Success).result
        assertEquals(validPreferredAppID, result.credentials.appID)
        assertEquals("token-result", result.credentials.tokenOnline)
    }

    @Test
    fun preferredAppIDMustBeExactly192LowercaseHex() {
        val invalidPreferred = "A".repeat(192)
        val transport = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}"))
            enqueue(passwordResponse("{\"code\":\"200\",\"token_online\":\"token\"}"))
        }
        val passwordSession = session(transport, PasswordFakeIdentityStore(identity))

        val outcome = passwordSession.login("13800138000", "password", preferredAppID = invalidPreferred)

        assertEquals(identity.appID, passwordFormFields(transport.requests.last().body)["appId"])
        val result = (outcome as UnicomPasswordLoginOutcome.Success).result
        assertEquals(identity.appID, result.credentials.appID)
    }

    @Test
    fun captchaContinuationSkipsSwitchAndRequiresRiskMobile() {
        val transport = PasswordRecordingTransport().apply {
            enqueue(
                passwordResponse(
                    """
                        {
                          "code":"ECS99999",
                          "type":"10",
                          "url":"https:\/\/captcha.example\/verify",
                          "mainTitle":"身份验证",
                          "mainDesc":"请完成图片验证",
                          "mobile":"encrypted-risk-mobile"
                        }
                    """.trimIndent(),
                ),
            )
        }
        val passwordSession = session(transport, PasswordFakeIdentityStore(identity))

        val outcome = passwordSession.login(
            mobile = "13800138000",
            password = "password",
            resultToken = "captcha-result-token",
        )

        assertEquals(1, transport.requests.size)
        val fields = passwordFormFields(transport.requests.single().body)
        assertEquals("captcha-result-token", fields["resultToken"])
        assertFalse(transport.requests.single().headers.containsKey("Cookie"))
        assertTrue(outcome is UnicomPasswordLoginOutcome.CaptchaRequired)
        val challenge = (outcome as UnicomPasswordLoginOutcome.CaptchaRequired).challenge
        assertEquals("身份验证", challenge.title)
        assertEquals("请完成图片验证", challenge.message)
        assertEquals("https://captcha.example/verify", challenge.url)
        assertEquals("encrypted-risk-mobile", challenge.bridgePayload["mobile"])
        assertFalse(challenge.bridgePayload.containsKey("channel"))

        val missingMobileTransport = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{\"code\":\"ECS99999\",\"type\":\"10\",\"url\":\"https://captcha.example/verify\"}"))
        }
        assertThrows(UnicomPasswordLoginException.MissingCaptchaMobile::class.java) {
            session(missingMobileTransport, PasswordFakeIdentityStore(identity)).login(
                "13800138000",
                "password",
                resultToken = "token",
            )
        }
    }

    @Test
    fun successfulPasswordLoginUpdatesCityBeforeCredentialCookieSnapshot() {
        val returnedAppID = "e".repeat(192)
        val transport = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}", setCookies = listOf("bootstrap=one; Path=/")))
            enqueue(
                passwordResponse(
                    """
                        {
                          "code":"success",
                          "appId":"$returnedAppID",
                          "token_online":"token-result",
                          "invalidat":"2026-09-01 00:00:00",
                          "list":[{"proCode":"011","cityCode":"110"}]
                        }
                    """.trimIndent(),
                    setCookies = listOf("session=active; Path=/"),
                ),
            )
        }
        val cityStore = PasswordFakeIdentityStore(identity)
        val passwordSession = session(transport, cityStore)

        val outcome = passwordSession.login("13800138000", "password")
        val result = (outcome as UnicomPasswordLoginOutcome.Success).result

        assertEquals("011|110", cityStore.cityCookie())
        val credentialCookies = passwordCookieMap(result.credentials.cookie)
        assertEquals("011|110", credentialCookies["city"])
        assertEquals("one", credentialCookies["bootstrap"])
        assertEquals("active", credentialCookies["session"])
        assertEquals(returnedAppID, result.credentials.appID)
        assertEquals("token-result", result.credentials.tokenOnline)
        assertEquals("2026-09-01 00:00:00", result.invalidAt)
    }

    @Test
    fun failureClassificationMatchesIosPasswordLogin() {
        val passwordRejected = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}"))
            enqueue(passwordResponse("{\"code\":\"ECS11721\",\"dsc\":\"ECS11721 密码错误\"}"))
        }
        assertThrows(UnicomPasswordLoginException.PasswordRejected::class.java) {
            session(passwordRejected, PasswordFakeIdentityStore(identity)).login("13800138000", "bad-password")
        }

        val smsRequired = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}"))
            enqueue(passwordResponse("{\"code\":\"X1\",\"dsc\":\"当前登录需要短信验证码验证\"}"))
        }
        assertThrows(UnicomPasswordLoginException.SmsVerificationRequired::class.java) {
            session(smsRequired, PasswordFakeIdentityStore(identity)).login("13800138000", "password")
        }

        val generic = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}"))
            enqueue(passwordResponse("{\"code\":\"X2\",\"dsc\":\"服务器拒绝请求\"}"))
        }
        assertThrows(UnicomPasswordLoginException.Server::class.java) {
            session(generic, PasswordFakeIdentityStore(identity)).login("13800138000", "password")
        }
    }

    @Test
    fun switchFailureIsNonFatalAndSuccessStillRequiresCookieAndTokenOnline() {
        val switchFailure = PasswordRecordingTransport().apply {
            enqueueError(IllegalStateException("bootstrap failed"))
            enqueue(passwordResponse("{\"code\":\"0\",\"token_online\":\"token\"}"))
        }
        val outcome = session(switchFailure, PasswordFakeIdentityStore(identity)).login("13800138000", "password")
        assertTrue(outcome is UnicomPasswordLoginOutcome.Success)

        val missingCookie = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{\"code\":\"0\",\"token_online\":\"token\"}"))
        }
        assertThrows(UnicomPasswordLoginException.MissingCookie::class.java) {
            session(missingCookie, PasswordFakeIdentityStore(identity)).login(
                "13800138000",
                "password",
                resultToken = "captcha-result-token",
            )
        }

        val missingToken = PasswordRecordingTransport().apply {
            enqueue(passwordResponse("{}"))
            enqueue(passwordResponse("{\"code\":\"0\"}"))
        }
        assertThrows(UnicomPasswordLoginException.MissingTokenOnline::class.java) {
            session(missingToken, PasswordFakeIdentityStore(identity)).login("13800138000", "password")
        }
    }

    @Test
    fun inputValidationRejectsInvalidMobileAndBlankPassword() {
        val passwordSession = session(PasswordRecordingTransport(), PasswordFakeIdentityStore(identity))
        assertThrows(UnicomPasswordLoginException.InvalidMobile::class.java) {
            passwordSession.login("10086", "password")
        }
        assertThrows(UnicomPasswordLoginException.MissingPassword::class.java) {
            passwordSession.login("13800138000", "   ")
        }
    }

    private fun session(
        transport: PasswordRecordingTransport,
        identityStore: PasswordFakeIdentityStore,
    ): UnicomPasswordLoginSession = UnicomPasswordLoginSession(
        identityStore = identityStore,
        transport = transport,
        clock = clock,
        random = Random(7),
        localIPv4Provider = { "192.0.2.10" },
    )
}

private class PasswordFakeIdentityStore(
    private val value: UnicomLoginDeviceIdentity,
    private var city: String = UnicomPasswordLoginSession.DEFAULT_CITY_COOKIE,
) : UnicomLoginDeviceIdentityStore {
    override fun identity(): UnicomLoginDeviceIdentity = value
    override fun cityCookie(): String = city
    override fun updateCityCookie(value: String) {
        city = value
    }
}

private class PasswordRecordingTransport : UnicomTransport {
    val requests = mutableListOf<UnicomRequest>()
    private val actions = ArrayDeque<(UnicomRequest) -> UnicomRawResponse>()

    fun enqueue(response: UnicomRawResponse) {
        actions.add { response }
    }

    fun enqueueError(error: Exception) {
        actions.add { throw error }
    }

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request.copy(body = request.body.copyOf(), headers = request.headers.toMap())
        check(actions.isNotEmpty()) { "No fake response queued for ${request.url}" }
        return actions.removeFirst().invoke(request)
    }
}

private fun passwordResponse(
    body: String,
    statusCode: Int = 200,
    setCookies: List<String> = emptyList(),
): UnicomRawResponse = UnicomRawResponse(
    statusCode = statusCode,
    body = body.toByteArray(Charsets.UTF_8),
    headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
)

private fun passwordFormFields(body: ByteArray): Map<String, String> = body.toString(Charsets.UTF_8)
    .split('&')
    .filter(String::isNotEmpty)
    .associate { pair ->
        val pieces = pair.split('=', limit = 2)
        URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
            URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
    }

private fun passwordCookieMap(cookie: String): Map<String, String> = cookie
    .split(';')
    .map(String::trim)
    .filter { it.contains('=') }
    .associate { item -> item.substringBefore('=') to item.substringAfter('=') }
