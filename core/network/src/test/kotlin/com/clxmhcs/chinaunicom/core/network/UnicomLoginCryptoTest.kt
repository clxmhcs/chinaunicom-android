package com.clxmhcs.chinaunicom.core.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnicomLoginCryptoTest {
    @Test
    fun encryptsWithTheFrozen1024BitPublicKey() {
        val ciphertext = UnicomLoginCrypto.encrypt("18600000000")

        assertEquals(128, Base64.getDecoder().decode(ciphertext).size)
    }

    @Test
    fun pkcs1PaddingMakesSeparateEncryptionsDistinct() {
        val first = UnicomLoginCrypto.encrypt("123456")
        val second = UnicomLoginCrypto.encrypt("123456")

        assertNotEquals(first, second)
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
    }

    @Test
    fun rejectsPlaintextThatCannotFitPkcs1Padding() {
        try {
            UnicomLoginCrypto.encrypt("x".repeat(118))
            fail("Expected plaintext-length rejection")
        } catch (_: UnicomLoginCryptoException.PlaintextTooLong) {
            // Expected: the frozen 1024-bit key has a 117-byte PKCS#1 v1.5 limit.
        }
    }
}
