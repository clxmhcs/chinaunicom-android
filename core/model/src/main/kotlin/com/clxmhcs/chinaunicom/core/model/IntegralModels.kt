package com.clxmhcs.chinaunicom.core.model

import java.time.Instant
import java.util.Locale

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
    val parserVersion: Int,
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
    val id: String get() = cycleID
    val yearMonth: String?
        get() {
            val parts = cycleID.split(Regex("\\D+")).filter { it.isNotEmpty() }
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
            COMMUNICATION -> IntegralDetailQuery("0", "3", null, "通信积分")
            REWARD -> IntegralDetailQuery("1", "3", null, "奖励积分")
            EXPIRING -> IntegralDetailQuery("2", "2", null, "本月到期积分")
        }
}

data class IntegralDetailQuery(
    val scoreType: String,
    val typeChar: String,
    val yearMonth: String?,
    val title: String,
) {
    val cacheKey: String get() = listOf(scoreType, typeChar, yearMonth ?: "all").joinToString("-")

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

data class IntegralFetchResult(
    val snapshot: IntegralSnapshot,
    val updatedCredentials: AccountCredentials?,
)

data class IntegralDetailsFetchResult(
    val items: List<IntegralDetailItem>,
    val updatedCredentials: AccountCredentials?,
)

enum class IntegralError(val errorDescription: String) {
    ACCOUNT_MISMATCH("积分 Cookie 与所选手机号码不一致，已停止查询以防止串号"),
    MISSING_TOTAL_SCORE("联通未返回总可用积分"),
}
