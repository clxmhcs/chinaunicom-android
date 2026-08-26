package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderAction
import com.clxmhcs.chinaunicom.core.model.MyOrderFetchResult
import com.clxmhcs.chinaunicom.core.model.MyOrderMember
import com.clxmhcs.chinaunicom.core.model.MyOrderPage
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun interface MyOrderNetworkClient {
    fun fetch(
        mobile: String,
        page: Int,
        pageSize: Int,
        credentials: AccountCredentials,
    ): MyOrderFetchResult
}

/** Source-equivalent implementation of iOS MyOrderClient.swift. */
class UnicomMyOrderClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(20_000L)),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(http = http),
    private val clock: Clock = Clock.systemUTC(),
    private val systemVersionProvider: () -> String = {
        System.getProperty("os.version")?.trim().orEmpty().ifEmpty { "11" }
    },
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : MyOrderNetworkClient {
    override fun fetch(
        mobile: String,
        page: Int,
        pageSize: Int,
        credentials: AccountCredentials,
    ): MyOrderFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie

        return try {
            val direct = fetchOnce(mobile, page, pageSize, originalCookie)
            MyOrderFetchResult(
                page = direct.page,
                updatedCredentials = updatedCredentials(credentials, direct.cookie, force = false),
            )
        } catch (directError: Exception) {
            if (!shouldActivateSession(directError)) throw directError

            val activated = try {
                sessionClient.activateSession(credentials)
            } catch (error: Exception) {
                throw UnicomAPIException.Server(
                    "我的订单会话恢复失败：${error.message ?: error::class.java.simpleName}",
                )
            }

            val retried = fetchOnce(
                mobile = mobile,
                page = page,
                pageSize = pageSize,
                cookie = UnicomCookieCodec.normalize(activated.cookie),
            )
            MyOrderFetchResult(
                page = retried.page,
                updatedCredentials = updatedCredentials(
                    credentials = activated,
                    cookie = retried.cookie,
                    force = activated != credentials,
                ),
            )
        }
    }

    private data class PageResponse(
        val page: MyOrderPage,
        val cookie: String,
    )

    private fun fetchOnce(
        mobile: String,
        page: Int,
        pageSize: Int,
        cookie: String,
    ): PageResponse {
        val timestamp = Instant.now(clock).toEpochMilli()
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        val response = http.post(
            url = "$BASE_URL$ORDER_PATH?timestamp=$timestamp",
            body = unicomFormEncoded(
                linkedMapOf(
                    "current_page" to page.coerceAtLeast(1).toString(),
                    "page_size" to pageSize.coerceAtLeast(1).toString(),
                    "loginNumber" to md5(mobile.filter(Char::isDigit)),
                ),
            ),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to "https://img.client.10010.com",
                "Referer" to "https://img.client.10010.com/",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS ${systemVersion.replace('.', '_')} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) unicom{version:$CLIENT_VERSION};ltst;OSVersion/$systemVersion",
            ),
        )

        if (UnicomResponseStatus.responseLooksExpired(response.data)) {
            throw UnicomAPIException.SessionExpired
        }

        val parsed = parse(response.data, pageSize)
        val updatedCookie = if (response.cookieMutations.isEmpty()) {
            cookie
        } else {
            UnicomCookieCodec.applying(response.cookieMutations, cookie)
        }
        return PageResponse(parsed, updatedCookie)
    }

    internal fun parse(data: ByteArray, pageSize: Int): MyOrderPage {
        val root = parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val code = root["respCode"].text() ?: root["code"].text() ?: ""
        if (code.isNotEmpty() && !UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            val message = root["respDesc"].text()
                ?: root["dsc"].text()
                ?: root["message"].text()
                ?: "我的订单查询失败（code: $code）"
            throw UnicomAPIException.Server(message)
        }

        val rawOrders = dictionaries(root["respData"])
        val orders = rawOrders.mapIndexed { index, raw -> makeOrder(raw, index) }
        return MyOrderPage(
            orders = orders,
            serverTime = root["timeYear"].text(),
            hasMore = orders.size >= pageSize.coerceAtLeast(1),
        )
    }

    private fun makeOrder(raw: JsonObject, index: Int): MyOrder {
        val orderID = raw["order_id"].text()
            ?: raw["order_no"].text()
            ?: raw["soc_order_id"].text()
            ?: raw["encodeOrderId"].text()
            ?: "order-$index-${raw["order_create_time"].text() ?: uuidProvider()}"

        val members = dictionaries(raw["order_member"]).mapIndexed { memberIndex, value ->
            MyOrderMember(
                id = value["order_member_id"].text()
                    ?: value["goods_id"].text()
                    ?: "$orderID-member-$memberIndex",
                goodsName = value["goods_name"].text(),
                price = value["price"].text(),
                integral = value["integral"].text(),
                goodsID = value["goods_id"].text(),
                productPicture = value["product_picture"].text(),
                tradeTags = strings(value["trade_tag"]),
            )
        }

        val actions = dictionaries(raw["button"]).mapIndexed { actionIndex, value ->
            val name = value["button_name"].text() ?: "操作"
            MyOrderAction(
                id = "$orderID-action-$actionIndex-$name",
                name = name,
                type = value["button_type"].text(),
                url = value["button_url"].text(),
                postParameter = value["post_param"].text(),
            )
        }

        return MyOrder(
            id = orderID,
            orderID = orderID,
            encodedOrderID = raw["encodeOrderId"].text(),
            sourceCode = raw["order_source"].text(),
            sourceName = raw["order_source_name"].text(),
            statusCode = raw["order_status_code"].text(),
            statusName = raw["order_status_name"].text() ?: raw["node_name"].text() ?: "",
            nodeCode = raw["node_code"].text(),
            nodeName = raw["node_name"].text(),
            createdAtText = raw["order_create_time"].text() ?: "",
            channelName = raw["in_mode_name"].text(),
            phoneNumber = raw["phone_number"].text(),
            maskedContactNumber = raw["acc_contacts_tel"].text(),
            accountNumber = raw["acc_number"].text(),
            address = raw["acc_address"].text(),
            goodsName = raw["goods_name"].text(),
            tradeType = raw["trade_type"].text(),
            sceneType = raw["scene_type"].text(),
            originNodeName = raw["origin_node_name"].text(),
            members = members,
            actions = actions,
            tradeTags = strings(raw["trade_tag"]),
        )
    }

    private fun updatedCredentials(
        credentials: AccountCredentials,
        cookie: String,
        force: Boolean,
    ): AccountCredentials? {
        if (!force && cookie == UnicomCookieCodec.normalize(credentials.cookie)) return null
        return AccountCredentials(
            cookie = cookie,
            appID = credentials.appID,
            tokenOnline = credentials.tokenOnline,
        )
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> {
            val message = error.serverMessage
            message.contains("cookie", ignoreCase = true) ||
                message.contains("登录") ||
                message.contains("会话")
        }
        else -> false
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun dictionaries(value: JsonElement?): List<JsonObject> = when (value) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonPrimitive -> if (value.isString) {
            val nested = runCatching { parseNetworkJson(value.content.toByteArray(Charsets.UTF_8)) }.getOrNull()
            (nested as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        } else emptyList()
        else -> emptyList()
    }

    private fun strings(value: JsonElement?): List<String> = when (value) {
        is JsonArray -> value.mapNotNull { it.text()?.trim()?.takeIf(String::isNotEmpty) }
        else -> value.text()?.trim()?.takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()
    }

    private fun JsonElement?.text(): String? = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> content.trim()
        else -> null
    }

    companion object {
        const val BASE_URL = "https://m.client.10010.com"
        const val ORDER_PATH = "/mobileservicequery/order/newQueryOrder"
        const val CLIENT_VERSION = "iphone_c@12.1400"
        const val DEFAULT_PAGE_SIZE = 15
    }
}
