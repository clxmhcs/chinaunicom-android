package com.clxmhcs.chinaunicom.capture

/**
 * UI-facing bounded history over both passive TUN parsing and the M14-F loopback proxy runtime.
 *
 * This is intentionally not durable storage. Clearing history only hides messages captured before
 * the clear action; no network payload is copied into another collection, database, preference or file.
 */
object CaptureHistoryStore {
    private const val MAX_VISIBLE_RECORDS = 128
    private val lock = Any()
    private var clearedThroughEpochMillis: Long = Long.MIN_VALUE

    fun records(): List<CaptureHttpMessage> = synchronized(lock) {
        (CaptureHttpRuntime.recentMessages() + CaptureProxyHttpRuntime.recentMessages())
            .asSequence()
            .filter { it.capturedAtEpochMillis > clearedThroughEpochMillis }
            .sortedByDescending { it.capturedAtEpochMillis }
            .take(MAX_VISIBLE_RECORDS)
            .toList()
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
