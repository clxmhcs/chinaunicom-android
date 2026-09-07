package com.clxmhcs.chinaunicom.core.network

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * RSA transport encryption used by the China Unicom password and SMS login endpoints.
 *
 * This component is deliberately limited to encryption. It neither retains nor logs a mobile
 * number, password, verification code, cookie, or token. Network dispatch is introduced only by
 * the later M5 login-session layer.
 */
object UnicomLoginCrypto {
    private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val PUBLIC_KEY_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDc+CZK9bBA9IU+gZUOc6FUGu7yO9Wp" +
            "TNB0PzmgFBh96Mg1WrovD1oqZ+eIF4LjvxKXGOdI79JRdve9NPhQo07+uqGQgE4imwNn" +
            "Rx7PFtCRryiIEcUoavuNtuRVoBAm6qdB0SrctgaqGfLgKvZHOnwTjyNqjBUxzMeQlEC2c" +
            "zEMSwIDAQAB"

    /** Returns base64 RSA/PKCS#1 v1.5 ciphertext compatible with the frozen iOS implementation. */
    fun encrypt(plaintext: String): String {
        val plainBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        try {
            val key = publicKey()
            val maximumPlaintextBytes = ((key as RSAPublicKey).modulus.bitLength() + 7) / 8 - 11
            if (plainBytes.size > maximumPlaintextBytes) {
                throw UnicomLoginCryptoException.PlaintextTooLong
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainBytes))
        } catch (failure: UnicomLoginCryptoException) {
            throw failure
        } catch (failure: Exception) {
            throw UnicomLoginCryptoException.EncryptionUnavailable
        } finally {
            plainBytes.fill(0)
        }
    }

    private fun publicKey(): PublicKey = try {
        val encoded = Base64.getDecoder().decode(PUBLIC_KEY_BASE64)
        try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
        } finally {
            encoded.fill(0)
        }
    } catch (failure: Exception) {
        throw UnicomLoginCryptoException.InvalidPublicKey
    }
}

sealed class UnicomLoginCryptoException(message: String) : Exception(message) {
    data object InvalidPublicKey : UnicomLoginCryptoException("invalidLoginPublicKey")
    data object PlaintextTooLong : UnicomLoginCryptoException("loginPlaintextTooLong")
    data object EncryptionUnavailable : UnicomLoginCryptoException("loginEncryptionUnavailable")
}
