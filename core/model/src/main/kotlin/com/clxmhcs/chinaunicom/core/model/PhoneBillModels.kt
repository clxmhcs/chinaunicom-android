package com.clxmhcs.chinaunicom.core.model

import java.time.Instant

data class BillMonth(
    val year: String,
    val month: String,
    val key: String = year + month.padStart(2, '0'),
) {
    val id: String get() = key
    val title: String get() = month.toIntOrNull()?.takeIf { it > 0 }?.let { "${it}月" } ?: month
    val subtitle: String get() = year
}

data class PhoneBillFetchResult(
    val snapshot: PhoneBillSnapshot,
    val updatedCredentials: AccountCredentials?,
)

data class PhoneBillMonthsFetchResult(
    val months: List<BillMonth>,
    val updatedCredentials: AccountCredentials?,
)

data class PhoneBillSnapshot(
    val month: BillMonth,
    val queryTime: String?,
    val summary: PhoneBillSummary,
    val userBills: List<UserBill>,
    val accountSections: List<BillItemSection>,
    val fetchedAt: Instant,
    val parserVersion: Int?,
) {
    companion object {
        const val CURRENT_PARSER_VERSION = 4
    }
}

data class PhoneBillSummary(
    val amountDue: String,
    val realPayFee: String,
    val totalPrice: String,
    val totalDiscount: String,
    val totalRealFee: String,
    val totalAdjustAfter: String,
    val totalAcctDiscnt: String?,
    val totalLateFee: String?,
    val allRebates: String?,
    val realPayFeeP: String?,
)

data class UserBill(
    val id: String,
    val mobile: String,
    val virtualUserTag: String?,
    val payable: String,
    val sections: List<BillItemSection>,
    val totalPrice: String?,
    val totalDiscount: String?,
    val totalRealFee: String?,
    val totalAdjustAfter: String?,
    val totalAcctDiscnt: String?,
    val totalLateFee: String?,
    val allRebates: String?,
    val realPayFeeP: String?,
) {
    val allItems: List<BillItem> get() = sections.flatMap { it.items }
}

data class BillItemSection(
    val id: String,
    val title: String,
    val items: List<BillItem>,
)

data class BillItem(
    val id: String,
    val name: String,
    val code: String?,
    val originalFee: String,
    val discount: String,
    val realFee: String,
)
