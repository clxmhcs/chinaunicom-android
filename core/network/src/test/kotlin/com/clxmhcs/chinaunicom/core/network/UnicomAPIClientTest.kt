package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
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

    private val renewalContext = UnicomSessionRenewalDeviceContext(
        deviceCode = "550E8400-E29B-41D4-A716-446655440000",
        deviceID = "b".repeat(64),
        uniqueIdentifier = "iosa" + "a".repeat(32),
        deviceModel = "Pixel-Test",
        deviceOS = "13",
        userAgentSystemVersion = "13",
        localIPv4Address = "192.0.2.8",
    )
    private val renewalProvider = UnicomSessionRenewalDeviceContextProvider { renewalContext }
    private val renewalClock = Clock.fixed(
        Instant.parse("2026-09-05T06:30:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun expiredQuotaUsesModernIosRenewalThenRetriesWithMutatedCredentials() {
        val transport = QueueTransport(
            UnicomRawResponse(200, "999998".encodeToByteArray()),
            UnicomRawResponse(
                200,
                gzip("{\"code\":\"0000\",\"appId\":\"app-2\",\"token_online\":\"new-token\"}".encodeToByteArray()),
                mapOf(
                    "Content-Encoding" to listOf("gzip"),
                    "Set-Cookie" to listOf("ecs_token=new; Path=/", "old=; Max-Age=0"),
                ),
            ),
            UnicomRawResponse(200, quotaJson),
        )
        val client = UnicomAPIClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            renewalDeviceContextProvider = renewalProvider,
            clock = renewalClock,
        )
        val result = client.fetchWidgetQuota(AccountCredentials("old=1; keep=2", "app-1", "old-token"))

        assertEquals(3, transport.requests.size)
        assertEquals(UnicomAPIClient.BASE_URL + UnicomAPIClient.QUOTA_PATH, transport.requests[0].url)
        assertEquals("old=1; keep=2", transport.requests[0].headers["Cookie"])

        val activation = transport.requests[1]
        assertEquals("https://loginhl.10010.com/mobileService/onLine.htm", activation.url)
        assertEquals("old=1; keep=2", activation.headers["Cookie"])
        assertEquals("application/x-www-form-urlencoded", activation.headers["Content-Type"])
        assertEquals("*/*", activation.headers["Accept"])
        assertEquals("zh-Hans-CN;q=1.0", activation.headers["Accept-Language"])
        assertEquals("gzip;q=1.0, compress;q=0.5", activation.headers["Accept-Encoding"])
        assertTrue(activation.headers.getValue("User-Agent").contains("ChinaUnicom4.x/12.15"))
        assertTrue(activation.headers.getValue("User-Agent").contains("build:4"))
        assertTrue(activation.headers.getValue("User-Agent").contains("unicom{version:iphone_c@12.1500}"))
        val activationBody = activation.body.toString(Charsets.UTF_8)
        listOf(
            "appId=app-1",
            "deviceBrand=iPhone",
            "deviceCode=550E8400-E29B-41D4-A716-446655440000",
            "deviceId=${"b".repeat(64)}",
            "deviceModel=Pixel-Test",
            "deviceOS=13",
            "flushkey=1",
            "isFirstInstall=0",
            "pip=192.0.2.8",
            "reqtime=2026-09-05%2014%3A30%3A00",
            "step=welcom",
            "token_online=old-token",
            "uniqueIdentifier=iosa${"a".repeat(32)}",
            "version=iphone_c%4012.1500",
            "voipToken=citc-default-token-do-not-push",
        ).forEach { expected -> assertTrue("missing $expected in $activationBody", activationBody.contains(expected)) }

        assertEquals("keep=2; ecs_token=new", transport.requests[2].headers["Cookie"])
        assertNotNull(result.updatedCredentials)
        assertEquals("keep=2; ecs_token=new", result.updatedCredentials?.cookie)
        assertEquals("app-2", result.updatedCredentials?.appID)
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
        val client = UnicomAPIClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            renewalDeviceContextProvider = renewalProvider,
            clock = renewalClock,
        )
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

    private fun gzip(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
}
