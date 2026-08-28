package com.clxmhcs.chinaunicom.capture

import android.content.Context

internal class CaptureTlsConfigurationStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): CaptureMitmConfiguration = CaptureMitmConfiguration(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        interceptHttps = preferences.getBoolean(KEY_INTERCEPT_HTTPS, true),
        excludedHosts = decodeHosts(preferences.getString(KEY_EXCLUDED_HOSTS, null)),
        includedHosts = decodeHosts(preferences.getString(KEY_INCLUDED_HOSTS, null)),
    ).normalized()

    fun write(configuration: CaptureMitmConfiguration) {
        val normalized = configuration.normalized()
        preferences.edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putBoolean(KEY_INTERCEPT_HTTPS, normalized.interceptHttps)
            .putString(KEY_EXCLUDED_HOSTS, normalized.excludedHosts.joinToString(HOST_SEPARATOR))
            .putString(KEY_INCLUDED_HOSTS, normalized.includedHosts.joinToString(HOST_SEPARATOR))
            .apply()
    }

    private fun decodeHosts(raw: String?): List<String> = raw
        .orEmpty()
        .split(HOST_SEPARATOR)
        .filter(String::isNotEmpty)

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.capture.tls.v1"
        private const val HOST_SEPARATOR = "\u001F"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERCEPT_HTTPS = "intercept_https"
        private const val KEY_EXCLUDED_HOSTS = "excluded_hosts"
        private const val KEY_INCLUDED_HOSTS = "included_hosts"

        fun create(context: Context): CaptureTlsConfigurationStore = CaptureTlsConfigurationStore(context)
    }
}
