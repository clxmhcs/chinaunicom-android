package com.clxmhcs.chinaunicom.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

class CaptureVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var packetReader: CaptureTunPacketReader? = null
    private val store by lazy { CaptureRuntimeStore.create(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture("抓包已停止")
            ACTION_START -> startCapture()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopCapture("系统已撤销 VPN 授权")
        super.onRevoke()
    }

    override fun onDestroy() {
        closePacketReader()
        closeTunnelInterface()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val current = store.readState().state
        if (current != CaptureTunnelState.FAILED && current != CaptureTunnelState.REQUIRES_PERMISSION) {
            store.writeState(CaptureStateSnapshot(CaptureTunnelState.STOPPED, "抓包服务已结束"))
        }
        super.onDestroy()
    }

    private fun startCapture() {
        if (tunnelInterface != null) {
            store.writeState(CaptureStateSnapshot(CaptureTunnelState.RUNNING, RUNNING_MESSAGE))
            return
        }

        if (VpnService.prepare(this) != null) {
            store.writeState(
                CaptureStateSnapshot(
                    CaptureTunnelState.REQUIRES_PERMISSION,
                    "需要先完成系统 VPN 授权",
                ),
            )
            stopSelf()
            return
        }

        startAsForeground()
        store.writeState(CaptureStateSnapshot(CaptureTunnelState.STARTING, "正在建立 VPN 捕获接口"))

        try {
            val descriptor = Builder()
                .setSession(NOTIFICATION_TITLE)
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
                .addRoute(SAFE_ROUTE_ADDRESS, SAFE_ROUTE_PREFIX_LENGTH)
                .setBlocking(false)
                .establish()
                ?: error("系统未返回可用的 VPN 接口")

            tunnelInterface = descriptor
            CapturePacketRuntime.beginSession()
            CaptureHttpRuntime.beginSession()
            packetReader = CaptureTunPacketReader(
                tunnelInterface = descriptor,
                onPacket = { packet -> CapturePacketRuntime.accept(packet) },
                onTcpSegment = { segment -> CaptureHttpRuntime.accept(segment) },
                onFailure = ::handlePacketReaderFailure,
            ).also { it.start() }
            store.writeState(CaptureStateSnapshot(CaptureTunnelState.RUNNING, RUNNING_MESSAGE))
        } catch (error: Exception) {
            closePacketReader()
            closeTunnelInterface()
            store.writeState(
                CaptureStateSnapshot(
                    CaptureTunnelState.FAILED,
                    error.message?.take(180) ?: "VPN 捕获接口启动失败",
                ),
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handlePacketReaderFailure(error: Throwable) {
        if (store.readState().state == CaptureTunnelState.STOPPING) return
        store.writeState(
            CaptureStateSnapshot(
                CaptureTunnelState.FAILED,
                error.message?.take(180) ?: "TUN 包读取失败",
            ),
        )
        closePacketReader()
        closeTunnelInterface()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopCapture(message: String) {
        store.writeState(CaptureStateSnapshot(CaptureTunnelState.STOPPING, "正在停止抓包"))
        closePacketReader()
        closeTunnelInterface()
        store.writeState(CaptureStateSnapshot(CaptureTunnelState.STOPPED, message))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closePacketReader() {
        packetReader?.close()
        packetReader = null
    }

    private fun closeTunnelInterface() {
        runCatching { tunnelInterface?.close() }
        tunnelInterface = null
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "抓包 VPN 运行状态"
                setShowBadge(false)
            },
        )

        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText("VPN 捕获骨架运行中")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)

        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(pendingIntent)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.clxmhcs.chinaunicom.capture.START"
        const val ACTION_STOP = "com.clxmhcs.chinaunicom.capture.STOP"

        internal const val TUN_ADDRESS = "192.0.2.2"
        internal const val TUN_PREFIX_LENGTH = 24
        internal const val SAFE_ROUTE_ADDRESS = "192.0.2.0"
        internal const val SAFE_ROUTE_PREFIX_LENGTH = 24
        internal const val MTU = 1500

        private const val NOTIFICATION_CHANNEL_ID = "chinaunicom_capture_vpn_v1"
        private const val NOTIFICATION_CHANNEL_NAME = "联通余量抓包 VPN"
        private const val NOTIFICATION_TITLE = "联通余量抓包工具"
        private const val NOTIFICATION_ID = 1401
        private const val RUNNING_MESSAGE = "VPN 已运行；M14-C 可重组测试网段明文 HTTP 头，尚未启用全量转发或 HTTPS 解密"
    }
}
