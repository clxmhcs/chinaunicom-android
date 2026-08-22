package com.clxmhcs.chinaunicom.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCipher {

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedCredentialPayload =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(associatedData)
            EncryptedCredentialPayload(
                iv = cipher.iv.copyOf(),
                ciphertext = cipher.doFinal(plaintext),
            )
        } catch (error: CredentialStorageException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStorageException.Crypto(error)
        }

    override fun decrypt(payload: EncryptedCredentialPayload, associatedData: ByteArray): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload.iv),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(payload.ciphertext)
        } catch (error: CredentialStorageException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStorageException.Crypto(error)
        }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "chinaunicom.account.credentials.aes.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

class SharedPreferencesCredentialBlobStorage(context: Context) : CredentialBlobStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun write(accountID: UUID, encryptedBlob: ByteArray) {
        val encoded = Base64.encodeToString(encryptedBlob, Base64.NO_WRAP)
        if (!preferences.edit().putString(accountID.toString(), encoded).commit()) {
            throw CredentialStorageException.Corrupted()
        }
    }

    override fun read(accountID: UUID): ByteArray? {
        val encoded = preferences.getString(accountID.toString(), null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw CredentialStorageException.Corrupted(error)
        }
    }

    override fun delete(accountID: UUID) {
        if (!preferences.edit().remove(accountID.toString()).commit()) {
            throw CredentialStorageException.Corrupted()
        }
    }

    override fun deleteAll() {
        if (!preferences.edit().clear().commit()) {
            throw CredentialStorageException.Corrupted()
        }
    }

    companion object {
        const val PREFERENCES_NAME = "chinaunicom.secure.credentials.v1"
    }
}

object AndroidCredentialStores {
    fun accountCredentials(context: Context): CredentialStore = SecureAccountCredentialStore(
        cipher = AndroidKeystoreCredentialCipher(),
        storage = SharedPreferencesCredentialBlobStorage(context),
    )
}
