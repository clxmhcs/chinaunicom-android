package com.clxmhcs.chinaunicom.core.parser

import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import java.math.BigDecimal
import java.math.RoundingMode

class FlowFormatter(private val unit: DisplayUnit) {
    fun string(mb: Double?): String {
        if (mb == null || !mb.isFinite()) return "--"
        val safeMB = mb.coerceAtLeast(0.0)
        return when (unit) {
            DisplayUnit.MEGABYTES -> formatMegabytes(safeMB)
            DisplayUnit.GIGABYTES -> formatGigabytes(safeMB)
            DisplayUnit.AUTOMATIC -> if (safeMB >= 1024) formatGigabytes(safeMB) else formatMegabytes(safeMB)
        }
    }

    private fun formatMegabytes(value: Double): String =
        decimalString(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)) + " MB"

    private fun formatGigabytes(value: Double): String {
        val rounded = BigDecimal.valueOf(value)
            .divide(BigDecimal.valueOf(1024), 12, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
        return if (rounded.stripTrailingZeros().scale() <= 0) {
            decimalString(rounded) + " GB"
        } else {
            rounded.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + " GB"
        }
    }

    private fun decimalString(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}

fun cleanedText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

fun String.maskedMobile(): String = if (length >= 7) take(3) + " **** " + takeLast(4) else this
