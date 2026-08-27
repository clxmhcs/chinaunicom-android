package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetail
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneFirstLevel
import com.clxmhcs.chinaunicom.core.model.TariffZoneIndex
import com.clxmhcs.chinaunicom.core.model.TariffZoneIndexFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneProductReference
import com.clxmhcs.chinaunicom.core.model.TariffZoneReferencesFetchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.model.TariffZoneSecondLevel
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface TariffZoneNetworkClient {
    fun fetchIndex(credentials: AccountCredentials): TariffZoneIndexFetchResult

    fun fetchProductReferences(
        credentials: AccountCredentials,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult

    fun fetchDetails(
        credentials: AccountCredentials,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult
}

/** Source-equivalent implementation of iOS TariffZoneClient.swift. */
class UnicomTariffZoneClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(25_000L)),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(http = http),
    private val systemVersionProvider: () -> String = {
        System.getProperty("os.version")?.trim().orEmpty().ifEmpty { "11" }
    },
    uuidProvider: () -> UUID = UUID::randomUUID,
) : TariffZoneNetworkClient {
    private val behaviorID: String = "IOS" + uuidProvider().toString().replace("-", "")

    override fun fetchIndex(credentials: AccountCredentials): TariffZoneIndexFetchResult {
        val response = request(
            credentials = credentials,
            path = INDEX_PATH,
            values = commonFormValues() + mapOf("provinceId" to "", "cityId" to ""),
        )
        val root = rootObject(response.data)
        requireSuccess(root, "资费目录")
        val data = root["data"].objectValue()

        val regions = data["provinceList"].objectList().mapNotNull { raw ->
            val provinceCode = raw.text("provCode") ?: return@mapNotNull null
            val cityCode = raw.text("cityCode") ?: return@mapNotNull null
            val provinceName = raw.text("provName") ?: return@mapNotNull null
            val cityName = raw.text("cityName") ?: return@mapNotNull null
            TariffZoneRegion(provinceCode, cityCode, provinceName, cityName)
        }.toMutableList()

        val userProvinceCode = data.text("userProCode").orEmpty()
        val userCityCode = data.text("userCityCode").orEmpty()
        val userProvinceName = data.text("userProName").orEmpty()
        val userCityName = data.text("userCityName").orEmpty()
        if (
            userProvinceCode.isNotEmpty() && userCityCode.isNotEmpty() &&
            userProvinceName.isNotEmpty() && userCityName.isNotEmpty() &&
            regions.none { it.provinceCode == userProvinceCode && it.cityCode == userCityCode }
        ) {
            regions.add(0, TariffZoneRegion(userProvinceCode, userCityCode, userProvinceName, userCityName))
        }

        val levels = data["levelList"].objectList().mapNotNull { raw ->
            val id = raw.text("firstLevel") ?: return@mapNotNull null
            val name = raw.text("firstLevelName") ?: return@mapNotNull null
            val secondLevels = raw["secondLevels"].objectList().mapNotNull { second ->
                val secondID = second.text("secondLevel") ?: return@mapNotNull null
                val secondName = second.text("secondLevelName") ?: return@mapNotNull null
                TariffZoneSecondLevel(secondID, secondName)
            }
            TariffZoneFirstLevel(id, name, secondLevels)
        }

        return TariffZoneIndexFetchResult(
            index = TariffZoneIndex(
                regions = regions,
                levels = levels,
                userProvinceCode = userProvinceCode,
                userCityCode = userCityCode,
            ),
            updatedCredentials = response.credentials.takeIf { it != credentials },
        )
    }

    override fun fetchProductReferences(
        credentials: AccountCredentials,
        scope: TariffZoneScope,
        firstLevel: String,
        secondLevel: String,
        region: TariffZoneRegion,
    ): TariffZoneReferencesFetchResult {
        val response = request(
            credentials = credentials,
            path = REFERENCES_PATH,
            values = commonFormValues() + mapOf(
                "tariffAttributes" to scope.rawValue,
                "firstLevel" to firstLevel,
                "secondLevel" to secondLevel,
                "provinceId" to region.provinceCode,
                "cityId" to region.cityCode,
            ),
        )
        val root = rootObject(response.data)
        if (root.isEmpty() || root.text("code") == "0001") {
            return TariffZoneReferencesFetchResult(
                references = emptyList(),
                updatedCredentials = response.credentials.takeIf { it != credentials },
            )
        }
        requireSuccess(root, "资费名称")
        val references = root["data"].objectValue()["dataList"].objectList().mapNotNull { raw ->
            val id = raw.text("id") ?: return@mapNotNull null
            val name = raw.text("name") ?: return@mapNotNull null
            TariffZoneProductReference(id, name)
        }
        return TariffZoneReferencesFetchResult(
            references = references,
            updatedCredentials = response.credentials.takeIf { it != credentials },
        )
    }

    override fun fetchDetails(
        credentials: AccountCredentials,
        references: List<TariffZoneProductReference>,
        page: Int,
        region: TariffZoneRegion,
    ): TariffZoneDetailsFetchResult {
        if (references.isEmpty()) {
            return TariffZoneDetailsFetchResult(emptyList(), null, null)
        }

        val pathIDs = references.joinToString("_") { it.id }
        val response = request(
            credentials = credentials,
            path = "$DETAIL_PATH/$pathIDs",
            values = commonFormValues() + mapOf(
                "page" to page.coerceAtLeast(1).toString(),
                "size" to references.size.toString(),
                "provinceId" to region.provinceCode,
                "cityId" to region.cityCode,
            ),
        )
        val root = rootObject(response.data)
        if (root.isEmpty() || root.text("code") == "0001") {
            return TariffZoneDetailsFetchResult(
                details = emptyList(),
                timeText = null,
                updatedCredentials = response.credentials.takeIf { it != credentials },
            )
        }
        requireSuccess(root, "资费详情")
        val data = root["data"].objectValue()
        var detailRows = data["detailList"].objectList()
        if (detailRows.isEmpty()) {
            detailRows = data["dataList"].objectList().flatMap { it["detailsList"].objectList() }
        }
        val details = detailRows.mapIndexed { index, raw ->
            TariffZoneDetail(
                id = raw.text("reportNo") ?: "${references.firstOrNull()?.id ?: "tariff"}-$index",
                reportNo = raw.text("reportNo").orEmpty(),
                name = raw.text("name").orEmpty(),
                codeType = raw.text("codeType").orEmpty(),
                feesStandard = raw.text("feesStandard").orEmpty(),
                feeUnit = raw.text("feeUnit").orEmpty(),
                otherFees = raw.text("otherFees").orEmpty(),
                extraFees = raw.text("extraFees").orEmpty(),
                minute = raw.text("minute").orEmpty(),
                commonData = raw.text("commonData").orEmpty(),
                dataUnit = raw.text("dataUnit").orEmpty(),
                sms = raw.text("sms").orEmpty(),
                orientTraffic = raw.text("orientTraffic").orEmpty(),
                orientTrafficUnit = raw.text("orientTrafficUnit").orEmpty(),
                iptv = raw.text("iptv").orEmpty(),
                broadBand = raw.text("broadBand").orEmpty(),
                equityCoupon = raw.text("equityCoupon").orEmpty(),
                serviceContent = raw.text("serviceContent").orEmpty(),
                useScope = raw.text("useScope").orEmpty(),
                validPeriod = raw.text("validPeriod").orEmpty(),
                onlinePeriod = raw.text("onlinePeriod").orEmpty(),
                saleChnl = raw.text("saleChnl").orEmpty(),
                unsubscribe = raw.text("unsubscribe").orEmpty(),
                startDate = formatDate(raw.text("startDate").orEmpty()),
                endDate = formatDate(raw.text("endDate").orEmpty()),
                contractDuty = raw.text("contractDuty").orEmpty(),
                otherDesc = raw.text("otherDesc").orEmpty(),
            )
        }
        return TariffZoneDetailsFetchResult(
            details = details,
            timeText = data.text("timeStr") ?: root.text("timeStr"),
            updatedCredentials = response.credentials.takeIf { it != credentials },
        )
    }

    private data class TariffHTTPResult(
        val data: ByteArray,
        val credentials: AccountCredentials,
    )

    private fun request(
        credentials: AccountCredentials,
        path: String,
        values: Map<String, String>,
    ): TariffHTTPResult {
        val normalizedCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (normalizedCookie.isEmpty()) throw UnicomAPIException.MissingCookie

        return try {
            requestOnce(credentials, normalizedCookie, path, values)
        } catch (error: Exception) {
            if (!shouldActivateSession(error)) throw error
            val activated = sessionClient.activateSession(credentials)
            val activatedCookie = UnicomCookieCodec.normalize(activated.cookie)
            if (activatedCookie.isEmpty()) throw UnicomAPIException.MissingCookie
            requestOnce(activated, activatedCookie, path, values)
        }
    }

    private fun requestOnce(
        credentials: AccountCredentials,
        cookie: String,
        path: String,
        values: Map<String, String>,
    ): TariffHTTPResult {
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        val response = http.post(
            url = ROOT + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to PAGE_ORIGIN,
                "Referer" to "$PAGE_ORIGIN/zifeizhuanqu/index.html",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS ${systemVersion.replace('.', '_')} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) unicom{version:iphone_c@12.1400};ltst;OSVersion/$systemVersion",
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) {
            throw UnicomAPIException.SessionExpired
        }
        val updatedCookie = if (response.cookieMutations.isEmpty()) {
            cookie
        } else {
            UnicomCookieCodec.applying(response.cookieMutations, cookie)
        }
        return TariffHTTPResult(response.data, credentials.copy(cookie = updatedCookie))
    }

    private fun commonFormValues(): Map<String, String> = linkedMapOf(
        "duanlianjieabc" to "",
        "channelCode" to "",
        "serviceType" to "",
        "saleChannel" to "",
        "externalSources" to "",
        "contactCode" to "",
        "behaviorId" to behaviorID,
    )

    private fun rootObject(data: ByteArray): JsonObject =
        parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse

    private fun requireSuccess(root: JsonObject, endpointName: String) {
        val code = root.text("code").orEmpty()
        if (UnicomResponseStatus.isSuccess(code)) return
        if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
        throw UnicomAPIException.Server(
            root.text("msg") ?: root.text("message")
            ?: "$endpointName 查询失败（code: ${code.ifEmpty { "未知" }}）",
        )
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> {
            val message = error.serverMessage
            message.contains("cookie", ignoreCase = true) || message.contains("登录") || message.contains("在线")
        }
        else -> false
    }

    private fun formatDate(raw: String): String {
        val value = raw.trim()
        if (value.length != 8 || value.any { !it.isDigit() }) return value
        return "${value.substring(0, 4)}/${value.substring(4, 6)}/${value.substring(6, 8)}"
    }

    private fun JsonObject.text(key: String): String? = this[key].textValue()

    private fun JsonElement?.objectValue(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonElement?.objectList(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonPrimitive -> runCatching {
            (parseNetworkJson(content.encodeToByteArray()) as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }.getOrDefault(emptyList())
        else -> emptyList()
    }

    private fun JsonElement?.textValue(): String? = when (this) {
        is JsonPrimitive -> content.trim().takeIf { it.isNotEmpty() }
        else -> null
    }

    companion object {
        const val ROOT = "https://mxx.client.10010.com"
        const val PAGE_ORIGIN = "https://imgxx.client.10010.com"
        const val INDEX_PATH = "/servicequerybusiness/queryTariffNew/indexData"
        const val REFERENCES_PATH = "/servicequerybusiness/queryTariffNew/threeLevelName"
        const val DETAIL_PATH = "/servicequerybusiness/queryTariffNew/operateData"
    }
}
