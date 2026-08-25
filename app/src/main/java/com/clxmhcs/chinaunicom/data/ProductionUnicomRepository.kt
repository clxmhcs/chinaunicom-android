package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshCoordinator
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** Release production repository backed by the M6-B quota refresh coordinator. */
class ProductionUnicomRepository(
    private val coordinator: QuotaRefreshCoordinator,
) : UnicomRepository {
    override val appState: StateFlow<UnicomAppState> = coordinator.state

    override suspend fun refreshAll() {
        coordinator.refreshAll()
    }

    override suspend fun refreshAccount(accountID: UUID) {
        coordinator.refreshAccount(accountID)
    }

    override suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) {
        coordinator.autoRefreshIfNeeded(trigger)
    }
}
