package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import java.util.Locale

// MARK: - Ordered business

data class OrderedBusinessSnapshot(
    val title: String?,
    val queryTime: String?,
    val fetchedAt: Instant,
    val sections: List<OrderedBusinessSection>,
) {
    val totalCount: Int
        get() = sections.sumOf { it.items.size }
}

data class OrderedBusinessSection(
    val id: String,
    val title: String,
    val icon: String,
    val items: List<OrderedBusinessItem>,
)

data class OrderedBusinessItem(
    val id: String,
    val name: String,
    val subtitle: String?,
    val fee: String?,
    val startDate: String?,
    val endDate: String?,
)

// MARK: - Phone bill

data class BillMonth(
    val year: String,
    val month: String,
    val key: String = year + month.padStart(2, '0'),
) {
    val id: String
        get() = key

    val title: String
        get() = month.toIntOrNull()?.takeIf { it > 0 }?.let { "${it}月" } ?: month

    val subtitle: String
        get() = year
}

data class PhoneBillSnapshot(
    val month: BillMonth,
    val queryTime: String?,
    val summary: PhoneBillSummary,
    val userBills: List<UserBill>,
    val accountSections: List<BillItemSection>,
    val fetchedAt: Instant,
    val parserVersion: Int? = CURRENT_PARSER_VERSION,
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
    val allItems: List<BillItem>
        get() = sections.flatMap { it.items }
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

// MARK: - Integral

data class IntegralSnapshot(
    val totalAvailable: Int,
    val communication: Int,
    val reward: Int,
    val directional: Int?,
    val expiredAndExpiringReward: Int,
    val expiringThisMonth: Int,
    val expiringCommunication: Int,
    val expiringReward: Int,
    val expirationDay: Int?,
    val couponCount: Int,
    val provinceCode: String?,
    val packageID: String?,
    val isUnicom: String?,
    val months: List<IntegralMonthSummary>,
    val fetchedAt: Instant,
    val parserVersion: Int = CURRENT_PARSER_VERSION,
) {
    companion object {
        const val CURRENT_PARSER_VERSION = 1
    }
}

data class IntegralMonthSummary(
    val cycleID: String,
    val addScore: Int,
    val consumedScore: Int,
    val expiredScore: Int,
) {
    val id: String
        get() = cycleID

    val yearMonth: String?
        get() {
            val parts = Regex("\\d+").findAll(cycleID).map { it.value }.toList()
            if (parts.size < 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            return String.format(Locale.US, "%04d%02d", year, month)
        }
}

data class IntegralDetailItem(
    val typeChar: String,
    val scoreType: String,
    val title: String,
    val scoreValue: String,
    val createTime: String?,
    val returnTime: String?,
    val endTime: String?,
    val orderTime: String?,
    val channelName: String?,
    val expireTime: String?,
    val expireTag: String?,
) {
    val id: String
        get() = listOf(
            typeChar,
            scoreType,
            title,
            scoreValue,
            createTime.orEmpty(),
            returnTime.orEmpty(),
            endTime.orEmpty(),
            orderTime.orEmpty(),
            channelName.orEmpty(),
            expireTime.orEmpty(),
            expireTag.orEmpty(),
        ).joinToString("|")
}

enum class IntegralSection(val rawValue: String, val title: String) {
    AVAILABLE("available", "可用积分"),
    COMMUNICATION("communication", "通信积分"),
    REWARD("reward", "奖励积分"),
    EXPIRING("expiring", "本月到期积分");

    fun score(snapshot: IntegralSnapshot): Int = when (this) {
        AVAILABLE -> snapshot.totalAvailable
        COMMUNICATION -> snapshot.communication
        REWARD -> snapshot.reward
        EXPIRING -> snapshot.expiringThisMonth
    }

    val detailQuery: IntegralDetailQuery?
        get() = when (this) {
            AVAILABLE -> null
            COMMUNICATION -> IntegralDetailQuery(
                scoreType = "0",
                typeChar = "3",
                yearMonth = null,
                title = "通信积分",
            )
            REWARD -> IntegralDetailQuery(
                scoreType = "1",
                typeChar = "3",
                yearMonth = null,
                title = "奖励积分",
            )
            EXPIRING -> IntegralDetailQuery(
                scoreType = "2",
                typeChar = "2",
                yearMonth = null,
                title = "本月到期积分",
            )
        }
}

data class IntegralDetailQuery(
    val scoreType: String,
    val typeChar: String,
    val yearMonth: String?,
    val title: String,
) {
    val cacheKey: String
        get() = listOf(scoreType, typeChar, yearMonth ?: "all").joinToString("-")

    companion object {
        fun month(
            month: IntegralMonthSummary,
            typeChar: String,
            title: String,
        ): IntegralDetailQuery? {
            val yearMonth = month.yearMonth ?: return null
            return IntegralDetailQuery(
                scoreType = "2",
                typeChar = typeChar,
                yearMonth = yearMonth,
                title = "${month.cycleID} · $title",
            )
        }
    }
}
