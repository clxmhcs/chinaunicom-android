package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.AppointmentTicketAvailabilityResult
import com.clxmhcs.chinaunicom.core.model.AppointmentTicketSlot
import com.clxmhcs.chinaunicom.core.model.AppointmentTicketSubmissionResult
import com.clxmhcs.chinaunicom.core.model.ServiceHallListItem
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

sealed class AppointmentTicketException(message: String) : Exception(message) {
    class Duplicate(message: String) : AppointmentTicketException(message)
    class Server(message: String) : AppointmentTicketException(message)
}

/** Native iOS-parity service-hall appointment client. No browser/H5 fallback. */
class UnicomAppointmentTicketClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(),
) {
    fun fetchAvailableSlots(
        credentials: AccountCredentials,
        hallID: String,
        epID: String,
    ): AppointmentTicketAvailabilityResult {
        val direct = performWithSession(credentials) { cookie ->
            val response = postForm(
                path = "/HallBusiness/reservationQueue/queryTimeByDay.do",
                values = mapOf("ehallId" to hallID, "epId" to epID),
                cookie = removingCookie("JSESSIONID", cookie),
            )
            parseAvailability(response.first) to response.second
        }
        val appointmentCredentials = credentials.withCookie(direct.second)
        val persistentCredentials = credentials.withCookie(removingCookie("JSESSIONID", direct.second))
        return AppointmentTicketAvailabilityResult(
            slots = direct.first.slots,
            businesses = direct.first.businesses,
            orderDescription = direct.first.orderDescription,
            appointmentCredentials = appointmentCredentials,
            updatedCredentials = persistentCredentials.takeIf { it != credentials },
        )
    }

    fun submit(
        credentials: AccountCredentials,
        hall: ServiceHallListItem,
        business: String,
        slot: AppointmentTicketSlot,
    ): AppointmentTicketSubmissionResult {
        val direct = performWithSession(credentials) { cookie ->
            val response = postForm(
                path = "/HallBusiness/reservationQueue/registering.do",
                values = mapOf(
                    "businessHallName" to hall.name,
                    "cityCode" to hall.cityCode,
                    "cityName" to hall.cityName,
                    "ehallId" to hall.id,
                    "endTime" to slot.endTime,
                    "epAddress" to hall.address,
                    "epId" to hall.epID,
                    "epJingDu_gb" to coordinateText(hall.longitude),
                    "epWeiDu_gb" to coordinateText(hall.latitude),
                    "orderBusiness" to business,
                    "orderDay" to slot.day,
                    "orderType" to "01",
                    "provinceCode" to hall.provinceCode,
                    "provinceName" to hall.provinceName,
                    "startTime" to slot.startTime,
                ),
                cookie = cookie,
            )
            parseSubmission(response.first) to response.second
        }
        val persistentCredentials = credentials.withCookie(removingCookie("JSESSIONID", direct.second))
        return AppointmentTicketSubmissionResult(
            appointmentID = direct.first.first,
            message = direct.first.second,
            updatedCredentials = persistentCredentials.takeIf { it != credentials },
        )
    }

    private fun <T> performWithSession(
        credentials: AccountCredentials,
        operation: (String) -> Pair<T, String>,
    ): Pair<T, String> {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            operation(originalCookie)
        } catch (error: Exception) {
            if (!shouldActivateSession(error)) throw error
            val activated = try {
                sessionClient.activateSession(credentials)
            } catch (activationError: Exception) {
                throw UnicomAPIException.Server("预约会话恢复失败：${activationError.message ?: "未知错误"}")
            }
            operation(UnicomCookieCodec.normalize(activated.cookie))
        }
    }

    private fun postForm(
        path: String,
        values: Map<String, String>,
        cookie: String,
    ): Pair<ByteArray, String> {
        val response = http.post(
            url = BASE_URL + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to ORIGIN,
                "Referer" to "$ORIGIN/",
                "User-Agent" to USER_AGENT,
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        val updated = if (response.cookieMutations.isEmpty()) cookie
        else UnicomCookieCodec.applying(response.cookieMutations, cookie)
        return response.data to updated
    }

    private fun parseAvailability(data: ByteArray): ParsedAvailability {
        val root = runCatching { JSONObject(data.toString(Charsets.UTF_8)) }
            .getOrElse { throw UnicomAPIException.InvalidResponse }
        validate(root)
        val payload = root.optJSONObject("data") ?: root
        val slots = buildList {
            val rows = payload.optJSONArray("dateList") ?: JSONArray()
            for (index in 0 until rows.length()) {
                val dayRow = rows.optJSONObject(index) ?: continue
                val day = dayRow.text("date") ?: dayRow.text("day") ?: dayRow.text("orderDay") ?: ""
                val timeRows = dayRow.optJSONArray("time")
                    ?: dayRow.optJSONArray("timeList")
                    ?: dayRow.optJSONArray("list")
                    ?: JSONArray()
                for (timeIndex in 0 until timeRows.length()) {
                    val row = timeRows.optJSONObject(timeIndex) ?: continue
                    val start = row.firstText("startTime", "beginTime", "start") ?: continue
                    val end = row.firstText("endTime", "finishTime", "end") ?: continue
                    val remaining = row.firstInt(
                        "normalOrderNum", "remainingCount", "remainNum", "residueNum",
                        "residue", "surplus", "surplusNum", "leftNum",
                    )
                    val status = row.firstText("status", "state", "dataState")?.lowercase(Locale.ROOT).orEmpty()
                    add(
                        AppointmentTicketSlot(
                            id = row.firstText("id", "timeId", "slotId") ?: "$day-$start-$end",
                            day = day,
                            startTime = start,
                            endTime = end,
                            remainingCount = remaining,
                            isAvailable = status == "0" && (remaining ?: 1) > 0,
                        ),
                    )
                }
            }
        }
        return ParsedAvailability(
            slots = slots,
            businesses = businessNames(payload.opt("businessList")),
            orderDescription = payload.text("orderDesc"),
        )
    }

    private fun parseSubmission(data: ByteArray): Pair<String?, String> {
        val root = runCatching { JSONObject(data.toString(Charsets.UTF_8)) }
            .getOrElse { throw UnicomAPIException.InvalidResponse }
        val code = root.firstText("rspcode", "code", "rsp_code", "status")
        val message = root.firstText("rspdesc", "msg", "message") ?: "预约提交成功"
        if (code in setOf("0003", "0004", "0005") || isDuplicate(message)) {
            throw AppointmentTicketException.Duplicate(
                "您的号码存在已预约取号订单，请勿重复预约。若需查询预约详情，可在中国联通 App 的附近营业厅中查看。",
            )
        }
        if (code == "gd0001") {
            throw AppointmentTicketException.Server("营业厅排队等候人数较多，暂停预约，请前往营业厅现场取号。")
        }
        if (code != null && !UnicomResponseStatus.isSuccess(code)) {
            throw AppointmentTicketException.Server(message)
        }
        val payload = root.optJSONObject("data")
        val id = root.firstText("reservationId", "recordId", "id")
            ?: payload?.firstText("reservationId", "recordId", "id")
        return id to message
    }

    private fun validate(root: JSONObject) {
        val code = root.firstText("code", "rspcode", "rsp_code", "status") ?: return
        if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
        if (UnicomResponseStatus.isSuccess(code) || code.lowercase(Locale.ROOT) in setOf("1", "true")) return
        val message = root.firstText("msg", "message", "rspdesc") ?: "预约服务暂不可用（$code）"
        if (isDuplicate(message)) throw AppointmentTicketException.Duplicate(message)
        throw AppointmentTicketException.Server(message)
    }

    private fun businessNames(value: Any?): List<String> {
        val rows = value as? JSONArray ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                when (val item = rows.opt(index)) {
                    is String -> item.trim().takeIf { it.isNotEmpty() }?.let(::add)
                    is JSONObject -> item.firstText("businessName", "name", "title")?.let(::add)
                }
            }
        }
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is AppointmentTicketException.Duplicate -> false
        is AppointmentTicketException.Server -> messageLooksSessionRelated(error.message.orEmpty())
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.HttpStatus -> error.statusCode in setOf(401, 403)
        is UnicomAPIException.Server -> messageLooksSessionRelated(error.serverMessage)
        else -> false
    }

    private fun messageLooksSessionRelated(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        return lower.contains("cookie") || message.contains("登录") || message.contains("在线") || message.contains("会话")
    }

    private fun AccountCredentials.withCookie(cookie: String): AccountCredentials = AccountCredentials(
        cookie = UnicomCookieCodec.normalize(cookie),
        appID = appID,
        tokenOnline = tokenOnline,
    )

    private fun removingCookie(name: String, cookie: String): String =
        UnicomCookieCodec.applying(listOf(UnicomCookieMutation(name, null)), cookie)

    private fun coordinateText(value: Double?): String {
        if (value == null || !value.isFinite()) return ""
        return String.format(Locale.US, "%.14f", value).trimEnd('0').trimEnd('.')
    }

    private fun isDuplicate(message: String): Boolean =
        message.contains("重复") || message.contains("已预约") || message.contains("预约取号订单")

    private fun JSONObject.text(key: String): String? {
        val value = opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> text(key) }

    private fun JSONObject.firstInt(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        val value = opt(key)
        when (value) {
            is Number -> value.toInt()
            else -> text(key)?.replace(",", "")?.toIntOrNull()
        }
    }

    private data class ParsedAvailability(
        val slots: List<AppointmentTicketSlot>,
        val businesses: List<String>,
        val orderDescription: String?,
    )

    companion object {
        private const val BASE_URL = "https://m.client.10010.com"
        private const val ORIGIN = "https://img.client.10010.com"
        private const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)  unicom{version:iphone_c@12.1500};ltst;OSVersion/18.0"
    }
}
