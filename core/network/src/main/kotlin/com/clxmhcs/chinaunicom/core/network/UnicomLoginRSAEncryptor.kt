package com.clxmhcs.chinaunicom.core.network

import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

sealed class UnicomLoginEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidPublicKey : UnicomLoginEncryptionException("invalidPublicKey")
    data object PlaintextTooLong : UnicomLoginEncryptionException("plaintextTooLong")
    class EncryptionFailed(cause: Throwable) : UnicomLoginEncryptionException("encryptionFailed", cause)
}

/**
 * Source-derived China Unicom login RSA helper.
 *
 * The frozen iOS implementation imports the same SubjectPublicKeyInfo key and
 * encrypts UTF-8 plaintext with RSA PKCS#1 v1.5 before Base64 encoding it.
 */
object UnicomLoginRSAEncryptor {
    const val PUBLIC_KEY_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDc+CZK9bBA9IU+gZUOc6FUGu7yO9WpTNB0PzmgFBh96Mg1WrovD1oqZ+eIF4LjvxKXGOdI79JRdve9NPhQo07+uqGQgE4imwNnRx7PFtCRryiIEcUoavuNtuRVoBAm6qdB0SrctgaqGfLgKvZHOnwTjyNqjBUxzMeQlEC2czEMSwIDAQAB"

    private val publicKey: RSAPublicKey by lazy {
        try {
            val encoded = Base64.getDecoder().decode(PUBLIC_KEY_BASE64)
            KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(encoded)) as RSAPublicKey
        } catch (error: Exception) {
            throw UnicomLoginEncryptionException.InvalidPublicKey
        }
    }

    fun encrypt(plaintext: String): String {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val maximumPlaintextBytes = (publicKey.modulus.bitLength() + 7) / 8 - 11
        if (plaintextBytes.size > maximumPlaintextBytes) {
            plaintextBytes.fill(0)
            throw UnicomLoginEncryptionException.PlaintextTooLong
        }

        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.getEncoder().encodeToString(cipher.doFinal(plaintextBytes))
        } catch (error: UnicomLoginEncryptionException) {
            throw error
        } catch (error: Exception) {
            throw UnicomLoginEncryptionException.EncryptionFailed(error)
        } finally {
            plaintextBytes.fill(0)
        }
    }
}
