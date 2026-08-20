package com.clxmhcs.chinaunicom.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.util.Locale

enum class MyOrderKind(val rawValue: String, val title: String) {
    ALL("all", "全部类型"),
    BUSINESS("business", "业务"),
    VOICE_AND_DATA("voiceAndData", "流量及语音"),
    PAYMENT("payment", "交费"),
    BROADBAND_INSTALL("broadbandInstall", "宽带新装"),
    RELOCATION("relocation", "移机服务"),
    POINTS("points", "积分及权益"),
    STOREFRONT("storefront", "营业厅"),
    RENEWAL("renewal", "续约续费"),
    MALL("mall", "商城"),
    OTHER("other", "其它"),
}

data class MyOrderMember(
    val id: String,
    val goodsName: String?,
    val price: String?,
    val integral: String?,
    val goodsID: String?,
    val productPicture: String?,
    val tradeTags: List<String>,
) {
    val normalizedGoodsName: String? get() = goodsName.trimmedOrNull()
    val normalizedPrice: String? get() = price.trimmedOrNull()
    val points: Int?
        get() = integral.trimmedOrNull()
            ?.split('|')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.maxOrNull()
}

data class MyOrderAction(
    val id: String,
    val name: String,
    val type: String?,
    val url: String?,
    val postParameter: String?,
) {
    val normalizedURL: URI?
        get() {
            val value = url.trimmedOrNull() ?: return null
            val candidate = runCatching { URI(value) }.getOrNull() ?: return null
            val scheme = candidate.scheme?.lowercase(Locale.ROOT) ?: return null
            return candidate.takeIf { scheme == "http" || scheme == "https" }
        }
}

data class MyOrder(
    val id: String,
    val orderID: String,
    val encodedOrderID: String?,
    val sourceCode: String?,
    val sourceName: String?,
    val statusCode: String?,
    val statusName: String,
    val nodeCode: String?,
    val nodeName: String?,
    val createdAtText: String,
    val channelName: String?,
    val phoneNumber: String?,
    val maskedContactNumber: String?,
    val accountNumber: String?,
    val address: String?,
    val goodsName: String?,
    val tradeType: String?,
    val sceneType: String?,
    val originNodeName: String?,
    val members: List<MyOrderMember>,
    val actions: List<MyOrderAction>,
    val tradeTags: List<String>,
) {
    val kind: MyOrderKind
        get() {
            when (sourceCode) {
                "D10" -> return MyOrderKind.PAYMENT
                "D4" -> return MyOrderKind.POINTS
                "D13" -> return MyOrderKind.STOREFRONT
                "D18" -> return MyOrderKind.RENEWAL
                "D14" -> return MyOrderKind.MALL
                "D7" -> return if (sceneType == "200089") MyOrderKind.RELOCATION else MyOrderKind.BROADBAND_INSTALL
                "D15" -> {
                    val allTags = (tradeTags + members.flatMap { it.tradeTags }).toSet()
                    val operation = members.firstOrNull()?.normalizedPrice
                    return if (allTags.contains("3") || operation == "订购" || operation == "退订") {
                        MyOrderKind.VOICE_AND_DATA
                    } else MyOrderKind.BUSINESS
                }
            }

            return when (sourceName?.replace("订单", "")) {
                "交费" -> MyOrderKind.PAYMENT
                "业务" -> MyOrderKind.BUSINESS
                "营业厅" -> MyOrderKind.STOREFRONT
                "续约续费" -> MyOrderKind.RENEWAL
                "商城" -> MyOrderKind.MALL
                "积分商城", "积分及权益" -> MyOrderKind.POINTS
                "宽带新装" -> MyOrderKind.BROADBAND_INSTALL
                "移机服务" -> MyOrderKind.RELOCATION
                else -> MyOrderKind.OTHER
            }
        }

    val categoryTitle: String
        get() {
            if (kind != MyOrderKind.OTHER) return kind.title
            val source = sourceName.trimmedOrNull() ?: return "其它"
            val title = source.replace("订单", "")
            return if (title.toIntOrNull() == null) title else "其它"
        }

    val primaryTitle: String
        get() {
            if (kind == MyOrderKind.RELOCATION) return "宽带编码：${displayServiceNumber ?: "--"}"
            members.firstOrNull()?.normalizedGoodsName?.let { return it }
            goodsName.trimmedOrNull()?.let { return it }
            tradeType.trimmedOrNull()?.let { return it }
            displayServiceNumber?.let { return "业务号码：$it" }
            return categoryTitle
        }

    val displayServiceNumber: String?
        get() {
            maskedContactNumber.trimmedOrNull()?.let { return it }
            accountNumber.trimmedOrNull()?.takeIf { it != "00000000" }?.let { return it }
            return phoneNumber?.maskedOrderNumber()
        }

    val operationType: String?
        get() {
            if (kind != MyOrderKind.VOICE_AND_DATA) return null
            val value = members.firstOrNull()?.normalizedPrice
            return value?.takeIf { it == "订购" || it == "退订" }
        }

    val displayPrice: String?
        get() {
            val value = members.firstOrNull()?.normalizedPrice ?: return null
            if (value == "订购" || value == "退订") return null
            return money(value)
        }

    val displayPoints: Int? get() = members.mapNotNull { it.points }.maxOrNull()

    val pictureURL: URI?
        get() = members.firstOrNull()?.productPicture.trimmedOrNull()?.let { raw ->
            runCatching { URI(raw) }.getOrNull()
        }

    val showsCancellationNotice: Boolean
        get() {
            val combined = listOf(statusName, nodeName, originNodeName)
                .mapNotNull { it?.lowercase(Locale.ROOT) }
                .joinToString("|")
            return combined.contains("退单") || combined.contains("取消")
        }

    val detailAction: MyOrderAction? get() = actions.firstOrNull { it.name.contains("查看详情") }

    private fun money(value: String): String = runCatching {
        BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    }.getOrElse { value }
}

data class MyOrderPage(
    val orders: List<MyOrder>,
    val serverTime: String?,
    val hasMore: Boolean,
)

data class MyOrderFetchResult(
    val page: MyOrderPage,
    val updatedCredentials: AccountCredentials?,
)

private fun String.maskedOrderNumber(): String {
    val digits = filter(Char::isDigit)
    if (digits.length < 7) return this
    return "${digits.take(3)}****${digits.takeLast(4)}"
}
