package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.ActivatedOrderedSession
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessItem
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSection
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Source-equivalent implementation of iOS OrderedBusinessClient.swift. */
class UnicomOrderedBusinessClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(20_000L)),
    private val clock: Clock = Clock.systemUTC(),
    private val systemVersionProvider: () -> String = {
        System.getProperty("os.version")?.trim().orEmpty().ifEmpty { "11" }
    },
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : OrderedBusinessNetworkClient {

    override fun fetch(credentials: AccountCredentials): OrderedBusinessFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie

        return try {
            OrderedBusinessFetchResult(fetchOnce(originalCookie), null)
        } catch (firstError: Exception) {
            if (!shouldRelogin(firstError)) throw firstError
            val appID = credentials.appID.trimmedOrNull()
            val tokenOnline = credentials.tokenOnline.trimmedOrNull()
            if (appID == null || tokenOnline == null) {
                throw UnicomAPIException.Server("已订业务 Cookie 会话已失效，且该号码未保存可用的 appId/token_online")
            }

            val activated = try {
                activate(originalCookie, appID, tokenOnline)
            } catch (error: Exception) {
                throw UnicomAPIException.Server(
                    "已订业务会话恢复失败：${error.message ?: error::class.java.simpleName}",
                )
            }

            val snapshot = try {
                fetchOnce(activated.cookie)
            } catch (error: Exception) {
                if (shouldRelogin(error)) {
                    throw UnicomAPIException.Server("已订业务 loginxx 会话恢复成功，但业务会话仍未建立")
                }
                throw error
            }

            val changed = activated.cookie != originalCookie ||
                activated.appID != appID ||
                activated.tokenOnline != tokenOnline
            OrderedBusinessFetchResult(
                snapshot = snapshot,
                updatedCredentials = if (changed) {
                    AccountCredentials(activated.cookie, activated.appID, activated.tokenOnline)
                } else null,
            )
        }
    }

    private fun fetchOnce(cookie: String): OrderedBusinessSnapshot {
        val common = mapOf(
            "duanlianjieabc" to "",
            "channelCode" to "",
            "serviceType" to "",
            "saleChannel" to "",
            "externalSources" to "",
            "contactCode" to "",
        )
        val allocation = postBusiness(
            ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ALLOCATE,
            common,
            cookie,
        )
        if (UnicomResponseStatus.responseLooksExpired(allocation.data)) {
            throw UnicomAPIException.Server("已订业务初始化返回会话失效")
        }
        if (!allocationSucceeded(allocation.data)) {
            throw UnicomAPIException.Server("已订业务初始化会话未建立")
        }

        val queryCookie = UnicomCookieCodec.applying(allocation.cookieMutations, cookie)
        val response = postBusiness(
            ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_QUERY,
            common + ("type" to "1"),
            queryCookie,
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) {
            throw UnicomAPIException.Server("已订业务查询返回会话失效")
        }
        return parse(response.data)
    }

    private fun activate(
        cookie: String,
        appID: String,
        tokenOnline: String,
    ): ActivatedOrderedSession {
        val deviceCode = UnicomCookieCodec.value("d_deviceCode", cookie)
            ?: UnicomCookieCodec.value("deviceCode", cookie)
            ?: UnicomCookieCodec.value("devicedId", cookie)
            ?: uuidProvider().toString()
        val deviceDigest = sha256(deviceCode)
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        val response = http.post(
            url = ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ONLINE,
            body = unicomFormEncoded(
                mapOf(
                    "reqtime" to currentChinaTime(),
                    "version" to ONLINE_VERSION,
                    "simOperator" to "--,--,65535,65535,--@--,--,65535,65535,--",
                    "token_online" to tokenOnline,
                    "appId" to appID,
                    "deviceId" to deviceDigest,
                    "pip" to "192.168.0.100",
                    "deviceModel" to "iPhone",
                    "deviceOS" to systemVersion,
                    "deviceBrand" to "iPhone",
                    "uniqueIdentifier" to "ios${deviceDigest.take(32)}",
                    "step" to "welcom",
                    "isFirstInstall" to "1",
                    "flushkey" to "1",
                    "deviceCode" to deviceCode,
                    "voipToken" to "citc-default-token-do-not-push",
                ),
            ),
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Accept" to "*/*",
                "Accept-Language" to "zh-Hans-CN;q=1.0",
                "User-Agent" to "ChinaUnicom4.x/12.13 (com.chinaunicom.mobilebusiness; build:1; iOS $systemVersion) Alamofire/4.7.3 unicom{version:$ONLINE_VERSION}",
            ),
        )
        val root = parseNetworkJson(response.data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val code = UnicomResponseStatus.topLevelCode(response.data).orEmpty()
        if (!UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            val message = recursiveString(root, setOf("dsc", "rsp_desc", "desc", "message"))
                ?: "联通在线状态维护失败（code: ${code.ifEmpty { "未知" }}）"
            throw UnicomAPIException.Server(message)
        }

        val renewedCookie = if (response.cookieMutations.isEmpty()) cookie
        else UnicomCookieCodec.applying(response.cookieMutations, cookie)
        return ActivatedOrderedSession(
            cookie = renewedCookie,
            appID = recursiveString(root, setOf("appId", "appid")).trimmedOrNull() ?: appID,
            tokenOnline = recursiveString(root, setOf("token_online", "tokenOnline")).trimmedOrNull() ?: tokenOnline,
        )
    }

    private fun postBusiness(
        path: String,
        values: Map<String, String>,
        cookie: String,
    ): UnicomHTTPResponse {
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        return http.post(
            url = ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ROOT + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to "https://imgxx.client.10010.com",
                "Referer" to "https://imgxx.client.10010.com/",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) unicom{version:$ONLINE_VERSION};ltst;OSVersion/$systemVersion",
            ),
        )
    }

    internal fun parse(data: ByteArray): OrderedBusinessSnapshot {
        val root = parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val code = root["code"].text().orEmpty()
        if (!UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            throw UnicomAPIException.Server(
                root["desc"].text() ?: root["message"].text() ?: "已订业务查询失败",
            )
        }
        val payload = root["data"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse

        val sections = mutableListOf<OrderedBusinessSection>()
        addSection("主套餐", "simcard.fill", payload["mainProductInfo"].objects(), sections)
        addSection("其他已订产品", "shippingbox.fill", payload["otherProductInfo"].objects(), sections)
        addSection("合约", "doc.text.fill", payload["contractInfo"].objects(), sections)

        val benefits = mutableListOf<OrderedBusinessItem>()
        payload["liuLiangProductInfo"].objects().forEachIndexed { index, item ->
            makeItem(
                item,
                index,
                names = listOf("productName", "packageName"),
                subtitle = item["productName"].text(),
            )?.let(benefits::add)
            item["discntInfo"].objects().forEachIndexed { discountIndex, discount ->
                makeItem(
                    discount,
                    index * 1000 + discountIndex,
                    names = listOf("discntName"),
                    fallbackStart = first(item, defaultStartKeys),
                    fallbackEnd = first(item, defaultEndKeys),
                    subtitle = item["packageName"].text().cleaned()?.let { "所属：$it" },
                )?.let(benefits::add)
            }
        }
        appendSection("套餐内业务与优惠", "gift.fill", benefits, sections)
        addSection("增值业务", "sparkles", payload["valueAdded"].objects(), sections)

        payload["probroadInfoArr"].objects().forEachIndexed { groupIndex, group ->
            val number = group["serialNumber"].text().cleaned() ?: "号码 ${groupIndex + 1}"
            val groupStart = first(group, defaultStartKeys)
            val groupEnd = first(group, defaultEndKeys)
            val items = group["probroadInfoData"].objects().mapIndexedNotNull { index, value ->
                makeItem(
                    value,
                    index,
                    fallbackStart = groupStart,
                    fallbackEnd = groupEnd,
                    subtitle = "关联号码：$number",
                )
            }
            appendSection("宽带/IPTV 产品 · $number", "wifi.router.fill", items, sections)
        }

        val services = payload["serviceinfo"].objects().mapIndexedNotNull { index, value ->
            val packageName = listOf(
                value["packagename"].text().cleaned(),
                value["productname"].text().cleaned(),
            ).filterNotNull().joinToString(" · ").cleaned()
            makeItem(
                value,
                index,
                names = listOf("servicename", "productname", "packagename"),
                starts = defaultStartKeys,
                subtitle = packageName,
            )
        }
        appendSection("功能服务", "slider.horizontal.3", services, sections)
        addSection("异常或失效业务", "exclamationmark.triangle.fill", payload["failureSheetInfo"].objects(), sections)

        return OrderedBusinessSnapshot(
            title = payload["commdityName"].text().cleaned() ?: payload["commodityName"].text().cleaned(),
            queryTime = payload["queryTime"].text().cleaned(),
            fetchedAt = Instant.now(clock),
            sections = sections,
        )
    }

    private fun addSection(
        title: String,
        icon: String,
        values: List<JsonObject>,
        sections: MutableList<OrderedBusinessSection>,
    ) {
        appendSection(title, icon, values.mapIndexedNotNull(::makeItem), sections)
    }

    private fun appendSection(
        title: String,
        icon: String,
        items: List<OrderedBusinessItem>,
        sections: MutableList<OrderedBusinessSection>,
    ) {
        val unique = items.distinctBy { it.id }
        if (unique.isEmpty()) return
        sections += OrderedBusinessSection(title, title, icon, unique)
    }

    private fun makeItem(
        value: JsonObject,
        index: Int,
        names: List<String> = defaultNameKeys,
        starts: List<String> = defaultStartKeys,
        fallbackStart: String? = null,
        fallbackEnd: String? = null,
        subtitle: String? = null,
    ): OrderedBusinessItem? {
        if (value.isEmpty()) return null
        val productID = first(
            value,
            listOf("productId", "packageId", "discntCode", "serviceid", "serviceId", "offerId"),
        ).orEmpty()
        val name = first(value, names) ?: if (productID.isEmpty()) "未识别业务" else "未命名业务（$productID）"
        val start = first(value, starts) ?: fallbackStart.cleaned()
        val end = first(value, defaultEndKeys) ?: fallbackEnd.cleaned()
        val stableID = if (productID.isNotEmpty()) {
            listOf(productID, name, start.orEmpty(), end.orEmpty()).joinToString("|")
        } else {
            val canonical = value.keys.sorted().mapNotNull { key ->
                value[key].text()?.let { "$key=$it" }
            }.joinToString("&")
            if (canonical.isEmpty()) "fallback|$name|$index" else "fallback|${sha256(canonical)}"
        }
        val cleanedSubtitle = subtitle.cleaned()
        return OrderedBusinessItem(
            id = stableID,
            name = name,
            subtitle = cleanedSubtitle.takeUnless { it == name },
            fee = first(value, listOf("productFee", "fee", "price")),
            startDate = start,
            endDate = end,
        )
    }

    private fun first(value: JsonObject, keys: List<String>): String? {
        val lower = value.entries.associate { it.key.lowercase() to it.value }
        return keys.asSequence().mapNotNull { key ->
            value[key].text().cleaned() ?: lower[key.lowercase()].text().cleaned()
        }.firstOrNull()
    }

    private fun allocationSucceeded(data: ByteArray): Boolean {
        val raw = data.toString(Charsets.UTF_8).trim()
        if (raw == "1" || raw == "\"1\"") return true
        val root = runCatching { parseNetworkJson(data) as? JsonObject }.getOrNull() ?: return false
        return UnicomResponseStatus.isSuccess(
            listOf("code", "rsp_code", "status")
                .asSequence()
                .mapNotNull { root[it].text().cleaned() }
                .firstOrNull(),
        )
    }

    private fun shouldRelogin(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> error.serverMessage.contains("登录") ||
            error.serverMessage.contains("会话") ||
            error.serverMessage.contains("cookie", ignoreCase = true)
        else -> false
    }

    private fun currentChinaTime(): String = Instant.now(clock)
        .atZone(ZoneId.of("Asia/Shanghai"))
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content
    private fun JsonElement?.objects(): List<JsonObject> = objectList()
    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val ONLINE_VERSION = "iphone_c@12.1300"
        val defaultNameKeys = listOf(
            "productName", "packageName", "discntName", "servicename",
            "serviceName", "offerName", "commodityName", "productTitle",
        )
        val defaultStartKeys = listOf(
            "startDate", "startDateFmt", "effectiveDate", "beginDate",
            "orderTime", "completedateFmt",
        )
        val defaultEndKeys = listOf(
            "endDate", "endDateFmt", "expireDate", "expiryDate",
            "invalidDate", "endXsbDate", "validDate",
        )
    }
}
