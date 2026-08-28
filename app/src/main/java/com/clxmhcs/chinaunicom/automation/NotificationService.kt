package com.clxmhcs.chinaunicom.automation

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.UnicomAccount

/**
 * Notification delivery boundary for M13.
 *
 * M13-A deliberately installs a no-op implementation: scheduling and refresh authority land
 * first, while Android notification channels/templates/permission handling are added in M13-B.
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

/** Process-facing seam so M13-B can install Android notification delivery without touching Worker networking. */
object NotificationServiceProvider {
    fun current(context: Context): NotificationService {
        context.applicationContext
        return NotificationService.NONE
    }
}
