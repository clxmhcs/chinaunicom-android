package com.clxmhcs.chinaunicom.data.account

import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.storage.AccountMetadataStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {
    private val now = Instant.parse("2026-08-22T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val accountID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    @Test
    fun validatedSeedCreatesSourceEquivalentMetadataAndPersistsIt() {
        val store = FakeAccountMetadataStore()
        val repository = DefaultAccountRepository(store, clock)
        val seed = ValidatedLoginAccountSeed(
            accountID = accountID,
            mobile = "13800138000",
            quota = quotaResult(),
        )

        val account = repository.createValidatedAccount("  ", seed)

        assertEquals("联通号码", account.displayName)
        assertEquals("13800138000", account.mobile)
        assertEquals("校园沃派", account.packageName)
        assertEquals(now, account.lastUpdatedAt)
        assertEquals(now, account.remainingQuerySnapshot?.updatedAt)
        assertNull(account.balanceYuan)
        assertNull(account.balanceUpdatedAt)
        assertNull(account.unavailableBalanceDetail)
        assertEquals(DisplayPlacement.PRIMARY, account.displayPreferences[0].placement)
        assertEquals(DisplayPlacement.SECONDARY, account.displayPreferences[1].placement)
        assertEquals(DisplayPlacement.SECONDARY, account.displayPreferences[2].placement)
        assertEquals(DisplayPlacement.DETAIL_ONLY, account.displayPreferences[3].placement)
        assertEquals(DisplayPlacement.DETAIL_ONLY, account.displayPreferences[4].placement)
        assertTrue(account.summaryGroups?.isNotEmpty() == true)
        assertEquals(listOf(account), store.accounts)
    }

    @Test
    fun loadingSortsBySortOrderAndRemovalRenumbersRemainingAccounts() {
        val a = UnicomAccount(id = UUID.randomUUID(), displayName = "A", mobile = "13800138001", sortOrder = 2)
        val b = UnicomAccount(id = UUID.randomUUID(), displayName = "B", mobile = "13800138002", sortOrder = 0)
        val c = UnicomAccount(id = UUID.randomUUID(), displayName = "C", mobile = "13800138003", sortOrder = 1)
        val store = FakeAccountMetadataStore(mutableListOf(a, b, c))
        val repository = DefaultAccountRepository(store, clock)

        assertEquals(listOf(b.id, c.id, a.id), repository.loadAccounts().map { it.id })
        repository.removeAccount(c.id)

        assertEquals(listOf(b.id, a.id), store.accounts.map { it.id })
        assertEquals(listOf(0, 1), store.accounts.map { it.sortOrder })
    }

    private fun quotaResult(): QuotaFetchResult = QuotaFetchResult(
        packageName = "校园沃派",
        packages = listOf(
            flow("flow-1", "国内流量"),
            flow("flow-2", "省内流量"),
            flow("flow-3", "校园流量"),
            flow("flow-4", "定向流量"),
        ),
        voicePackages = listOf(
            VoicePackage("voice-1", "国内语音", 300.0, 10.0, 290.0, false, false),
        ),
        remainingQuerySnapshot = null,
        balanceYuan = 999.0,
        unavailableBalanceDetail = null,
        quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
        updatedCredentials = null,
    )

    private fun flow(id: String, name: String) = FlowPackage(
        id = id,
        originalName = name,
        totalMB = 1024.0,
        usedMB = 100.0,
        remainingMB = 924.0,
        detectedQuotaType = QuotaType.LIMITED,
        detectedCategory = PackageCategory.GENERAL,
        isShared = false,
    )
}

private class FakeAccountMetadataStore(
    val accounts: MutableList<UnicomAccount> = mutableListOf(),
) : AccountMetadataStore {
    override fun loadAccounts(): List<UnicomAccount> = accounts.toList()

    override fun saveAccounts(accounts: List<UnicomAccount>) {
        this.accounts.clear()
        this.accounts.addAll(accounts)
    }

    override fun clear() {
        accounts.clear()
    }
}
