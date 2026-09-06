from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))


# Current iOS default/allowed automatic interval is 60 min / [30, 60, 90, 120, 150, 180].
replace_once(
    "data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRefreshCoordinator.kt",
    '''data class QuotaRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val refreshOnColdLaunch: Boolean = true,
    val refreshOnForeground: Boolean = true,
    val minimumIntervalMinutes: Int = 10,
    val accountGapSeconds: Int = 2,
)
''',
    '''data class QuotaRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val refreshOnColdLaunch: Boolean = true,
    val refreshOnForeground: Boolean = true,
    val minimumIntervalMinutes: Int = 60,
    val accountGapSeconds: Int = 2,
) {
    companion object {
        val ALLOWED_MINIMUM_INTERVAL_MINUTES = listOf(30, 60, 90, 120, 150, 180)
    }
}
''',
)

# Avoid nullable Instant leakage after success classification.
replace_once(
    "data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRefreshCoordinator.kt",
    '''        if (succeeded) {
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
''',
    '''        if (succeeded) {
            val successAt = requireNotNull(refreshedAt)
            when (invocation) {
                DashboardRefreshInvocation.SINGLE_MANUAL -> {
                    runtimeStore.recordSingleManualSuccess(accountID, successAt)
                    runtimeStore.recordGlobalManualSuccess(accountID, successAt)
                    runtimeStore.recordAutomaticSuccess(accountID, successAt)
                }
                DashboardRefreshInvocation.GLOBAL_MANUAL,
                DashboardRefreshInvocation.AUTOMATIC -> {
                    runtimeStore.recordGlobalManualSuccess(accountID, successAt)
                    runtimeStore.recordAutomaticSuccess(accountID, successAt)
                }
                DashboardRefreshInvocation.RAW -> Unit
            }
''',
)

# Preserve compatibility for test/fake implementations while Production overrides the manual entries.
replace_once(
    "data/refresh/src/main/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRepository.kt",
    '''    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
    suspend fun refreshAccount(accountID: UUID)
    suspend fun refreshAccountManually(accountID: UUID)
    suspend fun refreshAll()
    suspend fun refreshAllManually()
''',
    '''    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
    suspend fun refreshAccount(accountID: UUID)
    suspend fun refreshAccountManually(accountID: UUID) = refreshAccount(accountID)
    suspend fun refreshAll()
    suspend fun refreshAllManually() = refreshAll()
''',
)
replace_once(
    "app/src/main/java/com/clxmhcs/chinaunicom/data/UnicomRepository.kt",
    '''    suspend fun refreshAll()
    suspend fun refreshAllManually()
    suspend fun refreshAccount(accountID: UUID)
    suspend fun refreshAccountManually(accountID: UUID)
    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
''',
    '''    suspend fun refreshAll()
    suspend fun refreshAllManually() = refreshAll()
    suspend fun refreshAccount(accountID: UUID)
    suspend fun refreshAccountManually(accountID: UUID) = refreshAccount(accountID)
    suspend fun autoRefreshIfNeeded(trigger: QuotaAutomaticRefreshTrigger)
''',
)

# Match iOS normalization: invalid saved minimum interval falls back to the current default.
replace_once(
    "data/settings/src/main/kotlin/com/clxmhcs/chinaunicom/data/settings/SettingsRepository.kt",
    '''    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult {
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.quota
        val persisted = storage.write(codec.mergeQuotaPolicy(previousRaw, policy)); if (persisted) _quotaRefreshPolicy.value = policy
        return QuotaRefreshPolicySaveResult(persisted, previous == null || previous != policy, policy)
    }
''',
    '''    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult {
        val normalized = policy.copy(
            minimumIntervalMinutes = policy.minimumIntervalMinutes.takeIf(
                QuotaRefreshPolicy.ALLOWED_MINIMUM_INTERVAL_MINUTES::contains,
            ) ?: QuotaRefreshPolicy().minimumIntervalMinutes,
        )
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.quota
        val persisted = storage.write(codec.mergeQuotaPolicy(previousRaw, normalized)); if (persisted) _quotaRefreshPolicy.value = normalized
        return QuotaRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }
''',
)
replace_once(
    "data/settings/src/main/kotlin/com/clxmhcs/chinaunicom/data/settings/SettingsRepository.kt",
    '''                intValue(q?.get(MINIMUM_INTERVAL_MINUTES_KEY)) ?: qd.minimumIntervalMinutes,
''',
    '''                (intValue(q?.get(MINIMUM_INTERVAL_MINUTES_KEY)) ?: qd.minimumIntervalMinutes).takeIf(
                    QuotaRefreshPolicy.ALLOWED_MINIMUM_INTERVAL_MINUTES::contains,
                ) ?: qd.minimumIntervalMinutes,
''',
)

# Settings regression now asserts source-equivalent normalization instead of accepting invalid 15 minutes.
p = Path("data/settings/src/test/kotlin/com/clxmhcs/chinaunicom/data/settings/SettingsRepositoryTest.kt")
s = p.read_text()
s = s.replace(
    '''        assertEquals(policy, repository.quotaRefreshPolicy.value)
''',
    '''        val normalized = policy.copy(minimumIntervalMinutes = 60)
        assertEquals(normalized, repository.quotaRefreshPolicy.value)
''',
    1,
)
s = s.replace(
    '''        assertEquals(JsonPrimitive(15), quota["minimumIntervalMinutes"])
''',
    '''        assertEquals(JsonPrimitive(60), quota["minimumIntervalMinutes"])
''',
    1,
)
p.write_text(s)

# Update automatic-cooldown test to the current iOS per-account success/failure clocks.
p = Path("data/refresh/src/test/kotlin/com/clxmhcs/chinaunicom/data/refresh/QuotaRefreshCoordinatorTest.kt")
s = p.read_text()
old = '''        runtime.last = now.minusSeconds(5 * 60)
        assertFalse(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.last = now.minusSeconds(10 * 60)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.last = now.plusSeconds(30)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
'''
new = '''        runtime.automaticSuccess[firstID] = now.minusSeconds(5 * 60)
        runtime.automaticSuccess[secondID] = now.minusSeconds(5 * 60)
        assertFalse(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.automaticSuccess[firstID] = now.minusSeconds(60 * 60)
        runtime.automaticSuccess[secondID] = now.minusSeconds(60 * 60)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.automaticSuccess[firstID] = now.plusSeconds(30)
        runtime.automaticSuccess[secondID] = now.plusSeconds(30)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
'''
if s.count(old) != 1:
    raise SystemExit("QuotaRefreshCoordinatorTest cooldown anchor mismatch")
s = s.replace(old, new, 1)
old_fake = '''private class FakeRuntimeStore : QuotaRefreshRuntimeStore {
    var last: Instant? = null
    val recorded = mutableListOf<Instant>()

    override fun lastRefreshTriggeredAt(): Instant? = last

    override fun recordRefreshTriggeredAt(at: Instant) {
        last = at
        recorded += at
    }
}
'''
new_fake = '''private class FakeRuntimeStore : QuotaRefreshRuntimeStore {
    var last: Instant? = null
    val recorded = mutableListOf<Instant>()
    val singleManualSuccess = mutableMapOf<UUID, Instant>()
    val globalManualSuccess = mutableMapOf<UUID, Instant>()
    val automaticSuccess = mutableMapOf<UUID, Instant>()
    val automaticFailures = mutableMapOf<UUID, Instant>()

    override fun lastRefreshTriggeredAt(): Instant? = last

    override fun recordRefreshTriggeredAt(at: Instant) {
        last = at
        recorded += at
    }

    override fun lastSingleManualSuccessAt(accountID: UUID): Instant? = singleManualSuccess[accountID]
    override fun lastGlobalManualSuccessAt(accountID: UUID): Instant? = globalManualSuccess[accountID]
    override fun lastAutomaticSuccessAt(accountID: UUID): Instant? = automaticSuccess[accountID]
    override fun lastAutomaticFailureAttemptAt(accountID: UUID): Instant? = automaticFailures[accountID]
    override fun recordSingleManualSuccess(accountID: UUID, at: Instant) { singleManualSuccess[accountID] = at }
    override fun recordGlobalManualSuccess(accountID: UUID, at: Instant) { globalManualSuccess[accountID] = at }
    override fun recordAutomaticSuccess(accountID: UUID, at: Instant) {
        automaticSuccess[accountID] = at
        automaticFailures.remove(accountID)
    }
    override fun recordAutomaticFailureAttempt(accountID: UUID, at: Instant) { automaticFailures[accountID] = at }
}
'''
if s.count(old_fake) != 1:
    raise SystemExit("FakeRuntimeStore anchor mismatch")
s = s.replace(old_fake, new_fake, 1)
p.write_text(s)

# Frozen baseline docs were stale relative to current iOS source; correct only the quota-default statement.
p = Path("docs/migration/M6_BASELINE.md")
s = p.read_text()
s = s.replace(
    "source quota defaults are automatic=true, cold-launch=true, foreground=true, minimum interval 10 minutes, account gap 2 seconds;",
    "current iOS source quota defaults are automatic=true, cold-launch=true, foreground=true, minimum interval 60 minutes, account gap 2 seconds; invalid stored minimum intervals normalize to 60 minutes;",
    1,
)
p.write_text(s)
