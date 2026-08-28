package com.clxmhcs.chinaunicom.capture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTlsArchitectureTest {
    @After
    fun tearDown() {
        CaptureCertificateManager.reset()
    }

    @Test
    fun mitmConfigurationNormalizesAndHonorsDisabledAndExcludedHosts() {
        val disabled = CaptureMitmConfiguration(
            enabled = false,
            includedHosts = listOf(" API.EXAMPLE.COM "),
        )
        assertFalse(disabled.shouldIntercept("api.example.com"))

        val enabled = CaptureMitmConfiguration(
            enabled = true,
            interceptHttps = true,
            excludedHosts = listOf(" Private.Example.com "),
            includedHosts = listOf(" API.EXAMPLE.COM ", "api.example.com"),
        ).normalized()

        assertEquals(listOf("private.example.com"), enabled.excludedHosts)
        assertEquals(listOf("api.example.com"), enabled.includedHosts)
        assertTrue(enabled.shouldIntercept(" API.EXAMPLE.COM "))
        assertFalse(enabled.shouldIntercept("private.example.com"))
        assertFalse(enabled.shouldIntercept("other.example.com"))
    }

    @Test
    fun tlsInspectorOnlyMarksHttpsPortAsEligible() {
        val inspector = CaptureTlsInspector()
        assertTrue(inspector.inspect("example.com", 443))
        assertFalse(inspector.inspect("example.com", 80))
        assertFalse(inspector.inspect(" ", 443))
    }

    @Test
    fun sourceParityGeneratorNeverCreatesFakeCertificateMaterial() {
        val failure = runCatching { CaptureCertificateManager.generateRootCertificate() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(CaptureCertificateState.MISSING, CaptureCertificateManager.snapshot().state)
        assertFalse(CaptureCertificateManager.snapshot().hasRootCertificate)
    }

    @Test
    fun registeredCertificateMovesThroughExplicitUserTrustLifecycle() {
        val source = byteArrayOf(1, 2, 3, 4)
        CaptureCertificateManager.registerRootCertificate(source)
        source[0] = 99

        val registered = CaptureCertificateManager.snapshot()
        assertEquals(CaptureCertificateState.GENERATED, registered.state)
        assertTrue(registered.hasRootCertificate)
        assertFalse(registered.userConfirmedTrusted)

        val firstRead = CaptureCertificateManager.rootCertificateData()!!
        val secondRead = CaptureCertificateManager.rootCertificateData()!!
        assertEquals(1, firstRead[0].toInt())
        assertNotSame(firstRead, secondRead)

        CaptureCertificateManager.confirmTrustEnabledByUser()
        val trusted = CaptureCertificateManager.snapshot()
        assertEquals(CaptureCertificateState.USER_CONFIRMED_TRUSTED, trusted.state)
        assertTrue(trusted.userConfirmedTrusted)
        assertFalse(CaptureCertificateManager.isReadyForInterception())

        CaptureCertificateManager.revokeUserConfirmation()
        assertEquals(CaptureCertificateState.GENERATED, CaptureCertificateManager.snapshot().state)
    }

    @Test
    fun productionCoordinatorRefusesTlsInterceptionUntilRealSigningAndRelayExist() {
        CaptureCertificateManager.registerRootCertificate(byteArrayOf(9, 8, 7))
        CaptureCertificateManager.confirmTrustEnabledByUser()

        val coordinator = CaptureMitmProxyCoordinator()
        val prepared = coordinator.prepare(listOf("example.com"))

        assertEquals(CaptureMitmProxyState.FAILED, prepared.state)
        assertTrue(prepared.message.orEmpty().contains("动态站点证书生成尚未实现"))
        assertFalse(coordinator.shouldIntercept("example.com"))
        assertFalse(CaptureTlsCapabilities.HOST_CERTIFICATE_GENERATION_AVAILABLE)
        assertFalse(CaptureTlsCapabilities.ACTIVE_TLS_DECRYPTION_AVAILABLE)
    }

    @Test
    fun installationInstructionsRequireManualAndroid11CaInstall() {
        val instructions = CaptureCertificateManager.installationInstructions().joinToString("\n")
        assertTrue(instructions.contains("Android 11"))
        assertTrue(instructions.contains("手动安装 CA 证书"))
    }
}
