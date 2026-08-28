package com.clxmhcs.chinaunicom.capture

data class CaptureConfiguration(
    val targetHost: String? = null,
    val targetPath: String? = null,
    val captureAllHosts: Boolean = false,
    val additionalHosts: List<String> = emptyList(),
) {
    fun normalized(): CaptureConfiguration = copy(
        targetHost = targetHost?.trim()?.takeIf(String::isNotEmpty),
        targetPath = targetPath?.trim()?.takeIf(String::isNotEmpty),
        additionalHosts = additionalHosts
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct(),
    )
}

enum class CaptureTunnelState {
    STOPPED,
    REQUIRES_PERMISSION,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

data class CaptureStateSnapshot(
    val state: CaptureTunnelState = CaptureTunnelState.STOPPED,
    val message: String? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

sealed interface CaptureStartResult {
    data object Enqueued : CaptureStartResult
    data object RequiresPermission : CaptureStartResult
}
