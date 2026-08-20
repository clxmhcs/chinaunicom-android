package com.clxmhcs.chinaunicom.model

/**
 * UI display contract for quota cards.
 * Converts business data into values directly consumed by Compose.
 */
data class QuotaDisplayModel(
    val title: String,
    val subtitle: String,
    val remainingText: String,
    val progress: Float? = null,
    val expireText: String? = null
)

fun QuotaItem.toDisplayModel(): QuotaDisplayModel {
    val usedValue = used ?: 0L
    val totalValue = total ?: 0L

    val remaining = if (totalValue > 0) {
        totalValue - usedValue
    } else {
        0L
    }

    val progressValue = if (totalValue > 0) {
        usedValue.toFloat() / totalValue.toFloat()
    } else {
        null
    }

    return QuotaDisplayModel(
        title = title,
        subtitle = "套餐流量",
        remainingText = "剩余 $remaining $unit",
        progress = progressValue,
        expireText = expiredAt
    )
}
