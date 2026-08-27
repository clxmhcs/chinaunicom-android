package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.ServiceHallAction
import com.clxmhcs.chinaunicom.core.model.ServiceHallActionKind
import com.clxmhcs.chinaunicom.core.model.ServiceHallCategory
import com.clxmhcs.chinaunicom.core.model.ServiceHallCity
import com.clxmhcs.chinaunicom.core.model.ServiceHallCityFetchResult
import com.clxmhcs.chinaunicom.core.model.ServiceHallCoordinate
import com.clxmhcs.chinaunicom.core.model.ServiceHallFetchResult
import com.clxmhcs.chinaunicom.core.model.ServiceHallListItem
import com.clxmhcs.chinaunicom.core.model.ServiceHallOverview
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** iOS-parity nearby-service-hall client. */
class UnicomServiceHallClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(),
) {
    fun fetchCities(credentials: AccountCredentials): ServiceHallCityFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            val direct = fetchCitiesOnce(originalCookie)
            val final = credentials.withCookie(direct.second)
            ServiceHallCityFetchResult(direct.first, final.takeIf { it != credentials })
        } catch (error: Exception) {
            if (!shouldActivateSession(error)) throw error
            val activated = activate(credentials)
            val retried = fetchCitiesOnce(UnicomCookieCodec.normalize(activated.cookie))
            val final = activated.withCookie(retried.second)
            ServiceHallCityFetchResult(retried.first, final.takeIf { it != credentials })
        }
    }

    fun fetchOverview(
        credentials: AccountCredentials,
        provinceCode: String,
        cityCode: String,
        coordinate: ServiceHallCoordinate,
        category: ServiceHallCategory = ServiceHallCategory.SELF_OPERATED,
        pageIndex: Int = 0,
        labelIDs: List<String> = emptyList(),
    ): ServiceHallFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            val direct = fetchOnce(
                originalCookie, provinceCode, cityCode, coordinate, category, pageIndex, labelIDs,
            )
            val final = credentials.withCookie(direct.second)
            ServiceHallFetchResult(direct.first, final.takeIf { it != credentials })
        } catch (error: Exception) {
            if (!shouldActivateSession(error)) throw error
            val activated = activate(credentials)
            val retried = fetchOnce(
                UnicomCookieCodec.normalize(activated.cookie), provinceCode, cityCode,
                coordinate, category, pageIndex, labelIDs,
            )
            val final = activated.withCookie(retried.second)
            ServiceHallFetchResult(retried.first, final.takeIf { it != credentials })
        }
    }

    private fun fetchCitiesOnce(cookie: String): Pair<List<ServiceHallCity>, String> {
        val response = postForm(
            path = "/mobileService/customerService/getDefaultAddress.htm",
            values = mapOf("version" to CLIENT_VERSION),
            cookie = cookie,
        )
        val rows = runCatching { JSONArray(response.first.toString(Charsets.UTF_8)) }
            .getOrElse { throw UnicomAPIException.InvalidResponse }
        val cities = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val cityCode = row.text("cityCode") ?: continue
                val cityName = row.text("cityName") ?: continue
                add(
                    ServiceHallCity(
                        id = row.text("id") ?: "$cityCode-$index",
                        cityCode = cityCode,
                        cityName = cityName,
                        provinceCode = row.text("privienceCode") ?: row.text("provinceCode") ?: "",
                        provinceName = row.text("provienceName") ?: row.text("provinceName") ?: "",
                        longitude = row.number("longitude"),
                        latitude = row.number("latitude"),
                        sortLetters = row.text("sortLetters") ?: "",
                    ),
                )
            }
        }
        return cities to response.second
    }

    private fun fetchOnce(
        initialCookie: String,
        provinceCode: String,
        cityCode: String,
        coordinate: ServiceHallCoordinate,
        category: ServiceHallCategory,
        pageIndex: Int,
        labelIDs: List<String>,
    ): Pair<ServiceHallOverview, String> {
        var cookie = initialCookie
        val response = postForm(
            path = "/nearhall/customerService/getEhallListH5.do",
            values = mapOf(
                "provinceCode" to provinceCode,
                "version" to CLIENT_VERSION,
                "cityCode" to cityCode,
                "beginNum" to pageIndex.coerceAtLeast(0).toString(),
                "titleFlag" to category.wireValue,
                "labelIds" to labelIDs.joinToString(","),
                "destination_jd" to coordinateText(coordinate.longitude),
                "destination_wd" to coordinateText(coordinate.latitude),
                "longitude" to coordinateText(coordinate.longitude),
                "latitude" to coordinateText(coordinate.latitude),
                "reqPosition" to "sth5",
            ),
            cookie = cookie,
        )
        cookie = response.second
        val halls = parseHalls(response.first, category, coordinate)
        val actionsResult = runCatching { fetchAllowedActions(cityCode, cookie) }.getOrNull()
        if (actionsResult != null) cookie = actionsResult.second
        val actions = actionsResult?.first?.takeIf { it.isNotEmpty() } ?: fallbackActions
        return ServiceHallOverview(category, pageIndex.coerceAtLeast(0), halls, actions) to cookie
    }

    private fun fetchAllowedActions(cityCode: String, cookie: String): Pair<List<ServiceHallAction>, String> {
        val response = postForm(
            path = "/nearhall/customerService/getCityBusinessH5.do",
            values = mapOf(
                "duanlianjieabc" to "null",
                "cityCode" to cityCode,
                "version" to CLIENT_VERSION,
            ),
            cookie = cookie,
        )
        val rows = runCatching { JSONArray(response.first.toString(Charsets.UTF_8)) }
            .getOrElse { throw UnicomAPIException.InvalidResponse }
        val actions = buildList {
            for (index in 0 until rows.length()) {
                parseAllowedAction(rows.optJSONObject(index) ?: continue)?.let(::add)
            }
        }.sortedWith(compareBy<ServiceHallAction> { it.sortOrder }.thenBy { it.title })
        return actions to response.second
    }

    private fun parseHalls(
        data: ByteArray,
        category: ServiceHallCategory,
        coordinate: ServiceHallCoordinate,
    ): List<ServiceHallListItem> {
        val root = runCatching { JSONObject(data.toString(Charsets.UTF_8)) }
            .getOrElse { throw UnicomAPIException.InvalidResponse }
        val code = root.text("code")
        if (!code.isNullOrBlank() && !UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            throw UnicomAPIException.Server(root.text("msg") ?: "营业厅列表查询失败（code: $code）")
        }
        val rows = root.optJSONArray("ehallList") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val hallID = row.text("id") ?: continue
                val epID = row.text("epId") ?: continue
                val supportsAppointment = row.text("isShowOrder")?.let { it == "0" }
                val appointmentURL = if (supportsAppointment == false) null else perHallAppointmentURL(hallID, coordinate)
                val image = listOf("epBusinessImg", "epNewBusinessImg", "PinEpActImg")
                    .firstNotNullOfOrNull { row.text(it) }
                add(
                    ServiceHallListItem(
                        id = hallID,
                        epID = epID,
                        name = row.text("epName") ?: "营业厅",
                        category = category,
                        provinceCode = row.text("provCode") ?: "",
                        cityCode = row.text("cityCode") ?: "",
                        provinceName = row.text("epProvincename") ?: "",
                        cityName = row.text("epCityname") ?: "",
                        districtName = row.text("epXianname") ?: "",
                        address = row.text("epAddress") ?: "",
                        longitude = row.number("epJingDu"),
                        latitude = row.number("epWeiDu"),
                        distanceMeters = row.number("distance"),
                        businessHours = row.text("epBusinessTime") ?: "",
                        businessStatus = row.text("businessStatus") ?: "",
                        ratingText = row.text("starScore") ?: "",
                        imageURL = image,
                        labels = row.stringArray("ehallLabelMsgs"),
                        detailURL = row.text("ehall_frontAddress"),
                        supportsAppointment = supportsAppointment,
                        appointmentURL = appointmentURL,
                    ),
                )
            }
        }
    }

    private fun parseAllowedAction(row: JSONObject): ServiceHallAction? {
        val title = row.text("title") ?: return null
        val kind = when (title) {
            "我的预约" -> ServiceHallActionKind.MY_APPOINTMENTS
            "预约取号" -> ServiceHallActionKind.APPOINTMENT_TICKET
            else -> return null
        }
        val url = row.text("address") ?: return null
        return ServiceHallAction(
            id = row.text("id") ?: kind.name,
            kind = kind,
            title = title,
            iconURL = row.text("busPic"),
            destinationURL = url,
            loginFlag = row.text("needLogin") ?: "",
            sortOrder = row.text("sortNum")?.toIntOrNull() ?: row.number("sortNum")?.toInt() ?: 0,
        )
    }

    private fun postForm(path: String, values: Map<String, String>, cookie: String): Pair<ByteArray, String> {
        val response = http.post(
            url = BASE_URL + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to HALL_PAGE_ORIGIN,
                "Referer" to "$HALL_PAGE_ORIGIN/",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        val updatedCookie = if (response.cookieMutations.isEmpty()) cookie
        else UnicomCookieCodec.applying(response.cookieMutations, cookie)
        return response.data to updatedCookie
    }

    private fun activate(credentials: AccountCredentials): AccountCredentials = try {
        sessionClient.activateSession(credentials)
    } catch (error: Exception) {
        throw UnicomAPIException.Server("营业厅会话恢复失败：${error.message ?: "未知错误"}")
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> {
            val text = error.serverMessage.lowercase(Locale.ROOT)
            text.contains("cookie") || text.contains("登录") || text.contains("在线")
        }
        else -> false
    }

    private fun AccountCredentials.withCookie(cookie: String): AccountCredentials = AccountCredentials(
        cookie = UnicomCookieCodec.normalize(cookie),
        appID = appID,
        tokenOnline = tokenOnline,
    )

    private fun perHallAppointmentURL(hallID: String, coordinate: ServiceHallCoordinate): String =
        "https://img.client.10010.com/fjyyt/index.html#/ph?ehallId=$hallID&mejd=${coordinateText(coordinate.longitude)}&mewd=${coordinateText(coordinate.latitude)}"

    private fun coordinateText(value: Double): String = String.format(Locale.US, "%.14f", value)
        .trimEnd('0').trimEnd('.')

    private fun JSONObject.text(key: String): String? {
        val value = opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.number(key: String): Double? = text(key)?.replace(",", "")?.toDoubleOrNull()

    private fun JSONObject.stringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)?.toString()?.trim().orEmpty()
                if (value.isNotEmpty() && value != "null") add(value)
            }
        }
    }

    companion object {
        private const val CLIENT_VERSION = "iphone_c@12.1500"
        private const val HALL_PAGE_ORIGIN = "https://img.client.10010.com"
        private const val BASE_URL = "https://m.client.10010.com"

        private val fallbackActions = listOf(
            ServiceHallAction(
                id = "myAppointments",
                kind = ServiceHallActionKind.MY_APPOINTMENTS,
                title = "我的预约",
                iconURL = null,
                destinationURL = "https://img.client.10010.com/saomaquhao/index.html",
                loginFlag = "00",
                sortOrder = 3,
            ),
            ServiceHallAction(
                id = "appointmentTicket",
                kind = ServiceHallActionKind.APPOINTMENT_TICKET,
                title = "预约取号",
                iconURL = null,
                destinationURL = "https://img.client.10010.com/fjyyt/index.html#/shopList?from=menu",
                loginFlag = "01",
                sortOrder = 4,
            ),
        )
    }
}
