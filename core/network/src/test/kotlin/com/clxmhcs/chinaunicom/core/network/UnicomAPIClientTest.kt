package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomAPIClientTest {
    private val quotaJson = """
        {
          "code":"0000",
          "productname":"畅享套餐",
          "resources":[{"type":"flow","details":[{
            "feePolicyId":"F001","feePolicyName":"国内通用流量","resourceType":"01",
            "totalTxt":"10 GB","remainTxt":"6 GB","useTxt":"4 GB","typemark":"1"
          }]}]
        }
    """.trimIndent().encodeToByteArray()

    @Test
    fun expiredQuotaActivatesWithoutOldCookieThenRetriesWithMutatedCookie() {
        val transport = QueueTransport(
            UnicomRawResponse(200, "999998".encodeToByteArray()),
            UnicomRawResponse(
                200,
                "{\"code\":\"0000\",\"token_online\":\"new-token\"}".encodeToByteArray(),
                mapOf("Set-Cookie" to listOf("ecs_token=new; Path=/", "old=; Max-Age=0")),
            ),
            UnicomRawResponse(200, quotaJson),
        )
        val client = UnicomAPIClient(UnicomHTTPClient(transport, retryDelayMillis = 0))
        val result = client.fetchWidgetQuota(AccountCredentials("old=1; keep=2", "app-1", "old-token"))

        assertEquals(3, transport.requests.size)
        assertEquals(UnicomAPIClient.BASE_URL + UnicomAPIClient.QUOTA_PATH, transport.requests[0].url)
        assertEquals("old=1; keep=2", transport.requests[0].headers["Cookie"])

        val activation = transport.requests[1]
        assertEquals(UnicomAPIClient.ONLINE_URL, activation.url)
        assertFalse(activation.headers.keys.any { it.equals("Cookie", true) })
        assertEquals("application/x-www-form-urlencoded", activation.headers["Content-Type"])
        assertEquals(
            "appId=app-1&token_online=old-token&version=iphone_c%409.0100",
            activation.body.toString(Charsets.UTF_8),
        )

        assertEquals("keep=2; ecs_token=new", transport.requests[2].headers["Cookie"])
        assertNotNull(result.updatedCredentials)
        assertEquals("keep=2; ecs_token=new", result.updatedCredentials?.cookie)
        assertEquals("new-token", result.updatedCredentials?.tokenOnline)
        assertEquals("畅享套餐", result.packageName)
        assertEquals(1, result.packages.size)
        assertNull(result.remainingQuerySnapshot)
    }

    @Test
    fun balanceRequestUsesIosEndpointBodyAndSessionFallback() {
        val balancePayload = """
            {
              "curntbalancecust":"1,234.56",
              "unavailablelimitfeecust":12.5,
              "feefrozen":"3.00",
              "uAndF":"15.50",
              "limitInfoList":[{"depositname":"测试押金","unavailablelimitfee":"12.50"}],
              "frozeninfolist":[{"actionname":"测试冻结","leftmoney":"3.00"}]
            }
        """.trimIndent().encodeToByteArray()
        val transport = QueueTransport(UnicomRawResponse(200, balancePayload))
        val client = UnicomAPIClient(UnicomHTTPClient(transport, retryDelayMillis = 0))
        val result = client.fetchBalance(AccountCredentials("session=abc", null, null))

        assertEquals(1234.56, result.balanceYuan ?: 0.0, 0.0001)
        assertEquals("12.5", result.unavailableBalanceDetail?.unavailableLimitFee)
        assertEquals(1, result.unavailableBalanceDetail?.limitItems?.size)
        assertEquals(1, result.unavailableBalanceDetail?.frozenItems?.size)
        assertNull(result.updatedCredentials)

        val request = transport.requests.single()
        assertEquals(UnicomBalanceClient.BASE_URL + UnicomBalanceClient.PATH, request.url)
        assertEquals("session=abc", request.headers["Cookie"])
        assertTrue(request.body.toString(Charsets.UTF_8).contains("channel=client"))
        assertTrue(request.body.toString(Charsets.UTF_8).contains("language=chinese"))
    }
}
