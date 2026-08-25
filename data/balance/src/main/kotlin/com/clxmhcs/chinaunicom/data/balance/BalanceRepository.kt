package com.clxmhcs.chinaunicom.data.balance

import com.clxmhcs.chinaunicom.core.login.BalanceAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.BalanceFetchResult
import com.clxmhcs.chinaunicom.core.model.BalanceRefreshState
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshCoordinator
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

interface BalanceRefreshClient {
    fun hasCredentials(accountID: UUID): Boolean
    suspend fun fetchBalance(accountID: UUID): BalanceFetchResult
}

class LoginBalanceRefreshClient(
    private val lifecycle: BalanceAccountCredentialLifecycle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BalanceRefreshClient {
    override fun hasCredentials(accountID: UUID): Boolean = lifecycle.hasCredentials(accountID)

    override suspend fun fetchBalance(accountID: UUID): BalanceFetchResult =
        withContext(ioDispatcher) { lifecycle.refreshValidatedBalance(accountID) }
}

interface BalanceRepository {
    val state: StateFlow<BalanceRepositoryState>

    suspend fun runAutomaticRefreshLoop()
    suspend fun refreshBalancesIfNeeded()
    suspend fun refreshHomeBalanceManually()
    suspend fun addBalanceAccountGroup()
    suspend fun deleteBalanceAccountGroup(groupID: UUID)
    suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID)
    fun setHomeBalanceAccountID(accountID: UUID?)
    fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID)
    fun financialRepresentativeAccountID(accountID: UUID): UUID?
    fun nextAutomaticRefreshDelay(now: Instant = Instant.now()): Duration
}

/**
 * M6-D balance business coordinator.
 *
 * SharedBalanceCacheStore is the freshness/lease authority. Ordinary account metadata only mirrors
 * successful shared values for local display. Automatic failures retain the last successful cache
 * and a persisted per-unit attempt timestamp supplies the source-equivalent retry cooldown.
 */
class DefaultBalanceRepository(
    private val accountState: QuotaRefreshCoordinator,
    private val refreshClient: BalanceRefreshClient,
    private val sharedCache: SharedBalanceCacheStore,
    private val configurationStore: BalanceConfigurationStore,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : BalanceRepository {
    private val refreshUnitsLock = Mutex()
    private val automaticLoopLock = Mutex()
    private val _state = MutableStateFlow(
        BalanceRepositoryState(
            balanceAccountGroups = configurationStore.loadGroups(),
            homeBalanceAccountID = configurationStore.loadHomeBalanceAccountID(),
            lastAutomaticAttemptAt = configurationStore.loadLastAutomaticAttemptAt(),
        ),
    )

    override val state: StateFlow<BalanceRepositoryState> = _state.asStateFlow()

    init {
        normalizeConfigurationAndPersist()
        initializeHomeBalanceAccountIfNeeded()
        synchronizeSharedBalanceStateFromApp()
    }

    override suspend fun runAutomaticRefreshLoop() {
        if (!automaticLoopLock.tryLock()) return
        try {
            if (!settingsRepository.loadBalanceRefreshPolicy().automaticRefreshEnabled) return
            migrateLegacySharedBalanceCacheIfNeeded()
            refreshBalancesIfNeeded()
            while (currentCoroutineContext().isActive) {
                if (!settingsRepository.loadBalanceRefreshPolicy().automaticRefreshEnabled) return
                val delayMillis = nextAutomaticRefreshDelay(Instant.now(clock)).toMillis().coerceAtLeast(1_000L)
                sleeper(delayMillis)
                refreshBalancesIfNeeded()
            }
        } finally {
            automaticLoopLock.unlock()
        }
    }

    override suspend fun refreshBalancesIfNeeded() {
        if (!refreshUnitsLock.tryLock()) return
        try {
            if (!synchronizeSharedBalanceStateFromApp()) return
            val units = balanceRefreshUnits()
            if (units.isEmpty()) return
            _state.update { it.copy(isRefreshingBalanceUnits = true) }

            var performedNetworkRequest = false
            for (unit in units) {
                if (!currentCoroutineContext().isActive) break
                val anchor = unit.memberAccountIDs.firstOrNull() ?: continue
                val now = Instant.now(clock)

                if (isFailureRetryCoolingDown(unit, now)) {
                    sharedCache.cachedEntry(anchor, now)?.let { entry ->
                        clearAutomaticAttempt(unit.id)
                        applySharedEntryIfNewer(entry, unit)
                    }
                    continue
                }

                when (val claim = sharedCache.beginAutomaticRefresh(anchor, SharedBalanceRefreshSource.APP_AUTOMATIC, now)) {
                    is SharedBalanceRefreshClaim.Cached -> {
                        clearAutomaticAttempt(unit.id)
                        applySharedEntryIfNewer(claim.entry, unit)
                    }
                    is SharedBalanceRefreshClaim.InFlight -> {
                        sharedCache.latestEntry(anchor)?.let { applySharedEntryIfNewer(it, unit) }
                    }
                    is SharedBalanceRefreshClaim.Granted -> {
                        if (performedNetworkRequest) {
                            try {
                                sleeper(1_000L)
                            } catch (error: CancellationException) {
                                sharedCache.failRefresh(claim.token)
                                throw error
                            }
                        }
                        performedNetworkRequest = true
                        recordAutomaticAttempt(unit.id, now)
                        refreshBalanceUnit(
                            unit = unit,
                            token = claim.token,
                            showsHomeIndicator = _state.value.homeBalanceAccountID?.let(unit.memberAccountIDs::contains) == true,
                        )
                    }
                    SharedBalanceRefreshClaim.Unavailable -> Unit
                }
            }
        } finally {
            _state.update { it.copy(isRefreshingBalanceUnits = false) }
            refreshUnitsLock.unlock()
        }
    }

    override suspend fun refreshHomeBalanceManually() {
        if (_state.value.isRefreshingBalanceUnits) return
        val homeID = _state.value.homeBalanceAccountID ?: return
        if (!synchronizeSharedBalanceStateFromApp()) return
        val unit = balanceRefreshUnits().firstOrNull { homeID in it.memberAccountIDs } ?: return
        when (val claim = sharedCache.beginForcedRefresh(homeID, SharedBalanceRefreshSource.APP_MANUAL, Instant.now(clock))) {
            is SharedBalanceRefreshClaim.Granted -> refreshBalanceUnit(unit, claim.token, true)
            is SharedBalanceRefreshClaim.InFlight -> sharedCache.latestEntry(homeID)?.let { applySharedEntryIfNewer(it, unit) }
            is SharedBalanceRefreshClaim.Cached -> applySharedEntryIfNewer(claim.entry, unit)
            SharedBalanceRefreshClaim.Unavailable -> Unit
        }
    }

    override suspend fun addBalanceAccountGroup() {
        val groups = _state.value.balanceAccountGroups + BalanceAccountGroup(
            name = "合账组 ${_state.value.balanceAccountGroups.size + 1}",
        )
        publishGroups(groups)
        synchronizeSharedBalanceStateFromApp()
    }

    override suspend fun deleteBalanceAccountGroup(groupID: UUID) {
        val group = _state.value.balanceAccountGroups.firstOrNull { it.id == groupID } ?: return
        clearBalanceFor(group.memberAccountIDs)
        publishGroups(_state.value.balanceAccountGroups.filterNot { it.id == groupID })
        synchronizeSharedBalanceStateFromApp()
        refreshBalancesIfNeeded()
    }

    override suspend fun toggleBalanceAccount(accountID: UUID, groupID: UUID) {
        if (accountState.state.value.accounts.none { it.id == accountID }) return
        val groups = _state.value.balanceAccountGroups.toMutableList()
        val targetIndex = groups.indexOfFirst { it.id == groupID }
        if (targetIndex < 0) return
        val previous = groups.toList()
        val targetContains = accountID in groups[targetIndex].memberAccountIDs
        clearBalanceFor(listOf(accountID))

        if (targetContains) {
            groups[targetIndex] = groups[targetIndex].copy(memberAccountIDs = groups[targetIndex].memberAccountIDs - accountID)
        } else {
            for (index in groups.indices) {
                groups[index] = groups[index].copy(memberAccountIDs = groups[index].memberAccountIDs - accountID)
            }
            groups[targetIndex] = groups[targetIndex].copy(memberAccountIDs = groups[targetIndex].memberAccountIDs + accountID)
        }

        val normalized = normalizeGroups(groups)
        for (oldGroup in previous.filter { it.memberAccountIDs.size >= 2 }) {
            val newCount = normalized.firstOrNull { it.id == oldGroup.id }?.memberAccountIDs?.size ?: 0
            if (newCount < 2) clearBalanceFor(oldGroup.memberAccountIDs)
        }
        synchronizeConfiguredGroupBalanceCaches(normalized)
        publishGroups(normalized)
        synchronizeSharedBalanceStateFromApp()
        refreshBalancesIfNeeded()
    }

    override fun setHomeBalanceAccountID(accountID: UUID?) {
        if (accountID != null && accountState.state.value.accounts.none { it.id == accountID && it.isEnabled }) return
        configurationStore.saveHomeBalanceAccountID(accountID)
        _state.update { it.copy(homeBalanceAccountID = accountID) }
        synchronizeSharedBalanceStateFromApp()
    }

    override fun setDefaultFinancialAccountID(accountID: UUID?, groupID: UUID) {
        val groups = _state.value.balanceAccountGroups.toMutableList()
        val index = groups.indexOfFirst { it.id == groupID && it.memberAccountIDs.size >= 2 }
        if (index < 0) return
        if (accountID != null) {
            val eligible = accountID in groups[index].memberAccountIDs && accountState.state.value.accounts.any { it.id == accountID && it.isEnabled }
            if (!eligible) return
        }
        groups[index] = groups[index].copy(defaultAccountID = accountID)
        publishGroups(groups)
        synchronizeSharedBalanceStateFromApp()
    }

    override fun financialRepresentativeAccountID(accountID: UUID): UUID? {
        val accounts = accountState.state.value.accounts
        if (accounts.none { it.id == accountID }) return null
        val group = _state.value.balanceAccountGroups.firstOrNull { accountID in it.memberAccountIDs && it.memberAccountIDs.size >= 2 }
        return if (group != null) representativeForGroup(group, accounts) else accountID
    }

    override fun nextAutomaticRefreshDelay(now: Instant): Duration {
        if (!settingsRepository.loadBalanceRefreshPolicy().automaticRefreshEnabled) return Duration.ofSeconds(60)
        val units = balanceRefreshUnits()
        if (units.isEmpty()) return Duration.ofSeconds(60)
        val delays = units.mapNotNull { unit ->
            val anchor = unit.memberAccountIDs.firstOrNull() ?: return@mapNotNull null
            if (sharedCache.cachedEntry(anchor, now) != null) {
                return@mapNotNull Duration.between(now, sharedCache.nextAutomaticRefreshAt(anchor, now)).coerceAtLeast(Duration.ofSeconds(1))
            }
            remainingFailureRetryDelay(unit, now)?.let { return@mapNotNull it.coerceAtLeast(Duration.ofSeconds(1)) }
            Duration.between(now, sharedCache.nextAutomaticRefreshAt(anchor, now)).coerceAtLeast(Duration.ofSeconds(15))
        }
        return delays.minOrNull() ?: Duration.ofSeconds(60)
    }

    private suspend fun refreshBalanceUnit(
        unit: BalanceRefreshUnit,
        token: SharedBalanceRefreshLeaseToken,
        showsHomeIndicator: Boolean,
    ) {
        val indicatorStartedAt = if (showsHomeIndicator) beginHomeIndicator() else null
        try {
            val representativeID = representativeForUnit(unit) ?: error("账号缺少可用登录凭据")
            val result = refreshClient.fetchBalance(representativeID)
            val balanceYuan = result.balanceYuan ?: error("余额接口未返回有效余额")
            val completedAt = Instant.now(clock)
            val entry = sharedCache.completeRefresh(token, balanceYuan, representativeID, completedAt)
            if (entry == null) {
                if (showsHomeIndicator) finishHomeIndicator(indicatorStartedAt, false)
                return
            }
            clearAutomaticAttempt(unit.id)
            accountState.updateAccountsFromBalance { accounts ->
                val targets = unit.memberAccountIDs.toSet()
                accounts.map { account ->
                    if (account.id !in targets) account
                    else account.copy(
                        balanceYuan = entry.balanceYuan,
                        balanceUpdatedAt = entry.refreshedAt,
                        unavailableBalanceDetail = result.unavailableBalanceDetail,
                        lastErrorMessage = if (account.id == representativeID) null else account.lastErrorMessage,
                    )
                }
            }
            if (showsHomeIndicator) finishHomeIndicator(indicatorStartedAt, true)
        } catch (error: CancellationException) {
            sharedCache.failRefresh(token)
            if (showsHomeIndicator) finishHomeIndicator(indicatorStartedAt, false)
            throw error
        } catch (_: Throwable) {
            sharedCache.failRefresh(token)
            if (showsHomeIndicator) finishHomeIndicator(indicatorStartedAt, false)
        }
    }

    private fun balanceRefreshUnits(): List<BalanceRefreshUnit> {
        val accounts = accountState.state.value.accounts
        val valid = accounts.map { it.id }.toSet()
        val enabled = accounts.filter { it.isEnabled }.map { it.id }.toSet()
        val grouped = mutableSetOf<UUID>()
        val units = mutableListOf<BalanceRefreshUnit>()
        for (group in _state.value.balanceAccountGroups) {
            val members = group.memberAccountIDs.filter(valid::contains)
            if (members.size < 2) continue
            grouped += members
            if (members.none(enabled::contains)) continue
            units += BalanceRefreshUnit("group:${group.id}", members, group.id)
        }
        for (account in accounts) {
            if (account.isEnabled && account.id !in grouped) {
                units += BalanceRefreshUnit("account:${account.id}", listOf(account.id), null)
            }
        }
        return units
    }

    private fun allBalanceScopeUnits(): List<BalanceRefreshUnit> {
        val accounts = accountState.state.value.accounts
        val valid = accounts.map { it.id }.toSet()
        val grouped = mutableSetOf<UUID>()
        val units = mutableListOf<BalanceRefreshUnit>()
        for (group in _state.value.balanceAccountGroups) {
            val members = group.memberAccountIDs.filter(valid::contains)
            if (members.size < 2) continue
            grouped += members
            units += BalanceRefreshUnit("group:${group.id}", members, group.id)
        }
        for (account in accounts) if (account.id !in grouped) {
            units += BalanceRefreshUnit("account:${account.id}", listOf(account.id), null)
        }
        return units
    }

    private fun synchronizeSharedBalanceStateFromApp(): Boolean {
        settingsRepository.loadBalanceRefreshPolicy()
        val units = allBalanceScopeUnits()
        val scopes = units.map { unit ->
            SharedBalanceScope(unit.id, unit.memberAccountIDs, representativeForUnit(unit))
        }
        if (!sharedCache.replaceScopes(scopes, Instant.now(clock))) return false

        val accounts = accountState.state.value.accounts
        for (unit in units) {
            val anchor = unit.memberAccountIDs.firstOrNull() ?: continue
            val local = accounts.filter { it.id in unit.memberAccountIDs }
                .mapNotNull { account ->
                    val balance = account.balanceYuan ?: return@mapNotNull null
                    val updatedAt = account.balanceUpdatedAt ?: return@mapNotNull null
                    Triple(account.id, balance, updatedAt)
                }
                .maxByOrNull { it.third } ?: continue
            val shared = sharedCache.latestEntry(anchor)
            if (shared != null && !shared.refreshedAt.isBefore(local.third)) continue
            when (val claim = sharedCache.beginForcedRefresh(anchor, SharedBalanceRefreshSource.APP_AUTOMATIC, local.third)) {
                is SharedBalanceRefreshClaim.Granted -> sharedCache.completeRefresh(
                    claim.token,
                    local.second,
                    representativeForUnit(unit),
                    local.third,
                )
                else -> Unit
            }
        }
        return true
    }

    private suspend fun applySharedEntryIfNewer(entry: SharedBalanceCacheEntry, unit: BalanceRefreshUnit) {
        val accounts = accountState.state.value.accounts
        val localDate = accounts.filter { it.id in unit.memberAccountIDs }.mapNotNull { it.balanceUpdatedAt }.maxOrNull()
        if (localDate != null && entry.refreshedAt.isBefore(localDate)) return
        val targets = unit.memberAccountIDs.toSet()
        val needsUpdate = accounts.any { it.id in targets && (it.balanceYuan != entry.balanceYuan || it.balanceUpdatedAt != entry.refreshedAt) }
        if (!needsUpdate) return
        accountState.updateAccountsFromBalance { values ->
            values.map { account ->
                if (account.id !in targets) account else account.copy(
                    balanceYuan = entry.balanceYuan,
                    balanceUpdatedAt = entry.refreshedAt,
                    unavailableBalanceDetail = null,
                )
            }
        }
    }

    private fun representativeForUnit(unit: BalanceRefreshUnit): UUID? {
        val accounts = accountState.state.value.accounts
        val group = unit.balanceGroupID?.let { id -> _state.value.balanceAccountGroups.firstOrNull { it.id == id } }
        return if (group != null) representativeForGroup(group, accounts) else fallbackRepresentative(unit.memberAccountIDs, accounts)
    }

    private fun representativeForGroup(group: BalanceAccountGroup, accounts: List<UnicomAccount>): UUID? {
        val candidates = enabledCandidates(group.memberAccountIDs, accounts)
        if (candidates.isEmpty()) return null
        _state.value.homeBalanceAccountID?.takeIf { home -> candidates.any { it.id == home } }?.let { return it }
        group.defaultAccountID?.takeIf { configured -> candidates.any { it.id == configured } }?.let { return it }
        return fallbackRepresentative(group.memberAccountIDs, accounts)
    }

    private fun fallbackRepresentative(memberIDs: List<UUID>, accounts: List<UnicomAccount>): UUID? {
        val candidates = enabledCandidates(memberIDs, accounts)
        return candidates.firstOrNull { refreshClient.hasCredentials(it.id) }?.id ?: candidates.firstOrNull()?.id
    }

    private fun enabledCandidates(memberIDs: List<UUID>, accounts: List<UnicomAccount>): List<UnicomAccount> =
        accounts.filter { it.id in memberIDs && it.isEnabled }.sortedBy { it.sortOrder }

    private fun isFailureRetryCoolingDown(unit: BalanceRefreshUnit, now: Instant): Boolean =
        remainingFailureRetryDelay(unit, now) != null

    private fun remainingFailureRetryDelay(unit: BalanceRefreshUnit, now: Instant): Duration? {
        val interval = Duration.ofMinutes(settingsRepository.loadBalanceRefreshPolicy().failureRetryMinutes.coerceAtLeast(1).toLong())
        val last = _state.value.lastAutomaticAttemptAt[unit.id] ?: return null
        val elapsed = Duration.between(last, now)
        if (elapsed.isNegative || elapsed >= interval) return null
        return interval.minus(elapsed)
    }

    private fun recordAutomaticAttempt(unitID: String, at: Instant) {
        val updated = _state.value.lastAutomaticAttemptAt + (unitID to at)
        configurationStore.saveLastAutomaticAttemptAt(updated)
        _state.update { it.copy(lastAutomaticAttemptAt = updated) }
    }

    private fun clearAutomaticAttempt(unitID: String) {
        if (unitID !in _state.value.lastAutomaticAttemptAt) return
        val updated = _state.value.lastAutomaticAttemptAt - unitID
        configurationStore.saveLastAutomaticAttemptAt(updated)
        _state.update { it.copy(lastAutomaticAttemptAt = updated) }
    }

    private suspend fun clearBalanceFor(accountIDs: List<UUID>) {
        val targets = accountIDs.toSet()
        accountState.updateAccountsFromBalance { accounts ->
            accounts.map { account ->
                if (account.id !in targets) account else account.copy(
                    balanceYuan = null,
                    balanceUpdatedAt = null,
                    unavailableBalanceDetail = null,
                )
            }
        }
    }

    private suspend fun synchronizeConfiguredGroupBalanceCaches(groups: List<BalanceAccountGroup>) {
        for (group in groups) {
            if (group.memberAccountIDs.size < 2) continue
            val accounts = accountState.state.value.accounts.filter { it.id in group.memberAccountIDs }
            val source = accounts.filter { it.balanceYuan != null && it.balanceUpdatedAt != null }.maxByOrNull { it.balanceUpdatedAt!! } ?: continue
            val balance = source.balanceYuan ?: continue
            val updatedAt = source.balanceUpdatedAt ?: continue
            accountState.updateAccountsFromBalance { values ->
                values.map { account ->
                    if (account.id !in group.memberAccountIDs) account else account.copy(
                        balanceYuan = balance,
                        balanceUpdatedAt = updatedAt,
                        unavailableBalanceDetail = source.unavailableBalanceDetail,
                    )
                }
            }
        }
    }

    private fun publishGroups(groups: List<BalanceAccountGroup>) {
        val normalized = normalizeGroups(groups)
        configurationStore.saveGroups(normalized)
        _state.update { it.copy(balanceAccountGroups = normalized) }
    }

    private fun normalizeConfigurationAndPersist() {
        publishGroups(_state.value.balanceAccountGroups)
        val validHome = _state.value.homeBalanceAccountID?.takeIf { id -> accountState.state.value.accounts.any { it.id == id && it.isEnabled } }
        if (validHome != _state.value.homeBalanceAccountID) {
            configurationStore.saveHomeBalanceAccountID(validHome)
            _state.update { it.copy(homeBalanceAccountID = validHome) }
        }
    }

    private fun normalizeGroups(groups: List<BalanceAccountGroup>): List<BalanceAccountGroup> {
        val valid = accountState.state.value.accounts.map { it.id }.toSet()
        val claimed = mutableSetOf<UUID>()
        return groups.mapIndexed { index, group ->
            val members = group.memberAccountIDs.filter { it in valid && claimed.add(it) }
            val defaultID = group.defaultAccountID?.takeIf { it in members && accountState.state.value.accounts.any { account -> account.id == it && account.isEnabled } }
            group.copy(
                name = group.name.trim().ifEmpty { "合账组 ${index + 1}" },
                memberAccountIDs = members,
                defaultAccountID = defaultID,
            )
        }
    }

    private fun initializeHomeBalanceAccountIfNeeded() {
        if (_state.value.homeBalanceAccountID != null) return
        val first = accountState.state.value.accounts.filter { it.isEnabled }.minByOrNull { it.sortOrder }?.id ?: return
        configurationStore.saveHomeBalanceAccountID(first)
        _state.update { it.copy(homeBalanceAccountID = first) }
    }

    private suspend fun migrateLegacySharedBalanceCacheIfNeeded() {
        if (configurationStore.legacySharedBalanceMigrationCompleted()) return
        configurationStore.markLegacySharedBalanceMigrationCompleted()
        val accounts = accountState.state.value.accounts
        if (_state.value.balanceAccountGroups.isNotEmpty() || accounts.size <= 1) return
        val clearIDs = accounts.drop(1).map { it.id }
        clearBalanceFor(clearIDs)
    }

    private fun beginHomeIndicator(): Instant? {
        if (_state.value.balanceRefreshState == BalanceRefreshState.LOADING) return null
        _state.update { it.copy(balanceRefreshState = BalanceRefreshState.LOADING) }
        return Instant.now(clock)
    }

    private suspend fun finishHomeIndicator(startedAt: Instant?, succeeded: Boolean) {
        startedAt ?: return
        val elapsed = Duration.between(startedAt, Instant.now(clock)).toMillis()
        val remaining = MINIMUM_HOME_LOADING_MILLIS - elapsed
        if (remaining > 0) sleeper(remaining)
        _state.update { it.copy(balanceRefreshState = if (succeeded) BalanceRefreshState.IDLE else BalanceRefreshState.FAILED) }
    }

    private data class BalanceRefreshUnit(
        val id: String,
        val memberAccountIDs: List<UUID>,
        val balanceGroupID: UUID?,
    )

    companion object {
        const val MINIMUM_HOME_LOADING_MILLIS = 5_000L
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
