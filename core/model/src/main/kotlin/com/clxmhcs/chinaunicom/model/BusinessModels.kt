package com.clxmhcs.chinaunicom.model

/**
 * Shared display models migrated from iOS business concepts.
 * UI layers should consume these models instead of protocol responses.
 */
data class AccountSummary(
    val accountId: String,
    val maskedNumber: String,
    val balance: String? = null,
    val remainingData: List<QuotaItem> = emptyList(),
    val voice: VoiceSummary? = null
)

data class QuotaItem(
    val title: String,
    val used: Long? = null,
    val total: Long? = null,
    val unit: String = "MB",
    val expiredAt: String? = null
)

data class VoiceSummary(
    val usedMinutes: Long? = null,
    val totalMinutes: Long? = null
)

data class BusinessOverview(
    val accounts: List<AccountSummary> = emptyList(),
    val updatedAt: Long = 0L
)
