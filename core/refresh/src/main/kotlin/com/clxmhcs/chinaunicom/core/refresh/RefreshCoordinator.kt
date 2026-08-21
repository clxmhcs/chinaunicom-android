package com.clxmhcs.chinaunicom.core.refresh

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** A single independent account or a configured shared-account refresh unit. */
data class RefreshScope(
    val id: String,
    val memberAccountIds: Set<UUID>,
    val representativeAccountId: UUID?,
) {
    init {
        require(id.isNotBlank())
        require(memberAccountIds.isNotEmpty())
        require(representativeAccountId == null || representativeAccountId in memberAccountIds)
    }

    companion object {
        fun account(accountId: UUID): RefreshScope = RefreshScope(
            id = "account:$accountId",
            memberAccountIds = setOf(accountId),
            representativeAccountId = accountId,
        )
    }
}

enum class RefreshSource { AUTOMATIC, MANUAL }

data class RefreshLease internal constructor(
    val id: UUID,
    val scopeId: String,
    val source: RefreshSource,
    val startedAt: Instant,
    val expiresAt: Instant,
)

data class RefreshCacheEntry<T>(
    val scopeId: String,
    val value: T,
    val refreshedAt: Instant,
)

sealed interface RefreshDecision<out T> {
    data class Cached<T>(val entry: RefreshCacheEntry<T>) : RefreshDecision<T>
    data class Granted(val lease: RefreshLease) : RefreshDecision<Nothing>
    data class InFlight<T>(val latestEntry: RefreshCacheEntry<T>?) : RefreshDecision<T>
}

/**
 * In-process implementation of the M6 refresh semantics. It is intentionally UI-free and does
 * not launch work itself: a caller obtains [RefreshDecision.Granted], performs its one network
 * request, then reports success or failure with the lease it received.
 */
class RefreshCoordinator<T>(
    intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val leaseDurationSeconds: Long = DEFAULT_LEASE_SECONDS,
) {
    private var intervalMinutes: Int = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    private val scopes = mutableMapOf<String, RefreshScope>()
    private val cache = mutableMapOf<String, RefreshCacheEntry<T>>()
    private val leases = mutableMapOf<String, RefreshLease>()
    private val lock = Any()

    init {
        require(leaseDurationSeconds > 0)
    }

    fun setIntervalMinutes(minutes: Int) = synchronized(lock) {
        intervalMinutes = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    fun replaceScopes(nextScopes: Collection<RefreshScope>, now: Instant = Instant.now()) = synchronized(lock) {
        removeExpiredLeases(now)
        val next = nextScopes.associateBy { it.id }
        val changed = scopes.keys.union(next.keys).filter { scopes[it] != next[it] }.toSet()
        changed.forEach {
            cache.remove(it)
            leases.remove(it)
        }
        scopes.clear()
        scopes.putAll(next)
    }

    fun request(
        accountId: UUID,
        source: RefreshSource,
        now: Instant = Instant.now(),
    ): RefreshDecision<T> = synchronized(lock) {
        removeExpiredLeases(now)
        val scope = scopeFor(accountId)
        val latest = cache[scope.id]?.takeIf { it.scopeId == scope.id }
        if (source == RefreshSource.AUTOMATIC && latest != null && isFresh(latest, now)) {
            return@synchronized RefreshDecision.Cached(latest)
        }
        if (leases[scope.id] != null) return@synchronized RefreshDecision.InFlight(latest)

        val lease = RefreshLease(
            id = UUID.randomUUID(),
            scopeId = scope.id,
            source = source,
            startedAt = now,
            expiresAt = now.plusSeconds(leaseDurationSeconds),
        )
        leases[scope.id] = lease
        RefreshDecision.Granted(lease)
    }

    /** Commits only when [lease] is still the owner of its scope. */
    fun complete(lease: RefreshLease, value: T, refreshedAt: Instant = Instant.now()): Boolean = synchronized(lock) {
        val active = leases[lease.scopeId] ?: return@synchronized false
        if (active.id != lease.id) return@synchronized false
        leases.remove(lease.scopeId)
        cache[lease.scopeId] = RefreshCacheEntry(lease.scopeId, value, refreshedAt)
        true
    }

    /** Releases only the matching in-flight lease and leaves the last successful cache untouched. */
    fun fail(lease: RefreshLease): Boolean = synchronized(lock) {
        val active = leases[lease.scopeId] ?: return@synchronized false
        if (active.id != lease.id) return@synchronized false
        leases.remove(lease.scopeId)
        true
    }

    fun latest(accountId: UUID): RefreshCacheEntry<T>? = synchronized(lock) {
        cache[scopeFor(accountId).id]
    }

    fun nextAutomaticRefreshAt(accountId: UUID, now: Instant = Instant.now()): Instant = synchronized(lock) {
        val entry = cache[scopeFor(accountId).id] ?: return@synchronized now
        if (!sameLocalDay(entry.refreshedAt, now) || now.isBefore(entry.refreshedAt)) return@synchronized now
        val intervalDeadline = entry.refreshedAt.plusSeconds(intervalMinutes * 60L)
        val nextMidnight = ZonedDateTime.ofInstant(entry.refreshedAt, zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
        minOf(intervalDeadline, nextMidnight)
    }

    private fun scopeFor(accountId: UUID): RefreshScope = scopes.values.firstOrNull {
        accountId in it.memberAccountIds
    } ?: RefreshScope.account(accountId)

    private fun isFresh(entry: RefreshCacheEntry<T>, now: Instant): Boolean =
        sameLocalDay(entry.refreshedAt, now) && !now.isBefore(entry.refreshedAt) &&
            now.epochSecond - entry.refreshedAt.epochSecond < intervalMinutes * 60L

    private fun sameLocalDay(first: Instant, second: Instant): Boolean =
        ZonedDateTime.ofInstant(first, zoneId).toLocalDate() == ZonedDateTime.ofInstant(second, zoneId).toLocalDate()

    private fun removeExpiredLeases(now: Instant) {
        leases.entries.removeAll { (_, lease) -> !now.isBefore(lease.expiresAt) }
    }

    private companion object {
        private const val DEFAULT_INTERVAL_MINUTES = 60
        private const val DEFAULT_LEASE_SECONDS = 120L
        private const val MIN_INTERVAL_MINUTES = 1
        private const val MAX_INTERVAL_MINUTES = 24 * 60
    }
}
