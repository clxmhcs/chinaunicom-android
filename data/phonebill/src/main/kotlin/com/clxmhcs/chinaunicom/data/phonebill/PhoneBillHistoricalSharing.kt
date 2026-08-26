package com.clxmhcs.chinaunicom.data.phonebill

import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Historical phone-bill sharing is proven by bill membership, never by current balance grouping. */
class PhoneBillHistoricalCacheResolver {
    data class Match(val sourceAccountID: UUID, val snapshot: PhoneBillSnapshot)

    fun resolveBest(
        targetAccount: UnicomAccount,
        month: BillMonth,
        localAccounts: List<UnicomAccount>,
        cachedSnapshotsByAccount: Map<UUID, Map<String, PhoneBillSnapshot>>,
        cachePolicy: PhoneBillCachePolicy,
        now: Instant,
    ): Match? {
        if (month.key == cachePolicy.currentMonthKey(now)) return null
        val targetIdentity = localIdentity(targetAccount.mobile) ?: return null
        if (!isUniqueLocalIdentity(targetIdentity, targetAccount.id, localAccounts)) return null

        val ownMatch = cachedSnapshotsByAccount[targetAccount.id]?.get(month.key)?.let { snapshot ->
            if (cachePolicy.isFresh(snapshot, month, now) && occurrenceCount(targetIdentity, snapshot) == 1) {
                Match(targetAccount.id, snapshot)
            } else null
        }

        val sharedMatch = localAccounts.asSequence()
            .filter { it.id != targetAccount.id }
            .mapNotNull { account ->
                val sourceIdentity = localIdentity(account.mobile) ?: return@mapNotNull null
                if (!isUniqueLocalIdentity(sourceIdentity, account.id, localAccounts)) return@mapNotNull null
                val candidate = cachedSnapshotsByAccount[account.id]?.get(month.key) ?: return@mapNotNull null
                if (!cachePolicy.isFresh(candidate, month, now)) return@mapNotNull null
                if (occurrenceCount(sourceIdentity, candidate) != 1) return@mapNotNull null
                if (occurrenceCount(targetIdentity, candidate) != 1) return@mapNotNull null
                Match(account.id, candidate)
            }
            .maxByOrNull { it.snapshot.fetchedAt }

        return when {
            ownMatch == null -> sharedMatch
            sharedMatch == null -> ownMatch
            ownMatch.snapshot.fetchedAt >= sharedMatch.snapshot.fetchedAt -> ownMatch
            else -> sharedMatch
        }
    }

    fun snapshotBelongsToAccount(
        snapshot: PhoneBillSnapshot,
        targetAccount: UnicomAccount,
        localAccounts: List<UnicomAccount>,
    ): Boolean {
        val identity = localIdentity(targetAccount.mobile) ?: return false
        if (!isUniqueLocalIdentity(identity, targetAccount.id, localAccounts)) return false
        return occurrenceCount(identity, snapshot) == 1
    }

    private data class MobileIdentity(val maskKey: String)

    private fun localIdentity(raw: String): MobileIdentity? = normalizedFullMobile(raw)?.let { MobileIdentity(maskKey(it)) }

    private fun isUniqueLocalIdentity(identity: MobileIdentity, accountID: UUID, accounts: List<UnicomAccount>): Boolean {
        val matches = accounts.mapNotNull { account -> localIdentity(account.mobile)?.takeIf { it == identity }?.let { account.id } }
        return matches.size == 1 && matches.first() == accountID
    }

    private fun occurrenceCount(identity: MobileIdentity, snapshot: PhoneBillSnapshot): Int =
        snapshot.userBills.count { billMaskKey(it.mobile) == identity.maskKey }

    private fun normalizedFullMobile(raw: String): String? {
        var digits = raw.filter(Char::isDigit)
        if (digits.length == 13 && digits.startsWith("86")) digits = digits.drop(2)
        return digits.takeIf { it.length == 11 }
    }

    private fun maskKey(fullMobile: String): String = fullMobile.take(3) + "****" + fullMobile.takeLast(4)

    private fun billMaskKey(raw: String): String? {
        normalizedFullMobile(raw)?.let { return maskKey(it) }
        if ('*' !in raw) return null
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 7) return null
        return digits.take(3) + "****" + digits.takeLast(4)
    }
}

/** All network writes for the same historical month are serialized across store instances. */
class PhoneBillHistoricalQueryCoordinator private constructor() {
    private val guard = Mutex()
    private val monthLocks = mutableMapOf<String, Mutex>()

    suspend fun <T> withMonthLock(monthKey: String, block: suspend () -> T): T {
        val lock = guard.withLock { monthLocks.getOrPut(monthKey) { Mutex() } }
        return lock.withLock { block() }
    }

    companion object {
        val shared = PhoneBillHistoricalQueryCoordinator()
    }
}
