package com.clxmhcs.chinaunicom.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android mapping of the iOS credential Keychain service.
 *
 * The AES key is non-exportable in AndroidKeyStore. SharedPreferences contains only a versioned,
 * AES-GCM encrypted envelope and lives in credential-encrypted app storage. This class intentionally
 * performs no logging and never persists passwords, SMS codes, or account profile data.
 */
class AndroidKeystoreCredentialVault(context: Context) : CredentialVault {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        check(!appContext.isDeviceProtectedStorage) {
            "Credential vault must use credential-encrypted storage."
        }
    }

    override fun save(accountId: UUID, credentials: AccountCredentials) = synchronized(lock) {
        val plaintext = CredentialRecordCodec.encode(credentials)
        try {
            val encryptedRecord = encrypt(plaintext)
            if (!preferences.edit().putString(accountId.toString(), encryptedRecord).commit()) {
                throw CredentialVaultException()
            }
        } catch (failure: CredentialVaultException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialVaultException(failure)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun read(accountId: UUID): AccountCredentials? = synchronized(lock) {
        val encryptedRecord = preferences.getString(accountId.toString(), null) ?: return@synchronized null
        var plaintext: ByteArray? = null
        try {
            plaintext = decrypt(encryptedRecord)
            CredentialRecordCodec.decode(plaintext)
        } catch (failure: CredentialVaultException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialVaultException(failure)
        } finally {
            plaintext?.fill(0)
        }
    }

    override fun delete(accountId: UUID) = synchronized(lock) {
        try {
            if (!preferences.edit().remove(accountId.toString()).commit()) {
                throw CredentialVaultException()
            }
        } catch (failure: CredentialVaultException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialVaultException(failure)
        }
    }

    override fun deleteAll() = synchronized(lock) {
        try {
            if (!preferences.edit().clear().commit()) {
                throw CredentialVaultException()
            }
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (failure: CredentialVaultException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialVaultException(failure)
        }
    }

    private fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return try {
            JSONObject()
                .put("version", ENVELOPE_VERSION)
                .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .toString()
        } finally {
            ciphertext.fill(0)
        }
    }

    private fun decrypt(encryptedRecord: String): ByteArray {
        val envelope = JSONObject(encryptedRecord)
        require(envelope.optInt("version", -1) == ENVELOPE_VERSION)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        try {
            require(iv.isNotEmpty())
            require(ciphertext.isNotEmpty())
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return cipher.doFinal(ciphertext)
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        private const val PREFERENCES_NAME = "com.clxmhcs.chinaunicom.credentials.v1"
        private const val KEY_ALIAS = "com.clxmhcs.chinaunicom.credentials.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENVELOPE_VERSION = 1
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private val lock = Any()
    }
}

private object CredentialRecordCodec {
    private const val RECORD_VERSION = 1

    fun encode(credentials: AccountCredentials): ByteArray = JSONObject()
        .put("version", RECORD_VERSION)
        .put("cookie", credentials.cookie)
        .putOpt("appID", credentials.appID)
        .putOpt("tokenOnline", credentials.tokenOnline)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): AccountCredentials {
        val record = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(record.optInt("version", -1) == RECORD_VERSION)
        val cookie = record.optString("cookie").trim()
        require(cookie.isNotEmpty())
        return AccountCredentials(
            cookie = cookie,
            appID = record.optionalString("appID"),
            tokenOnline = record.optionalString("tokenOnline"),
        )
    }

    private fun JSONObject.optionalString(key: String): String? = when {
        !has(key) || isNull(key) -> null
        else -> optString(key).trim().ifEmpty { null }
    }
}
