package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.data.balance.BalanceRepository
import com.clxmhcs.chinaunicom.data.balance.BalanceRepositoryState
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.QuotaRepository
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** Release production repository backed by quota repository/AppState plus the balance repository. */
class ProductionUnicomRepository(
    private val quotaRepository: QuotaRepository,
    private val balanceRepository: BalanceRepository,
) : UnicomRepository {
    override val appState: StateFlow<UnicomAppState> = quotaRepository.state
    override val balanceState: StateFlow<BalanceRepositoryState> = balanceRepository.state

    override suspend fun refreshAll() = quotaRepository.refreshAll()
    override suspend fun refreshAccount(accountID: UUID) = quotaRepository.refreshAccount(accountID)
    override suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) = quotaRepository.autoRefreshIfNeeded(trigger)

    override suspend fun runBalanceAutoRefreshLoop() = balanceRepository.runAutomaticRefreshLoop()
    override suspend fun refreshHomeBalanceManually() = balanceRepository.refreshHomeBalanceManually()
    override suspend fun addBalanceAccountGroup() = balanceRepository.addBalanceAccountGroup()
    override suspend fun deleteBalanceAccountGroup(groupID: UUID) = balanceRepository.deleteBalanceAccountGroup(groupID)
    override suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID) = balanceRepository.toggleBalanceAccount(accountID, groupID)
    override fun setHomeBalanceAccountID(accountID: UUID?) = balanceRepository.setHomeBalanceAccountID(accountID)
    override fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID) = balanceRepository.setDefaultFinancialAccountID(accountID, groupID)
    override fun financialRepresentativeAccountID(accountID: UUID): UUID? = balanceRepository.financialRepresentativeAccountID(accountID)
}
