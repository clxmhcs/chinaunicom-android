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
 * WorkManager owns one durable, globally serialized Work chain. This class only translates the
 * already-persisted M11 Widget refresh policy into the nearest configured wall-clock occurrence.
 * Carrier networking is intentionally absent here and remains behind UnicomRepository.
 */
object AutomationCoordinator {
    internal const val WORK_TAG = "m13-quota-automation"
    internal const val UNIQUE_WORK_NAME = "m13-quota-automation"
    private const val MIN_BACKOFF_SECONDS = 10L

    /**
     * Reconciles durable work with current settings.
     *
     * App startup uses KEEP so reopening the UI cannot cancel an in-flight background refresh.
     * A user policy change uses REPLACE so stale times are removed immediately.
     */
    fun synchronize(
        context: Context,
        replaceExisting: Boolean = false,
    ) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        val policy = AndroidSettingsRepositories.refreshLogic(app)
            .loadWidgetRefreshPolicy()
            .normalized()

        if (!policy.automaticRefreshEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val now = ZonedDateTime.now()
        val occurrence = nextPolicyAutomationOccurrence(
            now = now,
            scheduledMinutes = policy.scheduledMinutes,
            compensationMinutes = policy.compensationMinutes,
        ) ?: run {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        enqueueOccurrence(
            workManager = workManager,
            policy = policy,
            occurrence = occurrence,
            now = now,
            existingWorkPolicy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
        )
    }

    /**
     * Appends exactly one next configured occurrence to the same unique chain. This is invoked only
     * after the current scheduled occurrence reaches a terminal domain result, so automatic carrier
     * refreshes stay serialized even when Android releases delayed work after Doze.
     */
    internal fun enqueueFollowing(context: Context) {
        val app = context.applicationContext
        val policy = AndroidSettingsRepositories.refreshLogic(app)
            .loadWidgetRefreshPolicy()
            .normalized()
        if (!policy.automaticRefreshEnabled) return

        val now = ZonedDateTime.now()
        val occurrence = nextFuturePolicyAutomationOccurrence(
            now = now,
            scheduledMinutes = policy.scheduledMinutes,
        ) ?: return

        enqueueOccurrence(
            workManager = WorkManager.getInstance(app),
            policy = policy,
            occurrence = occurrence,
            now = now,
            existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
    }

    private fun enqueueOccurrence(
        workManager: WorkManager,
        policy: WidgetRefreshPolicy,
        occurrence: AutomationOccurrence,
        now: ZonedDateTime,
        existingWorkPolicy: ExistingWorkPolicy,
    ) {
        val delayMillis = Duration.between(now.toInstant(), occurrence.targetAt.toInstant())
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
            .setInputData(
                workDataOf(QuotaRefreshWorker.KEY_SCHEDULED_MINUTE to occurrence.scheduledMinute),
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            existingWorkPolicy,
            request,
        )
    }
}

internal data class AutomationOccurrence(
    val scheduledMinute: Int,
    val targetAt: ZonedDateTime,
)

/**
 * Initial synchronization may compensate a just-missed slot, matching the existing M11 policy.
 * Once the compensation window has passed, that individual slot is considered for the next day.
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

/** Strictly future occurrence for one configured wall-clock minute. */
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

/** Chooses one nearest initial occurrence across the complete normalized policy. */
internal fun nextPolicyAutomationOccurrence(
    now: ZonedDateTime,
    scheduledMinutes: List<Int>,
    compensationMinutes: Int,
): AutomationOccurrence? = scheduledMinutes
    .filter { it in 0 until 24 * 60 }
    .distinct()
    .map { minute ->
        AutomationOccurrence(
            scheduledMinute = minute,
            targetAt = nextAutomationOccurrence(now, minute, compensationMinutes),
        )
    }
    .minWithOrNull(compareBy<AutomationOccurrence>({ it.targetAt.toInstant() }, { it.scheduledMinute }))

/** Chooses one strictly future occurrence across all configured slots. */
internal fun nextFuturePolicyAutomationOccurrence(
    now: ZonedDateTime,
    scheduledMinutes: List<Int>,
): AutomationOccurrence? = scheduledMinutes
    .filter { it in 0 until 24 * 60 }
    .distinct()
    .map { minute ->
        AutomationOccurrence(
            scheduledMinute = minute,
            targetAt = nextFutureAutomationOccurrence(now, minute),
        )
    }
    .minWithOrNull(compareBy<AutomationOccurrence>({ it.targetAt.toInstant() }, { it.scheduledMinute }))
