package com.clxmhcs.chinaunicom.data.phonebill

import com.clxmhcs.chinaunicom.core.login.PhoneBillAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.account.AccountRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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

sealed interface PhoneBillLoadState {
    data object Idle : PhoneBillLoadState
    data object Loading : PhoneBillLoadState
    data object Loaded : PhoneBillLoadState
    data class Failed(val message: String) : PhoneBillLoadState
}

data class PhoneBillStoreState(
    val sourceAccountID: UUID? = null,
    val months: List<BillMonth> = emptyList(),
    val selectedMonth: BillMonth? = null,
    val requestedMonth: BillMonth? = null,
    val failedMonth: BillMonth? = null,
    val snapshot: PhoneBillSnapshot? = null,
    val loadState: PhoneBillLoadState = PhoneBillLoadState.Idle,
)

interface PhoneBillStore {
    val state: StateFlow<PhoneBillStoreState>
    suspend fun loadIfNeeded(sourceAccount: UnicomAccount?)
    suspend fun reload(sourceAccount: UnicomAccount?)
    suspend fun select(month: BillMonth, sourceAccount: UnicomAccount?)
    suspend fun refreshSelectedMonth(sourceAccount: UnicomAccount?)
    suspend fun retry(sourceAccount: UnicomAccount?)
    suspend fun applyRefreshPolicyChange(sourceAccount: UnicomAccount?)
}

class DefaultPhoneBillStore(
    private val lifecycle: PhoneBillAccountCredentialLifecycle,
    private val cache: PhoneBillDiskCache,
    private val cachePolicy: PhoneBillCachePolicy,
    private val accountRepository: AccountRepository,
    private val historicalResolver: PhoneBillHistoricalCacheResolver = PhoneBillHistoricalCacheResolver(),
    private val historicalQueryCoordinator: PhoneBillHistoricalQueryCoordinator = PhoneBillHistoricalQueryCoordinator.shared,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) : PhoneBillStore {
    private val _state = MutableStateFlow(PhoneBillStoreState())
    override val state: StateFlow<PhoneBillStoreState> = _state.asStateFlow()

    private val requestMutex = Mutex()
    private val queueLock = Any()
    private var cachedSnapshots: Map<String, PhoneBillSnapshot> = emptyMap()
    private var activeSourceAccount: UnicomAccount? = null
    private var queuedMonthSelection: BillMonth? = null

    override suspend fun loadIfNeeded(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("还没有可查询账单的号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) reset(sourceAccount.id)
        if (_state.value.snapshot == null && _state.value.loadState !is PhoneBillLoadState.Loading) {
            reload(sourceAccount)
        }
    }

    override suspend fun reload(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("还没有可查询账单的号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) reset(sourceAccount.id)
        if (!requestMutex.tryLock()) return
        beginRequest(null)
        try {
            val requestDate = Instant.now(clock)
            val localAccounts = localAccounts(sourceAccount)
            withContext(ioDispatcher) { runCatching { cache.pruneAccounts(localAccounts.map { it.id }.toSet()) } }
            cachedSnapshots = withContext(ioDispatcher) { cache.load(sourceAccount.id) }

            val currentKey = cachePolicy.currentMonthKey(requestDate)
            val cachedCurrent = cachedSnapshots[currentKey]
            if (cachedCurrent != null) {
                if (!historicalResolver.snapshotBelongsToAccount(cachedCurrent, sourceAccount, localAccounts)) {
                    cachedSnapshots = withContext(ioDispatcher) {
                        runCatching { cache.removeSnapshot(sourceAccount.id, currentKey) }
                            .getOrElse { cachedSnapshots - currentKey }
                    }
                } else if (cachePolicy.isFresh(cachedCurrent, cachedCurrent.month, requestDate)) {
                    commitCached(cachedCurrent, cachedCurrent.month, recentMonths(requestDate))
                    return
                }
            }

            val monthsResult = withContext(ioDispatcher) { lifecycle.fetchMonthsValidated(sourceAccount.id) }
            val loadedMonths = cachePolicy.visibleMonths(monthsResult.months, requestDate)
            if (loadedMonths.isEmpty()) throw IllegalStateException("联通未返回13个月范围内的可查询账单月份")
            val currentMonth = loadedMonths.firstOrNull { it.key == currentKey }
                ?: throw IllegalStateException("联通未返回当前月份的账单入口")
            _state.update { it.copy(months = loadedMonths, requestedMonth = currentMonth) }
            val result = withContext(ioDispatcher) { lifecycle.fetchDetailValidated(sourceAccount.id, currentMonth) }
            persist(result.snapshot, sourceAccount.id, loadedMonths)
            commit(result.snapshot, currentMonth, loadedMonths)
        } catch (_: CancellationException) {
            finishCancellation()
        } catch (error: Exception) {
            finishFailure(error)
        } finally {
            requestMutex.unlock()
            consumeQueuedSelection()
        }
    }

    override suspend fun select(month: BillMonth, sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("还没有可查询账单的号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) {
            reset(sourceAccount.id)
            reload(sourceAccount)
            return
        }
        if (_state.value.months.none { it.id == month.id }) return
        if (!requestMutex.tryLock()) {
            synchronized(queueLock) {
                queuedMonthSelection = if (_state.value.requestedMonth?.id == month.id) null else month
            }
            return
        }

        try {
            val now = Instant.now(clock)
            if (month.key == cachePolicy.currentMonthKey(now)) {
                val cached = cachedSnapshots[month.key]
                if (cached != null &&
                    historicalResolver.snapshotBelongsToAccount(cached, sourceAccount, localAccounts(sourceAccount)) &&
                    cachePolicy.isFresh(cached, month, now)
                ) {
                    commitCached(cached, month, _state.value.months)
                    return
                }
            }
            fetchMonthRespectingHistoricalSharingLocked(month, sourceAccount, reuseSharedCacheBeforeNetwork = true)
        } finally {
            requestMutex.unlock()
            consumeQueuedSelection()
        }
    }

    override suspend fun refreshSelectedMonth(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("还没有可查询账单的号码")
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) {
            reset(sourceAccount.id)
            reload(sourceAccount)
            return
        }
        val month = _state.value.selectedMonth ?: _state.value.requestedMonth ?: _state.value.months.firstOrNull()
        if (month == null) return reload(sourceAccount)
        if (!requestMutex.tryLock()) return
        try {
            fetchMonthRespectingHistoricalSharingLocked(month, sourceAccount, reuseSharedCacheBeforeNetwork = false)
        } finally {
            requestMutex.unlock()
            consumeQueuedSelection()
        }
    }

    override suspend fun retry(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null) return failWithoutRequest("还没有可查询账单的号码")
        activeSourceAccount = sourceAccount
        val failed = _state.value.failedMonth
        if (failed == null || _state.value.sourceAccountID != sourceAccount.id) {
            reload(sourceAccount)
            return
        }
        if (!requestMutex.tryLock()) return
        try {
            val reuse = failed.key != cachePolicy.currentMonthKey(Instant.now(clock))
            fetchMonthRespectingHistoricalSharingLocked(failed, sourceAccount, reuseSharedCacheBeforeNetwork = reuse)
        } finally {
            requestMutex.unlock()
            consumeQueuedSelection()
        }
    }

    override suspend fun applyRefreshPolicyChange(sourceAccount: UnicomAccount?) {
        if (sourceAccount == null || _state.value.loadState is PhoneBillLoadState.Loading) return
        activeSourceAccount = sourceAccount
        if (_state.value.sourceAccountID != sourceAccount.id) return loadIfNeeded(sourceAccount)
        val month = _state.value.selectedMonth ?: _state.value.months.firstOrNull() ?: return reload(sourceAccount)
        val now = Instant.now(clock)
        if (month.key == cachePolicy.currentMonthKey(now)) {
            val cached = cachedSnapshots[month.key]
            if (cached != null &&
                historicalResolver.snapshotBelongsToAccount(cached, sourceAccount, localAccounts(sourceAccount)) &&
                cachePolicy.isFresh(cached, month, now)
            ) {
                commitCached(cached, month, _state.value.months)
                return
            }
        }
        select(month, sourceAccount)
    }

    private suspend fun fetchMonthRespectingHistoricalSharingLocked(
        month: BillMonth,
        sourceAccount: UnicomAccount,
        reuseSharedCacheBeforeNetwork: Boolean,
    ) {
        val now = Instant.now(clock)
        if (month.key == cachePolicy.currentMonthKey(now)) {
            fetchCurrentMonthLocked(month, sourceAccount)
            return
        }

        if (reuseSharedCacheBeforeNetwork) {
            resolveBestHistorical(sourceAccount, month, now)?.let { best ->
                if (best.sourceAccountID == sourceAccount.id) cachedSnapshots = cachedSnapshots + (month.key to best.snapshot)
                commitCached(best.snapshot, month, _state.value.months)
                return
            }
        }

        historicalQueryCoordinator.withMonthLock(month.key) {
            if (reuseSharedCacheBeforeNetwork) {
                resolveBestHistorical(sourceAccount, month, Instant.now(clock))?.let { best ->
                    if (best.sourceAccountID == sourceAccount.id) cachedSnapshots = cachedSnapshots + (month.key to best.snapshot)
                    commitCached(best.snapshot, month, _state.value.months)
                    return@withMonthLock
                }
            }
            beginRequest(month)
            try {
                val result = withContext(ioDispatcher) { lifecycle.fetchDetailValidated(sourceAccount.id, month) }
                persist(result.snapshot, sourceAccount.id, _state.value.months)
                commit(result.snapshot, month, _state.value.months)
            } catch (_: CancellationException) {
                finishCancellation()
            } catch (error: Exception) {
                finishFailure(error)
            }
        }
    }

    private suspend fun fetchCurrentMonthLocked(month: BillMonth, sourceAccount: UnicomAccount) {
        beginRequest(month)
        try {
            val result = withContext(ioDispatcher) { lifecycle.fetchDetailValidated(sourceAccount.id, month) }
            persist(result.snapshot, sourceAccount.id, _state.value.months)
            commit(result.snapshot, month, _state.value.months)
        } catch (_: CancellationException) {
            finishCancellation()
        } catch (error: Exception) {
            finishFailure(error)
        }
    }

    private suspend fun resolveBestHistorical(
        targetAccount: UnicomAccount,
        month: BillMonth,
        now: Instant,
    ): PhoneBillHistoricalCacheResolver.Match? {
        val all = withContext(ioDispatcher) { cache.loadAll() }
        return historicalResolver.resolveBest(
            targetAccount = targetAccount,
            month = month,
            localAccounts = localAccounts(targetAccount),
            cachedSnapshotsByAccount = all,
            cachePolicy = cachePolicy,
            now = now,
        )
    }

    private suspend fun persist(snapshot: PhoneBillSnapshot, accountID: UUID, months: List<BillMonth>) {
        cachedSnapshots = cachedSnapshots + (snapshot.month.key to snapshot)
        val visibleKeys = months.map { it.key }.toSet()
        if (visibleKeys.isNotEmpty()) cachedSnapshots = cachedSnapshots.filterKeys(visibleKeys::contains)
        withContext(ioDispatcher) {
            runCatching { cache.upsert(snapshot, accountID, visibleKeys) }
                .onSuccess { cachedSnapshots = it }
        }
    }

    private fun beginRequest(month: BillMonth?) {
        _state.update {
            it.copy(
                requestedMonth = month,
                failedMonth = null,
                loadState = PhoneBillLoadState.Loading,
            )
        }
    }

    private fun commit(snapshot: PhoneBillSnapshot, month: BillMonth, months: List<BillMonth>) {
        _state.update {
            it.copy(
                months = months,
                snapshot = snapshot,
                selectedMonth = month,
                requestedMonth = null,
                failedMonth = null,
                loadState = PhoneBillLoadState.Loaded,
            )
        }
    }

    private fun commitCached(snapshot: PhoneBillSnapshot, month: BillMonth, months: List<BillMonth>) =
        commit(snapshot, month, months)

    private fun finishFailure(error: Exception) {
        _state.update {
            it.copy(
                failedMonth = it.requestedMonth,
                requestedMonth = null,
                loadState = PhoneBillLoadState.Failed(error.message ?: error::class.java.simpleName),
            )
        }
    }

    private fun finishCancellation() {
        _state.update {
            it.copy(
                requestedMonth = null,
                loadState = if (it.snapshot == null) PhoneBillLoadState.Idle else PhoneBillLoadState.Loaded,
            )
        }
    }

    private fun reset(accountID: UUID) {
        cachedSnapshots = emptyMap()
        synchronized(queueLock) { queuedMonthSelection = null }
        _state.value = PhoneBillStoreState(sourceAccountID = accountID)
    }

    private fun failWithoutRequest(message: String) {
        _state.update {
            it.copy(requestedMonth = null, failedMonth = null, loadState = PhoneBillLoadState.Failed(message))
        }
    }

    private fun localAccounts(targetAccount: UnicomAccount): List<UnicomAccount> {
        val accounts = accountRepository.loadAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.id == targetAccount.id }
        if (index >= 0) accounts[index] = targetAccount else accounts += targetAccount
        return accounts
    }

    private fun recentMonths(at: Instant): List<BillMonth> {
        val current = at.atZone(zoneId).withDayOfMonth(1)
        return (0 until PhoneBillCachePolicy.VISIBLE_MONTH_COUNT).map { offset ->
            val date: ZonedDateTime = current.minusMonths(offset.toLong())
            val generated = BillMonth("%04d".format(date.year), "%02d".format(date.monthValue))
            cachedSnapshots[generated.key]?.month ?: generated
        }
    }

    private suspend fun consumeQueuedSelection() {
        val queued = synchronized(queueLock) {
            val result = queuedMonthSelection
            queuedMonthSelection = null
            result
        } ?: return
        val account = activeSourceAccount ?: return
        select(queued, account)
    }
}
