package com.clxmhcs.chinaunicom.widget

/**
 * Cross-module contract for user-initiated Widget refresh taps.
 *
 * The Widget module only emits an explicit in-app broadcast. The App module owns the receiver and
 * routes the request into the process-wide UnicomRepository authority; no carrier endpoint or
 * credential handling is allowed here.
 */
object WidgetRefreshActionContract {
    const val RECEIVER_CLASS_NAME = "com.clxmhcs.chinaunicom.data.WidgetManualRefreshReceiver"

    const val ACTION_REFRESH_SINGLE = "com.clxmhcs.chinaunicom.widget.action.REFRESH_SINGLE"
    const val ACTION_REFRESH_DUAL_LEFT = "com.clxmhcs.chinaunicom.widget.action.REFRESH_DUAL_LEFT"
    const val ACTION_REFRESH_DUAL_RIGHT = "com.clxmhcs.chinaunicom.widget.action.REFRESH_DUAL_RIGHT"
}
