package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.TariffZoneProductReference
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomTariffZoneClientTest {
    @Test
    fun indexUsesSourceEndpointHeadersFormAndParsesDetectedRegion() {
        val transport = QueueTransport(
            Response(
                body = """{"code":"0000","data":{"provinceList":[{"provCode":"011","cityCode":"111","provName":"北京","cityName":"备用"}],"userProCode":"011","userCityCode":"110","userProName":"北京","userCityName":"北京","levelList":[{"firstLevel":"1","firstLevelName":"套餐","secondLevels":[{"secondLevel":"1001","secondLevelName":"移网"}]}]}}""",
                headers = mapOf("Set-Cookie" to listOf("route=abc; Path=/")),
            ),
        )
        val client = UnicomTariffZoneClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            systemVersionProvider = { "26.6" },
            uuidProvider = { java.util.UUID.fromString("00000000-0000-0000-0000-000000000001") },
        )

        val result = client.fetchIndex(AccountCredentials("old=yes", "app", "token"))

        assertEquals("011", result.index.userProvinceCode)
        assertEquals("110", result.index.userCityCode)
        assertEquals("110", result.index.regions.first().cityCode)
        assertEquals("1001", result.index.levels.single().secondLevels.single().id)
        assertNotNull(result.updatedCredentials)
        assertTrue(result.updatedCredentials!!.cookie.contains("route=abc"))

        val request = transport.requests.single()
        assertEquals("${UnicomTariffZoneClient.ROOT}${UnicomTariffZoneClient.INDEX_PATH}", request.url)
        assertEquals("https://imgxx.client.10010.com", request.headers["Origin"])
        assertEquals("https://imgxx.client.10010.com/zifeizhuanqu/index.html", request.headers["Referer"])
        assertEquals(UnicomClientProfile.h5UserAgent("26.6"), request.headers["User-Agent"])
        assertTrue(request.headers["User-Agent"].orEmpty().contains("unicom{version:iphone_c@12.1500}"))
        assertTrue(request.headers["User-Agent"].orEmpty().contains("OSVersion/26.6"))
        val body = request.body.decodeToString()
        assertTrue(body.contains("provinceId="))
        assertTrue(body.contains("cityId="))
        assertTrue(body.contains("behaviorId=IOS00000000000000000000000000000001"))
    }

    @Test
    fun expiredSessionUsesSharedActivationThenRetriesWithSameH5Identity() {
        val transport = QueueTransport(
            Response("""{"code":"9998"}"""),
            Response(
                body = """{"code":"0000","appId":"app-new","token_online":"newToken"}""",
                headers = mapOf("Set-Cookie" to listOf("renewed=1; Path=/")),
            ),
            Response(
                body = """{"code":"0000","data":{"provinceList":[],"userProCode":"","userCityCode":"","userProName":"","userCityName":"","levelList":[]}}""",
            ),
        )
        val http = UnicomHTTPClient(transport, retryDelayMillis = 0)
        val client = UnicomTariffZoneClient(
            http = http,
            sessionClient = testUnicomAPIClient(http),
            systemVersionProvider = { "18.7" },
        )

        val result = client.fetchIndex(AccountCredentials("c_mobile=13800138000; sid=1", "app", "oldToken"))

        assertEquals(3, transport.requests.size)
        assertEquals(UnicomAPIClient.ONLINE_URL, transport.requests[1].url)
        assertTrue(transport.requests[1].body.decodeToString().contains("version=iphone_c%4012.1500"))
        val expectedUserAgent = UnicomClientProfile.h5UserAgent("18.7")
        assertEquals(expectedUserAgent, transport.requests[0].headers["User-Agent"])
        assertEquals(expectedUserAgent, transport.requests[2].headers["User-Agent"])
        assertTrue(transport.requests[2].headers["Cookie"].orEmpty().contains("renewed=1"))
        assertEquals("app-new", result.updatedCredentials?.appID)
        assertEquals("newToken", result.updatedCredentials?.tokenOnline)
    }

    @Test
    fun referencesTreatCode0001AsLegitimateEmptyResult() {
        val transport = QueueTransport(Response("""{"code":"0001","msg":"暂无数据"}"""))
        val client = UnicomTariffZoneClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            systemVersionProvider = { "26.6" },
        )
        val region = TariffZoneRegion("011", "110", "北京", "北京")

        val result = client.fetchProductReferences(
            AccountCredentials("a=b", "app", "token"),
            TariffZoneScope.NATIONAL,
            "1",
            "1001",
            region,
        )

        assertTrue(result.references.isEmpty())
        val request = transport.requests.single()
        assertTrue(request.url.endsWith(UnicomTariffZoneClient.REFERENCES_PATH))
        val body = request.body.decodeToString()
        assertTrue(body.contains("tariffAttributes=1"))
        assertTrue(body.contains("firstLevel=1"))
        assertTrue(body.contains("secondLevel=1001"))
        assertTrue(body.contains("provinceId=011"))
        assertTrue(body.contains("cityId=110"))
    }

    @Test
    fun detailsUseJoinedIDsAndParseNestedRowsAndDates() {
        val transport = QueueTransport(
            Response(
                """{"code":"0000","data":{"timeStr":"2026/08/27 20:40","dataList":[{"detailsList":[{"reportNo":"P001","name":"套餐A","feesStandard":"59","feeUnit":"元/月","commonData":"20","dataUnit":"GB","startDate":"20260102","endDate":"20261231"}]}]}}""",
            ),
        )
        val client = UnicomTariffZoneClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            systemVersionProvider = { "18.7" },
        )
        val references = listOf(
            TariffZoneProductReference("A1", "套餐A"),
            TariffZoneProductReference("B2", "套餐B"),
        )
        val region = TariffZoneRegion("011", "110", "北京", "北京")

        val result = client.fetchDetails(AccountCredentials("a=b", "app", "token"), references, 2, region)

        assertEquals(1, result.details.size)
        assertEquals("P001", result.details.single().reportNo)
        assertEquals("2026/01/02", result.details.single().startDate)
        assertEquals("2026/12/31", result.details.single().endDate)
        assertEquals("2026/08/27 20:40", result.timeText)
        val request = transport.requests.single()
        assertTrue(request.url.endsWith("${UnicomTariffZoneClient.DETAIL_PATH}/A1_B2"))
        assertEquals(UnicomClientProfile.h5UserAgent("18.7"), request.headers["User-Agent"])
        val body = request.body.decodeToString()
        assertTrue(body.contains("page=2"))
        assertTrue(body.contains("size=2"))
    }

    private data class Response(
        val body: String,
        val headers: Map<String, List<String>> = emptyMap(),
    )

    private class QueueTransport(vararg responses: Response) : UnicomTransport {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<UnicomRequest>()

        override fun post(request: UnicomRequest): UnicomRawResponse {
            requests += request
            val response = queue.removeFirst()
            return UnicomRawResponse(
                statusCode = 200,
                body = response.body.encodeToByteArray(),
                headers = response.headers,
            )
        }
    }
}
