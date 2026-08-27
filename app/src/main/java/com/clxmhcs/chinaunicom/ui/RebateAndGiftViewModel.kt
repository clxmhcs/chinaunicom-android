package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.RebateAndGiftAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomRebateAndGiftCredentialValidator
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.rebategift.AndroidRebateAndGiftStores
import com.clxmhcs.chinaunicom.data.rebategift.RebateAndGiftStore
import com.clxmhcs.chinaunicom.data.rebategift.RebateGiftRefreshPolicy as StoreRebateGiftRefreshPolicy
import com.clxmhcs.chinaunicom.data.rebategift.RebateGiftRefreshPolicyProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import kotlinx.coroutines.launch

class RebateAndGiftViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStoreProvider.create(appContext)
    private val refreshSettings = AndroidSettingsRepositories.refreshLogic(appContext)

    val store: RebateAndGiftStore = AndroidRebateAndGiftStores.create(
        context = appContext,
        credentialLifecycle = RebateAndGiftAccountCredentialLifecycle(
            validator = UnicomRebateAndGiftCredentialValidator(),
            credentialStore = credentialStore,
        ),
        policyProvider = RebateGiftRefreshPolicyProvider {
            val policy = refreshSettings.rebateGiftRefreshPolicy.value
            StoreRebateGiftRefreshPolicy(
                automaticRefreshEnabled = policy.automaticRefreshEnabled,
                monthlyRefreshDay = policy.monthlyRefreshDay,
                monthlyRefreshHour = policy.monthlyRefreshHour,
                queryImmediatelyWhenNoCache = policy.queryImmediatelyWhenNoCache,
            )
        },
    )
    val state = store.state

    fun load(account: UnicomAccount, scope: RebateQueryScope) {
        viewModelScope.launch { store.loadIfNeeded(account, scope) }
    }

    fun loadGift(account: UnicomAccount) {
        viewModelScope.launch { store.loadGiftIfNeeded(account) }
    }

    fun refresh(account: UnicomAccount, scope: RebateQueryScope) {
        viewModelScope.launch { store.manualRefresh(account, scope) }
    }
}
