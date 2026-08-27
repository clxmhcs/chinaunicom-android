package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.TariffZoneAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomTariffZoneCredentialValidator
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.model.TariffZoneSearchResult
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.tariffzone.DefaultTariffZoneStore
import com.clxmhcs.chinaunicom.data.tariffzone.TariffZoneStore
import kotlinx.coroutines.launch

class TariffZoneViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = CredentialStoreProvider.create(application.applicationContext)

    val store: TariffZoneStore = DefaultTariffZoneStore(
        lifecycle = TariffZoneAccountCredentialLifecycle(
            validator = UnicomTariffZoneCredentialValidator(),
            credentialStore = credentialStore,
        ),
    )
    val state = store.state

    fun load(account: UnicomAccount) = launch { store.load(account) }
    fun reload(account: UnicomAccount) = launch { store.reload(account) }
    fun setScope(account: UnicomAccount, scope: TariffZoneScope) = launch { store.setScope(account, scope) }
    fun selectRegion(account: UnicomAccount, region: TariffZoneRegion) = launch { store.selectRegion(account, region) }
    fun selectFirstLevel(account: UnicomAccount, id: String) = launch { store.selectFirstLevel(account, id) }
    fun selectSecondLevel(account: UnicomAccount, id: String) = launch { store.selectSecondLevel(account, id) }
    fun selectProducts(account: UnicomAccount, ids: Set<String>) = launch { store.selectProducts(account, ids) }
    fun loadMore(account: UnicomAccount) = launch { store.loadMore(account) }
    fun search(account: UnicomAccount, query: String) = launch { store.search(account, query) }
    fun endSearch() = store.endSearch()
    fun openSearchResult(account: UnicomAccount, result: TariffZoneSearchResult) = launch { store.openSearchResult(account, result) }
    fun closeSearchDetail() = store.closeSearchDetail()
    fun clear() = store.clear()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
