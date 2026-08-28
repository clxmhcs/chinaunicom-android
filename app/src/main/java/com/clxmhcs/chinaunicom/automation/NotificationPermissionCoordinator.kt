package com.clxmhcs.chinaunicom.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationSlot
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories

/**
 * Activity-facing permission request seam. Background workers never request permissions or launch UI.
 */
object NotificationPermissionCoordinator {
    @Volatile
    private var requester: (() -> Unit)? = null

    fun attach(request: () -> Unit) {
        requester = request
    }

    fun detach(request: () -> Unit) {
        if (requester === request) requester = null
    }

    fun requestFromUserAction() {
        requester?.invoke()
    }

    fun shouldRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val app = context.applicationContext
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return AndroidSettingsRepositories.shortcutNotifications(app)
            .profiles.value.values.any { it.slot != ShortcutNotificationSlot.NONE }
    }
}
