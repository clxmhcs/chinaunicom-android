package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.MyOrderAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.MyOrderDetailCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomMyOrderCredentialValidator
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.myorder.DefaultMyOrderStore
import com.clxmhcs.chinaunicom.data.myorder.MyOrderDetailStore
import com.clxmhcs.chinaunicom.data.myorder.SettingsMyOrderEntryRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import java.util.UUID
import kotlinx.coroutines.launch

/** Root-scoped functional wiring for M9-A3. UI styling remains intentionally rough. */
class MyOrderViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStoreProvider.create(appContext)
    private val settingsRepository = AndroidSettingsRepositories.refreshLogic(appContext)
    private val requestLifecycle = MyOrderAccountCredentialLifecycle(
        validator = UnicomMyOrderCredentialValidator(),
        credentialStore = credentialStore,
    )
    private val detailCredentialLifecycle = MyOrderDetailCredentialLifecycle(credentialStore)

    private val orderStore = DefaultMyOrderStore(
        lifecycle = requestLifecycle,
        entryRefreshPolicy = SettingsMyOrderEntryRefreshPolicy(settingsRepository),
    )
    private val detailStore = MyOrderDetailStore(detailCredentialLifecycle)

    val state = orderStore.state
    val detailState = detailStore.state

    fun load(account: UnicomAccount, force: Boolean = false) {
        viewModelScope.launch { orderStore.load(account, force) }
    }

    fun refresh(account: UnicomAccount) {
        viewModelScope.launch { orderStore.refresh(account) }
    }

    fun loadMoreIfNeeded(order: MyOrder, account: UnicomAccount) {
        viewModelScope.launch { orderStore.loadMoreIfNeeded(order, account) }
    }

    fun loadMore(account: UnicomAccount) {
        viewModelScope.launch { orderStore.loadMore(account) }
    }

    fun clearError() = orderStore.clearError()

    fun prepareDetail(account: UnicomAccount, order: MyOrder, force: Boolean = false) {
        detailStore.prepare(account, order, force)
    }

    fun reloadDetail(account: UnicomAccount, order: MyOrder) {
        detailStore.reload(account, order)
    }

    fun receiveDetailBridgeText(result: Result<String>, requestID: UUID) {
        detailStore.receiveBridgeText(result, requestID)
    }

    /** Transient WebView input only. Never place this value in Compose state, navigation or logs. */
    fun requireDetailCookieHeader(accountID: UUID): String =
        detailCredentialLifecycle.requireCookieHeader(accountID)
}
