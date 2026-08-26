package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.model.MyOrderBusinessDetail
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailContent
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailMode
import com.clxmhcs.chinaunicom.core.model.MyOrderRenewalDetail
import com.clxmhcs.chinaunicom.core.model.MyOrderSubProduct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

sealed class MyOrderDetailParsingException(message: String) : Exception(message) {
    data object InvalidBridgeResponse : MyOrderDetailParsingException("订单详情返回格式无法识别。")
    data class InvalidServerResponse(val serverMessage: String) : MyOrderDetailParsingException(serverMessage)
    data object EmptyDetail : MyOrderDetailParsingException("未获取到订单详情。")
}

class MyOrderDetailParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(bridgeText: String, mode: MyOrderDetailMode): MyOrderDetailContent {
        val bridge = objectFromText(bridgeText) ?: throw MyOrderDetailParsingException.InvalidBridgeResponse
        val detailText = string(bridge["detail"])
            ?.takeIf(String::isNotEmpty)
            ?: throw MyOrderDetailParsingException.InvalidBridgeResponse

        return when (mode) {
            MyOrderDetailMode.BUSINESS -> MyOrderDetailContent.Business(
                parseBusiness(detailText, string(bridge["products"])),
            )
            MyOrderDetailMode.RENEWAL -> MyOrderDetailContent.Renewal(parseRenewal(detailText))
            MyOrderDetailMode.UNSUPPORTED -> throw MyOrderDetailParsingException.EmptyDetail
        }
    }

    private fun parseBusiness(detailText: String, productText: String?): MyOrderBusinessDetail {
        val root = objectFromText(detailText) ?: throw MyOrderDetailParsingException.InvalidBridgeResponse
        val code = string(root["code"]).orEmpty()
        if (code != "0000") {
            throw MyOrderDetailParsingException.InvalidServerResponse(
                string(root["message"]) ?: "营业厅订单详情查询失败（code: ${code.ifEmpty { "未知" }}）",
            )
        }
        val data = root["data"] as? JsonObject ?: throw MyOrderDetailParsingException.EmptyDetail
        return MyOrderBusinessDetail(
            orderID = string(data["orderId"]).orEmpty(),
            businessName = string(data["businessName"]) ?: "业务订单",
            productName = string(data["productName"]).orEmpty(),
            mobile = string(data["mobile"]) ?: string(data["contactMobile"]).orEmpty(),
            acceptName = string(data["acceptName"]).orEmpty(),
            acceptNumber = string(data["acceptCbNo"]).orEmpty(),
            channelName = string(data["channelName"]).orEmpty(),
            handleTime = string(data["handleTime"]).orEmpty(),
            createTime = string(data["createTime"]).orEmpty(),
            networkName = string(data["netTypeName"]).orEmpty(),
            provinceName = string(data["provinceName"]).orEmpty(),
            areaName = string(data["areaName"]).orEmpty(),
            subProducts = parseSubProducts(productText),
        )
    }

    private fun parseSubProducts(text: String?): List<MyOrderSubProduct> {
        if (text == null) return emptyList()
        val root = objectFromText(text) ?: return emptyList()
        if (string(root["code"]) != "0000") return emptyList()
        val data = root["data"] as? JsonObject ?: return emptyList()
        val rows = data["rows"] as? JsonArray ?: return emptyList()
        return rows.mapIndexedNotNull { index, element ->
            val value = element as? JsonObject ?: return@mapIndexedNotNull null
            val productName = string(value["productName"]).orEmpty()
            if (productName.isEmpty()) return@mapIndexedNotNull null
            MyOrderSubProduct(
                id = string(value["productId"]) ?: "sub-product-$index",
                productName = productName,
                statusName = string(value["statusAttributeName"]).orEmpty(),
                startTime = string(value["startTime"]).orEmpty(),
                endTime = string(value["endTime"]).orEmpty(),
            )
        }
    }

    private fun parseRenewal(detailText: String): MyOrderRenewalDetail {
        val root = objectFromText(detailText) ?: throw MyOrderDetailParsingException.InvalidBridgeResponse
        val code = string(root["result"]).orEmpty()
        if (code != "0000") {
            throw MyOrderDetailParsingException.InvalidServerResponse(
                string(root["description"]) ?: "续约续费订单详情查询失败（code: ${code.ifEmpty { "未知" }}）",
            )
        }
        val values = root["info"] as? JsonArray ?: throw MyOrderDetailParsingException.EmptyDetail
        val data = values.firstOrNull() as? JsonObject ?: throw MyOrderDetailParsingException.EmptyDetail
        return MyOrderRenewalDetail(
            orderNo = string(data["orderNo"]).orEmpty(),
            productName = string(data["commName"]) ?: "续约续费",
            serviceType = string(data["serviceType"]).orEmpty(),
            createTime = string(data["createTime"]).orEmpty(),
            actionStartTime = string(data["newActionStartTime"]).orEmpty(),
            paymentTime = string(data["payCompleteTime"]).orEmpty(),
            updateTime = string(data["updateTime"]).orEmpty(),
            amountFen = integer(data["incomeTotalMoney"]),
        )
    }

    private fun objectFromText(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun string(value: JsonElement?): String? {
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.trim()
    }

    private fun integer(value: JsonElement?): Int? {
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }
}
