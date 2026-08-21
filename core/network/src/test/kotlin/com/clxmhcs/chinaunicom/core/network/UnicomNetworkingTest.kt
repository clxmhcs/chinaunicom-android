package com.clxmhcs.chinaunicom.core.network

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomNetworkingTest {
    @Test
    fun cookieNormalizeAndMutationsMatchIosSemantics() {
        val normalized = UnicomCookieCodec.normalize(
            "Cookie: a=1; Path=/; B=2; a=3; HttpOnly; Domain=.10010.com",
        )
        assertEquals("a=3; B=2", normalized)

        val mutations = UnicomCookieCodec.mutationsFromSetCookieHeaders(
            listOf(
                "a=4; Path=/, b=gone; Max-Age=0, c=hello; Expires=Wed, 09 Jun 2032 10:18:14 GMT",
                "D=5; Path=/",
            ),
        )
        assertEquals(4, mutations.size)
        assertNull(mutations.first { it.name.equals("b", true) }.value)
        assertEquals("a=4; c=hello; D=5", UnicomCookieCodec.applying(mutations, normalized))
        assertEquals("hello", UnicomCookieCodec.value("C", "c=hello"))
    }

    @Test
    fun responseStatusRecognizesIosSuccessAndExpiryMarkers() {
        assertTrue(UnicomResponseStatus.isSuccess("0000"))
        assertTrue(UnicomResponseStatus.isSuccess("SUCCESS"))
        assertTrue(UnicomResponseStatus.responseLooksExpired("{\"code\":\"999998\"}".encodeToByteArray()))
        assertTrue(UnicomResponseStatus.responseLooksExpired("Cookie 无效，请重新登录".encodeToByteArray()))
        assertFalse(UnicomResponseStatus.responseLooksExpired("{\"code\":\"0000\"}".encodeToByteArray()))
    }

    @Test
    fun formEncodingUsesSortedRfc3986PercentEncoding() {
        val encoded = unicomFormEncoded(mapOf("version" to "iphone_c@9.0100", "appId" to "a b", "中文" to "值"))
            .toString(Charsets.UTF_8)
        assertEquals("appId=a%20b&version=iphone_c%409.0100&%E4%B8%AD%E6%96%87=%E5%80%BC", encoded)
    }

    @Test
    fun httpRetriesExactlyOnceForIosEquivalentTransientFailuresAnd5xxButNotOthers() {
        val transientFailures = listOf(
            SocketTimeoutException("timed out"),
            UnknownHostException("cannot find host"),
            ConnectException("cannot connect to host"),
            NoRouteToHostException("not connected to internet"),
            SocketException("connection reset"),
            EOFException("connection lost"),
        )
        transientFailures.forEach { failure ->
            val transport = QueueTransport(
                failure,
                UnicomRawResponse(200, "ok".encodeToByteArray()),
            )
            val client = UnicomHTTPClient(transport, retryDelayMillis = 0)
            assertEquals("ok", client.post("https://example.invalid").data.toString(Charsets.UTF_8))
            assertEquals(2, transport.requests.size)
        }

        val serverTransport = QueueTransport(
            UnicomRawResponse(503, byteArrayOf()),
            UnicomRawResponse(200, "ok".encodeToByteArray()),
        )
        UnicomHTTPClient(serverTransport, retryDelayMillis = 0).post("https://example.invalid")
        assertEquals(2, serverTransport.requests.size)

        val nonRetryableFailures = listOf(
            IOException("generic io failure"),
            ProtocolException("protocol failure"),
            SSLHandshakeException("tls failure"),
        )
        nonRetryableFailures.forEach { failure ->
            val transport = QueueTransport(
                failure,
                UnicomRawResponse(200, "should-not-be-used".encodeToByteArray()),
            )
            val error = runCatching {
                UnicomHTTPClient(transport, retryDelayMillis = 0).post("https://example.invalid")
            }.exceptionOrNull()
            assertSame(failure, error)
            assertEquals(1, transport.requests.size)
        }

        val badRequest = QueueTransport(UnicomRawResponse(404, byteArrayOf()))
        val error = runCatching { UnicomHTTPClient(badRequest, 0).post("https://example.invalid") }.exceptionOrNull()
        assertTrue(error is UnicomAPIException.HttpStatus && error.statusCode == 404)
        assertEquals(1, badRequest.requests.size)
    }
}

internal class QueueTransport(vararg responses: Any) : UnicomTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<UnicomRequest>()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request
        val value = queue.removeFirst()
        if (value is Exception) throw value
        return value as UnicomRawResponse
    }
}
