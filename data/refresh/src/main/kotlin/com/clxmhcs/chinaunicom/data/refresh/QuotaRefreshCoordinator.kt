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

data class QuotaDashboardTimingPolicy(
    val singleManualIntervalMinutes: Int = 10,
    val globalManualIntervalMinutes: Int = 30,
    val automaticFailureRetryMinutes: Int = 5,
)

fun interface QuotaDashboardTimingPolicyProvider {
    fun load(): QuotaDashboardTimingPolicy
}

object SourceDefaultQuotaDashboardTimingPolicyProvider : QuotaDashboardTimingPolicyProvider {
    override fun load(): QuotaDashboardTimingPolicy = QuotaDashboardTimingPolicy()
}

interface QuotaRefreshRuntimeStore {
    fun lastRefreshTriggeredAt(): Instant?
    fun recordRefreshTriggeredAt(at: Instant)

    fun lastSingleManualSuccessAt(accountID: UUID): Instant? = null
    fun lastGlobalManualSuccessAt(accountID: UUID): Instant? = null
    fun lastAutomaticSuccessAt(accountID: UUID): Instant? = null
    fun lastAutomaticFailureAttemptAt(accountID: UUID): Instant? = null
    fun recordSingleManualSuccess(accountID: UUID, at: Instant) = Unit
    fun recordGlobalManualSuccess(accountID: UUID, at: Instant) = Unit
    fun recordAutomaticSuccess(accountID: UUID, at: Instant) = Unit
    fun recordAutomaticFailureAttempt(accountID: UUID, at: Instant) = Unit
}

private enum class DashboardRefreshInvocation {
    RAW,
    SINGLE_MANUAL,
    GLOBAL_MANUAL,
    AUTOMATIC,
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
 */
class QuotaRefreshCoordinator(
    private val accountRepository: AccountRepository,
    private val refreshClient: QuotaRefreshClient,
    private val runtimeStore: QuotaRefreshRuntimeStore,
    private val policyProvider: QuotaRefreshPolicyProvider = SourceDefaultQuotaRefreshPolicyProvider,
    private val dashboardTimingPolicyProvider: QuotaDashboardTimingPolicyProvider = SourceDefaultQuotaDashboardTimingPolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sleeper: suspend (Long) -> Unit = { milliseconds -> delay(milliseconds) },
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
        if (!automaticTriggerEnabled(policy, trigger) || _state.value.accounts.isEmpty()) return false
        return automaticRefreshCandidateIDs(
            policy = policy,
            timing = dashboardTimingPolicyProvider.load(),
            now = now,
        ).isNotEmpty()
    }

    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger) {
        val policy = policyProvider.load()
        if (!automaticTriggerEnabled(policy, trigger)) return
        val ids = automaticRefreshCandidateIDs(
            policy = policy,
            timing = dashboardTimingPolicyProvider.load(),
            now = Instant.now(clock),
        )
        refreshBatch(ids, DashboardRefreshInvocation.AUTOMATIC)
    }

    /** Raw single-account refresh used by non-dashboard business flows and Widget/App services. */
    suspend fun refreshAccount(accountID: UUID) {
        refreshAccountInternal(accountID = accountID, recordRefreshTriggeredAt = true)
    }

    /** Dashboard logo refresh: only this account's own successful manual timestamp gates the request. */
    suspend fun refreshAccountManually(accountID: UUID) {
        val account = _state.value.accounts.firstOrNull { it.id == accountID } ?: return
        val timing = dashboardTimingPolicyProvider.load()
        val now = Instant.now(clock)
        val last = runtimeStore.lastSingleManualSuccessAt(accountID)
        if (last != null && isWithinCooldown(last, now, timing.singleManualIntervalMinutes)) return

        val before = account.lastUpdatedAt
        refreshAccountInternal(accountID = accountID, recordRefreshTriggeredAt = false)
        recordTrackedOutcome(
            accountID = accountID,
            previousUpdatedAt = before,
            invocation = DashboardRefreshInvocation.SINGLE_MANUAL,
            failureAttemptAt = Instant.now(clock),
        )
    }

    /** Raw batch refresh remains available for automation; dashboard manual refresh uses the gated entry below. */
    suspend fun refreshAll() {
        val ids = _state.value.accounts.filter { it.isEnabled }.map { it.id }
        refreshBatch(ids, DashboardRefreshInvocation.RAW)
    }

    /** Dashboard refresh-all/pull-to-refresh: each enabled account has its own global-manual success clock. */
    suspend fun refreshAllManually() {
        val timing = dashboardTimingPolicyProvider.load()
        val now = Instant.now(clock)
        val ids = _state.value.accounts
            .filter { it.isEnabled }
            .filter { account ->
                val last = latestQuotaSuccessAt(account)
                last == null || !isWithinCooldown(last, now, timing.globalManualIntervalMinutes)
            }
            .map { it.id }
        refreshBatch(ids, DashboardRefreshInvocation.GLOBAL_MANUAL)
    }

    private fun automaticTriggerEnabled(
        policy: QuotaRefreshPolicy,
        trigger: QuotaAutomaticRefreshTrigger,
    ): Boolean {
        if (!policy.automaticRefreshEnabled) return false
        return when (trigger) {
            QuotaAutomaticRefreshTrigger.COLD_LAUNCH -> policy.refreshOnColdLaunch
            QuotaAutomaticRefreshTrigger.FOREGROUND -> policy.refreshOnForeground
            QuotaAutomaticRefreshTrigger.POLICY_CHANGE -> policy.refreshOnColdLaunch || policy.refreshOnForeground
        }
    }

    private fun automaticRefreshCandidateIDs(
        policy: QuotaRefreshPolicy,
        timing: QuotaDashboardTimingPolicy,
        now: Instant,
    ): List<UUID> = _state.value.accounts
        .filter { it.isEnabled }
        .filter { account ->
            val lastSuccess = latestQuotaSuccessAt(account)
            if (lastSuccess != null && isWithinCooldown(lastSuccess, now, policy.minimumIntervalMinutes)) {
                return@filter false
            }
            val lastFailure = runtimeStore.lastAutomaticFailureAttemptAt(account.id)
            if (lastFailure != null && isWithinCooldown(lastFailure, now, timing.automaticFailureRetryMinutes)) {
                return@filter false
            }
            true
        }
        .map { it.id }

    private suspend fun refreshBatch(
        accountIDs: List<UUID>,
        invocation: DashboardRefreshInvocation,
    ) {
        if (accountIDs.isEmpty() || !refreshAllLock.tryLock()) return
        try {
            if (invocation == DashboardRefreshInvocation.RAW) {
                runtimeStore.recordRefreshTriggeredAt(Instant.now(clock))
            }
            _state.update { it.copy(isRefreshingAll = true) }
            val gapMilliseconds = policyProvider.load().accountGapSeconds.coerceAtLeast(0) * 1_000L

            for ((index, accountID) in accountIDs.withIndex()) {
                val before = _state.value.accounts.firstOrNull { it.id == accountID }?.lastUpdatedAt
                refreshAccountInternal(accountID = accountID, recordRefreshTriggeredAt = false)
                recordTrackedOutcome(
                    accountID = accountID,
                    previousUpdatedAt = before,
                    invocation = invocation,
                    failureAttemptAt = Instant.now(clock),
                )
                if (index < accountIDs.lastIndex && gapMilliseconds > 0) {
                    sleeper(gapMilliseconds)
                }
            }
        } finally {
            _state.update { it.copy(isRefreshingAll = false) }
            refreshAllLock.unlock()
        }
    }

    private fun recordTrackedOutcome(
        accountID: UUID,
        previousUpdatedAt: Instant?,
        invocation: DashboardRefreshInvocation,
        failureAttemptAt: Instant,
    ) {
        if (invocation == DashboardRefreshInvocation.RAW) return
        val current = _state.value
        val refreshedAt = current.accounts.firstOrNull { it.id == accountID }?.lastUpdatedAt
        val succeeded = current.refreshState(accountID) == RefreshState.Succeeded &&
            refreshedAt != null &&
            (previousUpdatedAt == null || refreshedAt.isAfter(previousUpdatedAt))

        if (succeeded) {
            when (invocation) {
                DashboardRefreshInvocation.SINGLE_MANUAL -> {
                    runtimeStore.recordSingleManualSuccess(accountID, refreshedAt!!)
                    runtimeStore.recordGlobalManualSuccess(accountID, refreshedAt)
                    runtimeStore.recordAutomaticSuccess(accountID, refreshedAt)
                }
                DashboardRefreshInvocation.GLOBAL_MANUAL,
                DashboardRefreshInvocation.AUTOMATIC -> {
                    runtimeStore.recordGlobalManualSuccess(accountID, refreshedAt!!)
                    runtimeStore.recordAutomaticSuccess(accountID, refreshedAt)
                }
                DashboardRefreshInvocation.RAW -> Unit
            }
        } else if (
            invocation == DashboardRefreshInvocation.AUTOMATIC &&
            current.refreshState(accountID) is RefreshState.Failed
        ) {
            runtimeStore.recordAutomaticFailureAttempt(accountID, failureAttemptAt)
        }
    }

    private fun latestQuotaSuccessAt(account: UnicomAccount): Instant? {
        val successes = listOfNotNull(
            account.lastUpdatedAt,
            runtimeStore.lastSingleManualSuccessAt(account.id),
            runtimeStore.lastGlobalManualSuccessAt(account.id),
            runtimeStore.lastAutomaticSuccessAt(account.id),
        )
        return successes.maxOrNull() ?: runtimeStore.lastRefreshTriggeredAt()
    }

    private fun isWithinCooldown(last: Instant, now: Instant, minutes: Int): Boolean {
        val elapsed = Duration.between(last, now)
        if (elapsed.isNegative) return false
        return elapsed < Duration.ofMinutes(minutes.coerceAtLeast(1).toLong())
    }

    /**
     * Balance-only account mutation path. The candidate is published first because the durable
     * SharedBalanceCacheStore has already committed the successful network value. If ordinary
     * accounts.json persistence fails, keep the published value and surface persistenceErrorMessage;
     * a later shared-cache synchronization can repair the local snapshot.
     */
    suspend fun updateAccountsFromBalance(
        transform: (List<UnicomAccount>) -> List<UnicomAccount>,
    ): Boolean = accountPersistenceLock.withLock {
        val currentAccounts = _state.value.accounts
        val candidate = transform(currentAccounts).sortedBy { it.sortOrder }
        if (candidate == currentAccounts) return@withLock true

        _state.update { it.copy(accounts = candidate, persistenceErrorMessage = null) }
        try {
            persistAccounts(candidate)
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
            if (recordRefreshTriggeredAt) {
                runtimeStore.recordRefreshTriggeredAt(Instant.now(clock))
            }
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

        val refreshedAccount = mergeQuotaResult(
            account = previousAccounts[index],
            result = result,
            completedAt = completedAt,
        )
        val candidateAccounts = previousAccounts.toMutableList().apply {
            this[index] = refreshedAccount
        }

        _state.update { current ->
            current.copy(
                accounts = candidateAccounts,
                refreshStates = current.refreshStates + (accountID to RefreshState.Succeeded),
                persistenceErrorMessage = null,
            )
        }

        try {
            persistAccounts(candidateAccounts)
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

    private fun setRefreshState(accountID: UUID, refreshState: RefreshState) {
        _state.update { current ->
            current.copy(refreshStates = current.refreshStates + (accountID to refreshState))
        }
    }

    private fun removeRefreshState(accountID: UUID) {
        _state.update { current ->
            current.copy(refreshStates = current.refreshStates - accountID)
        }
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

    fun upsert(packageKey: String, kind: ResourceDisplayKind) {
        val index = preferences.indexOfFirst { it.packageKey == packageKey }
        preferences = if (index >= 0) {
            preferences.toMutableList().apply {
                this[index] = this[index].copy(
                    resourceKindOverride = kind,
                    placement = DisplayPlacement.DETAIL_ONLY,
                )
            }
        } else {
            preferences + PackageDisplayPreference(
                packageKey = packageKey,
                resourceKindOverride = kind,
                placement = DisplayPlacement.DETAIL_ONLY,
                sortOrder = (preferences.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )
        }
    }

    fun clear(packageKey: String) {
        val index = preferences.indexOfFirst { it.packageKey == packageKey }
        if (index >= 0) {
            preferences = preferences.toMutableList().apply {
                this[index] = this[index].copy(resourceKindOverride = null)
            }
        }
    }

    for (preference in snapshot) {
        when (preference.resourceKindOverride) {
            ResourceDisplayKind.VOICE -> {
                val flow = account.packages.firstOrNull { it.id == preference.packageKey } ?: continue
                (account.voicePackages ?: emptyList())
                    .filter { resourcesLookEquivalent(flow, it) }
                    .forEach { upsert(it.id, ResourceDisplayKind.VOICE) }
            }

            ResourceDisplayKind.FLOW -> {
                val voice = (account.voicePackages ?: emptyList())
                    .firstOrNull { it.id == preference.packageKey } ?: continue
                account.packages
                    .filter { resourcesLookEquivalent(it, voice) }
                    .forEach { upsert(it.id, ResourceDisplayKind.FLOW) }
            }

            ResourceDisplayKind.AUTOMATIC -> {
                account.packages.firstOrNull { it.id == preference.packageKey }?.let { flow ->
                    (account.voicePackages ?: emptyList())
                        .filter { resourcesLookEquivalent(flow, it) }
                        .forEach { clear(it.id) }
                }
                (account.voicePackages ?: emptyList())
                    .firstOrNull { it.id == preference.packageKey }
                    ?.let { voice ->
                        account.packages
                            .filter { resourcesLookEquivalent(it, voice) }
                            .forEach { clear(it.id) }
                    }
            }

            null -> Unit
        }
    }
    return account.copy(displayPreferences = preferences)
}

private fun resourcesLookEquivalent(flow: FlowPackage, voice: VoicePackage): Boolean {
    if (resourceNameKey(flow.originalName) != resourceNameKey(voice.originalName)) return false
    val flowValues = positiveResourceValues(listOf(flow.totalMB, flow.usedMB, flow.remainingMB))
    val voiceValues = positiveResourceValues(listOf(voice.totalMinutes, voice.usedMinutes, voice.remainingMinutes))
    return flowValues.isNotEmpty() && voiceValues.isNotEmpty() && flowValues.any(voiceValues::contains)
}

private fun positiveResourceValues(values: List<Double?>): Set<Int> = values.mapNotNull { value ->
    value?.takeIf { it.isFinite() && it > 0.0001 }?.let { (it * 100).roundToInt() }
}.toSet()

private fun resourceNameKey(value: String): String = value
    .replace('（', '(')
    .replace('）', ')')
    .replace(Regex("\\(语音\\)"), "")
    .replace(Regex("\\([^)]*\\)"), "")
    .filterNot(Char::isWhitespace)
    .lowercase(Locale.ROOT)

private fun stabilizeVoiceSummaryGroups(
    account: UnicomAccount,
    previousVoicePackages: List<VoicePackage>,
): UnicomAccount {
    val groups = account.voiceSummaryGroups?.takeIf { it.isNotEmpty() } ?: return account
    val previousByID = previousVoicePackages.associateBy { it.id }
    val currentPackages = account.resolvedVoicePackages
    val currentByID = currentPackages.associateBy { it.id }

    val stabilized = groups.map { originalGroup ->
        var hints = originalGroup.packageIdentityHints.orEmpty().toMutableMap()
        for (packageKey in originalGroup.packageKeys) {
            if (hints[packageKey] == null) {
                (previousByID[packageKey] ?: currentByID[packageKey])?.let { packageValue ->
                    hints[packageKey] = voiceIdentityHint(packageValue)
                }
            }
        }

        val remappedKeys = mutableListOf<String>()
        val remappedHints = mutableMapOf<String, VoicePackageIdentityHint>()
        for (packageKey in originalGroup.packageKeys) {
            val current = currentByID[packageKey]
            if (current != null) {
                if (current.id !in remappedKeys) remappedKeys += current.id
                remappedHints[current.id] = voiceIdentityHint(current)
                continue
            }

            val matched = hints[packageKey]?.let { matchingVoicePackage(it, currentPackages) }
            if (matched != null) {
                if (matched.id !in remappedKeys) remappedKeys += matched.id
                remappedHints[matched.id] = voiceIdentityHint(matched)
                continue
            }

            if (packageKey !in remappedKeys) remappedKeys += packageKey
            hints[packageKey]?.let { remappedHints[packageKey] = it }
        }

        originalGroup.copy(
            packageKeys = remappedKeys,
            packageIdentityHints = remappedHints.takeIf { it.isNotEmpty() },
        )
    }
    return account.copy(voiceSummaryGroups = stabilized)
}

private fun voiceIdentityHint(value: VoicePackage) = VoicePackageIdentityHint(
    originalName = value.originalName,
    rawType = value.rawType,
    rawCode = value.rawCode,
    isShared = value.isShared,
    isUnlimited = value.isUnlimited,
    totalMinutes = value.totalMinutes,
)

private fun matchingVoicePackage(
    hint: VoicePackageIdentityHint,
    candidates: List<VoicePackage>,
): VoicePackage? {
    val scoped = candidates.filter {
        it.isShared == hint.isShared && it.isUnlimited == hint.isUnlimited
    }
    if (scoped.isEmpty()) return null

    val name = normalizedVoiceIdentityText(hint.originalName)
    val rawType = normalizedVoiceIdentityText(hint.rawType.orEmpty())
    val rawCode = normalizedVoiceIdentityText(hint.rawCode.orEmpty())
    fun unique(matches: List<VoicePackage>) = matches.singleOrNull()

    if (name.isNotEmpty() && rawType.isNotEmpty() && rawCode.isNotEmpty()) {
        unique(scoped.filter {
            normalizedVoiceIdentityText(it.originalName) == name &&
                normalizedVoiceIdentityText(it.rawType.orEmpty()) == rawType &&
                normalizedVoiceIdentityText(it.rawCode.orEmpty()) == rawCode
        })?.let { return it }
    }
    if (name.isNotEmpty() && rawCode.isNotEmpty()) {
        unique(scoped.filter {
            normalizedVoiceIdentityText(it.originalName) == name &&
                normalizedVoiceIdentityText(it.rawCode.orEmpty()) == rawCode
        })?.let { return it }
    }
    if (name.isNotEmpty() && rawType.isNotEmpty()) {
        unique(scoped.filter {
            normalizedVoiceIdentityText(it.originalName) == name &&
                normalizedVoiceIdentityText(it.rawType.orEmpty()) == rawType
        })?.let { return it }
    }
    if (name.isNotEmpty()) {
        unique(scoped.filter { normalizedVoiceIdentityText(it.originalName) == name })?.let { return it }
    }
    if (rawType.isNotEmpty() && rawCode.isNotEmpty()) {
        return unique(scoped.filter {
            normalizedVoiceIdentityText(it.rawType.orEmpty()) == rawType &&
                normalizedVoiceIdentityText(it.rawCode.orEmpty()) == rawCode &&
                voiceTotalsMatch(hint.totalMinutes, it.totalMinutes)
        })
    }
    return null
}

private fun normalizedVoiceIdentityText(value: String): String = value
    .trim()
    .replace('（', '(')
    .replace('）', ')')
    .filterNot(Char::isWhitespace)
    .lowercase(Locale.ROOT)

private fun voiceTotalsMatch(lhs: Double?, rhs: Double?): Boolean = when {
    lhs == null && rhs == null -> true
    lhs != null && rhs != null -> lhs.isFinite() && rhs.isFinite() && abs(lhs - rhs) <= 0.01
    else -> false
}
