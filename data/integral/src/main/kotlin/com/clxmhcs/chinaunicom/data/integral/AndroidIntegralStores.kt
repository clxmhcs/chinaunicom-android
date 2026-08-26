package com.clxmhcs.chinaunicom.data.integral

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.IntegralRequestLifecycle
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository

object AndroidIntegralStores {
    fun create(
        context: Context,
        credentialLifecycle: IntegralRequestLifecycle,
        settingsRepository: SettingsRepository,
    ): IntegralStore {
        val policy = IntegralCachePolicy(SettingsIntegralRefreshPolicyProvider(settingsRepository))
        return DefaultIntegralStore(
            lifecycle = credentialLifecycle,
            cache = AndroidIntegralDiskCache(context.applicationContext),
            cachePolicy = policy,
        )
    }
}
