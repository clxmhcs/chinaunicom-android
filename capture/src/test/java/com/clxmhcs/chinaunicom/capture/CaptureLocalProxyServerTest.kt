package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CaptureLocalProxyServerTest {
    @Test
    fun parsesAbsoluteHttpTargetAndRewritesForOrigin() {
        val raw = (
            "GET http://api.example.com:8080/v1/quota?q=1 HTTP/1.1\r\n" +
                "Host: api.example.com:8080\r\n" +
                "Proxy-Connection: keep-alive\r\n" +
                "Proxy-Authorization: Basic test-value\r\n" +
                "Authorization: Bearer application-value\r\n\r\n"
            ).toByteArray(StandardCharsets.ISO_8859_1)

        val parsed = CaptureProxyRequestParser.parse(raw)
        assertNotNull(parsed)
        parsed!!
        assertEquals("GET", parsed.method)
        assertEquals("api.example.com", parsed.host)
        assertEquals(8080, parsed.port)
        assertEquals("/v1/quota?q=1", parsed.originTarget)
        assertFalse(parsed.isConnect)

        val rewritten = String(
            CaptureProxyRequestParser.rewriteForOrigin(raw, parsed),
            StandardCharsets.ISO_8859_1,
        )
        assertTrue(rewritten.startsWith("GET /v1/quota?q=1 HTTP/1.1\r\n"))
        assertFalse(rewritten.contains("Proxy-Connection", ignoreCase = true))
        assertFalse(rewritten.contains("Proxy-Authorization", ignoreCase = true))
        assertTrue(rewritten.contains("Authorization: Bearer application-value"))
    }

    @Test
    fun parsesConnectAuthorityWithoutInspectingTlsPayload() {
        val raw = (
            "CONNECT secure.example.com:443 HTTP/1.1\r\n" +
                "Host: secure.example.com:443\r\n\r\n"
            ).toByteArray(StandardCharsets.ISO_8859_1)

        val parsed = CaptureProxyRequestParser.parse(raw)
        assertNotNull(parsed)
        parsed!!
        assertTrue(parsed.isConnect)
        assertEquals("secure.example.com", parsed.host)
        assertEquals(443, parsed.port)
        assertEquals("secure.example.com:443", parsed.originTarget)
    }

    @Test
    fun parsesBracketedIpv6ConnectAuthority() {
        val raw = "CONNECT [2001:db8::10]:8443 HTTP/1.1\r\nHost: [2001:db8::10]:8443\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)

        val parsed = CaptureProxyRequestParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("2001:db8::10", parsed!!.host)
        assertEquals(8443, parsed.port)
    }

    @Test
    fun filterMatchesConfiguredHostSubdomainAndPath() {
        val request = CaptureProxyRequestHead(
            method = "GET",
            target = "http://api.example.com/v1/quota",
            version = "HTTP/1.1",
            host = "api.example.com",
            port = 80,
            originTarget = "/v1/quota",
            headers = emptyMap(),
        )
        val configuration = CaptureConfiguration(
            targetHost = "example.com",
            targetPath = "/v1",
            captureAllHosts = false,
        )

        assertTrue(CaptureProxyFilter.shouldRecord(configuration, request))
        assertFalse(
            CaptureProxyFilter.shouldRecord(
                configuration.copy(targetPath = "/v2"),
                request,
            ),
        )
    }

    @Test
    fun filterForwardsButDoesNotRecordUnselectedHosts() {
        val request = CaptureProxyRequestHead(
            method = "GET",
            target = "http://other.example/path",
            version = "HTTP/1.1",
            host = "other.example",
            port = 80,
            originTarget = "/path",
            headers = emptyMap(),
        )

        assertFalse(
            CaptureProxyFilter.shouldRecord(
                CaptureConfiguration(targetHost = "selected.example"),
                request,
            ),
        )
        assertTrue(
            CaptureProxyFilter.shouldRecord(
                CaptureConfiguration(captureAllHosts = true),
                request,
            ),
        )
    }

    @Test
    fun pathFilterDoesNotPretendConnectHasDecryptedPath() {
        val request = CaptureProxyRequestHead(
            method = "CONNECT",
            target = "secure.example:443",
            version = "HTTP/1.1",
            host = "secure.example",
            port = 443,
            originTarget = "secure.example:443",
            headers = emptyMap(),
        )

        assertFalse(
            CaptureProxyFilter.shouldRecord(
                CaptureConfiguration(captureAllHosts = true, targetPath = "/secret"),
                request,
            ),
        )
    }
}
