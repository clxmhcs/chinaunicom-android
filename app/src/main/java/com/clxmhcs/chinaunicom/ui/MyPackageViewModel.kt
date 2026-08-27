package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.MyPackageAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomMyPackageCredentialValidator
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.mypackage.AndroidMyPackageDiskCache
import com.clxmhcs.chinaunicom.data.mypackage.DefaultMyPackageStore
import com.clxmhcs.chinaunicom.data.mypackage.LoginMyPackageRefreshClient
import com.clxmhcs.chinaunicom.data.mypackage.SettingsMyPackagePolicyProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import kotlinx.coroutines.launch

/** M9-B2 rough functional wiring. Presentation parity remains deferred. */
class MyPackageViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStoreProvider.create(appContext)
    private val settingsRepository = AndroidSettingsRepositories.refreshLogic(appContext)
    private val credentialLifecycle = MyPackageAccountCredentialLifecycle(
        validator = UnicomMyPackageCredentialValidator(),
        credentialStore = credentialStore,
    )
    private val packageStore = DefaultMyPackageStore(
        client = LoginMyPackageRefreshClient(credentialLifecycle),
        cache = AndroidMyPackageDiskCache(appContext),
        policyProvider = SettingsMyPackagePolicyProvider(settingsRepository),
    )

    val state = packageStore.state

    fun load(account: UnicomAccount, force: Boolean = false) {
        viewModelScope.launch { packageStore.load(account, force) }
    }

    fun refresh(account: UnicomAccount) {
        viewModelScope.launch { packageStore.refresh(account) }
    }

    fun applyRefreshPolicyChange(account: UnicomAccount) {
        viewModelScope.launch { packageStore.applyRefreshPolicyChange(account) }
    }

    fun clear() = packageStore.clear()
}
