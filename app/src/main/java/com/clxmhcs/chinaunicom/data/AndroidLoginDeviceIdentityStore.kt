package com.clxmhcs.chinaunicom.data

import android.content.Context
import android.os.Build
import com.clxmhcs.chinaunicom.core.network.UnicomLoginDeviceIdentity
import com.clxmhcs.chinaunicom.core.network.UnicomLoginDeviceIdentityStore
import com.clxmhcs.chinaunicom.core.network.UnicomSMSLoginSession
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/**
 * Persistent non-account login device identity.
 *
 * These values model the stable installation/device identifiers used by the
 * frozen iOS login protocol. They are not account credentials, passwords,
 * SMS codes, Cookies or token_online values. Auto Backup is disabled at the
 * application manifest boundary, so these identifiers remain installation-local.
 */
class AndroidLoginDeviceIdentityStore(context: Context) : UnicomLoginDeviceIdentityStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val random = SecureRandom()

    override fun identity(): UnicomLoginDeviceIdentity {
        val deviceCode = loadOrCreate(KEY_DEVICE_CODE, ::validUUID) {
            UUID.randomUUID().toString().uppercase(Locale.US)
        }
        val uniqueIdentifier = loadOrCreate(KEY_UNIQUE_IDENTIFIER, ::validUniqueIdentifier) {
            "iosa" + randomHex(32)
        }
        val deviceID = loadOrCreate(KEY_DEVICE_ID, { validLowercaseHex(it, 64) }) {
            sha256Hex(deviceCode)
        }
        val appID = loadOrCreate(KEY_APP_ID, { validLowercaseHex(it, 192) }) {
            randomHex(192)
        }
        val deviceModel = Build.MODEL?.trim().orEmpty().ifEmpty { Build.DEVICE?.trim().orEmpty() }.ifEmpty { "Android" }
        val deviceOS = Build.VERSION.RELEASE.orEmpty()
            .split('.')
            .filter(String::isNotBlank)
            .take(2)
            .joinToString(".")
            .ifEmpty { Build.VERSION.SDK_INT.toString() }

        return UnicomLoginDeviceIdentity(
            deviceCode = deviceCode,
            uniqueIdentifier = uniqueIdentifier,
            deviceID = deviceID,
            appID = appID,
            deviceModel = deviceModel,
            deviceOS = deviceOS,
        )
    }

    override fun cityCookie(): String = preferences.getString(KEY_CITY_COOKIE, null)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: UnicomSMSLoginSession.DEFAULT_CITY_COOKIE

    override fun updateCityCookie(value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        check(preferences.edit().putString(KEY_CITY_COOKIE, normalized).commit()) {
            "Unable to persist China Unicom login city Cookie seed"
        }
    }

    private fun loadOrCreate(
        key: String,
        validator: (String) -> Boolean,
        generator: () -> String,
    ): String {
        preferences.getString(key, null)?.trim()?.takeIf(validator)?.let { return it }
        val generated = generator()
        check(validator(generated)) { "Generated invalid login identity value for $key" }
        check(preferences.edit().putString(key, generated).commit()) {
            "Unable to persist China Unicom login identity value"
        }
        return generated
    }

    private fun randomHex(length: Int): String {
        val bytes = ByteArray((length + 1) / 2)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun validUUID(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun validUniqueIdentifier(value: String): Boolean = value.startsWith("iosa") && value.length == 36

    private fun validLowercaseHex(value: String, length: Int): Boolean =
        value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.login.device.identity.v1"
        private const val KEY_DEVICE_CODE = "deviceCode"
        private const val KEY_UNIQUE_IDENTIFIER = "uniqueIdentifier"
        private const val KEY_DEVICE_ID = "deviceID"
        private const val KEY_APP_ID = "appID"
        private const val KEY_CITY_COOKIE = "cityCookie"
    }
}
