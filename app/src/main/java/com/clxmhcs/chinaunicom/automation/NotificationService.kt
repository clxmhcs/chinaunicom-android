package com.clxmhcs.chinaunicom.automation

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.UnicomAccount

/**
 * Notification delivery boundary for M13.
 *
 * Scheduling and carrier refresh stay outside this interface. The Android implementation consumes
 * committed account state plus the existing M11 shortcut-notification profiles.
 */
interface NotificationService {
    suspend fun onAutomaticRefreshCompleted(
        accounts: List<UnicomAccount>,
        scheduledMinute: Int,
    )

    suspend fun onAutomaticRefreshFailed(
        scheduledMinute: Int,
        error: Throwable,
    )

    companion object {
        val NONE: NotificationService = object : NotificationService {
            override suspend fun onAutomaticRefreshCompleted(
                accounts: List<UnicomAccount>,
                scheduledMinute: Int,
            ) = Unit

            override suspend fun onAutomaticRefreshFailed(
                scheduledMinute: Int,
                error: Throwable,
            ) = Unit
        }
    }
}

object NotificationServiceProvider {
    @Volatile
    private var instance: NotificationService? = null

    fun current(context: Context): NotificationService = instance ?: synchronized(this) {
        instance ?: AndroidNotificationService(context.applicationContext).also { instance = it }
    }
}
