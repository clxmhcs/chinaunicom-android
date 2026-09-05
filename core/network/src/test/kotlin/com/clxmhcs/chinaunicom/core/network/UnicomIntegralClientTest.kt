package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import java.time.Instant
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomIntegralClientTest {
    private val fetchedAt = Instant.parse("2026-08-26T03:00:00Z")
    private val mobile = "13800138000"

    @Test
    fun overviewUsesFrozenEndpointsFormsHeadersAndParserMapping() {
        val transport = IntegralQueueTransport(
            response(balancePayload(), listOf("balanceCookie=1; Path=/")),
            response(monthsPayload(), listOf("monthsCookie=2; Path=/")),
        )
        val result = client(transport).fetchOverview(
            credentials = AccountCredentials("c_mobile=$mobile; sid=1", "app", "token"),
            mobile = mobile,
            fetchedAt = fetchedAt,
        )

        assertEquals(2, transport.requests.size)
        val balance = transport.requests[0]
        val months = transport.requests[1]
        assertEquals(
            ComprehensiveBusinessEndpoints.INTEGRAL_ROOT + ComprehensiveBusinessEndpoints.INTEGRAL_BALANCE,
            balance.url,
        )
        assertEquals(
            ComprehensiveBusinessEndpoints.INTEGRAL_ROOT + ComprehensiveBusinessEndpoints.INTEGRAL_MONTHS,
            months.url,
        )
        assertTrue(balance.body.decodeToString().contains("position=123"))
        assertTrue(balance.body.decodeToString().contains("isTermShow=1"))
        assertTrue(months.body.decodeToString().contains("from=ZXGS97000017640%2C003"))
        assertEquals("https://img.client.10010.com", balance.headers["Origin"])
        assertTrue(balance.headers["User-Agent"].orEmpty().contains("iphone_c@12.1400"))
        assertEquals("c_mobile=$mobile; sid=1", balance.headers["Cookie"])
        assertEquals("c_mobile=$mobile; sid=1", months.headers["Cookie"])

        val snapshot = result.snapshot
        assertEquals(12345, snapshot.totalAvailable)
        assertEquals(2000, snapshot.communication)
        assertEquals(3000, snapshot.reward)
        assertEquals(900, snapshot.directional)
        assertEquals(400, snapshot.expiredAndExpiringReward)
        assertEquals(500, snapshot.expiringThisMonth)
        assertEquals(800, snapshot.expiringCommunication)
        assertEquals(700, snapshot.expiringReward)
        assertEquals(31, snapshot.expirationDay)
        assertEquals(6, snapshot.couponCount)
        assertEquals("11", snapshot.provinceCode)
        assertEquals("pkg", snapshot.packageID)
        assertEquals("1", snapshot.isUnicom)
        assertEquals(fetchedAt, snapshot.fetchedAt)
        assertEquals(1, snapshot.parserVersion)
        assertEquals(listOf("2026-08", "2026-07"), snapshot.months.map { it.cycleID })
        assertEquals(100, snapshot.months[0].addScore)
        assertEquals(20, snapshot.months[0].consumedScore)
        assertEquals(3, snapshot.months[0].expiredScore)
        assertEquals(
            "c_mobile=$mobile; sid=1; balanceCookie=1; monthsCookie=2",
            result.updatedCredentials?.cookie,
        )
    }

    @Test
    fun mismatchedCookieAccountFailsBeforeAnyCarrierRequestOrActivation() {
        val transport = IntegralQueueTransport()
        val error = runCatching {
            client(transport).fetchOverview(
                credentials = AccountCredentials("c_mobile=13900139000; sid=1", "app", "token"),
                mobile = mobile,
                fetchedAt = fetchedAt,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("防止串号"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun expiredIntegralSessionReusesModernSharedActivationThenRetries() {
        val transport = IntegralQueueTransport(
            response("{\"code\":\"9998\"}"),
            response(
                "{\"code\":\"0000\",\"appId\":\"app-new\",\"token_online\":\"newToken\"}",
                listOf("renewed=2; Path=/"),
            ),
            response(balancePayload()),
            response(monthsPayload()),
        )
        val result = client(transport).fetchOverview(
            credentials = AccountCredentials("c_mobile=$mobile; sid=1", "app", "oldToken"),
            mobile = mobile,
            fetchedAt = fetchedAt,
        )

        assertEquals(4, transport.requests.size)
        val activation = transport.requests[1]
        assertEquals(UnicomAPIClient.ONLINE_URL, activation.url)
        assertEquals("c_mobile=$mobile; sid=1", activation.headers["Cookie"])
        val activationBody = activation.body.decodeToString()
        assertTrue(activationBody.contains("appId=app"))
        assertTrue(activationBody.contains("token_online=oldToken"))
        assertTrue(activationBody.contains("version=iphone_c%4012.1500"))
        assertTrue(activationBody.contains("deviceCode=550E8400-E29B-41D4-A716-446655440000"))
        assertEquals("c_mobile=$mobile; sid=1; renewed=2", transport.requests[2].headers["Cookie"])
        assertEquals("app-new", result.updatedCredentials?.appID)
        assertEquals("newToken", result.updatedCredentials?.tokenOnline)
        assertEquals("c_mobile=$mobile; sid=1; renewed=2", result.updatedCredentials?.cookie)
    }

    @Test
    fun detailsUseSourceCacheKeyFieldsAndReturnCookieMutation() {
        val transport = IntegralQueueTransport(
            response(detailsPayload(), listOf("detailCookie=3; Path=/")),
        )
        val query = IntegralDetailQuery("2", "1", "202608", "2026-08 · 获取")
        val result = client(transport).fetchDetails(
            query = query,
            credentials = AccountCredentials("u_account=$mobile; sid=1", "app", "token"),
            mobile = mobile,
        )

        val request = transport.requests.single()
        assertEquals(
            ComprehensiveBusinessEndpoints.INTEGRAL_ROOT + ComprehensiveBusinessEndpoints.INTEGRAL_DETAILS,
            request.url,
        )
        val body = request.body.decodeToString()
        assertTrue(body.contains("scoreType=2"))
        assertTrue(body.contains("typeChar=1"))
        assertTrue(body.contains("yearMonth=202608"))
        assertTrue(body.contains("from=ZXGS97000017640%2C003"))
        assertEquals("获取积分", result.items.single().title)
        assertEquals("88", result.items.single().scoreValue)
        assertEquals("2-1-202608", query.cacheKey)
        assertEquals("u_account=$mobile; sid=1; detailCookie=3", result.updatedCredentials?.cookie)
    }

    private fun client(transport: IntegralQueueTransport): UnicomIntegralClient {
        val http = UnicomHTTPClient(transport, retryDelayMillis = 0)
        return UnicomIntegralClient(
            http = http,
            sessionClient = testUnicomAPIClient(http),
            systemVersionProvider = { "18.7" },
        )
    }

    private fun response(body: String, setCookies: List<String> = emptyList()) = UnicomRawResponse(
        statusCode = 200,
        body = body.encodeToByteArray(),
        headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
    )

    private fun balancePayload() = """
        {"code":"0000","resdata":{
          "provinceCode":"11","packageId":"pkg","isUnicom":"1",
          "data":[
            {"type":1,"number":12345},{"type":2,"number":2000},{"type":3,"number":3000},
            {"type":9,"number":900},{"type":4,"number":400},{"type":5,"number":500},
            {"type":8,"number":800},{"type":7,"number":700},{"type":10,"number":31},
            {"type":6,"number":6}
          ]}}
    """.trimIndent()

    private fun monthsPayload() = """
        {"code":"0000","resdata":[
          {"cycleId":"2026-08","addScore":100,"xfScore":20,"expireScore":3},
          {"cycleId":"2026-07","addScore":"80","xfScore":"10","expireScore":"1"}
        ]}
    """.trimIndent()

    private fun detailsPayload() = """
        {"code":"0000","resdata":[{
          "typeChar":"1","scoreType":"奖励积分","title":"获取积分","scoreValue":"88",
          "createTime":"2026-08-01 12:00:00","channelName":"联通APP"
        }]}
    """.trimIndent()
}

private class IntegralQueueTransport(vararg responses: UnicomRawResponse) : UnicomTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<UnicomRequest>()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request
        if (queue.isEmpty()) error("No queued response for ${request.url}")
        return queue.removeFirst()
    }
}
