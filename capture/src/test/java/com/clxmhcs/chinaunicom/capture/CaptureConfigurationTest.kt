package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureConfigurationTest {
    @Test
    fun normalizedConfigurationTrimsAndDeduplicatesHosts() {
        val configuration = CaptureConfiguration(
            targetHost = "  m.client.10010.com  ",
            targetPath = "  /mobileService/query  ",
            captureAllHosts = false,
            additionalHosts = listOf(" example.com ", "", "example.com", " api.example.com "),
        ).normalized()

        assertEquals("m.client.10010.com", configuration.targetHost)
        assertEquals("/mobileService/query", configuration.targetPath)
        assertEquals(listOf("example.com", "api.example.com"), configuration.additionalHosts)
        assertFalse(configuration.captureAllHosts)
    }

    @Test
    fun normalizedConfigurationDropsBlankTargetValues() {
        val configuration = CaptureConfiguration(
            targetHost = "   ",
            targetPath = "\t",
        ).normalized()

        assertNull(configuration.targetHost)
        assertNull(configuration.targetPath)
    }

    @Test
    fun initialStateIsStopped() {
        assertEquals(CaptureTunnelState.STOPPED, CaptureStateSnapshot().state)
    }
}
