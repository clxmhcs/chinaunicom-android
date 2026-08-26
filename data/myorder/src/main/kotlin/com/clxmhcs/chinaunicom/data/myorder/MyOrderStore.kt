package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.login.MyOrderRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.network.UnicomMyOrderClient
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

fun interface MyOrderEntryRefreshPolicy {
    fun refreshOnEntry(): Boolean
}

data class MyOrderStoreState(
    val activeAccountID: UUID? = null,
    val orders: List<MyOrder> = emptyList(),
    val isLoadingInitial: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val serverTime: String? = null,
    val errorMessage: String? = null,
)

interface MyOrderStore {
    val state: StateFlow<MyOrderStoreState>
    suspend fun load(account: UnicomAccount, force: Boolean = false)
    suspend fun refresh(account: UnicomAccount)
    suspend fun loadMoreIfNeeded(current: MyOrder, account: UnicomAccount)
    suspend fun loadMore(account: UnicomAccount)
    fun clearError()
}

/** Source-equivalent in-memory pagination state for iOS MyOrderStore.swift. No disk cache is added. */
class DefaultMyOrderStore(
    private val lifecycle: MyOrderRequestLifecycle,
    private val entryRefreshPolicy: MyOrderEntryRefreshPolicy = MyOrderEntryRefreshPolicy { true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageSize: Int = UnicomMyOrderClient.DEFAULT_PAGE_SIZE,
) : MyOrderStore {
    private val _state = MutableStateFlow(MyOrderStoreState())
    override val state: StateFlow<MyOrderStoreState> = _state.asStateFlow()

    private var nextPage = 1
    private var requestGeneration = UUID.randomUUID()
    private val initialLoadMutex = Mutex()
    private val loadMoreMutex = Mutex()

    override suspend fun load(account: UnicomAccount, force: Boolean) {
        if (_state.value.activeAccountID != account.id) reset(account.id)
        if (!force && !entryRefreshPolicy.refreshOnEntry()) return
        if (!force && _state.value.orders.isNotEmpty()) return
        loadPage(page = 1, account = account, replacing = true)
    }

    override suspend fun refresh(account: UnicomAccount) {
        if (_state.value.activeAccountID != account.id) reset(account.id)
        loadPage(page = 1, account = account, replacing = true)
    }

    override suspend fun loadMoreIfNeeded(current: MyOrder, account: UnicomAccount) {
        val currentState = _state.value
        if (currentState.activeAccountID != account.id || current.id != currentState.orders.lastOrNull()?.id) return
        loadMore(account)
    }

    override suspend fun loadMore(account: UnicomAccount) {
        val currentState = _state.value
        if (currentState.activeAccountID != account.id ||
            !currentState.hasMore ||
            currentState.isLoadingInitial ||
            currentState.isLoadingMore
        ) return
        loadPage(page = nextPage, account = account, replacing = false)
    }

    override fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun reset(accountID: UUID) {
        requestGeneration = UUID.randomUUID()
        nextPage = 1
        _state.value = MyOrderStoreState(activeAccountID = accountID)
    }

    private suspend fun loadPage(
        page: Int,
        account: UnicomAccount,
        replacing: Boolean,
    ) {
        if (_state.value.activeAccountID != account.id) return
        val mutex = if (replacing) initialLoadMutex else loadMoreMutex
        if (!mutex.tryLock()) return

        val generation = requestGeneration
        if (replacing) {
            _state.update { it.copy(isLoadingInitial = true) }
        } else {
            _state.update { it.copy(isLoadingMore = true) }
        }

        try {
            val result = withContext(ioDispatcher) {
                lifecycle.fetchValidated(
                    accountID = account.id,
                    mobile = account.mobile,
                    page = page,
                    pageSize = pageSize,
                )
            }
            if (generation != requestGeneration || _state.value.activeAccountID != account.id) return

            _state.update { current ->
                val merged = if (replacing) result.page.orders else current.orders + result.page.orders
                current.copy(
                    orders = unique(merged),
                    serverTime = result.page.serverTime,
                    hasMore = result.page.hasMore,
                    errorMessage = null,
                )
            }
            nextPage = page + 1
        } catch (_: CancellationException) {
            return
        } catch (error: Exception) {
            if (generation == requestGeneration) {
                _state.update { it.copy(errorMessage = error.message ?: error::class.java.simpleName) }
            }
        } finally {
            if (generation == requestGeneration) {
                _state.update {
                    if (replacing) it.copy(isLoadingInitial = false) else it.copy(isLoadingMore = false)
                }
            }
            mutex.unlock()
        }
    }

    private fun unique(values: List<MyOrder>): List<MyOrder> {
        val seen = mutableSetOf<String>()
        return values.filter { seen.add(it.id) }
    }
}
