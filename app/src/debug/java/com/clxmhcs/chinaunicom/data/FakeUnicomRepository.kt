package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceRepositoryState
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Debug-only fixture repository. Never compiled into release builds. */
internal class FakeUnicomRepository : UnicomRepository {
    private val fixtureAccount = UnicomAccount(
        displayName = "测试号码",
        mobile = "18600009025",
        packageName = "校园沃派38元套餐",
        packages = listOf(
            FlowPackage(
                id = "mock-flow-domestic",
                originalName = "国内通用流量",
                totalMB = 455000.0,
                usedMB = 60650.0,
                remainingMB = 394350.0,
                detectedQuotaType = QuotaType.LIMITED,
                detectedCategory = PackageCategory.GENERAL,
                isShared = false,
                endDateText = "2026-09-01",
            ),
            FlowPackage(
                id = "mock-flow-province",
                originalName = "省内流量",
                totalMB = 110000.0,
                usedMB = 0.0,
                remainingMB = 110000.0,
                detectedQuotaType = QuotaType.LIMITED,
                detectedCategory = PackageCategory.GENERAL,
                isShared = false,
            ),
        ),
        balanceYuan = 896.87,
        lastUpdatedAt = Instant.now(),
    )
    private val stateFlow = MutableStateFlow(UnicomAppState(accounts = listOf(fixtureAccount)))
    private val balanceStateFlow = MutableStateFlow(BalanceRepositoryState(homeBalanceAccountID = fixtureAccount.id))

    override val appState: StateFlow<UnicomAppState> = stateFlow.asStateFlow()
    override val balanceState: StateFlow<BalanceRepositoryState> = balanceStateFlow.asStateFlow()

    override suspend fun refreshAll() = Unit
    override suspend fun refreshAccount(accountID: UUID) = Unit
    override suspend fun refreshWidgetAccount(accountID: UUID, includeBalance: Boolean) = Unit
    override suspend fun refreshAutomation(includeBalance: Boolean) = Unit
    override suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) = Unit
    override suspend fun runBalanceAutoRefreshLoop() = Unit
    override suspend fun refreshHomeBalanceManually() = Unit
    override suspend fun addBalanceAccountGroup() = Unit
    override suspend fun deleteBalanceAccountGroup(groupID: UUID) = Unit
    override suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID) = Unit
    override fun setHomeBalanceAccountID(accountID: UUID?) = Unit
    override fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID) = Unit
    override fun financialRepresentativeAccountID(accountID: UUID): UUID? = accountID
}
