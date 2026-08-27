package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.GiftRecord
import com.clxmhcs.chinaunicom.core.model.GiftRecordsFetchResult
import com.clxmhcs.chinaunicom.core.model.RebateContract
import com.clxmhcs.chinaunicom.core.model.RebateContractsFetchResult
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import com.clxmhcs.chinaunicom.core.model.RebateReturnDetail
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface RebateAndGiftNetworkClient {
    fun fetchContracts(credentials: AccountCredentials, scope: RebateQueryScope): RebateContractsFetchResult
    fun fetchGiftRecords(credentials: AccountCredentials): GiftRecordsFetchResult
}

/** Source-equivalent implementation of iOS RebateAndGiftClient. */
class UnicomRebateAndGiftClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(20_000L)),
    private val activateSession: (AccountCredentials) -> AccountCredentials = UnicomAPIClient()::activateSession,
) : RebateAndGiftNetworkClient {
    companion object {
        const val ROOT = "https://hlbasic.10010.com"
        const val CONTRACT_REBATE = "/servicequerybusiness/grantsAndContractRebates/contractRebate"
        const val GIFT_RECORDS = "/servicequerybusiness/grantsAndContractRebates/canOpenAnInterfaceCall"
    }

    override fun fetchContracts(
        credentials: AccountCredentials,
        scope: RebateQueryScope,
    ): RebateContractsFetchResult {
        val cookie = requireCookie(credentials)
        return try {
            fetchContractsOnce(cookie, scope)
        } catch (error: Exception) {
            if (!shouldActivate(error) || credentials.appID.isNullOrBlank() || credentials.tokenOnline.isNullOrBlank()) throw error
            val activated = activateSession(credentials)
            fetchContractsOnce(UnicomCookieCodec.normalize(activated.cookie), scope)
                .copy(updatedCredentials = if (activated == credentials) null else activated)
        }
    }

    override fun fetchGiftRecords(credentials: AccountCredentials): GiftRecordsFetchResult {
        val cookie = requireCookie(credentials)
        return try {
            fetchGiftRecordsOnce(cookie)
        } catch (error: Exception) {
            if (!shouldActivate(error) || credentials.appID.isNullOrBlank() || credentials.tokenOnline.isNullOrBlank()) throw error
            val activated = activateSession(credentials)
            fetchGiftRecordsOnce(UnicomCookieCodec.normalize(activated.cookie))
                .copy(updatedCredentials = if (activated == credentials) null else activated)
        }
    }

    private fun fetchContractsOnce(cookie: String, scope: RebateQueryScope): RebateContractsFetchResult {
        val values = commonValues().toMutableMap().apply { put("qrytype", scope.queryType) }
        val root = post(CONTRACT_REBATE, values, cookie, "合约返赠查询失败")
        return RebateContractsFetchResult(
            contracts = parseContracts(root["data"]),
            queryTime = parseDate(first(root, "time", "queryTime", "queryDate")),
            updatedCredentials = null,
        )
    }

    private fun fetchGiftRecordsOnce(cookie: String): GiftRecordsFetchResult {
        val root = post(GIFT_RECORDS, commonValues(), cookie, "赠款记录查询失败")
        return GiftRecordsFetchResult(
            gifts = parseGifts(root["data"]),
            queryTime = parseDate(first(root, "time", "queryTime", "queryDate")),
            updatedCredentials = null,
        )
    }

    private fun post(
        path: String,
        values: Map<String, String>,
        cookie: String,
        fallback: String,
    ): JsonObject {
        val response = http.post(
            url = ROOT + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Cookie" to cookie,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        val root = parseNetworkJson(response.data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val code = listOf("code", "rsp_code", "status")
            .asSequence()
            .mapNotNull { root[it].stringValue()?.trim() }
            .firstOrNull { it.isNotEmpty() }
        if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
        if (!UnicomResponseStatus.isSuccess(code)) {
            throw UnicomAPIException.Server(first(root, "desc", "message", "dsc") ?: fallback)
        }
        return root
    }

    private fun parseContracts(raw: JsonElement?): List<RebateContract> = decodeArray(raw).mapIndexed { index, value ->
        val name = first(value, "actexplain", "actExplain")
            ?: first(value, "actname", "actName", "activityName")
            ?: "合约返赠"
        val mobile = first(value, "serialnumber", "serialNumber", "mobile", "phone").orEmpty()
        val start = first(value, "startdate", "startDate").orEmpty()
        val end = first(value, "enddate", "endDate").orEmpty()
        val detail = decodeArray(value["returndetail"]).map { detailValue ->
            RebateReturnDetail(
                freeMoney = money(first(detailValue, "returnfreemoney", "returnFreeMoney")),
                giftMoney = money(first(detailValue, "returngivmoney", "returnGiftMoney")),
                date = first(detailValue, "returntime", "returnTime").orEmpty(),
            )
        }
        RebateContract(
            id = "$mobile|$start|$end|$index",
            activityName = name,
            returnedAmount = money(first(value, "sumreturnmoney", "sumReturnMoney")),
            totalAmount = money(first(value, "totalmoney", "totalMoney")),
            frozenAmount = money(first(value, "fromoney", "froMoney", "frozenMoney")),
            mobile = mobile,
            startDate = start,
            endDate = end,
            detail = detail,
        )
    }

    private fun parseGifts(raw: JsonElement?): List<GiftRecord> = decodeArray(raw).mapIndexed { index, value ->
        GiftRecord(
            id = first(value, "id", "giftId", "serialNo") ?: "gift-$index",
            name = first(value, "giftName", "name", "title", "activityName") ?: "赠款记录",
            amount = money(first(value, "giftMoney", "amount", "money", "totalMoney")),
            mobile = first(value, "serialnumber", "serialNumber", "mobile", "phone").orEmpty(),
            date = first(value, "giftTime", "date", "time", "createTime").orEmpty(),
            description = first(value, "description", "desc", "remark").orEmpty(),
        )
    }

    private fun decodeArray(raw: JsonElement?): List<JsonObject> = when (raw) {
        is JsonArray -> raw.mapNotNull { it as? JsonObject }
        is JsonPrimitive -> {
            val text = raw.content.trim()
            if (text.isEmpty()) emptyList() else runCatching {
                (parseNetworkJson(text.toByteArray()) as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    .orEmpty()
            }.getOrDefault(emptyList())
        }
        else -> emptyList()
    }

    private fun commonValues(): Map<String, String> = linkedMapOf(
        "duanlianjieabc" to "",
        "channelCode" to "",
        "serviceType" to "",
        "saleChannel" to "",
        "externalSources" to "",
        "contactCode" to "",
        "ticket" to "",
        "ticketPhone" to "",
        "ticketChannel" to "",
    )

    private fun first(value: JsonObject, vararg keys: String): String? = keys.asSequence()
        .mapNotNull { key -> value[key].stringValue()?.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun money(value: String?): String = String.format(Locale.US, "%.2f", value?.toDoubleOrNull() ?: 0.0)

    private fun parseDate(value: String?): Instant? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val zone = ZoneId.of("Asia/Shanghai")
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmmss", "yyyy-MM-dd'T'HH:mm:ss")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.CHINA)).atZone(zone).toInstant()
            }.getOrNull()
        }
    }

    private fun requireCookie(credentials: AccountCredentials): String {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return cookie
    }

    private fun shouldActivate(error: Exception): Boolean = error is UnicomAPIException.SessionExpired
}
