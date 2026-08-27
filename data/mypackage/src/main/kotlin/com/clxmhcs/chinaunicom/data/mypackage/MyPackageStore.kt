package com.clxmhcs.chinaunicom.data.mypackage

import com.clxmhcs.chinaunicom.core.login.MyPackageAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.MyPackageFetchResult
import com.clxmhcs.chinaunicom.core.model.MyPackageSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.settings.MyPackageRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.PageEntryRefreshMode
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface MyPackageRefreshState {
    data object Idle : MyPackageRefreshState
    data object Loading : MyPackageRefreshState
    data class Failed(val message: String) : MyPackageRefreshState
    data class Warning(val message: String) : MyPackageRefreshState
}

data class MyPackageStoreState(
    val activeAccountID: UUID? = null,
    val snapshot: MyPackageSnapshot? = null,
    val snapshotFetchedAt: Instant? = null,
    val refreshState: MyPackageRefreshState = MyPackageRefreshState.Idle,
)

fun interface MyPackagePolicyProvider { fun current(): MyPackageRefreshPolicy }
class SettingsMyPackagePolicyProvider(private val settingsRepository: SettingsRepository) : MyPackagePolicyProvider {
    override fun current(): MyPackageRefreshPolicy = settingsRepository.loadMyPackageRefreshPolicy()
}
fun interface MyPackageRefreshClient { suspend fun fetch(accountID: UUID): MyPackageFetchResult }
class LoginMyPackageRefreshClient(
    private val lifecycle: MyPackageAccountCredentialLifecycle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MyPackageRefreshClient {
    override suspend fun fetch(accountID: UUID): MyPackageFetchResult = withContext(ioDispatcher) { lifecycle.refreshValidated(accountID) }
}

class DefaultMyPackageStore(
    private val client: MyPackageRefreshClient,
    private val cache: MyPackageDiskCache,
    private val policyProvider: MyPackagePolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(MyPackageStoreState())
    val state: StateFlow<MyPackageStoreState> = _state.asStateFlow()
    private var generation = UUID.randomUUID()

    suspend fun load(account: UnicomAccount, force: Boolean = false) {
        if (_state.value.activeAccountID != account.id) reset(account.id)
        if (force) return performLoad(account.id)
        restoreCacheIfNeeded(account.id)
        when (policyProvider.current().entryMode) {
            PageEntryRefreshMode.EVERY_ENTRY -> performLoad(account.id)
            PageEntryRefreshMode.REFRESH_WHEN_EXPIRED -> if (shouldRefreshExpired()) performLoad(account.id)
            PageEntryRefreshMode.MANUAL_ONLY -> applyManualOnlyMessage()
        }
    }

    suspend fun refresh(account: UnicomAccount) {
        if (_state.value.activeAccountID != account.id) reset(account.id)
        restoreCacheIfNeeded(account.id)
        performLoad(account.id)
    }

    suspend fun applyRefreshPolicyChange(account: UnicomAccount) {
        if (_state.value.refreshState == MyPackageRefreshState.Loading) return
        if (_state.value.activeAccountID != account.id) reset(account.id)
        restoreCacheIfNeeded(account.id)
        when (policyProvider.current().entryMode) {
            PageEntryRefreshMode.EVERY_ENTRY -> performLoad(account.id)
            PageEntryRefreshMode.REFRESH_WHEN_EXPIRED -> if (shouldRefreshExpired()) performLoad(account.id)
            PageEntryRefreshMode.MANUAL_ONLY -> applyManualOnlyMessage()
        }
    }

    fun clear() {
        generation = UUID.randomUUID()
        _state.value = MyPackageStoreState()
    }

    private fun applyManualOnlyMessage() {
        if (_state.value.snapshot == null) {
            _state.value = _state.value.copy(
                refreshState = MyPackageRefreshState.Failed("当前设置为仅手动刷新，暂无本地套餐缓存。点击“重新查询”可手动联网。"),
            )
        }
    }

    private fun reset(accountID: UUID) {
        generation = UUID.randomUUID()
        _state.value = MyPackageStoreState(activeAccountID = accountID)
    }

    private suspend fun restoreCacheIfNeeded(accountID: UUID) {
        val current = _state.value
        if (current.activeAccountID != accountID || current.snapshot != null || current.refreshState == MyPackageRefreshState.Loading) return
        val record = withContext(ioDispatcher) { cache.load(accountID) } ?: return
        if (_state.value.activeAccountID == accountID) {
            _state.value = _state.value.copy(
                snapshot = record.snapshot,
                snapshotFetchedAt = record.fetchedAt,
                refreshState = MyPackageRefreshState.Idle,
            )
        }
    }

    private fun shouldRefreshExpired(): Boolean {
        val current = _state.value
        val fetchedAt = current.snapshotFetchedAt ?: return true
        if (current.snapshot == null) return true
        val elapsed = Duration.between(fetchedAt, Instant.now(clock))
        if (elapsed.isNegative) return true
        return elapsed >= Duration.ofMinutes(policyProvider.current().cacheValidityMinutes.coerceAtLeast(1).toLong())
    }

    private suspend fun performLoad(accountID: UUID) {
        val current = _state.value
        if (current.activeAccountID != accountID || current.refreshState == MyPackageRefreshState.Loading) return
        val requestGeneration = generation
        _state.value = current.copy(refreshState = MyPackageRefreshState.Loading)
        try {
            val result = client.fetch(accountID)
            if (requestGeneration != generation || _state.value.activeAccountID != accountID) return
            val fetchedAt = Instant.now(clock)
            _state.value = _state.value.copy(
                snapshot = result.snapshot,
                snapshotFetchedAt = fetchedAt,
                refreshState = MyPackageRefreshState.Idle,
            )
            try {
                withContext(ioDispatcher) {
                    cache.save(MyPackageCacheRecord(snapshot = result.snapshot, fetchedAt = fetchedAt), accountID)
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    refreshState = MyPackageRefreshState.Warning("查询成功，但本地保存失败：${error.message ?: error::class.java.simpleName}"),
                )
            }
        } catch (_: CancellationException) {
            if (requestGeneration == generation) _state.value = _state.value.copy(refreshState = MyPackageRefreshState.Idle)
        } catch (error: Exception) {
            if (requestGeneration == generation) {
                _state.value = _state.value.copy(
                    refreshState = MyPackageRefreshState.Failed(error.message ?: error::class.java.simpleName),
                )
            }
        }
    }
}
