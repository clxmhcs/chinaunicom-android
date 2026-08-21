package com.clxmhcs.chinaunicom.model

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.QuotaType

/**
 * UI display contract for quota cards.
 *
 * The source stays as the authoritative M2 [FlowPackage]; this type only
 * carries already-derived presentation values into Compose.
 */
data class QuotaDisplayModel(
    val title: String,
    val subtitle: String,
    val remainingText: String,
    val progress: Float? = null,
    val expireText: String? = null,
)

fun FlowPackage.toDisplayModel(): QuotaDisplayModel {
    val remainingText = when {
        detectedQuotaType == QuotaType.UNLIMITED -> "不限量"
        remainingMB != null -> "剩余 ${rawNumber(remainingMB)} MB"
        else -> "剩余 --"
    }

    return QuotaDisplayModel(
        title = originalName,
        subtitle = "套餐流量",
        remainingText = remainingText,
        progress = detailDisplayFraction(detectedQuotaType)?.toFloat(),
        expireText = endDateText,
    )
}

private fun rawNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
