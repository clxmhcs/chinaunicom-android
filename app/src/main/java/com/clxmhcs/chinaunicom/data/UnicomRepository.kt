package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/**
 * App-facing repository contract.
 *
 * M6-B promotes the production account state to a StateFlow while keeping credentials and direct
 * network/session details below this boundary. Debug implements the same contract with isolated
 * fixture state; release delegates refresh work to QuotaRefreshCoordinator.
 */
interface UnicomRepository {
    val appState: StateFlow<UnicomAppState>

    suspend fun refreshAll()

    suspend fun refreshAccount(accountID: UUID)

    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
}
