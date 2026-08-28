package com.clxmhcs.chinaunicom.capture

data class CaptureMitmConfiguration(
    val enabled: Boolean = false,
    val interceptHttps: Boolean = true,
    val excludedHosts: List<String> = emptyList(),
    val includedHosts: List<String> = emptyList(),
) {
    fun normalized(): CaptureMitmConfiguration = copy(
        excludedHosts = normalizeHosts(excludedHosts),
        includedHosts = normalizeHosts(includedHosts),
    )

    fun shouldIntercept(host: String): Boolean {
        val normalizedHost = normalizeHost(host)
        if (!enabled || !interceptHttps || normalizedHost.isEmpty()) return false
        val normalized = normalized()
        if (normalized.excludedHosts.contains(normalizedHost)) return false
        return normalized.includedHosts.isEmpty() || normalized.includedHosts.contains(normalizedHost)
    }

    private fun normalizeHosts(hosts: List<String>): List<String> = hosts
        .map(::normalizeHost)
        .filter(String::isNotEmpty)
        .distinct()

    private fun normalizeHost(host: String): String = host.trim().lowercase()
}

enum class CaptureCertificateState {
    MISSING,
    GENERATED,
    INSTALLATION_READY,
    USER_CONFIRMED_TRUSTED,
}

data class CaptureCertificateSnapshot(
    val state: CaptureCertificateState = CaptureCertificateState.MISSING,
    val hasRootCertificate: Boolean = false,
    val userConfirmedTrusted: Boolean = false,
    val hostCertificateGenerationAvailable: Boolean = CaptureTlsCapabilities.HOST_CERTIFICATE_GENERATION_AVAILABLE,
    val activeTlsDecryptionAvailable: Boolean = CaptureTlsCapabilities.ACTIVE_TLS_DECRYPTION_AVAILABLE,
)

enum class CaptureMitmProxyState {
    STOPPED,
    PREPARING,
    READY,
    RUNNING,
    FAILED,
}

data class CaptureMitmProxySnapshot(
    val state: CaptureMitmProxyState = CaptureMitmProxyState.STOPPED,
    val interceptedHosts: List<String> = emptyList(),
    val message: String? = null,
)

/**
 * M14-D capability switches intentionally remain false.
 *
 * The iOS source currently exposes a MITM architecture but its real host-certificate generator is
 * still unavailable. Android must not claim TLS interception readiness until a real signing path,
 * transport relay, and device acceptance are implemented and independently gated.
 */
object CaptureTlsCapabilities {
    const val HOST_CERTIFICATE_GENERATION_AVAILABLE: Boolean = false
    const val ACTIVE_TLS_DECRYPTION_AVAILABLE: Boolean = false
}

/** Passive eligibility check only. This does not open sockets or decrypt TLS. */
class CaptureTlsInspector {
    fun inspect(host: String, port: Int): Boolean = host.trim().isNotEmpty() && port == HTTPS_PORT

    private companion object {
        const val HTTPS_PORT = 443
    }
}
