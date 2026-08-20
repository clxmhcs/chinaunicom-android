package com.clxmhcs.chinaunicom.model

/**
 * iOS visual baseline mapping contract.
 *
 * Converts unified business data into fields consumed by Compose UI.
 * This layer intentionally does not perform network requests.
 */
object IOSDisplayMapping {

    fun balanceText(balance: AccountSummary): String {
        return balance.balance?.let { "余额：${it}元" } ?: "余额：--"
    }

    fun quotaTitle(item: QuotaItem): String {
        return item.title
    }

    fun quotaRemaining(item: QuotaItem): String {
        val total = item.total ?: 0L
        val used = item.used ?: 0L
        val remaining = (total - used).coerceAtLeast(0L)
        return "剩余 ${remaining}${item.unit} / 共 ${total}${item.unit}"
    }

    fun voiceRemaining(item: VoiceSummary): String {
        val total = item.totalMinutes ?: 0L
        val used = item.usedMinutes ?: 0L
        val remaining = (total - used).coerceAtLeast(0L)
        return "剩余 ${remaining} 分钟 / 共 ${total} 分钟"
    }
}
