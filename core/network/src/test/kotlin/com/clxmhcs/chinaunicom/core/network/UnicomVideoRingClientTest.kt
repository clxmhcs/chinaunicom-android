package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomVideoRingClientTest {
    @Test
    fun sourceChainUsesNativeTicketAnd10155SessionHeaders() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"0000","ticket":"ticket-A"}"""),
            response("""{"success":true,"code":"200","result":{"userid":123,"caller":"18600001234"},"accessToken":"abc"}"""),
            response("""{"code":"200","result":1}"""),
            response("""{"code":"200","result":[{"memberName":"铂金会员","memberType":"15","isMember":1},{"memberName":"AI彩铃升级版","memberType":"76","isMember":0}]}"""),
            response("""{"code":"200","result":{"productlist":[{"spuId":"p1","spuName":"爱奇艺会员","spuImgurl":"https://example.invalid/a.png","rightNum":1,"received":1}]}}"""),
        )
        val client = UnicomVideoRingClient(
            transport = transport,
            activateSession = { error("activation not expected") },
            testOnly = Unit,
        )

        val result = client.fetchMemberState(
            AccountCredentials("ecs_token=ecsA; other=1", "app", "token"),
            "18600001234",
        )

        assertEquals("18600001234", result.state.phoneNumber)
        assertTrue(result.state.isEnabled)
        assertEquals(2, result.state.members.size)
        assertTrue(result.state.members.first().isMember)
        assertEquals("爱奇艺会员", result.state.benefits.single().name)
        assertEquals("1沃券", result.state.benefits.single().price)
        assertTrue(result.state.benefits.single().received == true)
        assertEquals("GET", transport.requests[0].method)
        assertTrue(transport.requests[0].url.contains("getTicketByNative"))
        assertTrue(transport.requests[0].url.contains("appId=edop_unicom_c43eac06"))
        assertEquals("ecs_token=ecsA; other=1", transport.requests[0].headers["Cookie"])
        assertTrue(transport.requests[1].body.decodeToString().contains("appid=edop_unicom_c43eac06"))
        assertEquals("3000013947", transport.requests[2].headers["appid"])
        assertEquals("123", transport.requests[2].headers["uid"])
        assertEquals("Bearer abc", transport.requests[2].headers["accessToken"])
        assertEquals("Bearer abc", transport.requests[2].headers["Authorization"])
        assertTrue(transport.requests[3].body.decodeToString().contains("includeAllConfigure"))
        assertTrue(transport.requests[4].body.decodeToString().contains("\"memberType\":\"15\""))
    }

    @Test
    fun failedNativeTicketRefreshesOnlySelectedAccountCredentialsThenRetries() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"9998","rsp_desc":"expired"}"""),
            response("""{"rsp_code":"0000","ticket":"ticket-B"}"""),
            response("""{"code":"200","result":{"userid":"u2","caller":"18600001234"},"accessToken":"Bearer zzz"}"""),
            response("""{"code":"200","result":0}"""),
            response("""{"code":"200","result":[{"memberName":"铂金会员","memberType":"15","isMember":0}]}"""),
            response("""{"code":"200","result":{"productlist":[]}}"""),
        )
        val renewed = AccountCredentials("ecs_token=newEcs; renewed=1", "app2", "token2")
        var activationCount = 0
        val client = UnicomVideoRingClient(
            transport = transport,
            activateSession = {
                activationCount += 1
                renewed
            },
            testOnly = Unit,
        )

        val result = client.fetchMemberState(
            AccountCredentials("ecs_token=oldEcs", "app", "token"),
            "18600001234",
        )

        assertEquals(1, activationCount)
        assertEquals(renewed, result.updatedCredentials)
        assertTrue(transport.requests[1].url.contains("token=newEcs"))
        assertFalse(result.state.isEnabled)
    }

    @Test
    fun callerMismatchIsHardFailureAndNeverReturnsCrossAccountData() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"0000","ticket":"ticket-C"}"""),
            response("""{"code":"200","result":{"userid":"u3","caller":"18500005678"},"accessToken":"abc"}"""),
        )
        val client = UnicomVideoRingClient(
            transport = transport,
            activateSession = { it },
            testOnly = Unit,
        )

        val error = runCatching {
            client.fetchMemberState(AccountCredentials("ecs_token=ecs", "app", "token"), "18600001234")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is VideoRingAPIException.AccountMismatch)
        assertTrue(error?.message?.contains("186****1234") == true)
        assertTrue(error?.message?.contains("185****5678") == true)
        assertEquals(2, transport.requests.size)
    }

    private fun response(body: String) = UnicomRawResponse(200, body.encodeToByteArray())

    private class QueueVideoRingTransport(vararg responses: UnicomRawResponse) : VideoRingTransport {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<VideoRingTransportRequest>()

        override fun execute(request: VideoRingTransportRequest): UnicomRawResponse {
            requests += request
            return queue.removeFirst()
        }
    }
}
