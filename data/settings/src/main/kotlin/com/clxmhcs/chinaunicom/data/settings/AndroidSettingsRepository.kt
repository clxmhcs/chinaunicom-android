package com.clxmhcs.chinaunicom.data.settings

import android.content.Context

private const val REFRESH_LOGIC_PREFERENCES_NAME = "chinaunicom.refresh.logic.settings.v1"
private const val APP_SETTINGS_PREFERENCES_NAME = "chinaunicom.app.settings.v1"
private const val APP_SETTINGS_STORAGE_KEY = "appSettings"

class SharedPreferencesRefreshLogicPolicyStorage(
    context: Context,
) : RefreshLogicPolicyStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        REFRESH_LOGIC_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? =
        preferences.getString(AppRefreshLogicPolicyCodec.STORAGE_KEY, null)

    override fun write(value: String): Boolean =
        preferences.edit()
            .putString(AppRefreshLogicPolicyCodec.STORAGE_KEY, value)
            .commit()
}

class SharedPreferencesAppSettingsStorage(
    context: Context,
) : AppSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        APP_SETTINGS_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString(APP_SETTINGS_STORAGE_KEY, null)

    override fun write(value: String): Boolean = preferences.edit()
        .putString(APP_SETTINGS_STORAGE_KEY, value)
        .commit()
}

object AndroidSettingsRepositories {
    fun refreshLogic(
        context: Context,
        balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    ): VideoRingSettingsRepository = UnifiedVideoRingSettingsRepository(
        storage = SharedPreferencesRefreshLogicPolicyStorage(context),
        balanceIntervalSynchronizer = balanceIntervalSynchronizer,
    )

    fun appSettings(context: Context): AppSettingsRepository = DefaultAppSettingsRepository(
        storage = SharedPreferencesAppSettingsStorage(context),
    )
}
