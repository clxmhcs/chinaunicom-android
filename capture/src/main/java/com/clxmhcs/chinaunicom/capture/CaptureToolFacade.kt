package com.clxmhcs.chinaunicom.capture

object CaptureToolFacade {
    fun readHistory(): List<CaptureHttpMessage> = CaptureHistoryStore.records()

    fun clearHistory() {
        CaptureHistoryStore.clear()
    }

    fun makeHarExport(): ByteArray = CaptureHarExporter.encode(readHistory())

    fun defaultHarFileName(): String = CaptureHarExporter.defaultFileName()

    fun readRootCertificateData(): ByteArray? = CaptureCertificateManager.rootCertificateData()
}
