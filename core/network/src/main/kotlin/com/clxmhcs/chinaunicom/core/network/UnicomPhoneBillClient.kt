package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillItem
import com.clxmhcs.chinaunicom.core.model.BillItemSection
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillMonthsFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSummary
import com.clxmhcs.chinaunicom.core.model.UserBill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Source-equivalent implementation of iOS PhoneBillClient.swift. */
class UnicomPhoneBillClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(20_000L)),
    private val clock: Clock = Clock.systemUTC(),
    private val activateSession: (AccountCredentials) -> AccountCredentials =
        UnicomAPIClient(http = http)::activateSession,
) : PhoneBillNetworkClient {
    override fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            PhoneBillMonthsFetchResult(fetchMonthsOnce(originalCookie), null)
        } catch (firstError: Exception) {
            if (!shouldActivateSession(firstError)) throw firstError
            val appID = credentials.appID.trimmedOrNull()
            val tokenOnline = credentials.tokenOnline.trimmedOrNull()
            if (appID == null || tokenOnline == null) {
                throw UnicomAPIException.Server("账单 Cookie 会话已失效，且该号码未保存可用的 appId/token_online")
            }
            val activated = try {
                activate(originalCookie, appID, tokenOnline)
            } catch (error: Exception) {
                throw UnicomAPIException.Server("账单会话恢复失败：${error.message ?: error::class.java.simpleName}")
            }
            val changed = activated.cookie != originalCookie || activated.appID != appID || activated.tokenOnline != tokenOnline
            PhoneBillMonthsFetchResult(
                months = fetchMonthsOnce(activated.cookie),
                updatedCredentials = if (changed) activated else null,
            )
        }
    }

    override fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            PhoneBillFetchResult(fetchDetailOnce(originalCookie, month), null)
        } catch (firstError: Exception) {
            if (!shouldActivateSession(firstError)) throw firstError
            val appID = credentials.appID.trimmedOrNull()
            val tokenOnline = credentials.tokenOnline.trimmedOrNull()
            if (appID == null || tokenOnline == null) {
                throw UnicomAPIException.Server("账单 Cookie 会话已失效，且该号码未保存可用的 appId/token_online")
            }
            val activated = try {
                activate(originalCookie, appID, tokenOnline)
            } catch (error: Exception) {
                throw UnicomAPIException.Server("账单会话恢复失败：${error.message ?: error::class.java.simpleName}")
            }
            val snapshot = fetchDetailOnce(activated.cookie, month)
            val changed = activated.cookie != originalCookie || activated.appID != appID || activated.tokenOnline != tokenOnline
            PhoneBillFetchResult(
                snapshot = snapshot,
                updatedCredentials = if (changed) activated else null,
            )
        }
    }

    private fun fetchMonthsOnce(cookie: String): List<BillMonth> {
        val response = postBill(
            ComprehensiveBusinessEndpoints.PHONE_BILL_MONTHS,
            commonValues(),
            cookie,
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        val root = parseNetworkJson(response.data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        validateSuccess(root, "账单月份查询失败")
        val data = root["data"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val months = data["months"].objectList().mapNotNull(::makeMonth)
        if (months.isEmpty()) throw UnicomAPIException.Server("联通未返回可查询账单月份")
        return months
    }

    private fun fetchDetailOnce(cookie: String, month: BillMonth): PhoneBillSnapshot {
        val response = postBill(
            ComprehensiveBusinessEndpoints.PHONE_BILL_DETAIL,
            mapOf("month" to month.key),
            cookie,
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired
        val root = parseNetworkJson(response.data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        validateSuccess(root, "账单明细查询失败")
        val data = root["data"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        return parseDetail(data, month)
    }

    private fun activate(originalCookie: String, appID: String, tokenOnline: String): AccountCredentials {
        val renewed = activateSession(
            AccountCredentials(
                cookie = originalCookie,
                appID = appID,
                tokenOnline = tokenOnline,
            ),
        )
        return AccountCredentials(
            cookie = UnicomCookieCodec.normalize(renewed.cookie),
            appID = renewed.appID.trimmedOrNull() ?: appID,
            tokenOnline = renewed.tokenOnline.trimmedOrNull() ?: tokenOnline,
        )
    }

    private fun postBill(path: String, values: Map<String, String>, cookie: String): UnicomHTTPResponse =
        http.post(
            url = ComprehensiveBusinessEndpoints.PHONE_BILL_ROOT + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Cookie" to cookie,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
        )

    private fun commonValues(): Map<String, String> = mapOf(
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

    internal fun parseDetail(data: JsonObject, month: BillMonth): PhoneBillSnapshot {
        val realPayFee = validatedMoney(data["realPayFee"]) ?: throw UnicomAPIException.InvalidResponse
        val acctBill = data["acctBill"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        if (acctBill["acctBillList"] !is JsonArray || data["userBillList"] !is JsonArray) throw UnicomAPIException.InvalidResponse
        val accountValues = acctBill["acctBillList"].objectList()
        val userValues = data["userBillList"].objectList()

        val userBills = userValues.mapIndexed { index, value -> makeUserBill(value, index) }
        val accountSections = accountValues.mapIndexed { index, value -> makeDetailSection(value, index, false) }
        return PhoneBillSnapshot(
            month = month,
            queryTime = first(data, listOf("times", "queryTime", "queryDate", "time")),
            summary = PhoneBillSummary(
                amountDue = amount(data["amountDue"]),
                realPayFee = realPayFee,
                totalPrice = amount(acctBill["totalPrice"]),
                totalDiscount = amount(acctBill["totalAdiscnt"]),
                totalRealFee = amount(acctBill["totalRealFee"]),
                totalAdjustAfter = amount(acctBill["totalAdjustAfter"]),
                totalAcctDiscnt = optionalAmount(acctBill["totalAcctDiscnt"]),
                totalLateFee = optionalAmount(acctBill["totalLateFee"]),
                allRebates = optionalAmount(acctBill["allRebates"]),
                realPayFeeP = optionalAmount(acctBill["realPayFeeP"]),
            ),
            userBills = userBills,
            accountSections = accountSections,
            fetchedAt = Instant.now(clock),
            parserVersion = PhoneBillSnapshot.CURRENT_PARSER_VERSION,
        )
    }

    private fun makeUserBill(value: JsonObject, index: Int): UserBill {
        if (value["userDetail"] !is JsonArray) throw UnicomAPIException.InvalidResponse
        val sections = value["userDetail"].objectList().mapIndexed { sectionIndex, raw ->
            makeDetailSection(raw, index * 1000 + sectionIndex, true)
        }
        val payable = first(
            value,
            listOf("realPayFeeP", "payable", "amountDue", "realPayFee", "totalRealFee", "totalFee", "realFee", "fee"),
        ) ?: sumAmounts(sections.flatMap { it.items }.map { it.realFee })
        return UserBill(
            id = first(value, listOf("serialnumber", "serialNumber", "mobile", "phone", "accNbr", "number")) ?: "user-$index",
            mobile = first(value, listOf("serialnumber", "serialNumber", "mobile", "phone", "accNbr", "number", "userNumber")) ?: "用户 ${index + 1}",
            virtualUserTag = first(value, listOf("virtualusertag", "virtualUserTag")),
            payable = moneyString(payable),
            sections = sections,
            totalPrice = optionalAmount(value["totalPrice"]),
            totalDiscount = optionalAmount(value["totalAdiscnt"]),
            totalRealFee = optionalAmount(value["totalRealFee"]),
            totalAdjustAfter = optionalAmount(value["totalAdjustAfter"]),
            totalAcctDiscnt = optionalAmount(value["totalAcctDiscnt"]),
            totalLateFee = optionalAmount(value["totalLateFee"]),
            allRebates = optionalAmount(value["allRebates"]),
            realPayFeeP = optionalAmount(value["realPayFeeP"]),
        )
    }

    private fun makeDetailSection(value: JsonObject, index: Int, usesLeafItems: Boolean): BillItemSection {
        val bill = value["bill"] as? JsonObject ?: value
        val title = first(
            bill,
            listOf("integrateitem", "integrateItem", "billTypeName", "typeName", "groupName", "itemName", "name", "title"),
        ) ?: first(
            value,
            listOf("integrateitem", "integrateItem", "billTypeName", "typeName", "groupName", "itemName", "name", "title"),
        ) ?: "消费明细"

        val rawItems = value["subItems"].objectList()
        val items = when {
            rawItems.isEmpty() -> makeBillItem(value, index.toString())?.let(::listOf).orEmpty()
            usesLeafItems -> rawItems.flatMapIndexed { itemIndex, raw -> leafBillItems(raw, "$index-$itemIndex") }
            else -> {
                val directItems = rawItems.mapIndexedNotNull { itemIndex, raw ->
                    makeBillItem(raw, (index * 1000 + itemIndex).toString())
                }
                if (directItems.isNotEmpty()) directItems
                else rawItems.flatMapIndexed { itemIndex, raw -> leafBillItems(raw, "$index-$itemIndex") }
            }
        }
        if (items.isEmpty()) throw UnicomAPIException.InvalidResponse
        return BillItemSection("$index-$title", title, items)
    }

    private fun leafBillItems(value: JsonObject, indexPrefix: String): List<BillItem> {
        val nested = value["subItems"].objectList()
        if (nested.isEmpty()) return makeBillItem(value, stableIndex(indexPrefix).toString())?.let(::listOf).orEmpty()
        return nested.flatMapIndexed { index, raw -> leafBillItems(raw, "$indexPrefix-$index") }
    }

    private fun makeBillItem(value: JsonObject, index: String): BillItem? {
        val bill = value["bill"] as? JsonObject ?: value
        val name = first(bill, listOf("integrateitem", "integrateItem", "itemName", "name", "title"))
            ?: first(value, listOf("integrateitem", "itemName", "name", "title"))
            ?: return null
        val originalFee = validatedMoney(bill["price"]) ?: return null
        val discount = validatedMoney(bill["adiscnt"]) ?: return null
        val realFee = validatedMoney(bill["fee"]) ?: return null
        val code = first(bill, listOf("integrateitemcode", "integrateItemCode", "code"))
        val stable = listOf(
            code,
            name,
            first(bill, listOf("price")),
            first(bill, listOf("adiscnt")),
            first(bill, listOf("fee")),
        ).filterNotNull().joinToString("|")
        return BillItem(
            id = if (stable.isEmpty()) "item-$index" else "$index|$stable",
            name = name,
            code = code,
            originalFee = originalFee,
            discount = discount,
            realFee = realFee,
        )
    }

    private fun makeMonth(value: JsonObject): BillMonth? {
        val key = first(value, listOf("historyMonthAndYear", "monthAndYear", "month"))
        val year = first(value, listOf("historyYear", "year"))
        val month = first(value, listOf("historyMonth", "month"))
        if (key != null && key.length >= 6) {
            return BillMonth(year ?: key.take(4), month ?: key.takeLast(2), key)
        }
        if (year == null || month == null) return null
        return BillMonth(year, month)
    }

    private fun validateSuccess(root: JsonObject, fallback: String) {
        val code = listOf("code", "rsp_code", "status")
            .asSequence().mapNotNull { root[it].stringValue()?.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        if (UnicomResponseStatus.isSuccess(code)) return
        if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
        throw UnicomAPIException.Server(first(root, listOf("desc", "message", "rsp_desc")) ?: fallback)
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> error.serverMessage.contains("登录") ||
            error.serverMessage.contains("会话") || error.serverMessage.contains("cookie", ignoreCase = true)
        else -> false
    }

    private fun first(value: JsonObject, keys: List<String>): String? =
        keys.asSequence().mapNotNull { value[it].stringValue()?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()

    private fun amount(value: JsonElement?): String = moneyString(value.stringValue() ?: "0.00")

    private fun optionalAmount(value: JsonElement?): String? = value.stringValue()?.let(::moneyString)

    private fun validatedMoney(value: JsonElement?): String? {
        val raw = value.stringValue() ?: return null
        return runCatching { moneyString(raw.replace(",", "")) }.getOrNull()
    }

    private fun sumAmounts(values: List<String>): String = moneyString(
        values.fold(BigDecimal.ZERO) { sum, value -> sum + decimal(value) },
    )

    private fun moneyString(value: String): String = moneyString(decimal(value))

    private fun moneyString(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun decimal(value: String): BigDecimal = value.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO

    private fun stableIndex(value: String): Long {
        var hash = 0L
        value.codePoints().forEach { scalar -> hash = hash * 31L + scalar.toLong() }
        return if (hash == Long.MIN_VALUE) 0L else kotlin.math.abs(hash)
    }
}
