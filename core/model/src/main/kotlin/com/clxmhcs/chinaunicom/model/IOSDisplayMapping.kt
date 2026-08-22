package com.clxmhcs.chinaunicom.model

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage

/**
 * Temporary iOS-style display mapping used by the rough Compose shell.
 *
 * Inputs are authoritative M2 domain models. Formatting will be tightened in
 * Android-M4-R3; this layer must not recreate quota/business state.
 */
object IOSDisplayMapping {

    fun balanceText(account: UnicomAccount): String {
        return account.balanceYuan?.let { "余额：${rawNumber(it)}元" } ?: "余额：--"
    }

    fun quotaTitle(item: FlowPackage): String = item.originalName

    fun quotaRemaining(item: FlowPackage): String {
        if (item.detectedQuotaType == QuotaType.UNLIMITED) return "不限量"
        val remaining = item.remainingMB?.let { "${rawNumber(it)}MB" } ?: "--"
        val total = item.totalMB?.let { "${rawNumber(it)}MB" } ?: "--"
        return "剩余 $remaining / 共 $total"
    }

    fun voiceRemaining(item: VoicePackage): String {
        if (item.isUnlimited) return "不限量"
        val remaining = item.remainingMinutes?.let { "${rawNumber(it)} 分钟" } ?: "--"
        val total = item.totalMinutes?.let { "${rawNumber(it)} 分钟" } ?: "--"
        return "剩余 $remaining / 共 $total"
    }

    private fun rawNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
