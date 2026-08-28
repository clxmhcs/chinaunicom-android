package com.clxmhcs.chinaunicom.capture

import android.content.Context

internal class CaptureRuntimeStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun readConfiguration(): CaptureConfiguration = CaptureConfiguration(
        targetHost = preferences.getString(KEY_TARGET_HOST, null),
        targetPath = preferences.getString(KEY_TARGET_PATH, null),
        captureAllHosts = preferences.getBoolean(KEY_CAPTURE_ALL_HOSTS, false),
        additionalHosts = preferences.getString(KEY_ADDITIONAL_HOSTS, null)
            .orEmpty()
            .split(HOST_SEPARATOR)
            .filter(String::isNotEmpty),
    ).normalized()

    fun writeConfiguration(configuration: CaptureConfiguration) {
        val normalized = configuration.normalized()
        preferences.edit()
            .putString(KEY_TARGET_HOST, normalized.targetHost)
            .putString(KEY_TARGET_PATH, normalized.targetPath)
            .putBoolean(KEY_CAPTURE_ALL_HOSTS, normalized.captureAllHosts)
            .putString(KEY_ADDITIONAL_HOSTS, normalized.additionalHosts.joinToString(HOST_SEPARATOR))
            .apply()
    }

    fun readState(): CaptureStateSnapshot {
        val rawState = preferences.getString(KEY_STATE, CaptureTunnelState.STOPPED.name)
        val state = runCatching { CaptureTunnelState.valueOf(rawState.orEmpty()) }
            .getOrDefault(CaptureTunnelState.STOPPED)
        return CaptureStateSnapshot(
            state = state,
            message = preferences.getString(KEY_MESSAGE, null),
            updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L)
                .takeIf { it > 0L }
                ?: System.currentTimeMillis(),
        )
    }

    fun writeState(snapshot: CaptureStateSnapshot) {
        preferences.edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putString(KEY_MESSAGE, snapshot.message)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtEpochMillis)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.capture.runtime.v1"
        private const val HOST_SEPARATOR = "\u001F"
        private const val KEY_TARGET_HOST = "target_host"
        private const val KEY_TARGET_PATH = "target_path"
        private const val KEY_CAPTURE_ALL_HOSTS = "capture_all_hosts"
        private const val KEY_ADDITIONAL_HOSTS = "additional_hosts"
        private const val KEY_STATE = "state"
        private const val KEY_MESSAGE = "message"
        private const val KEY_UPDATED_AT = "updated_at"

        fun create(context: Context): CaptureRuntimeStore = CaptureRuntimeStore(context)
    }
}
