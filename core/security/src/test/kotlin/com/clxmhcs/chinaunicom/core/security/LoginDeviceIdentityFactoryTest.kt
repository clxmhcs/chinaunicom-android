package com.clxmhcs.chinaunicom.core.security

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginDeviceIdentityFactoryTest {
    @Test
    fun createsSourceCompatibleIdentityShapes() {
        val identity = LoginDeviceIdentityFactory.create()

        assertTrue(identity.deviceCode.matches(Regex("[0-9A-F-]{36}")))
        assertTrue(identity.uniqueIdentifier.matches(Regex("iosa[0-9a-f]{32}")))
        assertTrue(identity.deviceId.matches(Regex("[0-9a-f]{64}")))
        assertTrue(identity.appId.matches(Regex("[0-9a-f]{192}")))
        assertEquals("017|170", identity.cityCookie)
    }

    @Test
    fun derivesDeviceIdFromDeviceCode() {
        val identity = LoginDeviceIdentityFactory.create()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(identity.deviceCode.toByteArray())
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

        assertEquals(expected, identity.deviceId)
    }
}
