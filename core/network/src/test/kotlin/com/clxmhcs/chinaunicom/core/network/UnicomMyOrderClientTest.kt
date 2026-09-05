package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyOrderKind
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomMyOrderClientTest {
    @Test
    fun requestMatchesSourceContractAndPersistsCookieMutation() {
        val transport = QueueTransport(
            mutableListOf(
                UnicomRawResponse(
                    statusCode = 200,
                    body = orderResponse().toByteArray(),
                    headers = mapOf("Set-Cookie" to listOf("sid=new; Path=/; HttpOnly")),
                ),
            ),
        )
        val http = UnicomHTTPClient(transport, retryDelayMillis = 0)
        val client = UnicomMyOrderClient(
            http = http,
            clock = Clock.fixed(Instant.parse("2026-08-26T11:00:00Z"), ZoneOffset.UTC),
            systemVersionProvider = { "18.7" },
        )
        val credentials = AccountCredentials("sid=old; keep=1", "app-id", "token-old")

        val result = client.fetch("186 1234 5678", page = 2, pageSize = 1, credentials = credentials)

        val request = transport.requests.single()
        assertTrue(request.url.startsWith("${UnicomMyOrderClient.BASE_URL}${UnicomMyOrderClient.ORDER_PATH}?timestamp="))
        val body = request.body.toString(Charsets.UTF_8)
        assertTrue(body.contains("current_page=2"))
        assertTrue(body.contains("page_size=1"))
        assertTrue(body.contains("loginNumber=${md5("18612345678")}"))
        assertEquals("https://img.client.10010.com", request.headers["Origin"])
        assertTrue(request.headers.getValue("User-Agent").contains("unicom{version:iphone_c@12.1400}"))
        assertEquals("new", UnicomCookieCodec.value("sid", result.updatedCredentials!!.cookie))
        assertEquals("1", UnicomCookieCodec.value("keep", result.updatedCredentials!!.cookie))
        assertEquals("token-old", result.updatedCredentials!!.tokenOnline)
        assertEquals(1, result.page.orders.size)
        assertTrue(result.page.hasMore)
    }

    @Test
    fun parserPreservesSourceClassificationMembersActionsAndServerTime() {
        val client = UnicomMyOrderClient()

        val page = client.parse(orderResponse().toByteArray(), pageSize = 15)

        val order = page.orders.single()
        assertEquals("order-1", order.orderID)
        assertEquals(MyOrderKind.VOICE_AND_DATA, order.kind)
        assertEquals("订购", order.operationType)
        assertEquals("校园流量包", order.primaryTitle)
        assertEquals("186****5678", order.displayServiceNumber)
        assertEquals(30, order.displayPoints)
        assertNotNull(order.detailAction)
        assertEquals("2026-08-26 19:00:00", page.serverTime)
        assertTrue(!page.hasMore)
    }

    @Test
    fun sessionExpiryUsesModernSharedActivationThenRetriesOrderRequest() {
        val transport = QueueTransport(
            mutableListOf(
                UnicomRawResponse(200, "{\"respCode\":\"9998\",\"respDesc\":\"登录失效\"}".toByteArray()),
                UnicomRawResponse(
                    200,
                    "{\"code\":\"0000\",\"appId\":\"app-new\",\"token_online\":\"token-new\"}".toByteArray(),
                    mapOf("Set-Cookie" to listOf("sid=activated; Path=/")),
                ),
                UnicomRawResponse(
                    200,
                    orderResponse().toByteArray(),
                    mapOf("Set-Cookie" to listOf("sid=business; Path=/")),
                ),
            ),
        )
        val http = UnicomHTTPClient(transport, retryDelayMillis = 0)
        val client = UnicomMyOrderClient(
            http = http,
            sessionClient = testUnicomAPIClient(http),
        )
        val credentials = AccountCredentials("sid=old", "app-id", "token-old")

        val result = client.fetch("18612345678", page = 1, pageSize = 15, credentials = credentials)

        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests[0].url.contains(UnicomMyOrderClient.ORDER_PATH))
        val activation = transport.requests[1]
        assertEquals(UnicomAPIClient.ONLINE_URL, activation.url)
        assertEquals("sid=old", activation.headers["Cookie"])
        assertTrue(activation.body.decodeToString().contains("version=iphone_c%4012.1500"))
        assertTrue(transport.requests[2].url.contains(UnicomMyOrderClient.ORDER_PATH))
        assertEquals("business", UnicomCookieCodec.value("sid", result.updatedCredentials!!.cookie))
        assertEquals("app-new", result.updatedCredentials!!.appID)
        assertEquals("token-new", result.updatedCredentials!!.tokenOnline)
    }

    @Test
    fun unchangedDirectCookieDoesNotEmitCredentials() {
        val transport = QueueTransport(mutableListOf(UnicomRawResponse(200, orderResponse().toByteArray())))
        val client = UnicomMyOrderClient(http = UnicomHTTPClient(transport, retryDelayMillis = 0))

        val result = client.fetch(
            "18612345678",
            page = 1,
            pageSize = 15,
            credentials = AccountCredentials("sid=old", "app-id", "token-old"),
        )

        assertNull(result.updatedCredentials)
    }

    private class QueueTransport(
        private val responses: MutableList<UnicomRawResponse>,
    ) : UnicomTransport {
        val requests = mutableListOf<UnicomRequest>()

        override fun post(request: UnicomRequest): UnicomRawResponse {
            requests += request
            check(responses.isNotEmpty()) { "No fake response left for ${request.url}" }
            return responses.removeAt(0)
        }
    }

    private fun orderResponse(): String = """
        {
          "respCode": "0000",
          "respDesc": "成功",
          "timeYear": "2026-08-26 19:00:00",
          "respData": [
            {
              "order_id": "order-1",
              "order_source": "D15",
              "order_source_name": "业务订单",
              "order_status_name": "已完成",
              "order_create_time": "2026-08-01 10:00:00",
              "phone_number": "18612345678",
              "order_member": [
                {
                  "order_member_id": "member-1",
                  "goods_name": "校园流量包",
                  "price": "订购",
                  "integral": "10|30",
                  "trade_tag": ["3"]
                }
              ],
              "button": [
                {
                  "button_name": "查看详情",
                  "button_type": "1",
                  "button_url": "https://omo.10010.com/dbh-evaluate-fe/index.html?orderId=order-1"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
