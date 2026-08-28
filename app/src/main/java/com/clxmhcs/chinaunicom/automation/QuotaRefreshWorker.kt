package com.clxmhcs.chinaunicom.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import kotlinx.coroutines.CancellationException

/**
 * Durable M13 automatic-refresh execution entry.
 *
 * The Worker owns no China Unicom client, credentials, endpoints, parser, persistence model or
 * Widget state. It enters the single App repository transaction and then hands presentation work
 * to the notification seam. Repository commit observers publish Widget state.
 */
class QuotaRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val scheduledMinute = inputData.getInt(KEY_SCHEDULED_MINUTE, INVALID_SCHEDULED_MINUTE)
        if (scheduledMinute !in 0 until 24 * 60) return Result.failure()

        val policy = AndroidSettingsRepositories.refreshLogic(applicationContext)
            .loadWidgetRefreshPolicy()
            .normalized()
        if (!policy.automaticRefreshEnabled || scheduledMinute !in policy.scheduledMinutes) {
            return Result.success()
        }

        val repository = UnicomRepositoryProvider.create(applicationContext)
        val notificationService = NotificationServiceProvider.current(applicationContext)

        return try {
            repository.refreshAutomation(includeBalance = true)
            runCatching {
                notificationService.onAutomaticRefreshCompleted(
                    accounts = repository.appState.value.accounts,
                    scheduledMinute = scheduledMinute,
                )
            }
            AutomationCoordinator.enqueueFollowing(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                runCatching {
                    notificationService.onAutomaticRefreshFailed(
                        scheduledMinute = scheduledMinute,
                        error = error,
                    )
                }
                AutomationCoordinator.enqueueFollowing(applicationContext)
                // The current scheduled occurrence is terminal after bounded retries. Mark the
                // Work node successful so its already-appended future occurrence is not poisoned.
                Result.success()
            }
        }
    }

    companion object {
        internal const val KEY_SCHEDULED_MINUTE = "scheduledMinute"
        private const val INVALID_SCHEDULED_MINUTE = -1
        private const val MAX_RETRY_ATTEMPTS = 2
    }
}
