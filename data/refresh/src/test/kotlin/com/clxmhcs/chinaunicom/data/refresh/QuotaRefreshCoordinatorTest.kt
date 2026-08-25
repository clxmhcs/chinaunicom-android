package com.clxmhcs.chinaunicom.data.refresh

import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingSMSSnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingVoiceSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaRefreshCoordinatorTest {
    private val now = Instant.parse("2026-08-22T02:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val firstID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val secondID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun restoresSortedAccountsAndAppliesAutomaticCooldownRules() {
        val repository = FakeAccountRepository(
            mutableListOf(
                account(secondID, "B", sortOrder = 1),
                account(firstID, "A", sortOrder = 0),
            ),
        )
        val runtime = FakeRuntimeStore()
        val coordinator = coordinator(repository, FakeRefreshClient(), runtime)

        assertEquals(listOf(firstID, secondID), coordinator.state.value.accounts.map { it.id })
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))

        runtime.last = now.minusSeconds(5 * 60)
        assertFalse(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.last = now.minusSeconds(10 * 60)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
        runtime.last = now.plusSeconds(30)
        assertTrue(coordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))

        val disabledPolicyCoordinator = coordinator(
            repository,
            FakeRefreshClient(),
            runtime,
            policy = QuotaRefreshPolicy(automaticRefreshEnabled = false),
        )
        assertFalse(disabledPolicyCoordinator.shouldAutoRefresh(QuotaAutomaticRefreshTrigger.COLD_LAUNCH, now))
    }

    @Test
    fun manualRefreshCoalescesSameAccountAndRecordsTriggerOnlyOnce() = runBlocking {
        val repository = FakeAccountRepository(mutableListOf(account(firstID, "A")))
        val runtime = FakeRuntimeStore()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = object : QuotaRefreshClient {
            var calls = 0
            override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult {
                calls += 1
                started.complete(Unit)
                release.await()
                return quotaResult(packageName = "new-plan")
            }
        }
        val coordinator = coordinator(repository, client, runtime)

        val first = async { coordinator.refreshAccount(firstID) }
        started.await()
        val duplicate = async { coordinator.refreshAccount(firstID) }
        duplicate.await()

        assertEquals(1, client.calls)
        assertEquals(1, runtime.recorded.size)
        assertEquals(RefreshState.Loading, coordinator.state.value.refreshState(firstID))

        release.complete(Unit)
        first.await()
        assertEquals(RefreshState.Succeeded, coordinator.state.value.refreshState(firstID))
    }

    @Test
    fun successfulRefreshPreservesBalanceAndUserPlacementWhileUpdatingQuota() = runBlocking {
        val original = account(firstID, "A").copy(
            packageName = "old-plan",
            balanceYuan = 88.66,
            displayPreferences = listOf(
                PackageDisplayPreference(
                    packageKey = "flow-old",
                    placement = DisplayPlacement.PRIMARY,
                    sortOrder = 0,
                ),
            ),
        )
        val repository = FakeAccountRepository(mutableListOf(original))
        val result = quotaResult(
            packageName = "",
            packages = listOf(flow("flow-old", "国内流量", remaining = 700.0)),
            withRemainingSnapshot = true,
        )
        val coordinator = coordinator(repository, FakeRefreshClient(result), FakeRuntimeStore())

        coordinator.refreshAccount(firstID)

        val refreshed = coordinator.state.value.accounts.single()
        assertEquals("old-plan", refreshed.packageName)
        assertEquals(88.66, refreshed.balanceYuan!!, 0.0)
        assertEquals(700.0, refreshed.packages.single().remainingMB!!, 0.0)
        assertEquals(DisplayPlacement.PRIMARY, refreshed.displayPreferences.single().placement)
        assertEquals(now, refreshed.lastUpdatedAt)
        assertEquals(now, refreshed.remainingQuerySnapshot?.updatedAt)
        assertNull(refreshed.lastErrorMessage)
        assertEquals(listOf(refreshed), repository.accounts)
    }

    @Test
    fun networkFailureKeepsOldQuotaAndPersistsLastError() = runBlocking {
        val original = account(firstID, "A")
        val repository = FakeAccountRepository(mutableListOf(original))
        val client = object : QuotaRefreshClient {
            override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult {
                error("network down")
            }
        }
        val coordinator = coordinator(repository, client, FakeRuntimeStore())

        coordinator.refreshAccount(firstID)

        val failed = coordinator.state.value.accounts.single()
        assertEquals(original.packages, failed.packages)
        assertEquals("network down", failed.lastErrorMessage)
        assertEquals(RefreshState.Failed("network down"), coordinator.state.value.refreshState(firstID))
        assertEquals(listOf(failed), repository.accounts)
    }

    @Test
    fun persistenceFailureRollsBackNetworkResultThenPersistsErrorOnOldAccount() = runBlocking {
        val original = account(firstID, "A")
        val repository = FakeAccountRepository(mutableListOf(original)).apply {
            failNextSave = true
        }
        val coordinator = coordinator(
            repository,
            FakeRefreshClient(quotaResult(packages = listOf(flow("new-flow", "新流量", 321.0)))),
            FakeRuntimeStore(),
        )

        coordinator.refreshAccount(firstID)

        val failed = coordinator.state.value.accounts.single()
        assertEquals(original.packages, failed.packages)
        assertEquals("disk full", failed.lastErrorMessage)
        assertEquals(RefreshState.Failed("disk full"), coordinator.state.value.refreshState(firstID))
        assertEquals(original.packages, repository.accounts.single().packages)
        assertEquals("disk full", repository.accounts.single().lastErrorMessage)
    }

    @Test
    fun refreshAllUsesEnabledAccountsSeriallyRecordsOnceAndSleepsConfiguredGap() = runBlocking {
        val thirdID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val repository = FakeAccountRepository(
            mutableListOf(
                account(firstID, "A", isEnabled = true, sortOrder = 0),
                account(secondID, "B", isEnabled = false, sortOrder = 1),
                account(thirdID, "C", isEnabled = true, sortOrder = 2),
            ),
        )
        val runtime = FakeRuntimeStore()
        val client = FakeRefreshClient()
        val sleeps = mutableListOf<Long>()
        val coordinator = QuotaRefreshCoordinator(
            accountRepository = repository,
            refreshClient = client,
            runtimeStore = runtime,
            policyProvider = QuotaRefreshPolicyProvider { QuotaRefreshPolicy(accountGapSeconds = 2) },
            clock = clock,
            ioDispatcher = Dispatchers.Unconfined,
            sleeper = { sleeps += it },
        )

        coordinator.refreshAll()

        assertEquals(listOf(firstID, thirdID), client.calls)
        assertEquals(listOf(now), runtime.recorded)
        assertEquals(listOf(2_000L), sleeps)
        assertFalse(coordinator.state.value.isRefreshingAll)
    }

    private fun coordinator(
        repository: FakeAccountRepository,
        client: QuotaRefreshClient,
        runtime: FakeRuntimeStore,
        policy: QuotaRefreshPolicy = QuotaRefreshPolicy(),
    ) = QuotaRefreshCoordinator(
        accountRepository = repository,
        refreshClient = client,
        runtimeStore = runtime,
        policyProvider = QuotaRefreshPolicyProvider { policy },
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
        sleeper = {},
    )

    private fun account(
        id: UUID,
        name: String,
        isEnabled: Boolean = true,
        sortOrder: Int = 0,
    ) = UnicomAccount(
        id = id,
        displayName = name,
        mobile = "13800138000",
        packageName = "old-plan",
        packages = listOf(flow("flow-old", "国内流量", 900.0)),
        lastUpdatedAt = now.minusSeconds(3600),
        isEnabled = isEnabled,
        sortOrder = sortOrder,
    )

    private fun quotaResult(
        packageName: String = "new-plan",
        packages: List<FlowPackage> = listOf(flow("flow-old", "国内流量", 800.0)),
        withRemainingSnapshot: Boolean = false,
    ) = QuotaFetchResult(
        packageName = packageName,
        packages = packages,
        voicePackages = emptyList(),
        remainingQuerySnapshot = if (withRemainingSnapshot) {
            RemainingQuerySnapshot(
                updatedAt = now.minusSeconds(600),
                members = emptyList(),
                flowSummaries = emptyList(),
                flowPackages = emptyList(),
                sharedFlowMemberTotals = emptyList(),
                voice = RemainingVoiceSnapshot(null, null, emptyList(), emptyList()),
                sms = RemainingSMSSnapshot(null, null, emptyList(), emptyList()),
            )
        } else null,
        balanceYuan = 999.0,
        unavailableBalanceDetail = null,
        quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
        updatedCredentials = null,
    )

    private fun flow(id: String, name: String, remaining: Double) = FlowPackage(
        id = id,
        originalName = name,
        totalMB = 1024.0,
        usedMB = 1024.0 - remaining,
        remainingMB = remaining,
        detectedQuotaType = QuotaType.LIMITED,
        detectedCategory = PackageCategory.GENERAL,
        isShared = false,
    )
}

private class FakeRuntimeStore : QuotaRefreshRuntimeStore {
    var last: Instant? = null
    val recorded = mutableListOf<Instant>()

    override fun lastRefreshTriggeredAt(): Instant? = last

    override fun recordRefreshTriggeredAt(at: Instant) {
        last = at
        recorded += at
    }
}

private class FakeRefreshClient(
    private val result: QuotaFetchResult = QuotaFetchResult(
        packageName = "plan",
        packages = emptyList(),
        voicePackages = emptyList(),
        balanceYuan = null,
        unavailableBalanceDetail = null,
        quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
        updatedCredentials = null,
    ),
) : QuotaRefreshClient {
    val calls = mutableListOf<UUID>()

    override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult {
        calls += accountID
        return result
    }
}

private class FakeAccountRepository(
    val accounts: MutableList<UnicomAccount>,
) : AccountRepository {
    var failNextSave = false

    override fun loadAccounts(): List<UnicomAccount> = accounts.toList()

    override fun createValidatedAccount(displayName: String, seed: ValidatedLoginAccountSeed): UnicomAccount {
        error("not used by refresh tests")
    }

    override fun replaceAccounts(accounts: List<UnicomAccount>) {
        if (failNextSave) {
            failNextSave = false
            throw IllegalStateException("disk full")
        }
        this.accounts.clear()
        this.accounts.addAll(accounts)
    }

    override fun removeAccount(accountID: UUID) {
        accounts.removeAll { it.id == accountID }
    }

    override fun clear() {
        accounts.clear()
    }
}
