package com.clxmhcs.chinaunicom.data.integral

import com.clxmhcs.chinaunicom.core.login.IntegralRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed interface IntegralLoadState {
    data object Idle : IntegralLoadState
    data object Loading : IntegralLoadState
    data object Loaded : IntegralLoadState
    data object ManualRequired : IntegralLoadState
    data class Failed(val message: String) : IntegralLoadState
}

data class IntegralStoreState(
    val sourceAccountID: UUID? = null,
    val snapshot: IntegralSnapshot? = null,
    val loadState: IntegralLoadState = IntegralLoadState.Idle,
    val isRefreshing: Boolean = false,
    val loadingDetailKey: String? = null,
    val errorMessage: String? = null,
)

interface IntegralStore {
    val state: StateFlow<IntegralStoreState>
    suspend fun loadIfNeeded(sourceAccount: UnicomAccount?)
    suspend fun applyRefreshPolicyChange(sourceAccount: UnicomAccount?)
    suspend fun manualRefresh(sourceAccount: UnicomAccount?, activeQuery: IntegralDetailQuery? = null)
    suspend fun retry(sourceAccount: UnicomAccount?)
    suspend fun loadDetails(query: IntegralDetailQuery, sourceAccount: UnicomAccount?, force: Boolean = false)
    fun details(query: IntegralDetailQuery): List<IntegralDetailItem>?
}

class DefaultIntegralStore(
    private val lifecycle: IntegralRequestLifecycle,
    private val cache: IntegralDiskCache,
    private val cachePolicy: IntegralCachePolicy,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IntegralStore {
    private val _state = MutableStateFlow(IntegralStoreState())
    override val state: StateFlow<IntegralStoreState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var cacheRecord: IntegralCacheRecord? = null
    private var requestID: UUID? = null
    private var activeSourceAccount: UnicomAccount? = null

    override suspend fun loadIfNeeded(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("未找到要查询积分的手机号码")
        activeSourceAccount = sourceAccount

        if (_state.value.sourceAccountID == sourceAccount.id) {
            if (_state.value.isRefreshing) return
            if (_state.value.snapshot != null) {
                if (cachePolicy.shouldCheckOnEntry() &&
                    cachePolicy.needsAutomaticRefresh(cacheRecord, Instant.now(clock))
                ) {
                    refreshOverview(sourceAccount)
                }
                return
            }
        }

        reset(sourceAccount.id)
        val cached = withContext(ioDispatcher) { cache.load(sourceAccount.id) }
        if (!isCurrentSource(sourceAccount.id)) return
        cacheRecord = cached
        if (cached != null && cached.snapshot.parserVersion == IntegralSnapshot.CURRENT_PARSER_VERSION) {
            _state.update {
                it.copy(
                    snapshot = cached.snapshot,
                    loadState = IntegralLoadState.Loaded,
                    errorMessage = null,
                )
            }
            if (cachePolicy.shouldCheckOnEntry() &&
                cachePolicy.needsAutomaticRefresh(cached, Instant.now(clock))
            ) {
                refreshOverview(sourceAccount)
            }
            return
        }

        cacheRecord = null
        if (!cachePolicy.shouldAutomaticallyQueryWithoutCache()) {
            _state.update {
                it.copy(
                    loadState = IntegralLoadState.ManualRequired,
                    errorMessage = null,
                )
            }
            return
        }
        refreshOverview(sourceAccount)
    }

    override suspend fun applyRefreshPolicyChange(sourceAccount: UnicomAccount?) {
        sourceAccount ?: return
        activeSourceAccount = sourceAccount
        if (_state.value.isRefreshing) return
        if (_state.value.sourceAccountID != sourceAccount.id) {
            loadIfNeeded(sourceAccount)
            return
        }
        if (_state.value.snapshot == null) {
            loadIfNeeded(sourceAccount)
            return
        }
        if (cachePolicy.needsAutomaticRefresh(cacheRecord, Instant.now(clock))) {
            refreshOverview(sourceAccount)
        }
    }

    override suspend fun manualRefresh(
        sourceAccount: UnicomAccount?,
        activeQuery: IntegralDetailQuery?,
    ) {
        if (sourceAccount == null) return failWithoutRequest("未找到要查询积分的手机号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) reset(sourceAccount.id)
        val succeeded = refreshOverview(sourceAccount)
        if (succeeded && activeQuery != null) {
            loadDetails(activeQuery, sourceAccount, force = true)
        }
    }

    override suspend fun retry(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("未找到要查询积分的手机号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) reset(sourceAccount.id)
        refreshOverview(sourceAccount)
    }

    override suspend fun loadDetails(
        query: IntegralDetailQuery,
        sourceAccount: UnicomAccount?,
        force: Boolean,
    ) {
        sourceAccount ?: return
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) {
            loadIfNeeded(sourceAccount)
        }
        if (_state.value.sourceAccountID != sourceAccount.id || _state.value.snapshot == null || cacheRecord == null) return
        if (!force && cacheRecord?.details?.containsKey(query.cacheKey) == true) return
        if (_state.value.loadingDetailKey == query.cacheKey) return

        _state.update { it.copy(loadingDetailKey = query.cacheKey, errorMessage = null) }
        try {
            val result = withContext(ioDispatcher) {
                lifecycle.fetchDetailsValidated(sourceAccount.id, sourceAccount.mobile, query)
            }
            if (!isCurrentSource(sourceAccount.id)) return
            val record = cacheRecord ?: throw IllegalStateException("积分缓存状态已失效")
            val updated = record.copy(details = record.details + (query.cacheKey to result.items))
            cacheRecord = updated
            try {
                withContext(ioDispatcher) { cache.save(updated, sourceAccount.id) }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = error.message ?: error::class.java.simpleName) }
            }
        } catch (_: CancellationException) {
            return
        } catch (error: Exception) {
            if (isCurrentSource(sourceAccount.id)) {
                _state.update { it.copy(errorMessage = error.message ?: error::class.java.simpleName) }
            }
        } finally {
            if (_state.value.loadingDetailKey == query.cacheKey) {
                _state.update { it.copy(loadingDetailKey = null) }
            }
        }
    }

    override fun details(query: IntegralDetailQuery): List<IntegralDetailItem>? =
        cacheRecord?.details?.get(query.cacheKey)

    private suspend fun refreshOverview(sourceAccount: UnicomAccount): Boolean {
        if (!refreshMutex.tryLock()) return false
        val currentRequestID = UUID.randomUUID()
        requestID = currentRequestID
        _state.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                loadState = if (it.snapshot == null) IntegralLoadState.Loading else it.loadState,
            )
        }

        return try {
            val requestDate = Instant.now(clock)
            val result = withContext(ioDispatcher) {
                lifecycle.fetchOverviewValidated(sourceAccount.id, sourceAccount.mobile, requestDate)
            }
            if (!isCurrent(currentRequestID, sourceAccount.id)) return false
            val record = IntegralCacheRecord(
                snapshot = result.snapshot,
                details = emptyMap(),
                refreshCycleKey = cachePolicy.refreshCycleKey(requestDate),
            )
            withContext(ioDispatcher) { cache.save(record, sourceAccount.id) }
            if (!isCurrent(currentRequestID, sourceAccount.id)) return false
            cacheRecord = record
            _state.update {
                it.copy(
                    snapshot = result.snapshot,
                    loadState = IntegralLoadState.Loaded,
                    isRefreshing = false,
                    errorMessage = null,
                )
            }
            true
        } catch (_: CancellationException) {
            finishCancellation(currentRequestID)
            false
        } catch (error: Exception) {
            if (requestID == currentRequestID) {
                val message = error.message ?: error::class.java.simpleName
                _state.update {
                    it.copy(
                        loadState = if (it.snapshot == null) IntegralLoadState.Failed(message) else IntegralLoadState.Loaded,
                        isRefreshing = false,
                        errorMessage = message,
                    )
                }
            }
            false
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun isCurrent(requestID: UUID, accountID: UUID): Boolean =
        this.requestID == requestID && _state.value.sourceAccountID == accountID

    private fun isCurrentSource(accountID: UUID): Boolean = _state.value.sourceAccountID == accountID

    private fun finishCancellation(currentRequestID: UUID) {
        if (requestID != currentRequestID) return
        _state.update {
            it.copy(
                isRefreshing = false,
                loadState = if (it.snapshot == null) IntegralLoadState.Idle else IntegralLoadState.Loaded,
            )
        }
    }

    private fun reset(accountID: UUID) {
        requestID = null
        cacheRecord = null
        _state.value = IntegralStoreState(sourceAccountID = accountID)
    }

    private fun failWithoutRequest(message: String) {
        requestID = null
        _state.update {
            it.copy(
                isRefreshing = false,
                loadState = IntegralLoadState.Failed(message),
                errorMessage = message,
            )
        }
    }
}
