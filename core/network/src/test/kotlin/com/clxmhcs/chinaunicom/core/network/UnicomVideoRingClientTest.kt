package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomVideoRingClientTest {
    private val clientUID = "0123456789abcdef0123456789abcdefabcd"

    @Test
    fun activeInlineChainUsesSigned10155HeadersAndMemberStateEndpoint() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"0000","ticket":"ticket-A"}"""),
            response("""{"code":"200","result":{"userid":"server-user","caller":"18600001234"},"accessToken":"abc"}"""),
            response("""{"code":"200","result":[{"memberName":"AI彩铃视听剧场会员","memberType":"87","isMember":0},{"memberName":"铂金会员","memberType":"15","isMember":0},{"memberName":"AI彩铃升级版","memberType":"76","isMember":0}]}"""),
            response("""{"code":"200","result":[{"memberName":"铂金会员","memberType":"15","status":1,"startTime":"20260801000000","endTime":"20270901000000"},{"memberName":"AI彩铃视听剧场会员","memberType":"87","status":0}]}"""),
        )
        val client = testClient(transport)

        val result = client.fetchMemberState(
            AccountCredentials("ecs_token=ecsA; other=1", "app", "token"),
            "18600001234",
        )

        assertEquals("18600001234", result.state.phoneNumber)
        assertEquals(3, result.state.members.size)
        assertFalse(result.state.members.first { it.memberType == "87" }.isMember)
        val platinum = result.state.members.first { it.memberType == "15" }
        assertTrue(platinum.isMember)
        assertEquals("20260801000000", platinum.startTime)
        assertEquals("20270901000000", platinum.endTime)
        assertFalse(result.state.members.first { it.memberType == "76" }.isMember)
        assertNull(result.updatedCredentials)

        assertEquals(4, transport.requests.size)
        assertTrue(transport.requests[0].url.contains("getTicketByNative"))
        assertEquals("ecs_token=ecsA; other=1", transport.requests[0].headers["Cookie"])
        assertTrue(transport.requests[1].url.endsWith(UnicomVideoRingClient.LOGIN))
        assertTrue(transport.requests[2].url.endsWith(UnicomVideoRingClient.MEMBER_INFO))
        assertTrue(transport.requests[3].url.endsWith(UnicomVideoRingClient.MEMBER_STATE))

        for (request in transport.requests.drop(1)) {
            assertEquals("3000013947", request.headers["appid"])
            assertEquals(clientUID, request.headers["uid"])
            assertEquals("1700000000000", request.headers["timestamp"])
            assertEquals("0.0000000000000001", request.headers["nonce"])
            assertEquals("73FBD1A1B1FFA192BEF433CAF736ADA9", request.headers["sign"])
            assertEquals("1018", request.headers["oswoversion"])
            assertEquals("zh-Hans-CN;q=1.0", request.headers["Accept-Language"])
            assertEquals(UnicomVideoRingClient.USER_AGENT, request.headers["User-Agent"])
        }
        assertNull(transport.requests[1].headers["accessToken"])
        assertEquals("Bearer abc", transport.requests[2].headers["accessToken"])
        assertEquals("Bearer abc", transport.requests[3].headers["accessToken"])
        assertNull(transport.requests[2].headers["Authorization"])
    }

    @Test
    fun failedNativeTicketRefreshesOnlySelectedCredentialsThenRetries() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"9998","rsp_desc":"expired"}"""),
            response("""{"rsp_code":"0000","ticket":"ticket-B"}"""),
            response("""{"code":"200","result":{"caller":"18600001234"},"accessToken":"Bearer zzz"}"""),
            response("""{"code":"200","result":[{"memberName":"铂金会员","memberType":"15","isMember":0}]}"""),
            response("""{"code":"200","result":[]}"""),
        )
        val renewed = AccountCredentials("ecs_token=newEcs; renewed=1", "app2", "token2")
        var activationCount = 0
        val client = UnicomVideoRingClient(
            transport = transport,
            activateSession = {
                activationCount += 1
                renewed
            },
            clientUID = clientUID,
            clockMillis = { 1_700_000_000_000L },
            nonceProvider = { "0.0000000000000001" },
            testOnly = Unit,
        )

        val result = client.fetchMemberState(
            AccountCredentials("ecs_token=oldEcs", "app", "token"),
            "18600001234",
        )

        assertEquals(1, activationCount)
        assertEquals(renewed, result.updatedCredentials)
        assertTrue(transport.requests[1].url.contains("token=newEcs"))
        assertEquals(3, result.state.members.size)
        assertTrue(result.state.members.none { it.isMember })
    }

    @Test
    fun callerMismatchIsHardFailureBeforeMemberQueries() {
        val transport = QueueVideoRingTransport(
            response("""{"rsp_code":"0000","ticket":"ticket-C"}"""),
            response("""{"code":"200","result":{"caller":"18500005678"},"accessToken":"abc"}"""),
        )
        val client = testClient(transport)

        val error = runCatching {
            client.fetchMemberState(AccountCredentials("ecs_token=ecs", "app", "token"), "18600001234")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is VideoRingAPIException.AccountMismatch)
        assertTrue(error?.message?.contains("186****1234") == true)
        assertTrue(error?.message?.contains("185****5678") == true)
        assertEquals(2, transport.requests.size)
    }

    private fun testClient(transport: VideoRingTransport) = UnicomVideoRingClient(
        transport = transport,
        activateSession = { error("activation not expected") },
        clientUID = clientUID,
        clockMillis = { 1_700_000_000_000L },
        nonceProvider = { "0.0000000000000001" },
        testOnly = Unit,
    )

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
