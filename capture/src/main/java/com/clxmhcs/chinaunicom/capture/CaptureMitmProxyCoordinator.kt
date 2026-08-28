package com.clxmhcs.chinaunicom.capture

/**
 * State-only HTTPS MITM coordinator for M14-D.
 *
 * Production capability switches are false, so this coordinator cannot enter READY/RUNNING in the
 * shipped M14-D build. It exists to lock down the same certificate/configuration orchestration seam
 * present in the iOS source before a real relay/signing implementation is introduced.
 */
internal class CaptureMitmProxyCoordinator(
    private val hostCertificateGenerationAvailable: Boolean = CaptureTlsCapabilities.HOST_CERTIFICATE_GENERATION_AVAILABLE,
    private val activeTlsDecryptionAvailable: Boolean = CaptureTlsCapabilities.ACTIVE_TLS_DECRYPTION_AVAILABLE,
) {
    private var configuration = CaptureMitmConfiguration()
    private var snapshot = CaptureMitmProxySnapshot()

    fun snapshot(): CaptureMitmProxySnapshot = snapshot

    fun prepare(hosts: List<String>): CaptureMitmProxySnapshot {
        snapshot = CaptureMitmProxySnapshot(state = CaptureMitmProxyState.PREPARING)

        if (!CaptureCertificateManager.isTrustConfirmed()) {
            return fail("抓包根证书尚未由用户确认完成信任配置")
        }
        if (!hostCertificateGenerationAvailable) {
            return fail("动态站点证书生成尚未实现")
        }
        if (!activeTlsDecryptionAvailable) {
            return fail("TLS 解密转发尚未实现")
        }

        val normalizedHosts = hosts
            .map { it.trim().lowercase() }
            .filter(String::isNotEmpty)
            .distinct()
        configuration = CaptureMitmConfiguration(
            enabled = true,
            interceptHttps = true,
            includedHosts = normalizedHosts,
        ).normalized()
        snapshot = CaptureMitmProxySnapshot(
            state = CaptureMitmProxyState.READY,
            interceptedHosts = normalizedHosts,
        )
        return snapshot
    }

    fun start(): CaptureMitmProxySnapshot {
        if (snapshot.state != CaptureMitmProxyState.READY) {
            return fail("HTTPS MITM 代理尚未完成准备")
        }
        snapshot = snapshot.copy(state = CaptureMitmProxyState.RUNNING, message = null)
        return snapshot
    }

    fun stop(): CaptureMitmProxySnapshot {
        configuration = configuration.copy(enabled = false)
        snapshot = CaptureMitmProxySnapshot(state = CaptureMitmProxyState.STOPPED)
        return snapshot
    }

    fun shouldIntercept(host: String): Boolean =
        snapshot.state == CaptureMitmProxyState.RUNNING && configuration.shouldIntercept(host)

    private fun fail(message: String): CaptureMitmProxySnapshot {
        snapshot = CaptureMitmProxySnapshot(
            state = CaptureMitmProxyState.FAILED,
            interceptedHosts = snapshot.interceptedHosts,
            message = message,
        )
        return snapshot
    }
}

internal object CaptureMitmRuntime {
    private val coordinator = CaptureMitmProxyCoordinator()

    fun snapshot(): CaptureMitmProxySnapshot = coordinator.snapshot()

    fun reset() {
        coordinator.stop()
    }
}
