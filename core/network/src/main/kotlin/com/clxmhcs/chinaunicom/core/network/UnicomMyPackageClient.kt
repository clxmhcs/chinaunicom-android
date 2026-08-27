package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyPackageActivity
import com.clxmhcs.chinaunicom.core.model.MyPackageBroadbandResource
import com.clxmhcs.chinaunicom.core.model.MyPackageChargeRule
import com.clxmhcs.chinaunicom.core.model.MyPackageFetchResult
import com.clxmhcs.chinaunicom.core.model.MyPackageMember
import com.clxmhcs.chinaunicom.core.model.MyPackageMemberGroup
import com.clxmhcs.chinaunicom.core.model.MyPackageSnapshot
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun interface MyPackageNetworkClient {
    fun fetch(credentials: AccountCredentials): MyPackageFetchResult
}

object MyPackageCrypto {
    private const val KEY_TEXT = "#user3ExtraInfo6"

    fun decryptMemberPayload(encodedValue: String): ByteArray {
        val decoded = runCatching { URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.name()) }.getOrNull()
            ?: throw UnicomAPIException.InvalidResponse
        val cipherText = runCatching { Base64.getDecoder().decode(decoded) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: throw UnicomAPIException.InvalidResponse
        val key = KEY_TEXT.toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        val plain = runCatching { cipher.doFinal(cipherText) }.getOrElse {
            throw UnicomAPIException.Server("套餐成员数据解密失败")
        }
        var end = plain.size
        while (end > 0 && plain[end - 1] == 0.toByte()) end--
        if (end == 0) throw UnicomAPIException.InvalidResponse
        return plain.copyOf(end)
    }
}

/** Source-equivalent implementation of iOS MyPackageClient.swift. */
class UnicomMyPackageClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(25_000L)),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(http = http),
    private val systemVersionProvider: () -> String = {
        System.getProperty("os.version")?.trim().orEmpty().ifEmpty { "11" }
    },
) : MyPackageNetworkClient {
    override fun fetch(credentials: AccountCredentials): MyPackageFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            val direct = fetchOnce(originalCookie)
            MyPackageFetchResult(direct.snapshot, updatedCredentials(credentials, direct.cookie, false))
        } catch (error: Exception) {
            if (!shouldActivateSession(error)) throw error
            val activated = try { sessionClient.activateSession(credentials) } catch (activationError: Exception) {
                throw UnicomAPIException.Server("我的套餐会话恢复失败：${activationError.message ?: activationError::class.java.simpleName}")
            }
            val retried = fetchOnce(UnicomCookieCodec.normalize(activated.cookie))
            MyPackageFetchResult(
                retried.snapshot,
                updatedCredentials(activated, retried.cookie, activated != credentials),
            )
        }
    }

    private data class FetchOnceResult(val snapshot: MyPackageSnapshot, val cookie: String)

    private fun fetchOnce(initialCookie: String): FetchOnceResult {
        var cookie = initialCookie
        val packageResponse = post("/servicequerybusiness/queryPackage/myPackage", baseFormValues(), cookie)
        cookie = applyCookies(packageResponse, cookie)
        val packageRoot = validatedRoot(packageResponse.data, "主套餐")
        val packageData = packageRoot["data"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val productID = packageData.text("productId").orEmpty()
        val productType = packageData.text("productType").orEmpty()
        val packageResourceType = packageData.text("packageResourceType").orEmpty()

        val resourceRoots = mutableListOf<JsonObject>()
        if (productID.isNotEmpty()) {
            for (type in listOf("1", "2")) {
                val optional = fetchOptionalRoot(
                    "/servicequerybusiness/queryPackage/myResourceDetails",
                    resourceFormValues(type, productID, productType),
                    cookie,
                    "套餐资源",
                )
                cookie = optional.second
                optional.first?.let(resourceRoots::add)
            }
        }

        val memberResult = fetchOptionalRoot(
            "/servicequerybusiness/queryPackage/myMemberMobile",
            baseFormValues() + ("chooseflag" to "1"),
            cookie,
            "套餐成员",
        )
        cookie = memberResult.second
        val prettyResult = fetchOptionalRoot(
            "/servicequerybusiness/queryPackage/myPrettyNumber",
            baseFormValues(),
            cookie,
            "靓号信息",
        )
        cookie = prettyResult.second

        val resourceData = resourceRoots.mapNotNull { it["data"] as? JsonObject }
        val packageDTOs = resourceData.mapNotNull { it["tPackageDTO"] as? JsonObject }
        val packageDTO = packageDTOs.firstOrNull() ?: JsonObject(emptyMap())
        val broadbandResources = dedupeBroadband(resourceData.flatMap { parseBroadbandResources(it["broadbandlist"]) })
        val broadbandTips = resourceData.mapNotNull { it.text("broadbandTips")?.let(::cleanHTML) }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val memberGroups = memberResult.first?.let { runCatching { parseMemberGroups(it) }.getOrDefault(emptyList()) }.orEmpty()
        val prettyData = prettyResult.first?.get("data") as? JsonObject ?: JsonObject(emptyMap())

        val snapshot = MyPackageSnapshot(
            productName = packageData.text("productName") ?: packageDTO.text("productName") ?: "我的套餐",
            productStartDate = packageData.text("productStartDate").orEmpty(),
            packageResourceType = packageResourceType,
            monthFee = packageDTO.text("monthFee").orEmpty(),
            packageDescription = cleanHTML(packageDTO.text("packageDesc") ?: packageDTO.text("reminders") ?: packageDTO.text("businessDesc").orEmpty()),
            businessRules = cleanHTML(packageDTO.text("businessDesc") ?: packageDTO.text("packageDesc") ?: packageDTO.text("reminders").orEmpty()),
            monthFeeDescription = cleanHTML(packageDTO.text("monthFeeDescription").orEmpty()),
            contractTips = cleanHTML(packageData.text("contractTips").orEmpty()),
            cannotCancelPrompt = cleanHTML(packageData.text("cannotCancelPrompt").orEmpty()),
            promotionURL = uri(packageData.text("wdtcShortLink")),
            promotionImageURL = uri(packageData.text("boradPic")),
            promotionText = cleanHTML(packageData.text("boradTitle") ?: packageData.text("broadTitle") ?: packageData.text("broadbandTitle") ?: packageData.text("wdtcTitle") ?: "温馨提示：装宽带限时加赠100元话费！"),
            activities = parseActivities(packageData["myActivit"]),
            mobileRules = dedupeRules(packageDTOs.flatMap(::parseMobileRules)),
            broadbandResources = broadbandResources,
            broadbandTips = broadbandTips,
            memberGroups = memberGroups,
            isPrettyNumber = prettyData.boolean("ispettynumber"),
        )
        return FetchOnceResult(snapshot, cookie)
    }

    private fun baseFormValues() = linkedMapOf(
        "duanlianjieabc" to "", "channelCode" to "", "serviceType" to "", "saleChannel" to "",
        "externalSources" to "", "contactCode" to "", "ticket" to "", "ticketChannel" to "", "ticketPhone" to "",
    )

    private fun resourceFormValues(type: String, productID: String, productType: String) = baseFormValues() + mapOf(
        "type" to type, "productid" to productID, "producttype" to productType, "nextProductid" to "", "nextProducttype" to "",
    )

    private fun post(path: String, values: Map<String, String>, cookie: String): UnicomHTTPResponse {
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        val response = http.post(
            url = BASE_URL + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to PAGE_ORIGIN,
                "Referer" to "$PAGE_ORIGIN/wodetaocan2024/index.html#/",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS ${systemVersion.replace('.', '_')} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) unicom{version:$CLIENT_VERSION};ltst;OSVersion/$systemVersion",
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        return response
    }

    private fun fetchOptionalRoot(path: String, values: Map<String, String>, cookie: String, endpoint: String): Pair<JsonObject?, String> {
        val response = try { post(path, values, cookie) } catch (error: Exception) {
            if (shouldActivateSession(error)) throw error
            return null to cookie
        }
        val updatedCookie = applyCookies(response, cookie)
        return try { validatedRoot(response.data, endpoint) to updatedCookie } catch (error: Exception) {
            if (shouldActivateSession(error)) throw error
            null to updatedCookie
        }
    }

    private fun validatedRoot(data: ByteArray, endpoint: String): JsonObject {
        val root = parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val code = root.text("code").orEmpty()
        if (!UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            throw UnicomAPIException.Server(root.text("desc") ?: root.text("message") ?: "$endpoint 查询失败（code: ${code.ifEmpty { "未知" }}）")
        }
        return root
    }

    private fun parseActivities(value: JsonElement?): List<MyPackageActivity> = value.objects().mapIndexed { index, raw ->
        val name = raw.text("activityname") ?: "合约"
        MyPackageActivity(raw.text("activityid") ?: "activity-$index-$name", name, raw.text("startdate").orEmpty(), raw.text("enddate").orEmpty(), raw.text("between").orEmpty())
    }

    private fun parseMobileRules(raw: JsonObject): List<MyPackageChargeRule> = listOfNotNull(
        rule("国内流量", raw.text("outsideFlow")), rule("国内语音", raw.text("outsideVoice")),
        rule("国内短信", raw.text("shortMessageExceed")), rule("国内彩信", raw.text("mmsExceed")),
    )

    private fun rule(title: String, value: String?): MyPackageChargeRule? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { MyPackageChargeRule(title, title, it) }

    private fun parseBroadbandResources(value: JsonElement?): List<MyPackageBroadbandResource> = value.objects().mapIndexed { index, raw ->
        val mobile = raw.text("mobile") ?: "宽带"
        MyPackageBroadbandResource(
            "broadband-$index-$mobile", mobile, normalizedSpeed(raw.text("speed")),
            normalizedSpeed(raw.text("upspeed") ?: raw.text("speed")), raw.text("startdate").orEmpty(), raw.text("enddate").orEmpty(),
        )
    }

    private fun parseMemberGroups(root: JsonObject): List<MyPackageMemberGroup> {
        val encrypted = root.text("data")?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val objectRoot = parseNetworkJson(MyPackageCrypto.decryptMemberPayload(encrypted)) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        return objectRoot["myNumbers"].objects().mapIndexedNotNull { groupIndex, raw ->
            val name = raw.text("packageName") ?: "套餐成员"
            val groupType = raw.text("groupType").orEmpty()
            if (groupType == "05") {
                val members = makeMembers(raw["myUserNumberInfos"].objects(), groupIndex, "主卡", "副卡")
                MyPackageMemberGroup("member-group-$groupIndex-$groupType-$name", name, groupType, emptyList(), members)
            } else {
                val primary = makeMembers(raw["mainNumberInfos"].objects(), groupIndex, "主成员", "成员")
                val members = makeMembers(raw["memberNumberInfos"].objects(), groupIndex, "主成员", "成员")
                if (primary.isEmpty() && members.isEmpty()) null
                else MyPackageMemberGroup("member-group-$groupIndex-$groupType-$name", name, groupType, primary, members)
            }
        }
    }

    private fun makeMembers(values: List<JsonObject>, groupIndex: Int, primaryRole: String, memberRole: String): List<MyPackageMember> =
        values.mapIndexedNotNull { index, raw ->
            val number = raw.text("serial_number")?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val primary = raw.text("main_num_flag") == "0"
            MyPackageMember(
                "member-$groupIndex-$index-$number", if (primary) primaryRole else memberRole,
                if (raw.text("service_class_code") == "0040") "宽带" else "移网", number,
                raw.text("member_user_name").orEmpty(), primary,
            )
        }

    private fun dedupeRules(values: List<MyPackageChargeRule>) = values.distinctBy { it.id }
    private fun dedupeBroadband(values: List<MyPackageBroadbandResource>) = values.distinctBy { listOf(it.mobile, it.packageSpeed, it.actualSpeed, it.startDate, it.endDate).joinToString("|") }
    private fun normalizedSpeed(value: String?): String {
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) return "--"
        return if (Regex("[A-Za-z]").containsMatchIn(clean)) clean else "${clean}M"
    }
    private fun applyCookies(response: UnicomHTTPResponse, cookie: String) = if (response.cookieMutations.isEmpty()) cookie else UnicomCookieCodec.applying(response.cookieMutations, cookie)
    private fun updatedCredentials(credentials: AccountCredentials, cookie: String, force: Boolean): AccountCredentials? =
        if (!force && cookie == UnicomCookieCodec.normalize(credentials.cookie)) null else AccountCredentials(cookie, credentials.appID, credentials.tokenOnline)
    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> error.serverMessage.lowercase().let { it.contains("cookie") || it.contains("登录") || it.contains("会话") }
        else -> false
    }
    private fun cleanHTML(value: String) = value.replace(Regex("(?i)</?br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
    private fun uri(value: String?): URI? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it) }.getOrNull() }
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.content?.lowercase() in setOf("true", "1")
    private fun JsonElement?.objects(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        else -> emptyList()
    }

    companion object {
        private const val BASE_URL = "https://mxx.client.10010.com"
        private const val PAGE_ORIGIN = "https://imgxx.client.10010.com"
        private const val CLIENT_VERSION = "iphone_c@12.1400"
    }
}
