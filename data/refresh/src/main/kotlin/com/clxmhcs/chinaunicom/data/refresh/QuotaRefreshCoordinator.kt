package com.clxmhcs.chinaunicom.data.refresh

import com.clxmhcs.chinaunicom.core.login.LoginAccountLifecycle
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.VoicePackageIdentityHint
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class QuotaAutomaticRefreshTrigger {
    COLD_LAUNCH,
    FOREGROUND,
    POLICY_CHANGE,
}

data class QuotaRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val refreshOnColdLaunch: Boolean = true,
    val refreshOnForeground: Boolean = true,
    val minimumIntervalMinutes: Int = 10,
    val accountGapSeconds: Int = 2,
)

fun interface QuotaRefreshPolicyProvider {
    fun load(): QuotaRefreshPolicy
}

object SourceDefaultQuotaRefreshPolicyProvider : QuotaRefreshPolicyProvider {
    override fun load(): QuotaRefreshPolicy = QuotaRefreshPolicy()
}

interface QuotaRefreshRuntimeStore {
    fun lastRefreshTriggeredAt(): Instant?
    fun recordRefreshTriggeredAt(at: Instant)
}

interface QuotaRefreshClient {
    suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult
}

class LoginQuotaRefreshClient(
    private val lifecycle: LoginAccountLifecycle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QuotaRefreshClient {
    override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult =
        withContext(ioDispatcher) { lifecycle.refreshValidatedQuota(accountID) }
}

data class UnicomAppState(
    val accounts: List<UnicomAccount> = emptyList(),
    val refreshStates: Map<UUID, RefreshState> = emptyMap(),
    val isRefreshingAll: Boolean = false,
    val persistenceErrorMessage: String? = null,
) {
    fun refreshState(accountID: UUID): RefreshState = refreshStates[accountID] ?: RefreshState.Idle
}

/**
 * Production quota/AppState authority.
 *
 * M6-D additionally exposes one serialized balance mutation entry point so quota and balance can
 * share the same in-memory account authority. Quota persistence failure rolls back its candidate;
 * shared-balance persistence failure deliberately keeps the already-published shared-cache value,
 * matching the iOS rule that the durable shared balance cache remains authoritative.
 *
 * M12 adds a target-neutral post-commit observer. It runs only after ordinary account persistence
 * succeeds; Widget snapshot/export failures are isolated and can never roll back valid carrier data.
 */
class QuotaRefreshCoordinator(
    private val accountRepository: AccountRepository,
    private val refreshClient: QuotaRefreshClient,
    private val runtimeStore: QuotaRefreshRuntimeStore,
    private val policyProvider: QuotaRefreshPolicyProvider = SourceDefaultQuotaRefreshPolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sleeper: suspend (Long) -> Unit = { milliseconds -> delay(milliseconds) },
    private val accountsCommittedObserver: suspend (List<UnicomAccount>) -> Unit = {},
) {
    private val accountLocks = ConcurrentHashMap<UUID, Mutex>()
    private val refreshAllLock = Mutex()
    private val accountPersistenceLock = Mutex()
    private val _state = MutableStateFlow(
        UnicomAppState(accounts = accountRepository.loadAccounts().sortedBy { it.sortOrder }),
    )

    val state: StateFlow<UnicomAppState> = _state.asStateFlow()

    fun shouldAutoRefresh(
        trigger: QuotaAutomaticRefreshTrigger,
        now: Instant = Instant.now(clock),
    ): Boolean {
        val policy = policyProvider.load()
        if (!policy.automaticRefreshEnabled || _state.value.accounts.isEmpty()) return false

        when (trigger) {
            QuotaAutomaticRefreshTrigger.COLD_LAUNCH -> if (!policy.refreshOnColdLaunch) return false
            QuotaAutomaticRefreshTrigger.FOREGROUND -> if (!policy.refreshOnForeground) return false
            QuotaAutomaticRefreshTrigger.POLICY_CHANGE -> {
                if (!policy.refreshOnColdLaunch && !policy.refreshOnForeground) return false
            }
        }

        val lastRefresh = runtimeStore.lastRefreshTriggeredAt() ?: return true
        val elapsed = Duration.between(lastRefresh, now)
        if (elapsed.isNegative) return true
        val cooldownMinutes = policy.minimumIntervalMinutes.coerceAtLeast(1).toLong()
        return elapsed >= Duration.ofMinutes(cooldownMinutes)
    }

    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) {
        if (shouldAutoRefresh(trigger)) refreshAll()
    }

    suspend fun refreshAccount(accountID: UUID) {
        refreshAccountInternal(accountID = accountID, recordRefreshTriggeredAt = true)
    }

    suspend fun refreshAll() {
        if (!refreshAllLock.tryLock()) return
        try {
            val ids = _state.value.accounts.filter { it.isEnabled }.map { it.id }
            if (ids.isEmpty()) return

            runtimeStore.recordRefreshTriggeredAt(Instant.now(clock))
            _state.update { it.copy(isRefreshingAll = true) }
            val gapMilliseconds = policyProvider.load().accountGapSeconds.coerceAtLeast(0) * 1_000L

            for ((index, accountID) in ids.withIndex()) {
                refreshAccountInternal(accountID = accountID, recordRefreshTriggeredAt = false)
                if (index < ids.lastIndex && gapMilliseconds > 0) {
                    sleeper(gapMilliseconds)
                }
            }
        } finally {
            _state.update { it.copy(isRefreshingAll = false) }
            refreshAllLock.unlock()
        }
    }

    suspend fun updateAccountsFromBalance(
        transform: (List<UnicomAccount>) -> List<UnicomAccount>,
    ): Boolean = accountPersistenceLock.withLock {
        val currentAccounts = _state.value.accounts
        val candidate = transform(currentAccounts).sortedBy { it.sortOrder }
        if (candidate == currentAccounts) return@withLock true

        _state.update { it.copy(accounts = candidate, persistenceErrorMessage = null) }
        try {
            persistAccounts(candidate)
            notifyAccountsCommitted(candidate)
            true
        } catch (error: Throwable) {
            _state.update { it.copy(persistenceErrorMessage = persistenceMessage(error)) }
            false
        }
    }

    fun clearPersistenceError() {
        _state.update { it.copy(persistenceErrorMessage = null) }
    }

    private suspend fun refreshAccountInternal(
        accountID: UUID,
        recordRefreshTriggeredAt: Boolean,
    ) {
        val accountLock = accountLocks.computeIfAbsent(accountID) { Mutex() }
        if (!accountLock.tryLock()) return
        try {
            if (_state.value.accounts.none { it.id == accountID }) return
            if (recordRefreshTriggeredAt) runtimeStore.recordRefreshTriggeredAt(Instant.now(clock))
            setRefreshState(accountID, RefreshState.Loading)

            try {
                val result = refreshClient.refreshValidatedQuota(accountID)
                commitQuotaSuccess(accountID, result, Instant.now(clock))
            } catch (error: CancellationException) {
                setRefreshState(accountID, RefreshState.Idle)
                throw error
            } catch (error: Throwable) {
                persistRefreshFailure(accountID, error)
            }
        } finally {
            accountLock.unlock()
        }
    }

    private suspend fun commitQuotaSuccess(
        accountID: UUID,
        result: QuotaFetchResult,
        completedAt: Instant,
    ) = accountPersistenceLock.withLock {
        val previousAccounts = _state.value.accounts
        val index = previousAccounts.indexOfFirst { it.id == accountID }
        if (index < 0) {
            removeRefreshState(accountID)
            return@withLock
        }

        val refreshedAccount = mergeQuotaResult(previousAccounts[index], result, completedAt)
        val candidateAccounts = previousAccounts.toMutableList().apply { this[index] = refreshedAccount }

        _state.update { current ->
            current.copy(
                accounts = candidateAccounts,
                refreshStates = current.refreshStates + (accountID to RefreshState.Succeeded),
                persistenceErrorMessage = null,
            )
        }

        try {
            persistAccounts(candidateAccounts)
            notifyAccountsCommitted(candidateAccounts)
        } catch (error: Throwable) {
            _state.update { current ->
                current.copy(
                    accounts = previousAccounts,
                    persistenceErrorMessage = persistenceMessage(error),
                )
            }
            throw error
        }
    }

    private suspend fun persistRefreshFailure(accountID: UUID, error: Throwable) =
        accountPersistenceLock.withLock {
            val message = error.message?.takeIf { it.isNotBlank() } ?: "刷新失败"
            val previousAccounts = _state.value.accounts
            val index = previousAccounts.indexOfFirst { it.id == accountID }
            if (index < 0) {
                removeRefreshState(accountID)
                return@withLock
            }

            val failedAccounts = previousAccounts.toMutableList().apply {
                this[index] = this[index].copy(lastErrorMessage = message)
            }
            _state.update { current ->
                current.copy(
                    accounts = failedAccounts,
                    refreshStates = current.refreshStates + (accountID to RefreshState.Failed(message)),
                )
            }

            try {
                persistAccounts(failedAccounts)
            } catch (persistenceError: Throwable) {
                _state.update { current ->
                    current.copy(
                        accounts = previousAccounts,
                        persistenceErrorMessage = persistenceMessage(persistenceError),
                    )
                }
            }
        }

    private suspend fun persistAccounts(accounts: List<UnicomAccount>) {
        withContext(ioDispatcher) { accountRepository.replaceAccounts(accounts) }
    }

    private suspend fun notifyAccountsCommitted(accounts: List<UnicomAccount>) {
        try {
            accountsCommittedObserver(accounts.sortedBy { it.sortOrder })
        } catch (_: Throwable) {
            // Snapshot/Widget side effects are intentionally non-authoritative.
        }
    }

    private fun setRefreshState(accountID: UUID, refreshState: RefreshState) {
        _state.update { current -> current.copy(refreshStates = current.refreshStates + (accountID to refreshState)) }
    }

    private fun removeRefreshState(accountID: UUID) {
        _state.update { current -> current.copy(refreshStates = current.refreshStates - accountID) }
    }

    private fun persistenceMessage(error: Throwable): String =
        "本地保存失败：${error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}"
}

private fun mergeQuotaResult(
    account: UnicomAccount,
    result: QuotaFetchResult,
    completedAt: Instant,
): UnicomAccount {
    val previousVoicePackages = account.resolvedVoicePackages
    val mergedPreferences = mergedPreferences(
        existing = account.displayPreferences,
        packages = result.packages,
        voicePackages = result.voicePackages,
    )
    var refreshed = account.copy(
        packageName = result.packageName.takeIf { it.isNotBlank() } ?: account.packageName,
        packages = result.packages,
        voicePackages = result.voicePackages,
        remainingQuerySnapshot = result.remainingQuerySnapshot?.copy(updatedAt = completedAt)
            ?: account.remainingQuerySnapshot,
        displayPreferences = mergedPreferences,
        quotaResourceStatus = result.quotaResourceStatus,
        lastUpdatedAt = completedAt,
        lastErrorMessage = null,
    )
    refreshed = synchronizeExistingResourceKindOverrides(refreshed)
    refreshed = stabilizeVoiceSummaryGroups(refreshed, previousVoicePackages)
    return refreshed
}

private fun mergedPreferences(
    existing: List<PackageDisplayPreference>,
    packages: List<FlowPackage>,
    voicePackages: List<VoicePackage>,
): List<PackageDisplayPreference> {
    val packageIDs = (packages.map { it.id } + voicePackages.map { it.id }).toSet()
    val output = existing.filter { it.packageKey in packageIDs }.toMutableList()
    var nextSortOrder = (output.maxOfOrNull { it.sortOrder } ?: -1) + 1

    for (packageID in packages.map { it.id } + voicePackages.map { it.id }) {
        if (output.none { it.packageKey == packageID }) {
            output += PackageDisplayPreference(
                packageKey = packageID,
                placement = DisplayPlacement.DETAIL_ONLY,
                sortOrder = nextSortOrder++,
            )
        }
    }
    return output
}

private fun synchronizeExistingResourceKindOverrides(account: UnicomAccount): UnicomAccount {
    val validIDs = (account.packages.map { it.id } + (account.voicePackages ?: emptyList()).map { it.id }).toSet()
    var preferences = account.displayPreferences.filter { it.packageKey in validIDs }
    val snapshot = preferences.toList()

    for (group in account.ambiguousResourceGroups) {
        val flowPreference = group.flowPackages.asSequence().mapNotNull { flow -> snapshot.firstOrNull { it.packageKey == flow.id } }.firstOrNull()
        val voicePreference = group.voicePackages.asSequence().mapNotNull { voice -> snapshot.firstOrNull { it.packageKey == voice.id } }.firstOrNull()
        val override = flowPreference?.resourceKindOverride?.takeIf { it != ResourceDisplayKind.AUTOMATIC }
            ?: voicePreference?.resourceKindOverride?.takeIf { it != ResourceDisplayKind.AUTOMATIC }
            ?: continue
        preferences = preferences.map { preference ->
            if (group.flowPackages.any { it.id == preference.packageKey } || group.voicePackages.any { it.id == preference.packageKey }) {
                preference.copy(resourceKindOverride = override)
            } else preference
        }
    }
    return account.copy(displayPreferences = preferences)
}

private fun stabilizeVoiceSummaryGroups(
    account: UnicomAccount,
    previousVoicePackages: List<VoicePackage>,
): UnicomAccount {
    val groups = account.voiceSummaryGroups ?: return account
    if (groups.isEmpty()) return account
    val currentPackages = account.resolvedVoicePackages
    if (currentPackages.isEmpty()) return account

    val currentByID = currentPackages.associateBy { it.id }
    val previousByID = previousVoicePackages.associateBy { it.id }
    val currentHints = currentPackages.map { it to voiceIdentityHint(it) }
    val updatedGroups = groups.map { group ->
        val resolvedKeys = group.packageKeys.mapNotNull { oldID ->
            if (currentByID.containsKey(oldID)) return@mapNotNull oldID
            val oldPackage = previousByID[oldID] ?: return@mapNotNull null
            val oldHint = voiceIdentityHint(oldPackage)
            currentHints.minByOrNull { (_, hint) -> voiceIdentityDistance(oldHint, hint) }
                ?.takeIf { (_, hint) -> voiceIdentityDistance(oldHint, hint) <= 12 }
                ?.first?.id
        }.distinct()
        group.copy(packageKeys = resolvedKeys)
    }
    return account.copy(voiceSummaryGroups = updatedGroups)
}

private fun voiceIdentityHint(packageValue: VoicePackage): VoicePackageIdentityHint = VoicePackageIdentityHint(
    normalizedName = packageValue.originalName.trim().filterNot(Char::isWhitespace).lowercase(Locale.ROOT),
    totalMinutes = packageValue.totalMinutes,
    remainingMinutes = packageValue.remainingMinutes,
    rawType = packageValue.rawType,
    rawCode = packageValue.rawCode,
)

private fun voiceIdentityDistance(lhs: VoicePackageIdentityHint, rhs: VoicePackageIdentityHint): Int {
    var score = 0
    if (lhs.normalizedName != rhs.normalizedName) score += 8
    if (lhs.rawCode != rhs.rawCode) score += 4
    if (lhs.rawType != rhs.rawType) score += 2
    score += numericDistanceScore(lhs.totalMinutes, rhs.totalMinutes)
    score += numericDistanceScore(lhs.remainingMinutes, rhs.remainingMinutes)
    return score
}

private fun numericDistanceScore(lhs: Double?, rhs: Double?): Int {
    if (lhs == null && rhs == null) return 0
    if (lhs == null || rhs == null) return 2
    val diff = abs(lhs - rhs)
    return when {
        diff < 0.01 -> 0
        diff < 1.0 -> 1
        else -> minOf(4, diff.roundToInt())
    }
}
