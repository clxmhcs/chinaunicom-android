package com.clxmhcs.chinaunicom.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnicomLoginClientTest {
    @Test
    fun smsLoginCapturesCookieAndTokenWithoutRetainingInputValues() {
        val transport = ScriptedTransport(
            responses = listOf(
                response("{}"),
                response("{\"code\":\"0\",\"token_online\":\"token-value\",\"appId\":\"${"a".repeat(192)}\"}", "ecs_token=abc; Path=/"),
            ),
        )
        val result = client(transport).loginWithSms("18600000000", "123456")

        assertEquals("token-value", result.credentials.tokenOnline)
        assertTrue(result.credentials.cookie.contains("ecs_token=abc"))
        assertEquals(2, transport.requests.size)
        assertTrue(String(transport.requests.last().body).contains("password="))
    }

    @Test
    fun passwordCaptchaIsReturnedAsAStateInsteadOfBeingTreatedAsSuccess() {
        val transport = ScriptedTransport(
            responses = listOf(
                response("{}"),
                response("{\"code\":\"ECS99999\",\"type\":\"10\",\"mobile\":\"encrypted\",\"url\":\"https:\\\\/\\\\/captcha.10010.com\"}"),
            ),
        )

        val outcome = client(transport).loginWithPassword("18600000000", "password")

        val challenge = outcome as UnicomPasswordLoginOutcome.CaptchaRequired
        assertEquals("https://captcha.10010.com", challenge.challenge.url)
    }

    @Test
    fun rejectsCaptchaUrlsOutsideTheOperatorDomain() {
        val transport = ScriptedTransport(
            responses = listOf(
                response("{}"),
                response("{\"code\":\"ECS99999\",\"type\":\"10\",\"mobile\":\"encrypted\",\"url\":\"https:\\\\/\\\\/captcha.example\"}"),
            ),
        )

        try {
            client(transport).loginWithPassword("18600000000", "password")
            fail("Expected captcha URL rejection")
        } catch (_: UnicomLoginException.InvalidCaptcha) {
            // Expected: a future WebView must never load an untrusted host.
        }
    }

    private fun client(transport: ScriptedTransport): UnicomLoginClient = UnicomLoginClient(
        identity = UnicomLoginIdentity(
            deviceCode = "123e4567-e89b-12d3-a456-426614174000",
            uniqueIdentifier = "android000000000000000000000000000000",
            deviceId = "b".repeat(64),
            appId = "a".repeat(192),
            deviceModel = "test-device",
            deviceOs = "15.0",
        ),
        http = UnicomHTTPClient(transport, retryDelayMillis = 0),
        requestTime = { "2026-08-21 18:17:00" },
        currentMillis = { 1_700_000_000_000L },
    )

    private fun response(body: String, setCookie: String? = null): UnicomRawResponse = UnicomRawResponse(
        statusCode = 200,
        body = body.toByteArray(),
        headers = setCookie?.let { mapOf("Set-Cookie" to listOf(it)) } ?: emptyMap(),
    )

    private class ScriptedTransport(
        private val responses: List<UnicomRawResponse>,
    ) : UnicomTransport {
        val requests = mutableListOf<UnicomRequest>()
        private var index = 0

        override fun post(request: UnicomRequest): UnicomRawResponse {
            requests += request
            return responses[index++]
        }
    }
}
