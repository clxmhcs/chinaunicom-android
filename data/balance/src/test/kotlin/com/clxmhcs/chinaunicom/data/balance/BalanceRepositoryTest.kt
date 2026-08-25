package com.clxmhcs.chinaunicom.data.balance

import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.BalanceFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshClient
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshCoordinator
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshRuntimeStore
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicySaveResult
import com.clxmhcs.chinaunicom.data.settings.QuotaRefreshPolicySaveResult
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceRepositoryTest {
    private val now = Instant.parse("2026-08-25T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val first = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val second = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun groupedUnitUsesHomeRepresentativeAndPublishesOneResultToAllMembers() = runBlocking {
        val quotaState = quotaState(FakeBalanceAccountRepository(mutableListOf(account(first, 0), account(second, 1))))
        val config = FakeBalanceConfigurationStore(
            groups = listOf(BalanceAccountGroup(name = "合账", memberAccountIDs = listOf(first, second), defaultAccountID = first)),
            home = second,
        )
        val client = FakeBalanceClient(setOf(first, second))
        val repository = balanceRepository(quotaState, client, config)

        repository.refreshBalancesIfNeeded()

        assertEquals(listOf(second), client.calls)
        assertEquals(88.66, quotaState.state.value.accounts[0].balanceYuan!!, 0.0)
        assertEquals(88.66, quotaState.state.value.accounts[1].balanceYuan!!, 0.0)
        assertTrue(repository.state.value.lastAutomaticAttemptAt.isEmpty())
    }

    @Test
    fun automaticFailurePreservesOldBalanceAndKeepsFailureCooldownAttempt() = runBlocking {
        val oldAt = now.minusSeconds(3600)
        val quotaState = quotaState(FakeBalanceAccountRepository(mutableListOf(account(first, 0).copy(balanceYuan = 12.34, balanceUpdatedAt = oldAt))))
        val client = FakeBalanceClient(setOf(first)).apply { fail = true }
        val repository = balanceRepository(quotaState, client, FakeBalanceConfigurationStore())

        repository.refreshBalancesIfNeeded()
        assertEquals(12.34, quotaState.state.value.accounts.single().balanceYuan!!, 0.0)
        assertEquals(1, client.calls.size)
        assertTrue(repository.state.value.lastAutomaticAttemptAt.containsKey("account:$first"))

        repository.refreshBalancesIfNeeded()
        assertEquals(1, client.calls.size)
    }

    @Test
    fun freshSharedCacheIsConsumedWithoutNetworkRequest() = runBlocking {
        val quotaState = quotaState(FakeBalanceAccountRepository(mutableListOf(account(first, 0))))
        val shared = SharedBalanceCacheStore(MemorySharedBalanceStorageForRepository(), ZoneOffset.UTC)
        shared.replaceScopes(listOf(SharedBalanceScope.account(first)), now)
        val token = (shared.beginForcedRefresh(first, SharedBalanceRefreshSource.WIDGET_MANUAL, now) as SharedBalanceRefreshClaim.Granted).token
        shared.completeRefresh(token, 66.0, first, now)
        val client = FakeBalanceClient(setOf(first))
        val repository = balanceRepository(quotaState, client, FakeBalanceConfigurationStore(), shared)

        repository.refreshBalancesIfNeeded()

        assertTrue(client.calls.isEmpty())
        assertEquals(66.0, quotaState.state.value.accounts.single().balanceYuan!!, 0.0)
        assertEquals(now, quotaState.state.value.accounts.single().balanceUpdatedAt)
    }

    @Test
    fun fallbackRepresentativePrefersEnabledAccountWithCredentials() {
        val quotaState = quotaState(FakeBalanceAccountRepository(mutableListOf(account(first, 0), account(second, 1))))
        val repository = balanceRepository(
            quotaState,
            FakeBalanceClient(setOf(second)),
            FakeBalanceConfigurationStore(groups = listOf(BalanceAccountGroup(name = "合账", memberAccountIDs = listOf(first, second)))),
        )
        assertEquals(second, repository.financialRepresentativeAccountID(first))
    }

    private fun balanceRepository(
        quotaState: QuotaRefreshCoordinator,
        client: FakeBalanceClient,
        config: FakeBalanceConfigurationStore,
        shared: SharedBalanceCacheStore = SharedBalanceCacheStore(MemorySharedBalanceStorageForRepository(), ZoneOffset.UTC),
    ) = DefaultBalanceRepository(
        accountState = quotaState,
        refreshClient = client,
        sharedCache = shared,
        configurationStore = config,
        settingsRepository = FakeSettingsRepository(),
        clock = clock,
        sleeper = {},
    )

    private fun quotaState(repository: FakeBalanceAccountRepository) = QuotaRefreshCoordinator(
        accountRepository = repository,
        refreshClient = object : QuotaRefreshClient {
            override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult = error("quota not used")
        },
        runtimeStore = object : QuotaRefreshRuntimeStore {
            override fun lastRefreshTriggeredAt(): Instant? = null
            override fun recordRefreshTriggeredAt(at: Instant) = Unit
        },
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
        sleeper = {},
    )

    private fun account(id: UUID, order: Int) = UnicomAccount(
        id = id,
        displayName = "A$order",
        mobile = "1380013800$order",
        isEnabled = true,
        sortOrder = order,
    )
}

private class FakeBalanceClient(private val credentialIDs: Set<UUID>) : BalanceRefreshClient {
    val calls = mutableListOf<UUID>()
    var fail = false
    override fun hasCredentials(accountID: UUID): Boolean = accountID in credentialIDs
    override suspend fun fetchBalance(accountID: UUID): BalanceFetchResult {
        calls += accountID
        if (fail) error("balance failed")
        return BalanceFetchResult(88.66, emptyBalanceDetail(), null)
    }
}

private fun emptyBalanceDetail() = UnavailableBalanceDetail("88.66", null, null, null, emptyList(), emptyList())

private class FakeBalanceConfigurationStore(
    var groups: List<BalanceAccountGroup> = emptyList(),
    var home: UUID? = null,
) : BalanceConfigurationStore {
    var attempts: Map<String, Instant> = emptyMap()
    var legacyDone = true
    override fun loadGroups(): List<BalanceAccountGroup> = groups
    override fun saveGroups(groups: List<BalanceAccountGroup>): Boolean { this.groups = groups; return true }
    override fun loadHomeBalanceAccountID(): UUID? = home
    override fun saveHomeBalanceAccountID(accountID: UUID?): Boolean { home = accountID; return true }
    override fun loadLastAutomaticAttemptAt(): Map<String, Instant> = attempts
    override fun saveLastAutomaticAttemptAt(value: Map<String, Instant>): Boolean { attempts = value; return true }
    override fun legacySharedBalanceMigrationCompleted(): Boolean = legacyDone
    override fun markLegacySharedBalanceMigrationCompleted(): Boolean { legacyDone = true; return true }
}

private class MemorySharedBalanceStorageForRepository(
    var value: SharedBalancePersistedState = SharedBalancePersistedState(),
) : SharedBalanceStateStorage {
    override fun <T> transaction(block: (SharedBalancePersistedState) -> SharedBalanceTransaction<T>): T? {
        val result = block(value)
        value = result.state
        return result.value
    }
}

private class FakeBalanceAccountRepository(
    val accounts: MutableList<UnicomAccount>,
) : AccountRepository {
    override fun loadAccounts(): List<UnicomAccount> = accounts.toList()
    override fun createValidatedAccount(displayName: String, seed: ValidatedLoginAccountSeed): UnicomAccount = error("unused")
    override fun replaceAccounts(accounts: List<UnicomAccount>) { this.accounts.clear(); this.accounts.addAll(accounts) }
    override fun removeAccount(accountID: UUID) { accounts.removeAll { it.id == accountID } }
    override fun clear() { accounts.clear() }
}

private class FakeSettingsRepository : SettingsRepository {
    private val quota = MutableStateFlow(QuotaRefreshPolicy())
    private val balance = MutableStateFlow(BalanceRefreshPolicy())
    override val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = quota
    override val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy> = balance
    override fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy = quota.value
    override fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy = balance.value
    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult = QuotaRefreshPolicySaveResult(true, quota.value != policy, policy).also { quota.value = policy }
    override fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult = BalanceRefreshPolicySaveResult(true, balance.value != policy, policy).also { balance.value = policy }
}
