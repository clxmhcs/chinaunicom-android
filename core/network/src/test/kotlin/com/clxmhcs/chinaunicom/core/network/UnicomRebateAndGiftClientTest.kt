package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomRebateAndGiftClientTest {
    @Test
    fun contractQueryUsesSourceScopeAndParsesStringArray() {
        val transport = QueueTransport(
            """{"code":"0000","queryTime":"2026-08-27 08:09:10","data":"[{\"actExplain\":\"活动A\",\"serialNumber\":\"18600001234\",\"startDate\":\"20260101\",\"endDate\":\"20261231\",\"sumReturnMoney\":\"12.5\",\"totalMoney\":\"30\",\"froMoney\":\"2\",\"returndetail\":[{\"returnFreeMoney\":\"1.2\",\"returnGiftMoney\":\"3\",\"returnTime\":\"20260827\"}]}]"}""",
        )
        val client = UnicomRebateAndGiftClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            activateSession = { error("activation not expected") },
        )

        val result = client.fetchContracts(AccountCredentials("a=b", "app", "token"), RebateQueryScope.ACCOUNT)

        assertEquals(1, result.contracts.size)
        assertEquals("活动A", result.contracts.single().activityName)
        assertEquals("12.50", result.contracts.single().returnedAmount)
        assertEquals("1.20", result.contracts.single().detail.single().freeMoney)
        val body = transport.requests.single().body.decodeToString()
        assertTrue(body.contains("qrytype=0"))
        assertTrue(body.contains("duanlianjieabc="))
    }

    @Test
    fun userScopeUsesQueryTypeOneAndGiftParsesArray() {
        val transport = QueueTransport(
            """{"code":"0000","data":[]}""",
            """{"code":"0000","data":[{"giftName":"赠款A","giftMoney":"8","serialnumber":"18600001234","giftTime":"20260827"}]}""",
        )
        val client = UnicomRebateAndGiftClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            activateSession = { error("activation not expected") },
        )
        val credentials = AccountCredentials("a=b", "app", "token")
        client.fetchContracts(credentials, RebateQueryScope.USER)
        val gifts = client.fetchGiftRecords(credentials)

        assertTrue(transport.requests.first().body.decodeToString().contains("qrytype=1"))
        assertEquals("赠款A", gifts.gifts.single().name)
        assertEquals("8.00", gifts.gifts.single().amount)
    }

    @Test
    fun expiredSessionActivatesAndReturnsRenewedCredentials() {
        val transport = QueueTransport(
            """{"code":"9998"}""",
            """{"code":"0000","data":[]}""",
        )
        val renewed = AccountCredentials("renewed=yes", "app", "token2")
        val client = UnicomRebateAndGiftClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            activateSession = { renewed },
        )

        val result = client.fetchGiftRecords(AccountCredentials("old=yes", "app", "token"))

        assertNotNull(result.updatedCredentials)
        assertEquals(renewed, result.updatedCredentials)
        assertEquals("renewed=yes", transport.requests.last().headers["Cookie"])
    }

    private class QueueTransport(vararg bodies: String) : UnicomTransport {
        private val responses = bodies.map { it.encodeToByteArray() }.toMutableList()
        val requests = mutableListOf<UnicomRequest>()

        override fun post(request: UnicomRequest): UnicomRawResponse {
            requests += request
            val body = responses.removeFirst()
            return UnicomRawResponse(statusCode = 200, body = body)
        }
    }
}
