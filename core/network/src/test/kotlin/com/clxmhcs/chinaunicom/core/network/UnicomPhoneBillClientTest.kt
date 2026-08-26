package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomPhoneBillClientTest {
    private val now = Instant.parse("2026-08-25T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun fetchMonthsUsesFrozenEndpointAndCommonForm() {
        val transport = PhoneBillQueueTransport(response(monthsPayload()))
        val result = client(transport).fetchMonths(AccountCredentials("sid=1", "app", "token"))

        assertNull(result.updatedCredentials)
        assertEquals(listOf("202608", "202607"), result.months.map { it.key })
        val request = transport.requests.single()
        assertEquals(
            ComprehensiveBusinessEndpoints.PHONE_BILL_ROOT + ComprehensiveBusinessEndpoints.PHONE_BILL_MONTHS,
            request.url,
        )
        assertEquals("sid=1", request.headers["Cookie"])
        val body = request.body.decodeToString()
        assertTrue(body.contains("duanlianjieabc="))
        assertTrue(body.contains("ticketChannel="))
    }

    @Test
    fun expiredMonthsSessionActivatesWithIos90100AndPersistsRenewedFieldsInResult() {
        val transport = PhoneBillQueueTransport(
            response("{\"code\":\"9998\"}"),
            response(
                "{\"code\":\"0000\",\"appId\":\"newApp\",\"token_online\":\"newToken\"}",
                setCookies = listOf("renewed=2; Path=/"),
            ),
            response(monthsPayload()),
        )
        val result = client(transport).fetchMonths(AccountCredentials("old=1", "oldApp", "oldToken"))

        assertEquals(3, transport.requests.size)
        val activation = transport.requests[1]
        assertEquals(ComprehensiveBusinessEndpoints.PHONE_BILL_ONLINE, activation.url)
        assertNull(activation.headers["Cookie"])
        val activationBody = activation.body.decodeToString()
        assertTrue(activationBody.contains("appId=oldApp"))
        assertTrue(activationBody.contains("token_online=oldToken"))
        assertTrue(activationBody.contains("version=iphone_c%409.0100"))
        assertEquals("old=1; renewed=2", result.updatedCredentials?.cookie)
        assertEquals("newApp", result.updatedCredentials?.appID)
        assertEquals("newToken", result.updatedCredentials?.tokenOnline)
        assertEquals("old=1; renewed=2", transport.requests[2].headers["Cookie"])
    }

    @Test
    fun detailParserPreservesMoneySectionsAndParserVersion() {
        val data = parseNetworkJson(detailPayload().encodeToByteArray()) as JsonObject
        val snapshot = client(PhoneBillQueueTransport()).parseDetail(
            data = data["data"] as JsonObject,
            month = BillMonth("2026", "08"),
        )

        assertEquals(now, snapshot.fetchedAt)
        assertEquals(4, snapshot.parserVersion)
        assertEquals("88.80", snapshot.summary.realPayFee)
        assertEquals("100.00", snapshot.summary.totalPrice)
        assertEquals("138****8000", snapshot.userBills.single().mobile)
        assertEquals("88.80", snapshot.userBills.single().payable)
        assertEquals("套餐月费", snapshot.userBills.single().allItems.single().name)
        assertEquals("100.00", snapshot.userBills.single().allItems.single().originalFee)
        assertEquals("11.20", snapshot.userBills.single().allItems.single().discount)
        assertEquals("88.80", snapshot.userBills.single().allItems.single().realFee)
        assertEquals("账户消费", snapshot.accountSections.single().title)
    }

    private fun client(transport: PhoneBillQueueTransport) = UnicomPhoneBillClient(
        http = UnicomHTTPClient(transport, retryDelayMillis = 0),
        clock = clock,
    )

    private fun response(body: String, setCookies: List<String> = emptyList()) = UnicomRawResponse(
        statusCode = 200,
        body = body.encodeToByteArray(),
        headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
    )

    private fun monthsPayload() = """
        {"code":"0000","data":{"months":[
          {"historyMonthAndYear":"202608","historyYear":"2026","historyMonth":"08"},
          {"historyMonthAndYear":"202607","historyYear":"2026","historyMonth":"07"}
        ]}}
    """.trimIndent()

    private fun detailPayload() = """
        {
          "code":"0000",
          "data":{
            "times":"2026-08-25 20:00:00",
            "amountDue":"88.8",
            "realPayFee":"88.80",
            "acctBill":{
              "totalPrice":"100",
              "totalAdiscnt":"11.2",
              "totalRealFee":"88.8",
              "totalAdjustAfter":"0",
              "acctBillList":[{
                "bill":{"integrateitem":"账户消费"},
                "subItems":[{"bill":{"integrateitem":"账户费","price":"100","adiscnt":"11.2","fee":"88.8"}}]
              }]
            },
            "userBillList":[{
              "serialnumber":"138****8000",
              "realPayFeeP":"88.8",
              "userDetail":[{
                "bill":{"integrateitem":"套餐"},
                "subItems":[{"bill":{"integrateitem":"套餐月费","integrateitemcode":"A1","price":"100","adiscnt":"11.2","fee":"88.8"}}]
              }]
            }]
          }
        }
    """.trimIndent()
}

private class PhoneBillQueueTransport(vararg responses: UnicomRawResponse) : UnicomTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<UnicomRequest>()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request
        if (queue.isEmpty()) error("No queued response for ${request.url}")
        return queue.removeFirst()
    }
}
