package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.IntegralAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.OrderedBusinessAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.PhoneBillAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomIntegralCredentialValidator
import com.clxmhcs.chinaunicom.core.login.UnicomOrderedBusinessCredentialValidator
import com.clxmhcs.chinaunicom.core.login.UnicomPhoneBillCredentialValidator
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.comprehensive.AndroidComprehensiveBusinessStores
import com.clxmhcs.chinaunicom.data.comprehensive.ComprehensiveBusinessStoreState
import com.clxmhcs.chinaunicom.data.integral.AndroidIntegralStores
import com.clxmhcs.chinaunicom.data.integral.IntegralStore
import com.clxmhcs.chinaunicom.data.orderedbusiness.AndroidOrderedBusinessStores
import com.clxmhcs.chinaunicom.data.orderedbusiness.OrderedBusinessStore
import com.clxmhcs.chinaunicom.data.phonebill.AndroidPhoneBillStores
import com.clxmhcs.chinaunicom.data.phonebill.PhoneBillStore
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * M8-E feature holder. Quota/balance account state remains owned by the root FlowViewModel/M6
 * repository; this view model owns only the three independent M8 business stores plus the
 * cache-only comprehensive points projection.
 */
class ComprehensiveBusinessViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStoreProvider.create(appContext)
    private val settingsRepository = AndroidSettingsRepositories.refreshLogic(appContext)
    private val accountRepository = DefaultAccountRepository(
        store = AndroidAccountMetadataStores.accounts(appContext),
    )

    private val comprehensiveStore = AndroidComprehensiveBusinessStores.create(appContext)
    val rootState: StateFlow<ComprehensiveBusinessStoreState> = comprehensiveStore.state

    val orderedBusinessStore: OrderedBusinessStore = AndroidOrderedBusinessStores.create(
        context = appContext,
        credentialLifecycle = OrderedBusinessAccountCredentialLifecycle(
            validator = UnicomOrderedBusinessCredentialValidator(),
            credentialStore = credentialStore,
        ),
        settingsRepository = settingsRepository,
    )
    val orderedState = orderedBusinessStore.state

    val phoneBillStore: PhoneBillStore = AndroidPhoneBillStores.create(
        context = appContext,
        credentialLifecycle = PhoneBillAccountCredentialLifecycle(
            validator = UnicomPhoneBillCredentialValidator(),
            credentialStore = credentialStore,
        ),
        settingsRepository = settingsRepository,
        accountRepository = accountRepository,
    )
    val phoneBillState = phoneBillStore.state

    val integralStore: IntegralStore = AndroidIntegralStores.create(
        context = appContext,
        credentialLifecycle = IntegralAccountCredentialLifecycle(
            validator = UnicomIntegralCredentialValidator(),
            credentialStore = credentialStore,
        ),
        settingsRepository = settingsRepository,
    )
    val integralState = integralStore.state

    fun loadCachedPoints(accountIDs: Collection<UUID>) {
        viewModelScope.launch { comprehensiveStore.loadCachedPoints(accountIDs) }
    }

    fun loadOrderedBusiness(account: UnicomAccount) {
        viewModelScope.launch { orderedBusinessStore.loadCachedOrRefreshIfMissing(account) }
    }

    fun refreshOrderedBusiness(account: UnicomAccount) {
        viewModelScope.launch { orderedBusinessStore.refresh(account) }
    }

    fun loadPhoneBill(account: UnicomAccount) {
        viewModelScope.launch { phoneBillStore.loadIfNeeded(account) }
    }

    fun selectPhoneBillMonth(month: BillMonth, account: UnicomAccount) {
        viewModelScope.launch { phoneBillStore.select(month, account) }
    }

    fun refreshPhoneBill(account: UnicomAccount) {
        viewModelScope.launch { phoneBillStore.refreshSelectedMonth(account) }
    }

    fun loadIntegral(account: UnicomAccount) {
        viewModelScope.launch { integralStore.loadIfNeeded(account) }
    }

    fun refreshIntegral(account: UnicomAccount, allAccountIDs: Collection<UUID>) {
        viewModelScope.launch {
            integralStore.manualRefresh(account)
            comprehensiveStore.loadCachedPoints(allAccountIDs)
        }
    }

    fun loadIntegralDetails(
        query: IntegralDetailQuery,
        account: UnicomAccount,
        force: Boolean = false,
    ) {
        viewModelScope.launch { integralStore.loadDetails(query, account, force) }
    }
}
