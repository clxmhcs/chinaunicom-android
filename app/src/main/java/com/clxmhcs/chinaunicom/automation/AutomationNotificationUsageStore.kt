package com.clxmhcs.chinaunicom.automation

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max

/**
 * Small local history used only to render [在线时长] / [本次用量] / [今日用量].
 * No credentials or carrier responses are persisted here.
 */
internal class AutomationNotificationUsageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun contextFor(account: UnicomAccount): AutomationUsageContext {
        val now = account.lastUpdatedAt ?: account.remainingQuerySnapshot?.updatedAt ?: Instant.now()
        val currentUsed = AutomationNotificationContentRenderer.primaryUsedMB(account)
        val previous = read(account.id)
        val usage = if (previous == null) {
            AutomationUsageContext()
        } else {
            val delta = max(0.0, currentUsed - previous.usedMB)
            val zone = ZoneId.systemDefault()
            val sameDay = previous.updatedAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
            AutomationUsageContext(
                intervalStartAt = previous.updatedAt,
                intervalUsedMB = delta,
                todayUsedMB = if (sameDay) max(0.0, previous.todayUsedMB + delta) else 0.0,
            )
        }
        write(
            account.id,
            UsageRecord(
                updatedAt = now,
                usedMB = currentUsed,
                todayUsedMB = usage.todayUsedMB ?: 0.0,
            ),
        )
        return usage
    }

    private fun read(accountID: UUID): UsageRecord? {
        val raw = preferences.getString(accountID.toString(), null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val epochMillis = parts[0].toLongOrNull() ?: return null
        val usedMB = parts[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val todayUsedMB = parts[2].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        return UsageRecord(Instant.ofEpochMilli(epochMillis), usedMB, todayUsedMB)
    }

    private fun write(accountID: UUID, record: UsageRecord) {
        val value = listOf(
            record.updatedAt.toEpochMilli().toString(),
            record.usedMB.toString(),
            record.todayUsedMB.toString(),
        ).joinToString("|")
        preferences.edit().putString(accountID.toString(), value).apply()
    }

    private data class UsageRecord(
        val updatedAt: Instant,
        val usedMB: Double,
        val todayUsedMB: Double,
    )

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.m13.notification.usage.v1"
    }
}
