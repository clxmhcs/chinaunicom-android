package com.clxmhcs.chinaunicom.data.settings

import android.content.Context

private const val REFRESH_LOGIC_PREFERENCES_NAME = "chinaunicom.refresh.logic.settings.v1"

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

object AndroidSettingsRepositories {
    fun refreshLogic(
        context: Context,
        balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    ): RebateGiftSettingsRepository = UnifiedRefreshSettingsRepository(
        storage = SharedPreferencesRefreshLogicPolicyStorage(context),
        balanceIntervalSynchronizer = balanceIntervalSynchronizer,
    )
}
