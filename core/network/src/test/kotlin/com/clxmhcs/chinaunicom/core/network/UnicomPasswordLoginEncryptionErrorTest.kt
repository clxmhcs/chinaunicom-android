package com.clxmhcs.chinaunicom.core.network

import org.junit.Assert.assertThrows
import org.junit.Test

class UnicomPasswordLoginEncryptionErrorTest {
    @Test
    fun oversizePasswordMapsToPasswordSpecificPlaintextTooLong() {
        val identity = UnicomLoginDeviceIdentity(
            deviceCode = "550E8400-E29B-41D4-A716-446655440000",
            uniqueIdentifier = "iosa" + "a".repeat(32),
            deviceID = "b".repeat(64),
            appID = "c".repeat(192),
            deviceModel = "Pixel-Test",
            deviceOS = "14.1",
        )
        val store = object : UnicomLoginDeviceIdentityStore {
            override fun identity(): UnicomLoginDeviceIdentity = identity
            override fun cityCookie(): String = UnicomPasswordLoginSession.DEFAULT_CITY_COOKIE
            override fun updateCityCookie(value: String) = Unit
        }
        val transport = UnicomTransport {
            error("transport must not run when password RSA validation fails")
        }
        val session = UnicomPasswordLoginSession(
            identityStore = store,
            transport = transport,
        )

        assertThrows(UnicomPasswordLoginException.PlaintextTooLong::class.java) {
            session.login("13800138000", "x".repeat(118))
        }
    }
}
