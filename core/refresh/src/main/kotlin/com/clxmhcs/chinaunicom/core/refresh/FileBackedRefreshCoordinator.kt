package com.clxmhcs.chinaunicom.core.refresh

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID

/** Encodes a cache value without coupling the refresh core to any network model. */
interface RefreshValueCodec<T> {
    fun encode(value: T): ByteArray
    fun decode(bytes: ByteArray): T
}

/** Raised when the durable refresh state cannot be safely decoded or committed. */
class RefreshPersistenceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * File-backed variant of [RefreshCoordinator]. Every state transition takes an operating-system
 * file lock and atomically replaces its state file, so app/widget/service processes share one
 * refresh lease and last-successful cache. It starts no work itself.
 */
class FileBackedRefreshCoordinator<T>(
    private val storageDirectory: File,
    private val valueCodec: RefreshValueCodec<T>,
    intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val leaseDurationSeconds: Long = DEFAULT_LEASE_SECONDS,
) {
    private var configuredIntervalMinutes = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    private val stateFile = File(storageDirectory, STATE_FILE_NAME)
    private val lockFile = File(storageDirectory, LOCK_FILE_NAME)

    init {
        require(leaseDurationSeconds > 0)
    }

    fun setIntervalMinutes(minutes: Int) = withState { state ->
        state.intervalMinutes = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        configuredIntervalMinutes = state.intervalMinutes
    }

    fun replaceScopes(nextScopes: Collection<RefreshScope>, now: Instant = Instant.now()) = withState { state ->
        state.removeExpiredLeases(now)
        val next = nextScopes.associateBy { it.id }
        val changed = state.scopes.keys.union(next.keys).filter { state.scopes[it] != next[it] }.toSet()
        changed.forEach {
            state.cache.remove(it)
            state.leases.remove(it)
        }
        state.scopes.clear()
        state.scopes.putAll(next)
    }

    fun request(accountId: UUID, source: RefreshSource, now: Instant = Instant.now()): RefreshDecision<T> = withState { state ->
        state.removeExpiredLeases(now)
        val scope = state.scopeFor(accountId)
        val latest = state.cache[scope.id]?.toEntry(scope.id)
        if (source == RefreshSource.AUTOMATIC && latest != null && state.isFresh(latest.refreshedAt, now)) {
            return@withState RefreshDecision.Cached(latest)
        }
        if (state.leases[scope.id] != null) return@withState RefreshDecision.InFlight(latest)

        val lease = RefreshLease(
            id = UUID.randomUUID(),
            scopeId = scope.id,
            source = source,
            startedAt = now,
            expiresAt = now.plusSeconds(leaseDurationSeconds),
        )
        state.leases[scope.id] = lease
        RefreshDecision.Granted(lease)
    }

    /** Commits only if [lease] is still the durable owner of its scope. */
    fun complete(lease: RefreshLease, value: T, refreshedAt: Instant = Instant.now()): Boolean = withState { state ->
        val active = state.leases[lease.scopeId] ?: return@withState false
        if (active.id != lease.id) return@withState false
        state.leases.remove(lease.scopeId)
        state.cache[lease.scopeId] = StoredCache(refreshedAt, valueCodec.encode(value))
        true
    }

    /** Releases only the matching durable lease, retaining the previous successful cache. */
    fun fail(lease: RefreshLease): Boolean = withState { state ->
        val active = state.leases[lease.scopeId] ?: return@withState false
        if (active.id != lease.id) return@withState false
        state.leases.remove(lease.scopeId)
        true
    }

    fun latest(accountId: UUID): RefreshCacheEntry<T>? = readState { state ->
        state.cache[state.scopeFor(accountId).id]?.toEntry(state.scopeFor(accountId).id)
    }

    fun nextAutomaticRefreshAt(accountId: UUID, now: Instant = Instant.now()): Instant = readState { state ->
        val entry = state.cache[state.scopeFor(accountId).id] ?: return@readState now
        if (!state.sameLocalDay(entry.refreshedAt, now) || now.isBefore(entry.refreshedAt)) return@readState now
        val intervalDeadline = entry.refreshedAt.plusSeconds(state.intervalMinutes * 60L)
        val nextMidnight = ZonedDateTime.ofInstant(entry.refreshedAt, zoneId)
            .toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        minOf(intervalDeadline, nextMidnight)
    }

    /** Clears durable refresh cache, scopes, and leases. Intended for explicit account reset only. */
    fun clear() = withLockedDirectory {
        if (stateFile.exists() && !stateFile.delete()) {
            throw RefreshPersistenceException("Unable to clear refresh state")
        }
    }

    private fun <R> withState(block: (StoredState) -> R): R = withLockedDirectory {
        val state = loadState()
        val result = block(state)
        writeState(state)
        result
    }

    private fun <R> readState(block: (StoredState) -> R): R = withLockedDirectory {
        block(loadState())
    }

    private fun <R> withLockedDirectory(block: () -> R): R = synchronized(processLock) {
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw RefreshPersistenceException("Unable to create refresh storage directory")
        }
        try {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val fileLock = channel.lock()
                try {
                    block()
                } finally {
                    fileLock.release()
                }
            }
        } catch (error: RefreshPersistenceException) {
            throw error
        } catch (error: Exception) {
            throw RefreshPersistenceException("Unable to lock refresh state", error)
        }
    }

    private fun loadState(): StoredState {
        if (!stateFile.exists()) return StoredState(configuredIntervalMinutes)
        val text = try {
            stateFile.readText(StandardCharsets.UTF_8)
        } catch (error: Exception) {
            throw RefreshPersistenceException("Unable to read refresh state", error)
        }
        if (text.isBlank()) return StoredState(configuredIntervalMinutes)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != VERSION_LINE) throw RefreshPersistenceException("Unsupported refresh state format")
        val state = StoredState(configuredIntervalMinutes)
        lines.drop(1).forEach { line -> parseLine(line, state) }
        configuredIntervalMinutes = state.intervalMinutes
        return state
    }

    private fun parseLine(line: String, state: StoredState) {
        val parts = line.split('|')
        try {
            when (parts.firstOrNull()) {
                "I" -> {
                    require(parts.size == 2)
                    state.intervalMinutes = parts[1].toInt().coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
                }
                "S" -> {
                    require(parts.size == 4)
                    val members = decode(parts[2]).split(',').filter { it.isNotBlank() }.map(UUID::fromString).toSet()
                    val representative = decode(parts[3]).takeIf { it.isNotBlank() }?.let(UUID::fromString)
                    val scope = RefreshScope(decode(parts[1]), members, representative)
                    state.scopes[scope.id] = scope
                }
                "C" -> {
                    require(parts.size == 4)
                    state.cache[decode(parts[1])] = StoredCache(Instant.ofEpochMilli(parts[2].toLong()), decodeBytes(parts[3]))
                }
                "L" -> {
                    require(parts.size == 6)
                    val scopeId = decode(parts[1])
                    state.leases[scopeId] = RefreshLease(
                        id = UUID.fromString(parts[2]), scopeId = scopeId,
                        source = RefreshSource.valueOf(parts[3]), startedAt = Instant.ofEpochMilli(parts[4].toLong()),
                        expiresAt = Instant.ofEpochMilli(parts[5].toLong()),
                    )
                }
                else -> throw IllegalArgumentException("unknown record")
            }
        } catch (error: Exception) {
            throw RefreshPersistenceException("Malformed refresh state record", error)
        }
    }

    private fun writeState(state: StoredState) {
        val contents = buildString {
            appendLine(VERSION_LINE)
            appendLine("I|${state.intervalMinutes}")
            state.scopes.values.sortedBy { it.id }.forEach { scope ->
                appendLine("S|${encode(scope.id)}|${encode(scope.memberAccountIds.sorted().joinToString(","))}|${encode(scope.representativeAccountId?.toString().orEmpty())}")
            }
            state.cache.toSortedMap().forEach { (scopeId, entry) ->
                appendLine("C|${encode(scopeId)}|${entry.refreshedAt.toEpochMilli()}|${encode(entry.value)}")
            }
            state.leases.toSortedMap().forEach { (scopeId, lease) ->
                appendLine("L|${encode(scopeId)}|${lease.id}|${lease.source.name}|${lease.startedAt.toEpochMilli()}|${lease.expiresAt.toEpochMilli()}")
            }
        }
        val temporary = File(storageDirectory, "$STATE_FILE_NAME.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(contents.toByteArray(StandardCharsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            throw RefreshPersistenceException("Unable to commit refresh state", error)
        }
    }

    private fun encode(value: String): String = encode(value.toByteArray(StandardCharsets.UTF_8))
    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun decode(value: String): String = decodeBytes(value).toString(StandardCharsets.UTF_8)
    private fun decodeBytes(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private inner class StoredState(var intervalMinutes: Int) {
        val scopes = mutableMapOf<String, RefreshScope>()
        val cache = mutableMapOf<String, StoredCache>()
        val leases = mutableMapOf<String, RefreshLease>()

        fun scopeFor(accountId: UUID): RefreshScope = scopes.values.firstOrNull { accountId in it.memberAccountIds }
            ?: RefreshScope.account(accountId)

        fun isFresh(refreshedAt: Instant, now: Instant): Boolean =
            sameLocalDay(refreshedAt, now) && !now.isBefore(refreshedAt) &&
                now.epochSecond - refreshedAt.epochSecond < intervalMinutes * 60L

        fun sameLocalDay(first: Instant, second: Instant): Boolean =
            ZonedDateTime.ofInstant(first, zoneId).toLocalDate() == ZonedDateTime.ofInstant(second, zoneId).toLocalDate()

        fun removeExpiredLeases(now: Instant) {
            leases.entries.removeAll { (_, lease) -> !now.isBefore(lease.expiresAt) }
        }
    }

    private inner class StoredCache(val refreshedAt: Instant, private val payload: ByteArray) {
        fun toEntry(scopeId: String): RefreshCacheEntry<T> = RefreshCacheEntry(scopeId, valueCodec.decode(payload), refreshedAt)
        val value: ByteArray get() = payload.copyOf()
    }

    private companion object {
        private val processLock = Any()
        private const val STATE_FILE_NAME = "refresh-state.v1"
        private const val LOCK_FILE_NAME = "refresh-state.lock"
        private const val VERSION_LINE = "version=1"
        private const val DEFAULT_INTERVAL_MINUTES = 60
        private const val DEFAULT_LEASE_SECONDS = 120L
        private const val MIN_INTERVAL_MINUTES = 1
        private const val MAX_INTERVAL_MINUTES = 24 * 60
    }
}
