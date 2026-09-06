package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshRuntimeStore
import java.time.Instant

/** Non-secret persisted runtime state for quota refresh cooldown. */
class AndroidQuotaRefreshRuntimeStore(context: Context) : QuotaRefreshRuntimeStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val dashboardSettings = AndroidDashboardRefreshSettings(context)

    override fun lastRefreshTriggeredAt(): Instant? {
        if (!preferences.contains(LAST_TRIGGERED_AT_KEY)) return null
        val epochMillis = preferences.getLong(LAST_TRIGGERED_AT_KEY, Long.MIN_VALUE)
        if (epochMillis == Long.MIN_VALUE) return null
        return runCatching { Instant.ofEpochMilli(epochMillis) }.getOrNull()
    }

    override fun recordRefreshTriggeredAt(at: Instant) {
        check(preferences.edit().putLong(LAST_TRIGGERED_AT_KEY, at.toEpochMilli()).commit()) {
            "Unable to persist quota refresh trigger time"
        }
    }

    override fun lastSingleManualSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastSingleManualSuccessAt(accountID)

    override fun lastGlobalManualSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastGlobalManualSuccessAt(accountID)

    override fun lastAutomaticSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastAutomaticSuccessAt(accountID)

    override fun lastAutomaticFailureAttemptAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastAutomaticFailureAttemptAt(accountID)

    override fun recordSingleManualSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordSingleManualSuccess(accountID, at)) { "Unable to persist single-manual refresh success" }
    }

    override fun recordGlobalManualSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordGlobalManualSuccess(accountID, at)) { "Unable to persist global-manual refresh success" }
    }

    override fun recordAutomaticSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordAutomaticSuccess(accountID, at)) { "Unable to persist automatic refresh success" }
    }

    override fun recordAutomaticFailureAttempt(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordAutomaticFailureAttempt(accountID, at)) { "Unable to persist automatic refresh failure" }
    }

    /** Source-equivalent AppStore.clearAll cleanup for the non-secret refresh cooldown marker. */
    fun clear(): Boolean {
        val legacyCleared = preferences.edit().clear().commit()
        val dashboardCleared = dashboardSettings.clear()
        return legacyCleared && dashboardCleared
    }

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.quota.refresh.runtime.v1"
        private const val LAST_TRIGGERED_AT_KEY = "lastRefreshTriggeredAt"
    }
}
