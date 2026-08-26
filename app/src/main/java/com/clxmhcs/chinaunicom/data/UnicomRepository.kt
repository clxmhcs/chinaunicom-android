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
