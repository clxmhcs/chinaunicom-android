package com.clxmhcs.chinaunicom.core.model

import java.time.Instant

enum class RebateQueryScope(
    val rawValue: String,
    val title: String,
    val queryType: String,
) {
    ACCOUNT("account", "账户", "0"),
    USER("user", "用户", "1");

    companion object {
        fun fromRawValue(value: String?): RebateQueryScope? = entries.firstOrNull { it.rawValue == value }
    }
}

data class RebateReturnDetail(
    val freeMoney: String,
    val giftMoney: String,
    val date: String,
)

data class RebateContract(
    val id: String,
    val activityName: String,
    val returnedAmount: String,
    val totalAmount: String,
    val frozenAmount: String,
    val mobile: String,
    val startDate: String,
    val endDate: String,
    val detail: List<RebateReturnDetail>,
) {
    val periodText: String
        get() {
            val start = monthText(startDate)
            val end = monthText(endDate)
            return when {
                start.isEmpty() -> end
                end.isEmpty() -> start
                else -> "$start-$end"
            }
        }

    private fun monthText(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 6) "${digits.take(4)}年${digits.drop(4).take(2)}月" else value
    }
}

data class GiftRecord(
    val id: String,
    val name: String,
    val amount: String,
    val mobile: String,
    val date: String,
    val description: String,
)

data class RebateContractsFetchResult(
    val contracts: List<RebateContract>,
    val queryTime: Instant?,
    val updatedCredentials: AccountCredentials?,
)

data class GiftRecordsFetchResult(
    val gifts: List<GiftRecord>,
    val queryTime: Instant?,
    val updatedCredentials: AccountCredentials?,
)
