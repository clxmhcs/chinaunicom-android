package com.clxmhcs.chinaunicom.data.balance

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class SharedBalanceRefreshSource(val rawValue: String) {
    APP_AUTOMATIC("appAutomatic"),
    APP_MANUAL("appManual"),
    WIDGET_AUTOMATIC("widgetAutomatic"),
    WIDGET_MANUAL("widgetManual"),
    SHORTCUT("shortcut"),
}

data class SharedBalanceScope(
    val id: String,
    val memberAccountIDs: List<UUID>,
    val representativeAccountID: UUID? = null,
) {
    fun normalized(): SharedBalanceScope {
        val members = memberAccountIDs.distinct().sortedBy(UUID::toString)
        return copy(
            id = id.trim(),
            memberAccountIDs = members,
            representativeAccountID = representativeAccountID?.takeIf(members::contains),
        )
    }

    companion object {
        fun account(accountID: UUID) = SharedBalanceScope(
            id = "account:$accountID",
            memberAccountIDs = listOf(accountID),
            representativeAccountID = accountID,
        )
    }
}

data class SharedBalanceCacheEntry(
    val scopeID: String,
    val memberAccountIDs: List<UUID>,
    val representativeAccountID: UUID?,
    val balanceYuan: Double,
    val refreshedAt: Instant,
    val source: SharedBalanceRefreshSource,
)

data class SharedBalanceRefreshLeaseToken(
    val leaseID: UUID,
    val scope: SharedBalanceScope,
    val source: SharedBalanceRefreshSource,
    val startedAt: Instant,
    val expiresAt: Instant,
)

sealed interface SharedBalanceRefreshClaim {
    data class Cached(val entry: SharedBalanceCacheEntry) : SharedBalanceRefreshClaim
    data class Granted(val token: SharedBalanceRefreshLeaseToken) : SharedBalanceRefreshClaim
    data class InFlight(val until: Instant) : SharedBalanceRefreshClaim
    data object Unavailable : SharedBalanceRefreshClaim
}

internal data class PersistedSharedBalanceLease(
    val leaseID: UUID,
    val scopeID: String,
    val memberAccountIDs: List<UUID>,
    val representativeAccountID: UUID?,
    val source: SharedBalanceRefreshSource,
    val startedAt: Instant,
    val expiresAt: Instant,
)

internal data class SharedBalancePersistedState(
    val schemaVersion: Int = SharedBalanceCacheStore.CURRENT_SCHEMA_VERSION,
    val refreshIntervalMinutes: Int = SharedBalanceCacheStore.DEFAULT_REFRESH_INTERVAL_MINUTES,
    val scopes: Map<String, SharedBalanceScope> = emptyMap(),
    val entries: Map<String, SharedBalanceCacheEntry> = emptyMap(),
    val leases: Map<String, PersistedSharedBalanceLease> = emptyMap(),
)

internal data class SharedBalanceTransaction<T>(
    val value: T,
    val state: SharedBalancePersistedState,
)

internal interface SharedBalanceStateStorage {
    fun <T> transaction(
        block: (SharedBalancePersistedState) -> SharedBalanceTransaction<T>,
    ): T?
}

/**
 * Durable source of truth for balance cache freshness and real-network ownership.
 *
 * The Android storage implementation serializes every read/modify/write through a file lock so
 * future widget/secondary-process callers cannot all observe an expired cache and start duplicate
 * balance requests. Cache freshness is scoped, same-local-day, interval-based and independent of
 * process-local state.
 */
class SharedBalanceCacheStore internal constructor(
    private val storage: SharedBalanceStateStorage,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun refreshIntervalMinutes(): Int = transaction { state ->
        state.refreshIntervalMinutes to state
    } ?: DEFAULT_REFRESH_INTERVAL_MINUTES

    fun setRefreshIntervalMinutes(minutes: Int): Boolean =
        transaction { state -> Unit to state.copy(refreshIntervalMinutes = normalizeInterval(minutes)) } != null

    fun replaceScopes(scopes: List<SharedBalanceScope>, now: Instant = Instant.now()): Boolean =
        transaction { original ->
            var state = removeExpiredLeases(original, now)
            val nextScopes = normalizedScopeRegistry(scopes)
            val previousScopes = state.scopes
            val removed = previousScopes.keys - nextScopes.keys
            val changed = previousScopes.keys.intersect(nextScopes.keys).filter { previousScopes[it] != nextScopes[it] }.toSet()
            val invalid = removed + changed
            val entries = state.entries
                .filterKeys { it !in invalid }
                .filter { (scopeID, entry) -> nextScopes[scopeID]?.let { entryMatchesScope(entry, it) } ?: false }
            val leases = state.leases
                .filterKeys { it !in invalid }
                .filter { (scopeID, lease) -> nextScopes[scopeID]?.let { leaseMatchesScope(lease, it) } ?: false }
            state = state.copy(scopes = nextScopes, entries = entries, leases = leases)
            Unit to state
        } != null

    fun scope(accountID: UUID): SharedBalanceScope = transaction { state ->
        resolvedScope(accountID, state) to state
    } ?: SharedBalanceScope.account(accountID)

    fun latestEntry(accountID: UUID): SharedBalanceCacheEntry? = transaction { state ->
        val scope = resolvedScope(accountID, state)
        val entry = state.entries[scope.id]?.takeIf { entryMatchesScope(it, scope) }
        entry to state
    }

    fun cachedEntry(accountID: UUID, now: Instant = Instant.now()): SharedBalanceCacheEntry? =
        transaction { original ->
            val state = removeExpiredLeases(original, now)
            val scope = resolvedScope(accountID, state)
            val entry = state.entries[scope.id]?.takeIf {
                entryMatchesScope(it, scope) && isFresh(it, state.refreshIntervalMinutes, now)
            }
            entry to state
        }

    fun lastSuccessfulRefreshAt(accountID: UUID): Instant? = latestEntry(accountID)?.refreshedAt

    fun nextAutomaticRefreshAt(accountID: UUID, now: Instant = Instant.now()): Instant =
        transaction { state ->
            val scope = resolvedScope(accountID, state)
            val entry = state.entries[scope.id]?.takeIf { entryMatchesScope(it, scope) }
                ?: return@transaction now to state
            if (entry.refreshedAt.isAfter(now)) return@transaction now to state
            val refreshedDate = entry.refreshedAt.atZone(zoneId).toLocalDate()
            if (refreshedDate != now.atZone(zoneId).toLocalDate()) return@transaction now to state
            val intervalAt = entry.refreshedAt.plusSeconds(normalizeInterval(state.refreshIntervalMinutes) * 60L)
            val nextDay = refreshedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            minOf(intervalAt, nextDay) to state
        } ?: now

    fun beginAutomaticRefresh(
        accountID: UUID,
        source: SharedBalanceRefreshSource,
        now: Instant = Instant.now(),
        leaseDuration: Duration = DEFAULT_LEASE_DURATION,
    ): SharedBalanceRefreshClaim = transaction { original ->
        var state = removeExpiredLeases(original, now)
        val scope = resolvedScope(accountID, state)
        val cached = state.entries[scope.id]?.takeIf {
            entryMatchesScope(it, scope) && isFresh(it, state.refreshIntervalMinutes, now)
        }
        if (cached != null) return@transaction SharedBalanceRefreshClaim.Cached(cached) to state
        val inFlight = state.leases[scope.id]?.takeIf { it.expiresAt.isAfter(now) }
        if (inFlight != null) return@transaction SharedBalanceRefreshClaim.InFlight(inFlight.expiresAt) to state
        val (token, nextState) = createLease(scope, source, now, leaseDuration, state)
        state = nextState
        SharedBalanceRefreshClaim.Granted(token) to state
    } ?: SharedBalanceRefreshClaim.Unavailable

    fun beginForcedRefresh(
        accountID: UUID,
        source: SharedBalanceRefreshSource,
        now: Instant = Instant.now(),
        leaseDuration: Duration = DEFAULT_LEASE_DURATION,
    ): SharedBalanceRefreshClaim = transaction { original ->
        var state = removeExpiredLeases(original, now)
        val scope = resolvedScope(accountID, state)
        val inFlight = state.leases[scope.id]?.takeIf { it.expiresAt.isAfter(now) }
        if (inFlight != null) return@transaction SharedBalanceRefreshClaim.InFlight(inFlight.expiresAt) to state
        val (token, nextState) = createLease(scope, source, now, leaseDuration, state)
        state = nextState
        SharedBalanceRefreshClaim.Granted(token) to state
    } ?: SharedBalanceRefreshClaim.Unavailable

    fun completeRefresh(
        token: SharedBalanceRefreshLeaseToken,
        balanceYuan: Double,
        representativeAccountID: UUID? = null,
        completedAt: Instant = Instant.now(),
    ): SharedBalanceCacheEntry? = transaction { original ->
        var state = removeExpiredLeases(original, completedAt)
        val lease = state.leases[token.scope.id]
        if (lease == null || lease.leaseID != token.leaseID || !leaseMatchesScope(lease, token.scope)) {
            return@transaction null to state
        }
        val currentScope = state.scopes[token.scope.id] ?: token.scope
        if (currentScope != token.scope) {
            return@transaction null to state.copy(leases = state.leases - token.scope.id)
        }
        val requestedRepresentative = representativeAccountID ?: lease.representativeAccountID
        val safeRepresentative = requestedRepresentative?.takeIf(currentScope.memberAccountIDs::contains)
        val entry = SharedBalanceCacheEntry(
            scopeID = currentScope.id,
            memberAccountIDs = currentScope.memberAccountIDs,
            representativeAccountID = safeRepresentative,
            balanceYuan = balanceYuan,
            refreshedAt = completedAt,
            source = token.source,
        )
        state = state.copy(
            entries = state.entries + (currentScope.id to entry),
            leases = state.leases - currentScope.id,
        )
        entry to state
    }

    fun failRefresh(token: SharedBalanceRefreshLeaseToken) {
        transaction { state ->
            val matching = state.leases[token.scope.id]?.leaseID == token.leaseID
            Unit to if (matching) state.copy(leases = state.leases - token.scope.id) else state
        }
    }

    fun invalidate(accountID: UUID) {
        transaction { state ->
            val scope = resolvedScope(accountID, state)
            Unit to state.copy(entries = state.entries - scope.id, leases = state.leases - scope.id)
        }
    }

    fun invalidateScope(scopeID: String) {
        transaction { state -> Unit to state.copy(entries = state.entries - scopeID, leases = state.leases - scopeID) }
    }

    fun clearAll() {
        transaction { state -> Unit to state.copy(scopes = emptyMap(), entries = emptyMap(), leases = emptyMap()) }
    }

    private fun <T> transaction(
        body: (SharedBalancePersistedState) -> Pair<T, SharedBalancePersistedState>,
    ): T? = storage.transaction { raw ->
        val normalized = normalizeState(raw)
        val (value, next) = body(normalized)
        SharedBalanceTransaction(value, normalizeState(next))
    }

    private fun createLease(
        scope: SharedBalanceScope,
        source: SharedBalanceRefreshSource,
        now: Instant,
        duration: Duration,
        state: SharedBalancePersistedState,
    ): Pair<SharedBalanceRefreshLeaseToken, SharedBalancePersistedState> {
        val safeSeconds = duration.seconds.coerceIn(MINIMUM_LEASE_SECONDS, MAXIMUM_LEASE_SECONDS)
        val token = SharedBalanceRefreshLeaseToken(
            leaseID = UUID.randomUUID(),
            scope = scope,
            source = source,
            startedAt = now,
            expiresAt = now.plusSeconds(safeSeconds),
        )
        val lease = PersistedSharedBalanceLease(
            leaseID = token.leaseID,
            scopeID = scope.id,
            memberAccountIDs = scope.memberAccountIDs,
            representativeAccountID = scope.representativeAccountID,
            source = source,
            startedAt = token.startedAt,
            expiresAt = token.expiresAt,
        )
        return token to state.copy(leases = state.leases + (scope.id to lease))
    }

    private fun resolvedScope(accountID: UUID, state: SharedBalancePersistedState): SharedBalanceScope =
        state.scopes.values.firstOrNull { accountID in it.memberAccountIDs } ?: SharedBalanceScope.account(accountID)

    private fun normalizedScopeRegistry(scopes: List<SharedBalanceScope>): Map<String, SharedBalanceScope> {
        val output = linkedMapOf<String, SharedBalanceScope>()
        val claimed = mutableSetOf<UUID>()
        for (raw in scopes) {
            val scope = raw.normalized()
            if (scope.id.isEmpty() || scope.memberAccountIDs.isEmpty()) continue
            val members = scope.memberAccountIDs.toSet()
            if (members.any(claimed::contains)) continue
            claimed += members
            output[scope.id] = scope
        }
        return output
    }

    private fun entryMatchesScope(entry: SharedBalanceCacheEntry, scope: SharedBalanceScope): Boolean =
        entry.scopeID == scope.id && entry.memberAccountIDs.toSet() == scope.memberAccountIDs.toSet()

    private fun leaseMatchesScope(lease: PersistedSharedBalanceLease, scope: SharedBalanceScope): Boolean =
        lease.scopeID == scope.id && lease.memberAccountIDs.toSet() == scope.memberAccountIDs.toSet()

    private fun isFresh(entry: SharedBalanceCacheEntry, intervalMinutes: Int, now: Instant): Boolean {
        if (entry.refreshedAt.atZone(zoneId).toLocalDate() != now.atZone(zoneId).toLocalDate()) return false
        val elapsed = Duration.between(entry.refreshedAt, now)
        if (elapsed.isNegative) return false
        return elapsed < Duration.ofMinutes(normalizeInterval(intervalMinutes).toLong())
    }

    private fun removeExpiredLeases(state: SharedBalancePersistedState, now: Instant): SharedBalancePersistedState =
        state.copy(leases = state.leases.filterValues { it.expiresAt.isAfter(now) })

    private fun normalizeState(state: SharedBalancePersistedState): SharedBalancePersistedState {
        val scopes = normalizedScopeRegistry(state.scopes.values.toList())
        val entries = state.entries.filter { (scopeID, entry) ->
            val scope = scopes[scopeID]
            if (scope != null) entryMatchesScope(entry, scope)
            else scopeID.startsWith("account:") && entry.scopeID == scopeID && entry.memberAccountIDs.size == 1
        }
        val leases = state.leases.filter { (scopeID, lease) ->
            val scope = scopes[scopeID]
            if (scope != null) leaseMatchesScope(lease, scope)
            else scopeID.startsWith("account:") && lease.scopeID == scopeID && lease.memberAccountIDs.size == 1
        }
        return state.copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            refreshIntervalMinutes = normalizeInterval(state.refreshIntervalMinutes),
            scopes = scopes,
            entries = entries,
            leases = leases,
        )
    }

    private fun normalizeInterval(minutes: Int): Int = minutes.coerceIn(MINIMUM_REFRESH_INTERVAL_MINUTES, MAXIMUM_REFRESH_INTERVAL_MINUTES)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 60
        val DEFAULT_LEASE_DURATION: Duration = Duration.ofMinutes(2)
        const val MINIMUM_REFRESH_INTERVAL_MINUTES = 1
        const val MAXIMUM_REFRESH_INTERVAL_MINUTES = 24 * 60
        const val MINIMUM_LEASE_SECONDS = 15L
        const val MAXIMUM_LEASE_SECONDS = 10 * 60L
    }
}
