package com.clxmhcs.chinaunicom.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.WidgetRefreshPolicy
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * M13 automatic-refresh scheduler authority.
 *
 * WorkManager owns durable execution. This class only translates the already-persisted M11 Widget
 * refresh policy into one queued occurrence per configured wall-clock slot. Carrier networking is
 * intentionally absent here and remains behind UnicomRepository.
 */
object AutomationCoordinator {
    internal const val WORK_TAG = "m13-quota-automation"
    private const val UNIQUE_WORK_PREFIX = "m13-quota-automation"
    private const val MIN_BACKOFF_SECONDS = 10L

    fun synchronize(context: Context) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        val policy = AndroidSettingsRepositories.refreshLogic(app)
            .loadWidgetRefreshPolicy()
            .normalized()

        workManager.cancelAllWorkByTag(WORK_TAG)
        if (!policy.automaticRefreshEnabled) return

        val now = ZonedDateTime.now()
        policy.scheduledMinutes.forEach { scheduledMinute ->
            val target = nextAutomationOccurrence(
                now = now,
                scheduledMinute = scheduledMinute,
                compensationMinutes = policy.compensationMinutes,
            )
            enqueueOccurrence(
                workManager = workManager,
                policy = policy,
                scheduledMinute = scheduledMinute,
                target = target,
                now = now,
            )
        }
    }

    /** Called only after a scheduled occurrence reaches a terminal result. */
    internal fun enqueueFollowing(
        context: Context,
        scheduledMinute: Int,
    ) {
        val app = context.applicationContext
        val policy = AndroidSettingsRepositories.refreshLogic(app)
            .loadWidgetRefreshPolicy()
            .normalized()
        if (!policy.automaticRefreshEnabled || scheduledMinute !in policy.scheduledMinutes) return

        val now = ZonedDateTime.now()
        val target = nextFutureAutomationOccurrence(now, scheduledMinute)
        enqueueOccurrence(
            workManager = WorkManager.getInstance(app),
            policy = policy,
            scheduledMinute = scheduledMinute,
            target = target,
            now = now,
        )
    }

    private fun enqueueOccurrence(
        workManager: WorkManager,
        policy: WidgetRefreshPolicy,
        scheduledMinute: Int,
        target: ZonedDateTime,
        now: ZonedDateTime,
    ) {
        val delayMillis = Duration.between(now.toInstant(), target.toInstant())
            .toMillis()
            .coerceAtLeast(0L)
        val backoffSeconds = policy.failureRetrySeconds.toLong().coerceAtLeast(MIN_BACKOFF_SECONDS)
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, backoffSeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(QuotaRefreshWorker.KEY_SCHEDULED_MINUTE to scheduledMinute))
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            occurrenceWorkName(scheduledMinute, target),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun occurrenceWorkName(scheduledMinute: Int, target: ZonedDateTime): String =
        "$UNIQUE_WORK_PREFIX-${target.toInstant().epochSecond}-$scheduledMinute"
}

/**
 * Initial synchronization may compensate a just-missed slot, matching the existing M11 policy.
 * Once the compensation window has passed, the slot is queued for the next local day.
 */
internal fun nextAutomationOccurrence(
    now: ZonedDateTime,
    scheduledMinute: Int,
    compensationMinutes: Int,
): ZonedDateTime {
    val minute = scheduledMinute.coerceIn(0, 24 * 60 - 1)
    val compensation = compensationMinutes.coerceAtLeast(0)
    val todayTarget = now.toLocalDate()
        .atStartOfDay(now.zone)
        .plusMinutes(minute.toLong())
    val compensationEnd = todayTarget.plusMinutes(compensation.toLong())

    return when {
        now.isBefore(todayTarget) -> todayTarget
        !now.isAfter(compensationEnd) -> now
        else -> todayTarget.plusDays(1)
    }
}

/** Strictly future occurrence used after a Worker finishes, preventing same-window reschedule loops. */
internal fun nextFutureAutomationOccurrence(
    now: ZonedDateTime,
    scheduledMinute: Int,
): ZonedDateTime {
    val minute = scheduledMinute.coerceIn(0, 24 * 60 - 1)
    val todayTarget = now.toLocalDate()
        .atStartOfDay(now.zone)
        .plusMinutes(minute.toLong())
    return if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
}
