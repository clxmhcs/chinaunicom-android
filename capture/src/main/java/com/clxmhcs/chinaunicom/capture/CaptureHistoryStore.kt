package com.clxmhcs.chinaunicom.capture

/**
 * UI-facing M14-E history view over M14-C's bounded process-local message ring.
 *
 * This is intentionally not durable storage. Clearing history only hides messages captured before
 * the clear action; no network payload is copied into another collection, database, preference or file.
 */
object CaptureHistoryStore {
    private val lock = Any()
    private var clearedThroughEpochMillis: Long = Long.MIN_VALUE

    fun records(): List<CaptureHttpMessage> = synchronized(lock) {
        CaptureHttpRuntime.recentMessages()
            .filter { it.capturedAtEpochMillis > clearedThroughEpochMillis }
            .sortedByDescending { it.capturedAtEpochMillis }
    }

    fun clear(nowEpochMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            clearedThroughEpochMillis = nowEpochMillis
        }
    }

    fun resetVisibility() {
        synchronized(lock) {
            clearedThroughEpochMillis = Long.MIN_VALUE
        }
    }
}
