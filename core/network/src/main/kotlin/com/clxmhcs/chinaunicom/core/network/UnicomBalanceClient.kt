package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.FrozenBalanceItem
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnavailableLimitItem
import kotlinx.serialization.json.JsonObject

internal data class UnicomBalanceWireResult(
    val balanceYuan: Double?,
    val detail: UnavailableBalanceDetail,
)

internal class UnicomBalanceClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(),
) {
    companion object {
        const val BASE_URL = "https://m.client.10010.com"
        const val PATH = "/servicequerybusiness/balancenew/accountBalancenew.htm"
    }

    fun fetchBalance(cookie: String): UnicomBalanceWireResult {
        val response = http.post(
            url = BASE_URL + PATH,
            body = unicomFormEncoded(
                mapOf(
                    "duanlianjieabc" to "",
                    "channelCode" to "",
                    "serviceType" to "",
                    "saleChannel" to "",
                    "externalSources" to "",
                    "contactCode" to "",
                    "ticket" to "",
                    "ticketPhone" to "",
                    "ticketChannel" to "",
                    "language" to "chinese",
                    "channel" to "client",
                ),
            ),
            headers = mapOf(
                "Cookie" to cookie,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired

        val root = parseNetworkJson(response.data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        val currentBalance = root.string("curntbalancecust")
        val detail = UnavailableBalanceDetail(
            currentBalance = currentBalance,
            unavailableLimitFee = root.string("unavailablelimitfeecust"),
            frozenFee = root.string("feefrozen"),
            totalUnavailable = root.string("uAndF"),
            limitItems = root["limitInfoList"].objectList().map { item ->
                UnavailableLimitItem(
                    depositName = item.string("depositname"),
                    unavailableLimitFee = item.string("unavailablelimitfee"),
                    belongSerialNumber = item.string("belongserialnumber"),
                    endCycle = item.string("endcycle"),
                    depositInfo = item.string("depositinfo"),
                    userStyle = item.string("userstyle"),
                )
            },
            frozenItems = root["frozeninfolist"].objectList().map { item ->
                FrozenBalanceItem(
                    actionName = item.string("actionname"),
                    serialNumber = item.string("serialnumber"),
                    actionMoney = item.string("actionmoney"),
                    usedMoney = item.string("usedmoney"),
                    leftMoney = item.string("leftmoney"),
                    actionDepart = item.string("actiondepart"),
                    startCycle = item.string("startcycle"),
                    endCycle = item.string("endcycle"),
                )
            },
        )
        val balance = currentBalance?.replace(",", "")?.toDoubleOrNull()
        return UnicomBalanceWireResult(balance, detail)
    }

    private fun JsonObject.string(key: String): String? = this[key].stringValue().trimmedOrNull()
}
