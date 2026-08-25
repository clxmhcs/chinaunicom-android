package com.clxmhcs.chinaunicom.data.orderedbusiness

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.OrderedBusinessAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository

object AndroidOrderedBusinessStores {
    fun create(
        context: Context,
        credentialLifecycle: OrderedBusinessAccountCredentialLifecycle,
        settingsRepository: SettingsRepository,
    ): OrderedBusinessStore = DefaultOrderedBusinessStore(
        client = LoginOrderedBusinessRefreshClient(credentialLifecycle),
        cache = AndroidOrderedBusinessDiskCache(context.applicationContext),
        policyProvider = SettingsOrderedBusinessPolicyProvider(settingsRepository),
    )
}
