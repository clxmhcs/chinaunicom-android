package com.clxmhcs.chinaunicom.data

import android.content.Context
import android.os.Build
import com.clxmhcs.chinaunicom.core.network.UnicomLoginDeviceIdentity
import com.clxmhcs.chinaunicom.core.network.UnicomLoginDeviceIdentityStore
import com.clxmhcs.chinaunicom.core.network.UnicomSMSLoginSession
import com.clxmhcs.chinaunicom.core.security.AndroidKeystoreCredentialCipher
import com.clxmhcs.chinaunicom.core.security.EncryptedCredentialPayload
import com.clxmhcs.chinaunicom.core.security.SharedPreferencesCredentialBlobStorage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/**
 * Persistent installation-level identity for China Unicom login requests.
 *
 * The frozen iOS app keeps deviceCode / uniqueIdentifier / deviceID / appID in
 * Keychain. Android therefore protects the same stable identifiers with a
 * dedicated Android Keystore AES-GCM key. They are intentionally isolated from
 * account credentials, so deleting an account does not rotate the login device.
 * The non-secret city seed remains in app-private Preferences.
 */
class AndroidLoginDeviceIdentityStore(context: Context) : UnicomLoginDeviceIdentityStore {
    private data class StableIdentity(
        val deviceCode: String,
        val uniqueIdentifier: String,
        val deviceID: String,
        val appID: String,
    )

    private val applicationContext = context.applicationContext
    private val cipher = AndroidKeystoreCredentialCipher(IDENTITY_KEY_ALIAS)
    private val encryptedStorage = SharedPreferencesCredentialBlobStorage(
        applicationContext,
        IDENTITY_ENCRYPTED_PREFERENCES,
    )
    private val cityPreferences = applicationContext.getSharedPreferences(
        CITY_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val random = SecureRandom()

    override fun identity(): UnicomLoginDeviceIdentity {
        val stable = loadStableIdentity() ?: createStableIdentity().also(::saveStableIdentity)
        return UnicomLoginDeviceIdentity(
            deviceCode = stable.deviceCode,
            uniqueIdentifier = stable.uniqueIdentifier,
            deviceID = stable.deviceID,
            appID = stable.appID,
            deviceModel = Build.MODEL?.trim().orEmpty()
                .ifEmpty { Build.DEVICE?.trim().orEmpty() }
                .ifEmpty { "Android" },
            deviceOS = Build.VERSION.RELEASE.orEmpty()
                .split('.')
                .filter(String::isNotBlank)
                .take(2)
                .joinToString(".")
                .ifEmpty { Build.VERSION.SDK_INT.toString() },
        )
    }

    override fun cityCookie(): String = cityPreferences.getString(KEY_CITY_COOKIE, null)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: UnicomSMSLoginSession.DEFAULT_CITY_COOKIE

    override fun updateCityCookie(value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        check(cityPreferences.edit().putString(KEY_CITY_COOKIE, normalized).commit()) {
            "Unable to persist China Unicom login city Cookie seed"
        }
    }

    private fun loadStableIdentity(): StableIdentity? {
        val encryptedBlob = encryptedStorage.read(IDENTITY_STORAGE_ID) ?: return null
        val payload = decodeEnvelope(encryptedBlob)
        val plaintext = cipher.decrypt(payload, IDENTITY_AAD)
        return try {
            decodeStableIdentity(plaintext).takeIf(::isValid)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun saveStableIdentity(value: StableIdentity) {
        check(isValid(value)) { "Invalid China Unicom login device identity" }
        val plaintext = encodeStableIdentity(value)
        try {
            val encrypted = cipher.encrypt(plaintext, IDENTITY_AAD)
            encryptedStorage.write(IDENTITY_STORAGE_ID, encodeEnvelope(encrypted))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun createStableIdentity(): StableIdentity {
        val deviceCode = UUID.randomUUID().toString().uppercase(Locale.US)
        return StableIdentity(
            deviceCode = deviceCode,
            uniqueIdentifier = "iosa" + randomHex(32),
            deviceID = sha256Hex(deviceCode),
            appID = randomHex(192),
        )
    }

    private fun isValid(value: StableIdentity): Boolean =
        runCatching { UUID.fromString(value.deviceCode) }.isSuccess &&
            value.uniqueIdentifier.startsWith("iosa") && value.uniqueIdentifier.length == 36 &&
            validLowercaseHex(value.deviceID, 64) &&
            validLowercaseHex(value.appID, 192)

    private fun randomHex(length: Int): String {
        val bytes = ByteArray((length + 1) / 2)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun validLowercaseHex(value: String, length: Int): Boolean =
        value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun encodeStableIdentity(value: StableIdentity): ByteArray = ByteArrayOutputStream().let { output ->
        DataOutputStream(output).use { stream ->
            stream.writeInt(IDENTITY_FORMAT_VERSION)
            listOf(value.deviceCode, value.uniqueIdentifier, value.deviceID, value.appID).forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                stream.writeInt(bytes.size)
                stream.write(bytes)
            }
        }
        output.toByteArray()
    }

    private fun decodeStableIdentity(data: ByteArray): StableIdentity = DataInputStream(ByteArrayInputStream(data)).use { stream ->
        require(stream.readInt() == IDENTITY_FORMAT_VERSION)
        fun readField(): String {
            val length = stream.readInt()
            require(length in 1..512)
            val bytes = ByteArray(length)
            stream.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }
        val value = StableIdentity(readField(), readField(), readField(), readField())
        require(stream.available() == 0)
        value
    }

    private fun encodeEnvelope(payload: EncryptedCredentialPayload): ByteArray = ByteArrayOutputStream().let { output ->
        DataOutputStream(output).use { stream ->
            stream.writeInt(ENVELOPE_VERSION)
            stream.writeInt(payload.iv.size)
            stream.write(payload.iv)
            stream.writeInt(payload.ciphertext.size)
            stream.write(payload.ciphertext)
        }
        output.toByteArray()
    }

    private fun decodeEnvelope(data: ByteArray): EncryptedCredentialPayload = DataInputStream(ByteArrayInputStream(data)).use { stream ->
        require(stream.readInt() == ENVELOPE_VERSION)
        val ivLength = stream.readInt()
        require(ivLength in 1..64)
        val iv = ByteArray(ivLength)
        stream.readFully(iv)
        val ciphertextLength = stream.readInt()
        require(ciphertextLength in 1..4096)
        val ciphertext = ByteArray(ciphertextLength)
        stream.readFully(ciphertext)
        require(stream.available() == 0)
        EncryptedCredentialPayload(iv, ciphertext)
    }

    companion object {
        private const val IDENTITY_KEY_ALIAS = "chinaunicom.login.device.identity.aes.v1"
        private const val IDENTITY_ENCRYPTED_PREFERENCES = "chinaunicom.secure.login.identity.v1"
        private const val CITY_PREFERENCES = "chinaunicom.login.city.v1"
        private const val KEY_CITY_COOKIE = "cityCookie"
        private const val IDENTITY_FORMAT_VERSION = 1
        private const val ENVELOPE_VERSION = 1
        private val IDENTITY_STORAGE_ID = UUID.fromString("25d76391-9bd4-4a89-a894-efdb4129bc1a")
        private val IDENTITY_AAD = "chinaunicom.login.device.identity.v1".toByteArray(Charsets.UTF_8)
    }
}
