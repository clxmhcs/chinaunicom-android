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

    /** Source-equivalent AppStore.clearAll cleanup for the non-secret refresh cooldown marker. */
    fun clear(): Boolean = preferences.edit().remove(LAST_TRIGGERED_AT_KEY).commit()

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.quota.refresh.runtime.v1"
        private const val LAST_TRIGGERED_AT_KEY = "lastRefreshTriggeredAt"
    }
}
