package com.clxmhcs.chinaunicom.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.clxmhcs.chinaunicom.MainActivity
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationProfile
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationSlot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Android implementation of the M13 notification boundary. */
internal class AndroidNotificationService(
    context: Context,
) : NotificationService {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val settingsRepository = AndroidSettingsRepositories.shortcutNotifications(app)
    private val usageStore = AutomationNotificationUsageStore(app)

    init {
        ensureChannel()
    }

    override suspend fun onAutomaticRefreshCompleted(
        accounts: List<UnicomAccount>,
        scheduledMinute: Int,
    ) {
        if (!notificationsAllowed()) return
        val accountIndex = accounts.associateBy(UnicomAccount::id)
        configuredProfiles().forEach { profile ->
            val account = accountIndex[profile.accountID] ?: return@forEach
            val error = account.lastErrorMessage?.trim()?.takeIf(String::isNotEmpty)
            if (error != null) {
                if (profile.settings.notifyOnFailure) {
                    post(
                        profile = profile,
                        account = account,
                        scheduledMinute = scheduledMinute,
                        failure = true,
                        content = AutomationNotificationContentRenderer.failure(account, error),
                    )
                }
                return@forEach
            }

            val content = AutomationNotificationContentRenderer.render(
                account = account,
                settings = profile.settings,
                usage = usageStore.contextFor(account),
            ) ?: return@forEach
            post(
                profile = profile,
                account = account,
                scheduledMinute = scheduledMinute,
                failure = false,
                content = content,
            )
        }
    }

    override suspend fun onAutomaticRefreshFailed(
        scheduledMinute: Int,
        error: Throwable,
    ) {
        if (!notificationsAllowed()) return
        val accountIndex = runCatching {
            UnicomRepositoryProvider.create(app).appState.value.accounts.associateBy(UnicomAccount::id)
        }.getOrDefault(emptyMap())
        configuredProfiles()
            .filter { it.settings.notifyOnFailure }
            .forEach { profile ->
                val account = accountIndex[profile.accountID]
                post(
                    profile = profile,
                    account = account,
                    scheduledMinute = scheduledMinute,
                    failure = true,
                    content = AutomationNotificationContentRenderer.failure(
                        account = account,
                        message = error.message ?: error::class.java.simpleName,
                    ),
                )
            }
    }

    private fun configuredProfiles(): List<ShortcutNotificationProfile> =
        settingsRepository.profiles.value.values
            .filter { it.slot != ShortcutNotificationSlot.NONE }
            .sortedBy { it.slot.ordinal }

    private fun post(
        profile: ShortcutNotificationProfile,
        account: UnicomAccount?,
        scheduledMinute: Int,
        failure: Boolean,
        content: AutomationNotificationContent,
    ) {
        val slotLabel = profile.slot.title
        val title = if (slotLabel.isBlank()) content.title else "[$slotLabel] ${content.title}"
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app,
            profile.accountID.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(content.body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(if (failure) Notification.CATEGORY_ERROR else Notification.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setWhen((account?.lastUpdatedAt ?: Instant.now()).toEpochMilli())
            .setShowWhen(true)

        if (content.subtitle.isNotBlank()) builder.setSubText(content.subtitle)
        manager.notify(notificationID(profile.accountID, scheduledMinute, failure), builder.build())
    }

    private fun notificationID(accountID: UUID, scheduledMinute: Int, failure: Boolean): Int {
        val date = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        var result = accountID.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + scheduledMinute
        result = 31 * result + if (failure) 1 else 0
        return result
    }

    private fun notificationsAllowed(): Boolean {
        val runtimeAllowed = Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeAllowed && manager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "联通余量自动通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "自动刷新完成后显示已绑定 A/B/C/D 号码的流量、语音和余额"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        internal const val CHANNEL_ID = "chinaunicom_quota_automation_v1"
        internal const val GROUP_KEY = "chinaunicom.quota.automation"
    }
}
