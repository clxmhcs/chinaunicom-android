package com.clxmhcs.chinaunicom.data.balance

import com.clxmhcs.chinaunicom.core.model.BalanceRefreshState
import java.time.Instant
import java.util.UUID

data class BalanceAccountGroup(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val memberAccountIDs: List<UUID> = emptyList(),
    val defaultAccountID: UUID? = null,
)

data class BalanceRepositoryState(
    val balanceAccountGroups: List<BalanceAccountGroup> = emptyList(),
    val homeBalanceAccountID: UUID? = null,
    val balanceRefreshState: BalanceRefreshState = BalanceRefreshState.IDLE,
    val isRefreshingBalanceUnits: Boolean = false,
    val lastAutomaticAttemptAt: Map<String, Instant> = emptyMap(),
)

interface BalanceConfigurationStore {
    fun loadGroups(): List<BalanceAccountGroup>
    fun saveGroups(groups: List<BalanceAccountGroup>): Boolean
    fun loadHomeBalanceAccountID(): UUID?
    fun saveHomeBalanceAccountID(accountID: UUID?): Boolean
    fun loadLastAutomaticAttemptAt(): Map<String, Instant>
    fun saveLastAutomaticAttemptAt(value: Map<String, Instant>): Boolean
    fun legacySharedBalanceMigrationCompleted(): Boolean
    fun markLegacySharedBalanceMigrationCompleted(): Boolean
}
