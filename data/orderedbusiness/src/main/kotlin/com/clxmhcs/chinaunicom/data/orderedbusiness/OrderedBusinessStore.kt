package com.clxmhcs.chinaunicom.data.orderedbusiness

import com.clxmhcs.chinaunicom.core.login.OrderedBusinessAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.settings.CachedBusinessEntryMode
import com.clxmhcs.chinaunicom.data.settings.OrderedBusinessRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface OrderedBusinessRefreshState {
    data object Idle : OrderedBusinessRefreshState
    data object Loading : OrderedBusinessRefreshState
    data class Failed(val message: String) : OrderedBusinessRefreshState
    data class Warning(val message: String) : OrderedBusinessRefreshState
}

data class OrderedBusinessStoreState(
    val snapshots: Map<UUID, OrderedBusinessSnapshot> = emptyMap(),
    val refreshStates: Map<UUID, OrderedBusinessRefreshState> = emptyMap(),
    val isRefreshingAll: Boolean = false,
)

fun interface OrderedBusinessPolicyProvider {
    fun current(): OrderedBusinessRefreshPolicy
}

class SettingsOrderedBusinessPolicyProvider(
    private val settingsRepository: SettingsRepository,
) : OrderedBusinessPolicyProvider {
    override fun current(): OrderedBusinessRefreshPolicy = settingsRepository.loadOrderedBusinessRefreshPolicy()
}

fun interface OrderedBusinessRefreshClient {
    suspend fun fetch(accountID: UUID): OrderedBusinessFetchResult
}

class LoginOrderedBusinessRefreshClient(
    private val lifecycle: OrderedBusinessAccountCredentialLifecycle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OrderedBusinessRefreshClient {
    override suspend fun fetch(accountID: UUID): OrderedBusinessFetchResult =
        withContext(ioDispatcher) { lifecycle.refreshValidated(accountID) }
}

interface OrderedBusinessStore {
    val state: StateFlow<OrderedBusinessStoreState>

    fun snapshot(accountID: UUID): OrderedBusinessSnapshot?
    fun refreshState(accountID: UUID): OrderedBusinessRefreshState
    suspend fun loadCachedOrRefreshIfMissing(account: UnicomAccount)
    suspend fun reconcileAccounts(accountIDs: List<UUID>)
    suspend fun refresh(account: UnicomAccount)
    suspend fun refresh(accountID: UUID)
    suspend fun refreshAll(accounts: List<UnicomAccount>)
}

/** Source-equivalent M8-B store behavior from iOS OrderedBusinessStore.swift. */
class DefaultOrderedBusinessStore(
    private val client: OrderedBusinessRefreshClient,
    private val cache: OrderedBusinessDiskCache,
    private val policyProvider: OrderedBusinessPolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : OrderedBusinessStore {
    private val _state = MutableStateFlow(OrderedBusinessStoreState())
    override val state: StateFlow<OrderedBusinessStoreState> = _state.asStateFlow()

    private val cacheLoadMutex = Mutex()
    private val cacheWriteMutex = Mutex()
    private val refreshAllMutex = Mutex()
    private val loadingMutex = Mutex()
    private val loadingAccountIDs = mutableSetOf<UUID>()
    @Volatile private var cacheLoaded = false

    override fun snapshot(accountID: UUID): OrderedBusinessSnapshot? = _state.value.snapshots[accountID]

    override fun refreshState(accountID: UUID): OrderedBusinessRefreshState =
        _state.value.refreshStates[accountID] ?: OrderedBusinessRefreshState.Idle

    override suspend fun loadCachedOrRefreshIfMissing(account: UnicomAccount) {
        ensureCacheLoaded()
        if (shouldRefreshOnEntry(account.id, Instant.now(clock))) refresh(account.id)
    }

    override suspend fun reconcileAccounts(accountIDs: List<UUID>) {
        ensureCacheLoaded()
        val validIDs = accountIDs.toSet()
        val current = _state.value.snapshots
        val filtered = current.filterKeys(validIDs::contains)
        if (filtered.size != current.size) {
            _state.update { it.copy(snapshots = filtered) }
            runCatching { saveSnapshots(filtered) }
        }

        val policy = policyProvider.current()
        val now = Instant.now(clock)
        val refreshIDs = accountIDs.filter { shouldRefreshOnEntry(it, now, policy) }
        val gapMillis = policy.refreshAllAccountGapSeconds.coerceAtLeast(0) * 1_000L
        for ((index, accountID) in refreshIDs.withIndex()) {
            refresh(accountID)
            if (index < refreshIDs.lastIndex && gapMillis > 0L) {
                try {
                    sleeper(gapMillis)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }

    override suspend fun refresh(account: UnicomAccount) = refresh(account.id)

    override suspend fun refresh(accountID: UUID) {
        ensureCacheLoaded()
        val started = loadingMutex.withLock {
            if (!loadingAccountIDs.add(accountID)) return@withLock false
            _state.update { current ->
                current.copy(refreshStates = current.refreshStates + (accountID to OrderedBusinessRefreshState.Loading))
            }
            true
        }
        if (!started) return

        try {
            val result = client.fetch(accountID)
            _state.update { current ->
                current.copy(
                    snapshots = current.snapshots + (accountID to result.snapshot),
                    refreshStates = current.refreshStates + (accountID to OrderedBusinessRefreshState.Idle),
                )
            }
            val snapshots = _state.value.snapshots
            try {
                saveSnapshots(snapshots)
            } catch (error: Exception) {
                _state.update { current ->
                    current.copy(
                        refreshStates = current.refreshStates + (
                            accountID to OrderedBusinessRefreshState.Warning(
                                "查询成功，但本地保存失败：${error.message ?: error::class.java.simpleName}",
                            )
                        ),
                    )
                }
            }
        } catch (_: CancellationException) {
            _state.update { current ->
                current.copy(refreshStates = current.refreshStates + (accountID to OrderedBusinessRefreshState.Idle))
            }
        } catch (error: Exception) {
            _state.update { current ->
                current.copy(
                    refreshStates = current.refreshStates + (
                        accountID to OrderedBusinessRefreshState.Failed(
                            error.message ?: error::class.java.simpleName,
                        )
                    ),
                )
            }
        } finally {
            loadingMutex.withLock { loadingAccountIDs.remove(accountID) }
        }
    }

    override suspend fun refreshAll(accounts: List<UnicomAccount>) {
        if (accounts.isEmpty() || !refreshAllMutex.tryLock()) return
        _state.update { it.copy(isRefreshingAll = true) }
        try {
            val gapMillis = policyProvider.current().refreshAllAccountGapSeconds.coerceAtLeast(0) * 1_000L
            for ((index, account) in accounts.withIndex()) {
                refresh(account.id)
                if (index < accounts.lastIndex && gapMillis > 0L) {
                    try {
                        sleeper(gapMillis)
                    } catch (_: CancellationException) {
                        break
                    }
                }
            }
        } finally {
            _state.update { it.copy(isRefreshingAll = false) }
            refreshAllMutex.unlock()
        }
    }

    private fun shouldRefreshOnEntry(
        accountID: UUID,
        now: Instant,
        policy: OrderedBusinessRefreshPolicy = policyProvider.current(),
    ): Boolean {
        val cached = _state.value.snapshots[accountID]
        return when (policy.entryMode) {
            CachedBusinessEntryMode.CACHE_PREFERRED -> cached == null && policy.noCacheAutoQuery
            CachedBusinessEntryMode.REFRESH_WHEN_EXPIRED -> {
                if (cached == null) {
                    policy.noCacheAutoQuery
                } else {
                    val elapsed = Duration.between(cached.fetchedAt, now)
                    elapsed.isNegative || elapsed >= Duration.ofHours(policy.cacheValidityHours.coerceAtLeast(1).toLong())
                }
            }
            CachedBusinessEntryMode.EVERY_ENTRY -> true
            CachedBusinessEntryMode.MANUAL_ONLY -> false
        }
    }

    private suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        cacheLoadMutex.withLock {
            if (cacheLoaded) return
            val cached = withContext(ioDispatcher) { cache.load() }
            _state.update { current ->
                current.copy(snapshots = cached + current.snapshots)
            }
            cacheLoaded = true
        }
    }

    private suspend fun saveSnapshots(snapshots: Map<UUID, OrderedBusinessSnapshot>) {
        cacheWriteMutex.withLock {
            withContext(ioDispatcher) { cache.save(snapshots) }
        }
    }
}
