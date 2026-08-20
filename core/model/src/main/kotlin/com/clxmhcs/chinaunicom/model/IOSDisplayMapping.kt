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
        return item.name
    }

    fun quotaRemaining(item: QuotaItem): String {
        return "剩余 ${item.remaining} / 共 ${item.total}"
    }

    fun voiceRemaining(item: VoiceSummary): String {
        return "剩余 ${item.remainingMinutes} 分钟 / 共 ${item.totalMinutes} 分钟"
    }
}
