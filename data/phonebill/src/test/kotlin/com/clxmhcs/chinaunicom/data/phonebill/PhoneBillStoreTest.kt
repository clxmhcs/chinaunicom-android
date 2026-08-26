package com.clxmhcs.chinaunicom.data.phonebill

import com.clxmhcs.chinaunicom.core.login.PhoneBillAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.PhoneBillCredentialValidator
import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillMonthsFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSummary
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.UserBill
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBillStoreTest {
    private val now = Instant.parse("2026-08-25T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val target = UnicomAccount(displayName = "A", mobile = "13800138000")
    private val source = UnicomAccount(displayName = "B", mobile = "13900139000")
    private val current = BillMonth("2026", "08")
    private val historical = BillMonth("2026", "07")
    private val policy = PhoneBillCachePolicy(PhoneBillPolicyProvider { PhoneBillRefreshPolicy() })

    @Test
    fun cachePolicyKeepsThirteenMonthsAndRejectsOldParser() {
        val months = (1..15).map { offset ->
            val date = now.atZone(java.time.ZoneId.of("Asia/Shanghai")).withDayOfMonth(1).minusMonths((offset - 1).toLong())
            BillMonth("%04d".format(date.year), "%02d".format(date.monthValue))
        }
        assertEquals(13, policy.visibleMonths(months, now).size)
        assertTrue(policy.isFresh(snapshot(current, now.minusSeconds(60), listOf("138****8000")), current, now))
        assertTrue(!policy.isFresh(snapshot(current, now.minusSeconds(60), listOf("138****8000")).copy(parserVersion = 3), current, now))
    }

    @Test
    fun historicalResolverUsesNewestVerifiedSharedBillMembership() {
        val resolver = PhoneBillHistoricalCacheResolver()
        val own = snapshot(historical, Instant.parse("2026-08-18T12:00:00Z"), listOf("138****8000"))
        val shared = snapshot(historical, Instant.parse("2026-08-20T12:00:00Z"), listOf("139****9000", "138****8000"))
        val match = resolver.resolveBest(
            targetAccount = target,
            month = historical,
            localAccounts = listOf(target, source),
            cachedSnapshotsByAccount = mapOf(target.id to mapOf(historical.key to own), source.id to mapOf(historical.key to shared)),
            cachePolicy = policy,
            now = now,
        )
        assertEquals(source.id, match?.sourceAccountID)
        assertEquals(shared, match?.snapshot)
    }

    @Test
    fun freshCurrentCacheLoadsWithoutNetworkAndHistoricalSelectionReusesSharedCache() = runBlocking {
        var networkCalls = 0
        val validator = object : PhoneBillCredentialValidator {
            override fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult {
                networkCalls++
                error("network must not run")
            }
            override fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult {
                networkCalls++
                error("network must not run")
            }
        }
        val credentials = MemoryCredentialStore(mutableMapOf(target.id to AccountCredentials("c=1", "a", "t")))
        val currentSnapshot = snapshot(current, now.minusSeconds(60), listOf("138****8000"))
        val sharedHistorical = snapshot(historical, Instant.parse("2026-08-20T12:00:00Z"), listOf("139****9000", "138****8000"))
        val cache = MemoryPhoneBillCache(
            mutableMapOf(
                target.id to mutableMapOf(current.key to currentSnapshot),
                source.id to mutableMapOf(historical.key to sharedHistorical),
            ),
        )
        val store = DefaultPhoneBillStore(
            lifecycle = PhoneBillAccountCredentialLifecycle(validator, credentials),
            cache = cache,
            cachePolicy = policy,
            accountRepository = MemoryAccountRepository(listOf(target, source)),
            clock = clock,
        )

        store.loadIfNeeded(target)
        assertEquals(currentSnapshot, store.state.value.snapshot)
        assertEquals(13, store.state.value.months.size)
        store.select(historical, target)

        assertEquals(sharedHistorical, store.state.value.snapshot)
        assertEquals(historical.key, store.state.value.selectedMonth?.key)
        assertEquals(0, networkCalls)
    }

    @Test
    fun snapshotCodecRoundTripsNestedBillData() {
        val value = snapshot(current, now, listOf("138****8000"))
        val codec = PhoneBillSnapshotJsonCodec()
        val raw = codec.encode(mapOf(target.id to mapOf(current.key to value)))
        val decoded = codec.decode(raw)
        assertEquals(value, decoded?.get(target.id)?.get(current.key))
    }

    private fun snapshot(month: BillMonth, fetchedAt: Instant, mobiles: List<String>) = PhoneBillSnapshot(
        month = month,
        queryTime = "2026-08-25 20:00:00",
        summary = PhoneBillSummary("0.00", "88.80", "100.00", "11.20", "88.80", "0.00", null, null, null, null),
        userBills = mobiles.mapIndexed { index, mobile ->
            UserBill("u$index", mobile, null, "88.80", emptyList(), null, null, null, null, null, null, null, null)
        },
        accountSections = emptyList(),
        fetchedAt = fetchedAt,
        parserVersion = PhoneBillSnapshot.CURRENT_PARSER_VERSION,
    )
}

private class MemoryPhoneBillCache(
    private val values: MutableMap<UUID, MutableMap<String, PhoneBillSnapshot>> = mutableMapOf(),
) : PhoneBillDiskCache {
    override fun load(accountID: UUID): Map<String, PhoneBillSnapshot> = values[accountID]?.toMap().orEmpty()
    override fun loadAll(): Map<UUID, Map<String, PhoneBillSnapshot>> = values.mapValues { it.value.toMap() }
    override fun upsert(snapshot: PhoneBillSnapshot, accountID: UUID, keepingMonthKeys: Set<String>): Map<String, PhoneBillSnapshot> {
        val account = values.getOrPut(accountID) { mutableMapOf() }
        account[snapshot.month.key] = snapshot
        if (keepingMonthKeys.isNotEmpty()) account.keys.retainAll(keepingMonthKeys)
        return account.toMap()
    }
    override fun removeSnapshot(accountID: UUID, monthKey: String): Map<String, PhoneBillSnapshot> {
        values[accountID]?.remove(monthKey)
        return values[accountID]?.toMap().orEmpty()
    }
    override fun pruneAccounts(keeping: Set<UUID>) { values.keys.retainAll(keeping) }
    override fun clear() { values.clear() }
}

private class MemoryCredentialStore(
    private val values: MutableMap<UUID, AccountCredentials> = mutableMapOf(),
) : CredentialStore {
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}

private class MemoryAccountRepository(initial: List<UnicomAccount>) : AccountRepository {
    private var accounts = initial
    override fun loadAccounts(): List<UnicomAccount> = accounts
    override fun createValidatedAccount(displayName: String, seed: ValidatedLoginAccountSeed): UnicomAccount = error("unused")
    override fun replaceAccounts(accounts: List<UnicomAccount>) { this.accounts = accounts }
    override fun removeAccount(accountID: UUID) { accounts = accounts.filterNot { it.id == accountID } }
    override fun clear() { accounts = emptyList() }
}
