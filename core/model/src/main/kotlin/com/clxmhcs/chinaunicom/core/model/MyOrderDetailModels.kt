package com.clxmhcs.chinaunicom.core.model

import java.net.URI
import java.util.UUID

enum class MyOrderDetailMode(val rawValue: String) {
    BUSINESS("business"),
    RENEWAL("renewal"),
    UNSUPPORTED("unsupported"),
}

/**
 * Public detail request metadata. Cookie/token material is intentionally excluded from ordinary state;
 * the M5 credential lifecycle provides the Cookie only at WebView execution time.
 */
data class MyOrderDetailRequest(
    val id: UUID = UUID.randomUUID(),
    val accountID: UUID,
    val actionURL: URI,
    val mode: MyOrderDetailMode,
    val orderID: String,
    val serviceType: String?,
)

data class MyOrderSubProduct(
    val id: String,
    val productName: String,
    val statusName: String,
    val startTime: String,
    val endTime: String,
)

data class MyOrderBusinessDetail(
    val orderID: String,
    val businessName: String,
    val productName: String,
    val mobile: String,
    val acceptName: String,
    val acceptNumber: String,
    val channelName: String,
    val handleTime: String,
    val createTime: String,
    val networkName: String,
    val provinceName: String,
    val areaName: String,
    val subProducts: List<MyOrderSubProduct>,
)

data class MyOrderRenewalDetail(
    val orderNo: String,
    val productName: String,
    val serviceType: String,
    val createTime: String,
    val actionStartTime: String,
    val paymentTime: String,
    val updateTime: String,
    val amountFen: Int?,
) {
    val acceptanceTime: String
        get() = updateTime.nonBlankOrNull()
            ?: paymentTime.nonBlankOrNull()
            ?: actionStartTime.nonBlankOrNull()
            ?: createTime
}

sealed interface MyOrderDetailContent {
    data class Business(val detail: MyOrderBusinessDetail) : MyOrderDetailContent
    data class Renewal(val detail: MyOrderRenewalDetail) : MyOrderDetailContent
}

private fun String.nonBlankOrNull(): String? = trim().takeIf(String::isNotEmpty)
