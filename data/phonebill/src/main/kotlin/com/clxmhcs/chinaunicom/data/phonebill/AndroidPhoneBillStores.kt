package com.clxmhcs.chinaunicom.data.phonebill

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.PhoneBillAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository

object AndroidPhoneBillStores {
    fun create(
        context: Context,
        credentialLifecycle: PhoneBillAccountCredentialLifecycle,
        settingsRepository: SettingsRepository,
        accountRepository: AccountRepository,
    ): PhoneBillStore {
        val policy = PhoneBillCachePolicy(SettingsPhoneBillPolicyProvider(settingsRepository))
        return DefaultPhoneBillStore(
            lifecycle = credentialLifecycle,
            cache = AndroidPhoneBillDiskCache(context.applicationContext),
            cachePolicy = policy,
            accountRepository = accountRepository,
        )
    }
}
