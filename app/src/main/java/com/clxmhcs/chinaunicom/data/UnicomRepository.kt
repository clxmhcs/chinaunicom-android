package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.data.balance.BalanceRepositoryState
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** App-facing production state/repository contract. */
interface UnicomRepository {
    val appState: StateFlow<UnicomAppState>
    val balanceState: StateFlow<BalanceRepositoryState>

    suspend fun refreshAll()
    suspend fun refreshAccount(accountID: UUID)
    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)

    /**
     * User-initiated Widget refresh entry. Widget code must call this App-side authority rather than
     * owning a second carrier client. Balance uses the existing shared freshness/lease gate.
     */
    suspend fun refreshWidgetAccount(accountID: UUID, includeBalance: Boolean)

    /**
     * M13 durable automation transaction. WorkManager enters here instead of owning carrier clients.
     * Quota, optional shared-gated balance and committed Widget publication stay one App transaction.
     */
    suspend fun refreshAutomation(includeBalance: Boolean)

    /** Rehydrates the single production AppState after validated account metadata is created. */
    suspend fun reloadAccountsFromPersistence() = Unit

    suspend fun runBalanceAutoRefreshLoop()
    suspend fun refreshHomeBalanceManually()
    suspend fun addBalanceAccountGroup()
    suspend fun deleteBalanceAccountGroup(groupID: UUID)
    suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID)
    fun setHomeBalanceAccountID(accountID: UUID?)
    fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID)
    fun financialRepresentativeAccountID(accountID: UUID): UUID?
}
