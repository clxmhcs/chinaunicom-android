package com.clxmhcs.chinaunicom.capture

import android.content.Context
import android.content.Intent
import android.net.VpnService
import java.io.File

object CaptureVpnController {
    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)

    fun isPermissionGranted(context: Context): Boolean = permissionIntent(context) == null

    fun start(context: Context): CaptureStartResult {
        val app = context.applicationContext
        val store = CaptureRuntimeStore.create(app)
        if (VpnService.prepare(app) != null) {
            store.writeState(
                CaptureStateSnapshot(
                    state = CaptureTunnelState.REQUIRES_PERMISSION,
                    message = "需要先完成系统 VPN 授权",
                ),
            )
            return CaptureStartResult.RequiresPermission
        }

        app.startForegroundService(
            Intent(app, CaptureVpnService::class.java)
                .setAction(CaptureVpnService.ACTION_START),
        )
        return CaptureStartResult.Enqueued
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        app.startService(
            Intent(app, CaptureVpnService::class.java)
                .setAction(CaptureVpnService.ACTION_STOP),
        )
    }

    fun readState(context: Context): CaptureStateSnapshot =
        CaptureRuntimeStore.create(context).readState()

    fun readConfiguration(context: Context): CaptureConfiguration =
        CaptureRuntimeStore.create(context).readConfiguration()

    fun saveConfiguration(context: Context, configuration: CaptureConfiguration) {
        CaptureRuntimeStore.create(context).writeConfiguration(configuration)
    }

    fun readPacketSession(): CapturePacketSessionSnapshot = CapturePacketRuntime.snapshot()

    fun readRecentPacketMetadata(): List<CapturePacketMetadata> = CapturePacketRuntime.recentPackets()

    fun readHttpSession(): CaptureHttpSessionSnapshot {
        val passive = CaptureHttpRuntime.snapshot()
        val proxy = CaptureProxyHttpRuntime.snapshot()
        return CaptureHttpSessionSnapshot(
            messageCount = passive.messageCount + proxy.messageCount,
            requestCount = passive.requestCount + proxy.requestCount,
            responseCount = passive.responseCount + proxy.responseCount,
            droppedStreamCount = passive.droppedStreamCount + proxy.droppedStreamCount,
        )
    }

    fun readRecentHttpMessages(): List<CaptureHttpMessage> =
        (CaptureHttpRuntime.recentMessages() + CaptureProxyHttpRuntime.recentMessages())
            .sortedByDescending { it.capturedAtEpochMillis }
            .take(128)

    fun readMitmConfiguration(context: Context): CaptureMitmConfiguration =
        CaptureTlsConfigurationStore.create(context).read()

    fun saveMitmConfiguration(context: Context, configuration: CaptureMitmConfiguration) {
        CaptureTlsConfigurationStore.create(context).write(configuration)
    }

    fun readCertificateSnapshot(): CaptureCertificateSnapshot = CaptureCertificateManager.snapshot()

    fun certificateInstallationInstructions(): List<String> =
        CaptureCertificateManager.installationInstructions()

    fun registerRootCertificate(data: ByteArray) {
        CaptureCertificateManager.registerRootCertificate(data)
    }

    fun makeInstallableRootCertificate(context: Context): File =
        CaptureCertificateManager.makeInstallableCertificateFile(context)

    fun confirmRootCertificateTrustByUser() {
        CaptureCertificateManager.confirmTrustEnabledByUser()
    }

    fun revokeRootCertificateTrustConfirmation() {
        CaptureCertificateManager.revokeUserConfirmation()
    }

    fun resetRootCertificate(context: Context) {
        CaptureCertificateManager.reset(context)
        CaptureMitmRuntime.reset()
    }

    fun readMitmProxySnapshot(): CaptureMitmProxySnapshot = CaptureMitmRuntime.snapshot()
}
