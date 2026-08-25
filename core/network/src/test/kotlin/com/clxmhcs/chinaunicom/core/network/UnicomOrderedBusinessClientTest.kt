package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomOrderedBusinessClientTest {
    private val now = Instant.parse("2026-08-25T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun directFetchAllocatesBusinessSessionAppliesCookieAndParsesSnapshot() {
        val transport = OrderedBusinessQueueTransport(
            response("1", setCookies = listOf("ordered_sid=abc; Path=/")),
            response(successPayload()),
        )
        val client = client(transport)

        val result = client.fetch(AccountCredentials("base=1; d_deviceCode=DEV", "app", "token"))

        assertNull(result.updatedCredentials)
        assertEquals(now, result.snapshot.fetchedAt)
        assertEquals("融合套餐", result.snapshot.title)
        assertEquals("2026-08-25 20:00:00", result.snapshot.queryTime)
        assertEquals(listOf("主套餐", "套餐内业务与优惠", "功能服务"), result.snapshot.sections.map { it.title })
        assertEquals(4, result.snapshot.totalCount)
        assertEquals(2, transport.requests.size)
        assertEquals(
            ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ROOT + ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ALLOCATE,
            transport.requests[0].url,
        )
        assertEquals(
            "base=1; d_deviceCode=DEV; ordered_sid=abc",
            transport.requests[1].headers["Cookie"],
        )
        assertTrue(transport.requests[1].body.decodeToString().contains("type=1"))
        assertEquals("https://imgxx.client.10010.com", transport.requests[1].headers["Origin"])
    }

    @Test
    fun expiredBusinessSessionUsesLoginxxThenReturnsRenewedCredentials() {
        val transport = OrderedBusinessQueueTransport(
            response("{\"code\":\"9998\"}"),
            response(
                "{\"code\":\"0000\",\"appId\":\"newApp\",\"token_online\":\"newToken\"}",
                setCookies = listOf("renewed=2; Path=/"),
            ),
            response("\"1\""),
            response(successPayload()),
        )
        val client = client(transport)

        val result = client.fetch(AccountCredentials("d_deviceCode=ABC; old=1", "oldApp", "oldToken"))

        assertEquals(4, transport.requests.size)
        val activation = transport.requests[1]
        assertEquals(ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ONLINE, activation.url)
        assertEquals("d_deviceCode=ABC; old=1", activation.headers["Cookie"])
        val body = activation.body.decodeToString()
        assertTrue(body.contains("appId=oldApp"))
        assertTrue(body.contains("token_online=oldToken"))
        assertTrue(body.contains("version=iphone_c%4012.1300"))
        assertTrue(body.contains("deviceCode=ABC"))
        assertTrue(body.contains("uniqueIdentifier=ios${sha256("ABC").take(32)}"))
        assertTrue(body.contains("reqtime=2026-08-25%2020%3A00%3A00"))

        assertEquals("d_deviceCode=ABC; old=1; renewed=2", result.updatedCredentials?.cookie)
        assertEquals("newApp", result.updatedCredentials?.appID)
        assertEquals("newToken", result.updatedCredentials?.tokenOnline)
        assertEquals("d_deviceCode=ABC; old=1; renewed=2", transport.requests[2].headers["Cookie"])
    }

    @Test
    fun expiredSessionWithoutAppIdTokenFailsBeforeActivationRequest() {
        val transport = OrderedBusinessQueueTransport(response("{\"code\":\"9998\"}"))
        val error = runCatching {
            client(transport).fetch(AccountCredentials("cookie=1", null, null))
        }.exceptionOrNull()

        assertTrue(error is UnicomAPIException.Server)
        assertEquals(
            "已订业务 Cookie 会话已失效，且该号码未保存可用的 appId/token_online",
            (error as UnicomAPIException.Server).serverMessage,
        )
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun parserDeduplicatesSameStableProductIdWithinSection() {
        val duplicatePayload = """
            {
              "code":"0000",
              "data":{
                "mainProductInfo":[
                  {"productId":"p1","productName":"套餐","startDate":"2026-01-01"},
                  {"productId":"p1","productName":"套餐","startDate":"2026-01-01"}
                ]
              }
            }
        """.trimIndent().encodeToByteArray()
        val snapshot = client(OrderedBusinessQueueTransport()).parse(duplicatePayload)

        assertEquals(1, snapshot.sections.single().items.size)
        assertEquals("p1|套餐|2026-01-01|", snapshot.sections.single().items.single().id)
    }

    private fun client(transport: OrderedBusinessQueueTransport) = UnicomOrderedBusinessClient(
        http = UnicomHTTPClient(transport, retryDelayMillis = 0),
        clock = clock,
        systemVersionProvider = { "18.7" },
        uuidProvider = { UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") },
    )

    private fun response(body: String, setCookies: List<String> = emptyList()) = UnicomRawResponse(
        statusCode = 200,
        body = body.encodeToByteArray(),
        headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
    )

    private fun successPayload(): String = """
        {
          "code":"0000",
          "data":{
            "commdityName":"融合套餐",
            "queryTime":"2026-08-25 20:00:00",
            "mainProductInfo":[{"productId":"main","productName":"主套餐A","productFee":"99","startDate":"2026-01-01"}],
            "liuLiangProductInfo":[{
              "productId":"flow","productName":"流量包","packageName":"套餐A",
              "discntInfo":[{"discntCode":"d1","discntName":"优惠A","startDate":"2026-01-01"}]
            }],
            "serviceinfo":[{"serviceid":"svc","servicename":"来电显示","packagename":"套餐A"}]
          }
        }
    """.trimIndent()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private class OrderedBusinessQueueTransport(vararg responses: UnicomRawResponse) : UnicomTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<UnicomRequest>()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request
        if (queue.isEmpty()) error("No queued response for ${request.url}")
        return queue.removeFirst()
    }
}
