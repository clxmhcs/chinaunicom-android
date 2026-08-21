package com.clxmhcs.chinaunicom.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Stable login-device identity required by the M5 session protocol. It is isolated from account
 * credentials so account removal can never silently rotate the device identity.
 */
interface LoginDeviceIdentityStore {
    fun readOrCreate(): LoginDeviceIdentity

    fun updateCityCookie(cityCookie: String): LoginDeviceIdentity

    fun delete()
}

data class LoginDeviceIdentity(
    val deviceCode: String,
    val uniqueIdentifier: String,
    val deviceId: String,
    val appId: String,
    val cityCookie: String,
)

/** Safe-to-display failure that intentionally contains no persisted identifier values. */
class LoginDeviceIdentityStoreException internal constructor(cause: Throwable? = null) :
    Exception("Login device identity storage is unavailable or invalid.", cause)

class AndroidKeystoreLoginDeviceIdentityStore(context: Context) : LoginDeviceIdentityStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        check(!appContext.isDeviceProtectedStorage) {
            "Login device identity must use credential-encrypted storage."
        }
    }

    override fun readOrCreate(): LoginDeviceIdentity = synchronized(lock) {
        val stored = preferences.getString(RECORD_KEY, null)
        if (stored == null) {
            val created = LoginDeviceIdentityFactory.create()
            write(created)
            return@synchronized created
        }
        read(stored)
    }

    override fun updateCityCookie(cityCookie: String): LoginDeviceIdentity = synchronized(lock) {
        if (!isValidCityCookie(cityCookie)) throw LoginDeviceIdentityStoreException()
        val current = readOrCreate()
        val updated = current.copy(cityCookie = cityCookie)
        write(updated)
        updated
    }

    override fun delete() = synchronized(lock) {
        try {
            if (!preferences.edit().clear().commit()) throw LoginDeviceIdentityStoreException()
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (failure: LoginDeviceIdentityStoreException) {
            throw failure
        } catch (failure: Exception) {
            throw LoginDeviceIdentityStoreException(failure)
        }
    }

    private fun read(encryptedRecord: String): LoginDeviceIdentity {
        var plaintext: ByteArray? = null
        return try {
            plaintext = decrypt(encryptedRecord)
            LoginDeviceIdentityCodec.decode(plaintext)
        } catch (failure: LoginDeviceIdentityStoreException) {
            throw failure
        } catch (failure: Exception) {
            throw LoginDeviceIdentityStoreException(failure)
        } finally {
            plaintext?.fill(0)
        }
    }

    private fun write(identity: LoginDeviceIdentity) {
        val plaintext = LoginDeviceIdentityCodec.encode(identity)
        try {
            if (!preferences.edit().putString(RECORD_KEY, encrypt(plaintext)).commit()) {
                throw LoginDeviceIdentityStoreException()
            }
        } catch (failure: LoginDeviceIdentityStoreException) {
            throw failure
        } catch (failure: Exception) {
            throw LoginDeviceIdentityStoreException(failure)
        } finally {
            plaintext.fill(0)
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
            require(iv.isNotEmpty() && ciphertext.isNotEmpty())
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

    private fun isValidCityCookie(value: String): Boolean {
        val parts = value.split('|')
        return parts.size == 2 && parts.all { it.isNotBlank() && it.all(Char::isDigit) }
    }

    private companion object {
        private const val PREFERENCES_NAME = "com.clxmhcs.chinaunicom.login-identity.v1"
        private const val RECORD_KEY = "identity"
        private const val KEY_ALIAS = "com.clxmhcs.chinaunicom.login-identity.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENVELOPE_VERSION = 1
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private val lock = Any()
    }
}

internal object LoginDeviceIdentityFactory {
    private val random = SecureRandom()

    fun create(): LoginDeviceIdentity {
        val deviceCode = UUID.randomUUID().toString().uppercase()
        return LoginDeviceIdentity(
            deviceCode = deviceCode,
            uniqueIdentifier = "iosa" + randomHex(32),
            deviceId = sha256Hex(deviceCode),
            appId = randomHex(192),
            cityCookie = DEFAULT_CITY_COOKIE,
        )
    }

    private fun randomHex(length: Int): String {
        val bytes = ByteArray((length + 1) / 2)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }.take(length)
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

    private const val DEFAULT_CITY_COOKIE = "017|170"
}

private object LoginDeviceIdentityCodec {
    private const val VERSION = 1

    fun encode(identity: LoginDeviceIdentity): ByteArray = JSONObject()
        .put("version", VERSION)
        .put("deviceCode", identity.deviceCode)
        .put("uniqueIdentifier", identity.uniqueIdentifier)
        .put("deviceId", identity.deviceId)
        .put("appId", identity.appId)
        .put("cityCookie", identity.cityCookie)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): LoginDeviceIdentity {
        val record = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(record.optInt("version", -1) == VERSION)
        val identity = LoginDeviceIdentity(
            deviceCode = record.getString("deviceCode"),
            uniqueIdentifier = record.getString("uniqueIdentifier"),
            deviceId = record.getString("deviceId"),
            appId = record.getString("appId"),
            cityCookie = record.getString("cityCookie"),
        )
        require(UUID.fromString(identity.deviceCode) != null)
        require(identity.uniqueIdentifier.matches(Regex("iosa[0-9a-f]{32}")))
        require(identity.deviceId.matches(Regex("[0-9a-f]{64}")))
        require(identity.appId.matches(Regex("[0-9a-f]{192}")))
        require(identity.cityCookie.matches(Regex("[0-9]+\\|[0-9]+")))
        return identity
    }
}
