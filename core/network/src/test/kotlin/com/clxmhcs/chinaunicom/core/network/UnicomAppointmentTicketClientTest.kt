package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.ServiceHallCategory
import com.clxmhcs.chinaunicom.core.model.ServiceHallCoordinate
import com.clxmhcs.chinaunicom.core.model.ServiceHallListItem
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicomAppointmentTicketClientTest {
    @Test
    fun availabilityUsesNativeEndpointDropsOldJsessionidAndKeepsFreshOneTransiently() {
        val transport = AppointmentQueueTransport(
            response(
                """{"code":"0000","data":{"businessList":[{"businessName":"综合业务"}],"orderDesc":"请按预约时间到厅","dateList":[{"date":"2026-08-29","timeList":[{"id":"slot-1","startTime":"09:00","endTime":"09:30","normalOrderNum":"3","status":"0"}]}]}}""",
                listOf("JSESSIONID=fresh; Path=/", "hall_cookie=1; Path=/"),
            ),
        )
        val result = client(transport).fetchAvailableSlots(
            credentials = AccountCredentials("c_mobile=13800138000; JSESSIONID=stale; sid=1", "app", "token"),
            hallID = "hall-1",
            epID = "ep-1",
        )

        val request = transport.requests.single()
        assertTrue(request.url.endsWith("/HallBusiness/reservationQueue/queryTimeByDay.do"))
        assertTrue(request.body.decodeToString().contains("ehallId=hall-1"))
        assertTrue(request.body.decodeToString().contains("epId=ep-1"))
        assertFalse(request.headers["Cookie"].orEmpty().contains("JSESSIONID=stale"))
        assertTrue(result.appointmentCredentials.cookie.contains("JSESSIONID=fresh"))
        assertFalse(result.updatedCredentials?.cookie.orEmpty().contains("JSESSIONID="))
        assertEquals("综合业务", result.businesses.single())
        assertEquals("slot-1", result.slots.single().id)
        assertTrue(result.slots.single().isAvailable)
        assertEquals(3, result.slots.single().remainingCount)
    }

    @Test
    fun submitUsesNativeEndpointAndAllIosParityFields() {
        val transport = AppointmentQueueTransport(
            response("""{"code":"0000","message":"预约成功","data":{"reservationId":"order-1"}}"""),
        )
        val availability = com.clxmhcs.chinaunicom.core.model.AppointmentTicketSlot(
            id = "slot-1",
            day = "2026-08-29",
            startTime = "09:00",
            endTime = "09:30",
            remainingCount = 2,
            isAvailable = true,
        )
        val hall = ServiceHallListItem(
            id = "hall-1",
            epID = "ep-1",
            name = "测试营业厅",
            category = ServiceHallCategory.SELF_OPERATED,
            provinceCode = "11",
            cityCode = "110",
            provinceName = "测试省",
            cityName = "测试市",
            districtName = "测试区",
            address = "测试路1号",
            longitude = 116.4,
            latitude = 39.9,
            distanceMeters = 100.0,
            businessHours = "09:00-18:00",
            businessStatus = "营业中",
            ratingText = "5",
            imageURL = null,
            labels = emptyList(),
            detailURL = null,
            supportsAppointment = true,
            appointmentURL = null,
        )
        val result = client(transport).submit(
            credentials = AccountCredentials("c_mobile=13800138000; JSESSIONID=fresh; sid=1", "app", "token"),
            hall = hall,
            business = "综合业务",
            slot = availability,
        )

        val request = transport.requests.single()
        assertTrue(request.url.endsWith("/HallBusiness/reservationQueue/registering.do"))
        val body = request.body.decodeToString()
        for (fragment in listOf(
            "businessHallName=%E6%B5%8B%E8%AF%95%E8%90%A5%E4%B8%9A%E5%8E%85",
            "cityCode=110",
            "ehallId=hall-1",
            "epId=ep-1",
            "orderBusiness=%E7%BB%BC%E5%90%88%E4%B8%9A%E5%8A%A1",
            "orderDay=2026-08-29",
            "orderType=01",
            "startTime=09%3A00",
            "endTime=09%3A30",
        )) assertTrue("missing $fragment in $body", body.contains(fragment))
        assertEquals("order-1", result.appointmentID)
        assertEquals("预约成功", result.message)
    }

    @Test
    fun duplicateAppointmentResponseIsHardFailure() {
        val transport = AppointmentQueueTransport(
            response("""{"code":"0003","message":"已有预约取号订单"}"""),
        )
        val error = runCatching {
            client(transport).submit(
                credentials = AccountCredentials("sid=1", "app", "token"),
                hall = minimalHall(),
                business = "综合业务",
                slot = com.clxmhcs.chinaunicom.core.model.AppointmentTicketSlot("1", "2026-08-29", "09:00", "09:30", 1, true),
            )
        }.exceptionOrNull()
        assertTrue(error is AppointmentTicketException.Duplicate)
    }

    private fun minimalHall() = ServiceHallListItem(
        id = "hall-1", epID = "ep-1", name = "营业厅", category = ServiceHallCategory.SELF_OPERATED,
        provinceCode = "11", cityCode = "110", provinceName = "省", cityName = "市", districtName = "区",
        address = "地址", longitude = 1.0, latitude = 2.0, distanceMeters = null, businessHours = "",
        businessStatus = "", ratingText = "", imageURL = null, labels = emptyList(), detailURL = null,
        supportsAppointment = true, appointmentURL = null,
    )

    private fun client(transport: AppointmentQueueTransport): UnicomAppointmentTicketClient {
        val http = UnicomHTTPClient(transport, retryDelayMillis = 0)
        return UnicomAppointmentTicketClient(http, UnicomAPIClient(http = http))
    }

    private fun response(body: String, setCookies: List<String> = emptyList()) = UnicomRawResponse(
        statusCode = 200,
        body = body.encodeToByteArray(),
        headers = if (setCookies.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookies),
    )
}

private class AppointmentQueueTransport(vararg responses: UnicomRawResponse) : UnicomTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<UnicomRequest>()

    override fun post(request: UnicomRequest): UnicomRawResponse {
        requests += request
        check(queue.isNotEmpty()) { "Unexpected request: ${request.url}" }
        return queue.removeFirst()
    }
}
