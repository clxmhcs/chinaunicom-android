package com.clxmhcs.chinaunicom.core.parser

import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount

/**
 * Pure presentation mapping for flow quota values.
 *
 * The authoritative values remain on [FlowPackage]. This formatter only turns
 * those values into the same display semantics used by the iOS app and must
 * never infer remaining quota from total-used.
 */
data class FlowPackageDisplayText(
    val title: String,
    val remainingText: String,
    val detailText: String?,
    val progress: Double?,
)

fun flowPackageDisplayText(
    account: UnicomAccount,
    packageValue: FlowPackage,
    unit: DisplayUnit = DisplayUnit.AUTOMATIC,
): FlowPackageDisplayText {
    val formatter = FlowFormatter(unit)
    val quotaType = account.quotaType(packageValue)

    return if (quotaType == QuotaType.UNLIMITED) {
        FlowPackageDisplayText(
            title = account.displayName(packageValue),
            remainingText = "不限量",
            detailText = "已用 ${formatter.string(packageValue.usedMB)}",
            progress = null,
        )
    } else {
        FlowPackageDisplayText(
            title = account.displayName(packageValue),
            remainingText = "剩余 ${formatter.string(packageValue.remainingMB)}",
            detailText = "已用 ${formatter.string(packageValue.usedMB)} / 总量 ${formatter.string(packageValue.totalMB)}",
            progress = packageValue.usedFraction,
        )
    }
}
