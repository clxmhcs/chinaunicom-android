package com.clxmhcs.chinaunicom.data.rebategift

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.RebateAndGiftRequestLifecycle

object AndroidRebateAndGiftStores {
    fun create(
        context: Context,
        credentialLifecycle: RebateAndGiftRequestLifecycle,
        policyProvider: RebateGiftRefreshPolicyProvider = RebateGiftRefreshPolicyProvider { RebateGiftRefreshPolicy() },
    ): RebateAndGiftStore = DefaultRebateAndGiftStore(
        lifecycle = credentialLifecycle,
        cache = AndroidRebateAndGiftDiskCache(context.applicationContext),
        policyProvider = policyProvider,
    )
}
