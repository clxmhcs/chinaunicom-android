package com.clxmhcs.chinaunicom.data.refresh

import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/**
 * Quota-domain repository boundary exposed to the application layer.
 *
 * The coordinator remains the single refresh/AppState authority. This repository intentionally
 * delegates to that authority instead of duplicating state, locks, refresh policy or persistence
 * behavior. M6-E therefore completes the repository architecture without changing the accepted
 * iOS-equivalent quota semantics from M6-B/C.
 */
interface QuotaRepository {
    val state: StateFlow<UnicomAppState>

    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
    suspend fun refreshAccount(accountID: UUID)
    suspend fun refreshAccountManually(accountID: UUID) = refreshAccount(accountID)
    suspend fun refreshAll()
    suspend fun refreshAllManually() = refreshAll()
}

class DefaultQuotaRepository(
    private val coordinator: QuotaRefreshCoordinator,
) : QuotaRepository {
    override val state: StateFlow<UnicomAppState> = coordinator.state

    override suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) =
        coordinator.autoRefreshIfNeeded(trigger)

    override suspend fun refreshAccount(accountID: UUID) = coordinator.refreshAccount(accountID)

    override suspend fun refreshAccountManually(accountID: UUID) = coordinator.refreshAccountManually(accountID)

    override suspend fun refreshAll() = coordinator.refreshAll()

    override suspend fun refreshAllManually() = coordinator.refreshAllManually()
}
