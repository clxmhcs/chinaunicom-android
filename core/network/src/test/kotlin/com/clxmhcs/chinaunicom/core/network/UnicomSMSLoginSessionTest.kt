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

class UnicomSMSLoginSessionTest {
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
    fun sendCodePreparesSwitchThenPostsSourceDefinedFormAndAccumulatesCookies() {
        val transport = RecordingTransport().apply {
            enqueue(
                response(
                    body = "{}",
                    setCookies = listOf("bootstrap=one; Path=/; Secure"),
                ),
            )
            enqueue(
                response(
                    body = "{\"rsp_code\":\"0000\",\"rsp_desc\":\"验证码已发送\"}",
                    setCookies = listOf("sms=two; Path=/; Secure"),
                ),
            )
        }
        val cityStore = FakeIdentityStore(identity)
        val session = session(transport, cityStore)

        val outcome = session.sendCode("+86 138-0013-8000")

        assertEquals(2, transport.requests.size)
        val switchRequest = transport.requests[0]
        assertEquals(UnicomSMSLoginSession.SWITCH_URL, switchRequest.url)
        assertEquals("application/json", switchRequest.headers["Content-Type"])
        val userAgent = switchRequest.headers.getValue("User-Agent")
        assertTrue(userAgent.contains("ChinaUnicom4.x/12.15"))
        assertTrue(userAgent.contains("build:4"))
        assertTrue(userAgent.contains("unicom{version:iphone_c@12.1500}"))
        val switchCookies = cookieMap(switchRequest.headers.getValue("Cookie"))
        assertEquals("iphone_c@12.1500", switchCookies["c_version"])
        assertEquals("GGPD", switchCookies["channel"])
        assertEquals(identity.deviceCode, switchCookies["devicedId"])
        assertEquals("017|170", switchCookies["city"])
        assertTrue(switchCookies.getValue("PvSessionId").startsWith("20260822010000"))

        val switchJSON = Json.parseToJsonElement(switchRequest.body.toString(Charsets.UTF_8)).jsonObject
        assertEquals("237", switchJSON.getValue("version").jsonPrimitive.content)
        assertEquals("iphone_c@12.1500", switchJSON.getValue("appVersion").jsonPrimitive.content)
        assertEquals("017", switchJSON.getValue("provinceCode").jsonPrimitive.content)
        assertEquals("1787360400000", switchJSON.getValue("timestamp").jsonPrimitive.content)
        assertTrue(switchJSON.getValue("seq").jsonPrimitive.content.startsWith("__NSDictionaryM_1787360400000_"))
        assertTrue(switchJSON.getValue("mobile").jsonPrimitive.content.endsWith("%3D"))

        val sendRequest = transport.requests[1]
        assertEquals(UnicomSMSLoginSession.SEND_CODE_URL, sendRequest.url)
        val sendFields = formFields(sendRequest.body)
        assertEquals("6", sendFields["loginCodeLen"])
        assertEquals("017", sendFields["provinceCode"])
        assertEquals("170", sendFields["cityCode"])
        assertEquals("iphone_c@12.1500", sendFields["version"])
        assertEquals(identity.appID, sendFields["appId"])
        assertEquals(128, Base64.getDecoder().decode(sendFields.getValue("mobile")).size)
        assertEquals("one", cookieMap(sendRequest.headers.getValue("Cookie"))["bootstrap"])

        assertTrue(outcome is UnicomSMSSendOutcome.CodeSent)
        assertEquals("验证码已发送", (outcome as UnicomSMSSendOutcome.CodeSent).message)
        val accumulated = cookieMap(session.currentCookieHeader())
        assertEquals("one", accumulated["bootstrap"])
        assertEquals("two", accumulated["sms"])
    }

    @Test
    fun resultTokenContinuationSkipsSwitchAndMapsSmsCaptchaChallenge() {
        val transport = RecordingTransport().apply {
            enqueue(
                response(
                    body = """
                        {
                          "rsp_code":"ECS99998",
                          "type":"10",
                          "url":"https://captcha.example/verify",
                          "mainTitle":"短信安全验证",
                          "mainDesc":"请完成验证",
                          "mobile":"encrypted-risk-mobile"
                        }
                    """.trimIndent(),
                ),
            )
        }
        val session = session(transport, FakeIdentityStore(identity))

        val outcome = session.sendCode(
            mobile = "13800138000",
            resultToken = "captcha-result-token",
            preferredAppID = "preferred-app",
        )

        assertEquals(1, transport.requests.size)
        assertEquals(UnicomSMSLoginSession.SEND_CODE_URL, transport.requests.single().url)
        val fields = formFields(transport.requests.single().body)
        assertEquals("captcha-result-token", fields["resultToken"])
        assertEquals("preferred-app", fields["appId"])
        assertEquals("iphone_c@12.1500", fields["version"])
        assertFalse(transport.requests.single().headers.containsKey("Cookie"))

        assertTrue(outcome is UnicomSMSSendOutcome.CaptchaRequired)
        val challenge = (outcome as UnicomSMSSendOutcome.CaptchaRequired).challenge
        assertEquals("短信安全验证", challenge.title)
        assertEquals("请完成验证", challenge.message)
        assertEquals("https://captcha.example/verify", challenge.url)
        assertEquals("smssms", challenge.bridgePayload["channel"])
        assertEquals("encrypted-risk-mobile", challenge.bridgePayload["mobile"])
    }

    @Test
    fun loginEncryptsCodeReturnsCredentialsAndUpdatesCityAfterCookieSnapshot() {
        val returnedAppID = "d".repeat(192)
        val transport = RecordingTransport().apply {
            enqueue(response(body = "{}", setCookies = listOf("bootstrap=one; Path=/")))
            enqueue(
                response(
                    body = """
                        {
                          "code":"0",
                          "appId":"$returnedAppID",
                          "token_online":"token-result",
                          "invalidat":"2026-09-01 00:00:00",
                          "list":[{"proCode":"011","cityCode":"110"}]
                        }
                    """.trimIndent(),
                    setCookies = listOf("session=active; Path=/; Secure"),
                ),
            )
        }
        val cityStore = FakeIdentityStore(identity)
        val session = session(transport, cityStore)

        val result = session.login("13800138000", "12 34-56", preferredAppID = "preferred-app")

        assertEquals(2, transport.requests.size)
        val fields = formFields(transport.requests.last().body)
        assertEquals("0", fields["loginStyle"])
        assertEquals("1", fields["voiceoff_flag"])
        assertEquals("2", fields["keyVersion"])
        assertEquals("iphone_c@12.1500", fields["version"])
        assertEquals("preferred-app", fields["appId"])
        assertEquals(128, Base64.getDecoder().decode(fields.getValue("mobile")).size)
        assertEquals(128, Base64.getDecoder().decode(fields.getValue("password")).size)
        assertFalse(fields.getValue("password").contains("123456"))

        assertEquals(returnedAppID, result.credentials.appID)
        assertEquals("token-result", result.credentials.tokenOnline)
        assertEquals("2026-09-01 00:00:00", result.invalidAt)
        val returnedCookie = cookieMap(result.credentials.cookie)
        assertEquals("017|170", returnedCookie["city"])
        assertEquals("one", returnedCookie["bootstrap"])
        assertEquals("active", returnedCookie["session"])
        assertEquals("011|110", cityStore.cityCookie())
        assertEquals("011|110", cookieMap(session.currentCookieHeader())["city"])
    }

    @Test
    fun switchFailureDoesNotBlockCodeRequest() {
        val transport = RecordingTransport().apply {
            enqueueError(IllegalStateException("bootstrap failed"))
            enqueue(response(body = "{\"rsp_code\":\"0\"}"))
        }
        val session = session(transport, FakeIdentityStore(identity))

        val outcome = session.sendCode("13800138000")

        assertEquals(2, transport.requests.size)
        assertTrue(outcome is UnicomSMSSendOutcome.CodeSent)
    }

    @Test
    fun loginRequiresSixDigitsAndTokenOnline() {
        val transport = RecordingTransport()
        val session = session(transport, FakeIdentityStore(identity))
        assertThrows(UnicomSMSLoginException.InvalidCode::class.java) {
            session.login("13800138000", "12345")
        }

        transport.enqueue(response(body = "{}"))
        transport.enqueue(response(body = "{\"code\":\"0\",\"appId\":\"app\"}"))
        assertThrows(UnicomSMSLoginException.MissingTokenOnline::class.java) {
            session.login("13800138000", "123456")
        }
    }

    @Test
    fun rsaEncryptorUsesFrozen1024BitPkcs1KeyAndRejectsOversizePlaintext() {
        val first = UnicomLoginRSAEncryptor.encrypt("13800138000")
        val second = UnicomLoginRSAEncryptor.encrypt("13800138000")

        assertEquals(128, Base64.getDecoder().decode(first).size)
        assertEquals(128, Base64.getDecoder().decode(second).size)
        assertFalse(first == second)
        assertThrows(UnicomLoginEncryptionException.PlaintextTooLong::class.java) {
            UnicomLoginRSAEncryptor.encrypt("x".repeat(118))
        }
    }

    private fun session(
        transport: RecordingTransport,
        identityStore: FakeIdentityStore,
    ): UnicomSMSLoginSession = UnicomSMSLoginSession(
        identityStore = identityStore,
        transport = transport,
        clock = clock,
        random = Random(7),
        localIPv4Provider = { "192.0.2.10" },
    )
}

private class FakeIdentityStore(
    private val value: UnicomLoginDeviceIdentity,
    private var city: String = UnicomSMSLoginSession.DEFAULT_CITY_COOKIE,
) : UnicomLoginDeviceIdentityStore {
    override fun identity(): UnicomLoginDeviceIdentity = value
    override fun cityCookie(): String = city
    override fun updateCityCookie(value: String) {
        city = value
    }
}

private class RecordingTransport : UnicomTransport {
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

private fun response(
    body: String,
    statusCode: Int = 200,
    setCookies: List<String> = emptyList(),
): UnicomRawResponse = UnicomRawResponse(
    statusCode = statusCode,
    body = body.toByteArray(Charsets.UTF_8),
    headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
)

private fun formFields(body: ByteArray): Map<String, String> = body.toString(Charsets.UTF_8)
    .split('&')
    .filter(String::isNotEmpty)
    .associate { pair ->
        val pieces = pair.split('=', limit = 2)
        URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
            URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
    }

private fun cookieMap(cookie: String): Map<String, String> = cookie
    .split(';')
    .map(String::trim)
    .filter { it.contains('=') }
    .associate { item -> item.substringBefore('=') to item.substringAfter('=') }
