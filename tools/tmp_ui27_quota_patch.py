from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))


# 1) data/refresh: keep raw refresh paths for automation/widget; add explicit App-manual gates.
p = Path("data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRefreshCoordinator.kt")
s = p.read_text()
old = '''fun interface QuotaRefreshPolicyProvider {
    fun load(): QuotaRefreshPolicy
}

object SourceDefaultQuotaRefreshPolicyProvider : QuotaRefreshPolicyProvider {
    override fun load(): QuotaRefreshPolicy = QuotaRefreshPolicy()
}

interface QuotaRefreshRuntimeStore {
    fun lastRefreshTriggeredAt(): Instant?
    fun recordRefreshTriggeredAt(at: Instant)
}
'''
new = '''fun interface QuotaRefreshPolicyProvider {
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
'''
if s.count(old) != 1:
    raise SystemExit("QuotaRefreshCoordinator definitions anchor mismatch")
s = s.replace(old, new, 1)
old = '''    private val runtimeStore: QuotaRefreshRuntimeStore,
    private val policyProvider: QuotaRefreshPolicyProvider = SourceDefaultQuotaRefreshPolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
'''
new = '''    private val runtimeStore: QuotaRefreshRuntimeStore,
    private val policyProvider: QuotaRefreshPolicyProvider = SourceDefaultQuotaRefreshPolicyProvider,
    private val dashboardTimingPolicyProvider: QuotaDashboardTimingPolicyProvider = SourceDefaultQuotaDashboardTimingPolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
'''
if s.count(old) != 1:
    raise SystemExit("QuotaRefreshCoordinator constructor anchor mismatch")
s = s.replace(old, new, 1)
start = s.index("    fun shouldAutoRefresh(")
end = s.index("    /**\n     * Balance-only account mutation path.", start)
new_block = '''    fun shouldAutoRefresh(
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

'''
s = s[:start] + new_block + s[end:]
p.write_text(s)

# 2) Repository boundaries: raw refresh is retained for Widget/automation; dashboard gets manual entries.
replace_once(
    "data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRepository.kt",
    """    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)\n    suspend fun refreshAccount(accountID: UUID)\n    suspend fun refreshAll()\n""",
    """    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)\n    suspend fun refreshAccount(accountID: UUID)\n    suspend fun refreshAccountManually(accountID: UUID)\n    suspend fun refreshAll()\n    suspend fun refreshAllManually()\n""",
)
replace_once(
    "data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRepository.kt",
    """    override suspend fun refreshAccount(accountID: UUID) = coordinator.refreshAccount(accountID)\n\n    override suspend fun refreshAll() = coordinator.refreshAll()\n""",
    """    override suspend fun refreshAccount(accountID: UUID) = coordinator.refreshAccount(accountID)\n\n    override suspend fun refreshAccountManually(accountID: UUID) = coordinator.refreshAccountManually(accountID)\n\n    override suspend fun refreshAll() = coordinator.refreshAll()\n\n    override suspend fun refreshAllManually() = coordinator.refreshAllManually()\n""",
)
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/data/UnicomRepository.kt",
    """    suspend fun refreshAll()\n    suspend fun refreshAccount(accountID: UUID)\n    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)\n""",
    """    suspend fun refreshAll()\n    suspend fun refreshAllManually()\n    suspend fun refreshAccount(accountID: UUID)\n    suspend fun refreshAccountManually(accountID: UUID)\n    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)\n""",
)
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/data/ProductionUnicomRepository.kt",
    """    override suspend fun refreshAll() {\n        quotaRepository.refreshAll()\n        publishCommittedAccounts()\n    }\n\n    override suspend fun refreshAccount(accountID: UUID) {\n        quotaRepository.refreshAccount(accountID)\n        publishCommittedAccounts()\n    }\n""",
    """    override suspend fun refreshAll() {\n        quotaRepository.refreshAll()\n        publishCommittedAccounts()\n    }\n\n    override suspend fun refreshAllManually() {\n        quotaRepository.refreshAllManually()\n        publishCommittedAccounts()\n    }\n\n    override suspend fun refreshAccount(accountID: UUID) {\n        quotaRepository.refreshAccount(accountID)\n        publishCommittedAccounts()\n    }\n\n    override suspend fun refreshAccountManually(accountID: UUID) {\n        quotaRepository.refreshAccountManually(accountID)\n        publishCommittedAccounts()\n    }\n""",
)
replace_once(
    "app/src/debug/java/com/clxmhcs/chinaunicom/data/FakeUnicomRepository.kt",
    """    override suspend fun refreshAll() = Unit\n    override suspend fun refreshAccount(accountID: UUID) = Unit\n""",
    """    override suspend fun refreshAll() = Unit\n    override suspend fun refreshAllManually() = Unit\n    override suspend fun refreshAccount(accountID: UUID) = Unit\n    override suspend fun refreshAccountManually(accountID: UUID) = Unit\n""",
)

# 3) Main dashboard buttons use App-manual entries; other business and automation stay raw.
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowViewModel.kt",
    "runCatching { repository.refreshAll() }",
    "runCatching { repository.refreshAllManually() }",
)
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowViewModel.kt",
    """    fun refreshAccount(accountID: UUID) {\n        viewModelScope.launch { repository.refreshAccount(accountID) }\n    }\n""",
    """    fun refreshAccount(accountID: UUID) {\n        viewModelScope.launch { repository.refreshAccount(accountID) }\n    }\n\n    fun refreshAccountManually(accountID: UUID) {\n        viewModelScope.launch { repository.refreshAccountManually(accountID) }\n    }\n""",
)
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowHomeScreen.kt",
    "onRefreshAccount = { flowViewModel.refreshAccount(detailAccount.id) },",
    "onRefreshAccount = { flowViewModel.refreshAccountManually(detailAccount.id) },",
)

# 4) Android runtime adapter delegates source-shaped per-account clocks to the dashboard settings store.
p = Path("app/src/main/java/com/clxmhcs/chinaunicom/data/AndroidQuotaRefreshRuntimeStore.kt")
s = p.read_text()
anchor = '''    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
'''
if s.count(anchor) != 1:
    raise SystemExit("AndroidQuotaRefreshRuntimeStore preferences anchor mismatch")
s = s.replace(anchor, anchor + "    private val dashboardSettings = AndroidDashboardRefreshSettings(context)\n", 1)
anchor = '''    override fun recordRefreshTriggeredAt(at: Instant) {
        check(preferences.edit().putLong(LAST_TRIGGERED_AT_KEY, at.toEpochMilli()).commit()) {
            "Unable to persist quota refresh trigger time"
        }
    }

'''
if s.count(anchor) != 1:
    raise SystemExit("AndroidQuotaRefreshRuntimeStore record anchor mismatch")
extension = anchor + '''    override fun lastSingleManualSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastSingleManualSuccessAt(accountID)

    override fun lastGlobalManualSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastGlobalManualSuccessAt(accountID)

    override fun lastAutomaticSuccessAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastAutomaticSuccessAt(accountID)

    override fun lastAutomaticFailureAttemptAt(accountID: java.util.UUID): Instant? =
        dashboardSettings.lastAutomaticFailureAttemptAt(accountID)

    override fun recordSingleManualSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordSingleManualSuccess(accountID, at)) { "Unable to persist single-manual refresh success" }
    }

    override fun recordGlobalManualSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordGlobalManualSuccess(accountID, at)) { "Unable to persist global-manual refresh success" }
    }

    override fun recordAutomaticSuccess(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordAutomaticSuccess(accountID, at)) { "Unable to persist automatic refresh success" }
    }

    override fun recordAutomaticFailureAttempt(accountID: java.util.UUID, at: Instant) {
        check(dashboardSettings.recordAutomaticFailureAttempt(accountID, at)) { "Unable to persist automatic refresh failure" }
    }

'''
s = s.replace(anchor, extension, 1)
old_clear = "    fun clear(): Boolean = preferences.edit().remove(LAST_TRIGGERED_AT_KEY).commit()\n"
new_clear = '''    fun clear(): Boolean {
        val legacyCleared = preferences.edit().clear().commit()
        val dashboardCleared = dashboardSettings.clear()
        return legacyCleared && dashboardCleared
    }
'''
if s.count(old_clear) != 1:
    raise SystemExit("AndroidQuotaRefreshRuntimeStore clear anchor mismatch")
s = s.replace(old_clear, new_clear, 1)
p.write_text(s)

# 5) Production/debug wiring supplies the three current-iOS dashboard timing values.
for path in [
    "app/src/debug/java/com/clxmhcs/chinaunicom/data/UnicomRepositoryProvider.kt",
    "app/src/release/java/com/clxmhcs/chinaunicom/data/UnicomRepositoryProvider.kt",
]:
    p = Path(path)
    s = p.read_text()
    import_anchor = "import com.clxmhcs.chinaunicom.data.refresh.LoginQuotaRefreshClient\n"
    if s.count(import_anchor) != 1:
        raise SystemExit(f"{path}: import anchor mismatch")
    s = s.replace(
        import_anchor,
        import_anchor
        + "import com.clxmhcs.chinaunicom.data.refresh.QuotaDashboardTimingPolicy\n"
        + "import com.clxmhcs.chinaunicom.data.refresh.QuotaDashboardTimingPolicyProvider\n",
        1,
    )
    anchor = '''            policyProvider = QuotaRefreshPolicyProvider {
                settingsRepository.loadQuotaRefreshPolicy()
            },
'''
    if s.count(anchor) != 1:
        raise SystemExit(f"{path}: policyProvider anchor mismatch")
    replacement = anchor + '''            dashboardTimingPolicyProvider = QuotaDashboardTimingPolicyProvider {
                val timing = AndroidDashboardRefreshSettings(appContext).load()
                QuotaDashboardTimingPolicy(
                    singleManualIntervalMinutes = timing.singleManualIntervalMinutes,
                    globalManualIntervalMinutes = timing.globalManualIntervalMinutes,
                    automaticFailureRetryMinutes = timing.automaticFailureRetryMinutes,
                )
            },
'''
    s = s.replace(anchor, replacement, 1)
    p.write_text(s)

# 6) Route to the new quota editor and make global restore reset auxiliary dashboard timing values.
p = Path("app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsAppRefreshLogicIosScreen.kt")
s = p.read_text()
old_decl = "private fun QuotaRefreshDraftEditor(policy: QuotaRefreshPolicy, onChange: (QuotaRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) ="
new_decl = "private fun LegacyQuotaRefreshDraftEditor(policy: QuotaRefreshPolicy, onChange: (QuotaRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) ="
if s.count(old_decl) != 1:
    raise SystemExit(f"legacy quota editor declaration count={s.count(old_decl)}")
s = s.replace(old_decl, new_decl, 1)
old_restore = '''                    val defaults = AppRefreshDraft.defaults()
                    if (saveCurrent(defaults)) {
                        draft = defaults
                        saved = defaults
                    }
'''
new_restore = '''                    val defaults = AppRefreshDraft.defaults()
                    val timingRestored = com.clxmhcs.chinaunicom.data.AndroidDashboardRefreshSettings(context).restoreDefaults()
                    if (timingRestored && saveCurrent(defaults)) {
                        draft = defaults
                        saved = defaults
                    }
'''
if s.count(old_restore) != 1:
    raise SystemExit("restore defaults anchor mismatch")
s = s.replace(old_restore, new_restore, 1)
p.write_text(s)
