package com.clxmhcs.chinaunicom.data.refresh

import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaRepositoryTest {
    private val now = Instant.parse("2026-08-25T10:30:00Z")
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun defaultRepositorySharesCoordinatorStateAndDelegatesRefreshOperations() = runBlocking {
        val accountRepository = FakeQuotaAccountRepository(
            mutableListOf(
                UnicomAccount(
                    id = accountID,
                    displayName = "联通号码",
                    mobile = "13800138000",
                    isEnabled = true,
                    sortOrder = 0,
                ),
            ),
        )
        val refreshClient = FakeQuotaRepositoryRefreshClient()
        val runtimeStore = FakeQuotaRepositoryRuntimeStore()
        val coordinator = QuotaRefreshCoordinator(
            accountRepository = accountRepository,
            refreshClient = refreshClient,
            runtimeStore = runtimeStore,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            ioDispatcher = Dispatchers.Unconfined,
            sleeper = {},
        )
        val repository: QuotaRepository = DefaultQuotaRepository(coordinator)

        assertSame(coordinator.state, repository.state)

        repository.refreshAccount(accountID)
        assertEquals(listOf(accountID), refreshClient.calls)
        assertEquals(listOf(now), runtimeStore.recorded)
        assertTrue(repository.state.value.accounts.single().lastErrorMessage == null)

        repository.refreshAll()
        assertEquals(listOf(accountID, accountID), refreshClient.calls)
        assertEquals(listOf(now, now), runtimeStore.recorded)
    }
}

private class FakeQuotaRepositoryRefreshClient : QuotaRefreshClient {
    val calls = mutableListOf<UUID>()

    override suspend fun refreshValidatedQuota(accountID: UUID): QuotaFetchResult {
        calls += accountID
        return QuotaFetchResult(
            packageName = "plan",
            packages = emptyList(),
            voicePackages = emptyList(),
            balanceYuan = null,
            unavailableBalanceDetail = null,
            quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
            updatedCredentials = null,
        )
    }
}

private class FakeQuotaRepositoryRuntimeStore : QuotaRefreshRuntimeStore {
    val recorded = mutableListOf<Instant>()
    private var last: Instant? = null

    override fun lastRefreshTriggeredAt(): Instant? = last

    override fun recordRefreshTriggeredAt(at: Instant) {
        last = at
        recorded += at
    }
}

private class FakeQuotaAccountRepository(
    val accounts: MutableList<UnicomAccount>,
) : AccountRepository {
    override fun loadAccounts(): List<UnicomAccount> = accounts.toList()

    override fun createValidatedAccount(displayName: String, seed: ValidatedLoginAccountSeed): UnicomAccount =
        error("not used")

    override fun replaceAccounts(accounts: List<UnicomAccount>) {
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
