package com.clxmhcs.chinaunicom.data

import android.content.Context
import java.time.Instant
import java.util.UUID

/**
 * Android counterpart of the current iOS Dashboard*RefreshSettings UserDefaults state.
 *
 * This is intentionally separate from AppRefreshLogic schema-3: iOS also keeps the three
 * dashboard-only timing preferences and their per-account runtime timestamps outside the schema-3
 * policy document. The data is non-secret and lives in app-private SharedPreferences.
 */
data class DashboardRefreshTimingConfig(
    val singleManualIntervalMinutes: Int,
    val globalManualIntervalMinutes: Int,
    val automaticFailureRetryMinutes: Int,
)

class AndroidDashboardRefreshSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): DashboardRefreshTimingConfig = DashboardRefreshTimingConfig(
        singleManualIntervalMinutes = normalized(
            preferences.getInt(SINGLE_MANUAL_INTERVAL_KEY, DEFAULT_SINGLE_MANUAL_INTERVAL_MINUTES),
            ALLOWED_SINGLE_MANUAL_INTERVAL_MINUTES,
            DEFAULT_SINGLE_MANUAL_INTERVAL_MINUTES,
        ),
        globalManualIntervalMinutes = normalized(
            preferences.getInt(GLOBAL_MANUAL_INTERVAL_KEY, DEFAULT_GLOBAL_MANUAL_INTERVAL_MINUTES),
            ALLOWED_GLOBAL_MANUAL_INTERVAL_MINUTES,
            DEFAULT_GLOBAL_MANUAL_INTERVAL_MINUTES,
        ),
        automaticFailureRetryMinutes = normalized(
            preferences.getInt(AUTOMATIC_FAILURE_RETRY_KEY, DEFAULT_AUTOMATIC_FAILURE_RETRY_MINUTES),
            ALLOWED_AUTOMATIC_FAILURE_RETRY_MINUTES,
            DEFAULT_AUTOMATIC_FAILURE_RETRY_MINUTES,
        ),
    )

    fun saveSingleManualIntervalMinutes(value: Int): Boolean = preferences.edit()
        .putInt(
            SINGLE_MANUAL_INTERVAL_KEY,
            normalized(value, ALLOWED_SINGLE_MANUAL_INTERVAL_MINUTES, DEFAULT_SINGLE_MANUAL_INTERVAL_MINUTES),
        )
        .commit()

    fun saveGlobalManualIntervalMinutes(value: Int): Boolean = preferences.edit()
        .putInt(
            GLOBAL_MANUAL_INTERVAL_KEY,
            normalized(value, ALLOWED_GLOBAL_MANUAL_INTERVAL_MINUTES, DEFAULT_GLOBAL_MANUAL_INTERVAL_MINUTES),
        )
        .commit()

    fun saveAutomaticFailureRetryMinutes(value: Int): Boolean = preferences.edit()
        .putInt(
            AUTOMATIC_FAILURE_RETRY_KEY,
            normalized(value, ALLOWED_AUTOMATIC_FAILURE_RETRY_MINUTES, DEFAULT_AUTOMATIC_FAILURE_RETRY_MINUTES),
        )
        .commit()

    /** Matches iOS restore-default behavior: reset preferences, keep real historical timestamps. */
    fun restoreDefaults(): Boolean = preferences.edit()
        .putInt(SINGLE_MANUAL_INTERVAL_KEY, DEFAULT_SINGLE_MANUAL_INTERVAL_MINUTES)
        .putInt(GLOBAL_MANUAL_INTERVAL_KEY, DEFAULT_GLOBAL_MANUAL_INTERVAL_MINUTES)
        .putInt(AUTOMATIC_FAILURE_RETRY_KEY, DEFAULT_AUTOMATIC_FAILURE_RETRY_MINUTES)
        .commit()

    fun lastSingleManualSuccessAt(accountID: UUID): Instant? = instant(SINGLE_MANUAL_SUCCESS_PREFIX, accountID)
    fun lastGlobalManualSuccessAt(accountID: UUID): Instant? = instant(GLOBAL_MANUAL_SUCCESS_PREFIX, accountID)
    fun lastAutomaticSuccessAt(accountID: UUID): Instant? = instant(AUTOMATIC_SUCCESS_PREFIX, accountID)
    fun lastAutomaticFailureAttemptAt(accountID: UUID): Instant? = instant(AUTOMATIC_FAILURE_PREFIX, accountID)

    fun latestQuotaSuccessAt(accountID: UUID, accountLastUpdatedAt: Instant?): Instant? = listOfNotNull(
        accountLastUpdatedAt,
        lastSingleManualSuccessAt(accountID),
        lastGlobalManualSuccessAt(accountID),
        lastAutomaticSuccessAt(accountID),
    ).maxOrNull()

    fun recordSingleManualSuccess(accountID: UUID, at: Instant): Boolean =
        putInstant(SINGLE_MANUAL_SUCCESS_PREFIX, accountID, at)

    fun recordGlobalManualSuccess(accountID: UUID, at: Instant): Boolean =
        putInstant(GLOBAL_MANUAL_SUCCESS_PREFIX, accountID, at)

    fun recordAutomaticSuccess(accountID: UUID, at: Instant): Boolean = preferences.edit()
        .putLong(key(AUTOMATIC_SUCCESS_PREFIX, accountID), at.toEpochMilli())
        .remove(key(AUTOMATIC_FAILURE_PREFIX, accountID))
        .commit()

    fun recordAutomaticFailureAttempt(accountID: UUID, at: Instant): Boolean =
        putInstant(AUTOMATIC_FAILURE_PREFIX, accountID, at)

    /** Full account wipe cleanup; configuration and runtime markers are both non-secret. */
    fun clear(): Boolean = preferences.edit().clear().commit()

    private fun instant(prefix: String, accountID: UUID): Instant? {
        val key = key(prefix, accountID)
        if (!preferences.contains(key)) return null
        val value = preferences.getLong(key, Long.MIN_VALUE)
        if (value == Long.MIN_VALUE) return null
        return runCatching { Instant.ofEpochMilli(value) }.getOrNull()
    }

    private fun putInstant(prefix: String, accountID: UUID, at: Instant): Boolean = preferences.edit()
        .putLong(key(prefix, accountID), at.toEpochMilli())
        .commit()

    private fun key(prefix: String, accountID: UUID): String = prefix + accountID.toString()

    private fun normalized(value: Int, allowed: List<Int>, fallback: Int): Int =
        value.takeIf(allowed::contains) ?: fallback

    companion object {
        const val DEFAULT_SINGLE_MANUAL_INTERVAL_MINUTES = 10
        const val DEFAULT_GLOBAL_MANUAL_INTERVAL_MINUTES = 30
        const val DEFAULT_AUTOMATIC_FAILURE_RETRY_MINUTES = 5

        val ALLOWED_SINGLE_MANUAL_INTERVAL_MINUTES = listOf(7, 10, 15, 20, 25, 30)
        val ALLOWED_GLOBAL_MANUAL_INTERVAL_MINUTES = listOf(30, 60, 90, 120, 150, 180)
        val ALLOWED_AUTOMATIC_FAILURE_RETRY_MINUTES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60)
        val ALLOWED_AUTOMATIC_MINIMUM_INTERVAL_MINUTES = listOf(30, 60, 90, 120, 150, 180)
        val ALLOWED_ACCOUNT_GAP_SECONDS = listOf(1, 2, 3, 5, 8, 10)

        private const val PREFERENCES_NAME = "chinaunicom.dashboard.refresh.settings.v1"
        private const val SINGLE_MANUAL_INTERVAL_KEY = "logoManualRefreshIntervalMinutes"
        private const val GLOBAL_MANUAL_INTERVAL_KEY = "globalManualRefreshIntervalMinutes"
        private const val AUTOMATIC_FAILURE_RETRY_KEY = "automaticFailureRetryMinutes"
        private const val SINGLE_MANUAL_SUCCESS_PREFIX = "logoManualRefreshSuccess."
        private const val GLOBAL_MANUAL_SUCCESS_PREFIX = "globalManualRefreshSuccess."
        private const val AUTOMATIC_SUCCESS_PREFIX = "automaticRefreshSuccess."
        private const val AUTOMATIC_FAILURE_PREFIX = "automaticRefreshFailureAttempt."
    }
}
