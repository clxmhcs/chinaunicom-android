package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceRepository
import com.clxmhcs.chinaunicom.data.balance.BalanceRepositoryState
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.QuotaRepository
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** Production repository backed by the one quota/AppState authority plus balance repository. */
class ProductionUnicomRepository(
    private val quotaRepository: QuotaRepository,
    private val balanceRepository: BalanceRepository,
    private val reloadAccountsFromPersistenceAction: suspend () -> Unit = {},
    private val accountsCommittedAction: suspend (List<UnicomAccount>) -> Unit = {},
) : UnicomRepository {
    override val appState: StateFlow<UnicomAppState> = quotaRepository.state
    override val balanceState: StateFlow<BalanceRepositoryState> = balanceRepository.state

    override suspend fun refreshAll() {
        quotaRepository.refreshAll()
        publishCommittedAccounts()
    }

    override suspend fun refreshAccount(accountID: UUID) {
        quotaRepository.refreshAccount(accountID)
        publishCommittedAccounts()
    }

    override suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) {
        quotaRepository.autoRefreshIfNeeded(trigger)
        publishCommittedAccounts()
    }

    override suspend fun reloadAccountsFromPersistence() {
        reloadAccountsFromPersistenceAction()
        publishCommittedAccounts()
    }

    override suspend fun runBalanceAutoRefreshLoop() = balanceRepository.runAutomaticRefreshLoop()

    override suspend fun refreshHomeBalanceManually() {
        balanceRepository.refreshHomeBalanceManually()
        publishCommittedAccounts()
    }

    override suspend fun addBalanceAccountGroup() = balanceRepository.addBalanceAccountGroup()
    override suspend fun deleteBalanceAccountGroup(groupID: UUID) = balanceRepository.deleteBalanceAccountGroup(groupID)
    override suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID) = balanceRepository.toggleBalanceAccount(accountID, groupID)
    override fun setHomeBalanceAccountID(accountID: UUID?) = balanceRepository.setHomeBalanceAccountID(accountID)
    override fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID) = balanceRepository.setDefaultFinancialAccountID(accountID, groupID)
    override fun financialRepresentativeAccountID(accountID: UUID): UUID? = balanceRepository.financialRepresentativeAccountID(accountID)

    private suspend fun publishCommittedAccounts() {
        runCatching { accountsCommittedAction(appState.value.accounts.sortedBy { it.sortOrder }) }
    }
}
