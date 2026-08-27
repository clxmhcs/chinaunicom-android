package com.clxmhcs.chinaunicom.data.rebategift

import com.clxmhcs.chinaunicom.core.login.RebateAndGiftRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.GiftRecord
import com.clxmhcs.chinaunicom.core.model.RebateContract
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RebateGiftRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val monthlyRefreshDay: Int = 2,
    val monthlyRefreshHour: Int = 8,
    val queryImmediatelyWhenNoCache: Boolean = true,
)

fun interface RebateGiftRefreshPolicyProvider {
    fun current(): RebateGiftRefreshPolicy
}

data class RebateAndGiftStoreState(
    val accountID: UUID? = null,
    val displayedScope: RebateQueryScope = RebateQueryScope.ACCOUNT,
    val contracts: List<RebateContract> = emptyList(),
    val gifts: List<GiftRecord> = emptyList(),
    val queryTime: Instant? = null,
    val giftQueryTime: Instant? = null,
    val lastManualRefreshAt: Instant? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasVisibleContent: Boolean get() = contracts.isNotEmpty() || gifts.isNotEmpty()
}

interface RebateAndGiftStore {
    val state: StateFlow<RebateAndGiftStoreState>
    suspend fun loadIfNeeded(account: UnicomAccount, scope: RebateQueryScope)
    suspend fun loadGiftIfNeeded(account: UnicomAccount)
    suspend fun manualRefresh(account: UnicomAccount, scope: RebateQueryScope)
}

class DefaultRebateAndGiftStore(
    private val lifecycle: RebateAndGiftRequestLifecycle,
    private val cache: RebateAndGiftDiskCache,
    private val policyProvider: RebateGiftRefreshPolicyProvider = RebateGiftRefreshPolicyProvider { RebateGiftRefreshPolicy() },
    private val now: () -> Instant = Instant::now,
) : RebateAndGiftStore {
    private val _state = MutableStateFlow(RebateAndGiftStoreState())
    override val state: StateFlow<RebateAndGiftStoreState> = _state.asStateFlow()

    private var restoredAccountID: UUID? = null
    private var contractsByScope: Map<String, List<RebateContract>> = emptyMap()
    private var gifts: List<GiftRecord> = emptyList()
    private var queryTimesByScope: Map<String, Instant> = emptyMap()
    private var giftQueryTime: Instant? = null
    private var lastManualRefreshAt: Instant? = null
    private var automaticRefreshMonth: String? = null
    private var displayedScope = RebateQueryScope.ACCOUNT
    private var activeRequestID: UUID? = null
    private var activeRequestAccountID: UUID? = null
    private var activeRequestScope: RebateQueryScope? = null

    override suspend fun loadIfNeeded(account: UnicomAccount, scope: RebateQueryScope) {
        restoreCacheIfNeeded(account.id)
        setDisplayedScope(scope)
        publish(errorMessage = null)
        if (shouldRefreshContracts(scope)) refreshAll(account, scope, isManual = false)
    }

    override suspend fun loadGiftIfNeeded(account: UnicomAccount) {
        restoreCacheIfNeeded(account.id)
        if (giftQueryTime == null && shouldRefreshGift()) {
            refreshAll(account, displayedScope, isManual = false)
        }
    }

    override suspend fun manualRefresh(account: UnicomAccount, scope: RebateQueryScope) {
        restoreCacheIfNeeded(account.id)
        setDisplayedScope(scope)
        refreshAll(account, scope, isManual = true)
    }

    private suspend fun refreshAll(account: UnicomAccount, scope: RebateQueryScope, isManual: Boolean) {
        if (!isManual && _state.value.loading && activeRequestAccountID == account.id && activeRequestScope == scope) return
        if (!lifecycle.hasCredentials(account.id)) {
            publish(errorMessage = "当前号码缺少可用凭据")
            return
        }

        val requestID = UUID.randomUUID()
        activeRequestID = requestID
        activeRequestAccountID = account.id
        activeRequestScope = scope
        publish(loading = true, errorMessage = null)

        try {
            val contractResult = withContext(Dispatchers.IO) {
                lifecycle.fetchContractsValidated(account.id, scope)
            }
            if (!isCurrentRequest(requestID, account.id, scope)) return

            val giftResult = withContext(Dispatchers.IO) {
                lifecycle.fetchGiftRecordsValidated(account.id)
            }
            if (!isCurrentRequest(requestID, account.id, scope)) return

            val timestamp = now()
            contractsByScope = contractsByScope.toMutableMap().apply {
                put(scope.rawValue, contractResult.contracts)
            }
            queryTimesByScope = queryTimesByScope.toMutableMap().apply {
                put(scope.rawValue, contractResult.queryTime ?: timestamp)
            }
            gifts = giftResult.gifts
            giftQueryTime = giftResult.queryTime ?: timestamp
            if (isManual) lastManualRefreshAt = timestamp else automaticRefreshMonth = monthKey(timestamp)

            var persistenceWarning: String? = null
            runCatching { cache.save(account.id, cacheRecord()) }
                .onFailure { persistenceWarning = "返费/赠费已刷新，但本地缓存保存失败：${it.message ?: it::class.java.simpleName}" }
            finishRequest(requestID)
            publish(loading = false, loaded = true, errorMessage = persistenceWarning)
        } catch (error: Exception) {
            if (!isCurrentRequest(requestID, account.id, scope)) return
            finishRequest(requestID)
            publish(
                loading = false,
                loaded = restoredAccountID != null,
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun restoreCacheIfNeeded(accountID: UUID) {
        if (restoredAccountID == accountID) return
        invalidateActiveRequest()
        restoredAccountID = accountID
        contractsByScope = emptyMap()
        gifts = emptyList()
        queryTimesByScope = emptyMap()
        giftQueryTime = null
        lastManualRefreshAt = null
        automaticRefreshMonth = null
        displayedScope = RebateQueryScope.ACCOUNT
        val record = cache.load(accountID)
        if (record != null) {
            contractsByScope = record.contractsByScope
            gifts = record.gifts
            queryTimesByScope = record.queryTimesByScope
            giftQueryTime = record.giftQueryTime
            lastManualRefreshAt = record.lastManualRefreshAt
            automaticRefreshMonth = record.automaticRefreshMonth
        }
        publish(loaded = record != null, errorMessage = null)
    }

    private fun setDisplayedScope(scope: RebateQueryScope) {
        if (displayedScope != scope && activeRequestScope != null && activeRequestScope != scope) {
            invalidateActiveRequest()
        }
        displayedScope = scope
        publish(errorMessage = null)
    }

    private fun shouldRefreshContracts(scope: RebateQueryScope): Boolean {
        val policy = policyProvider.current()
        if (queryTimesByScope[scope.rawValue] == null && policy.queryImmediatelyWhenNoCache) return true
        return shouldAutomaticallyRefresh(policy)
    }

    private fun shouldRefreshGift(): Boolean {
        val policy = policyProvider.current()
        if (giftQueryTime == null && policy.queryImmediatelyWhenNoCache) return true
        return shouldAutomaticallyRefresh(policy)
    }

    private fun shouldAutomaticallyRefresh(policy: RebateGiftRefreshPolicy, instant: Instant = now()): Boolean {
        if (!policy.automaticRefreshEnabled) return false
        val current = instant.atZone(CHINA_ZONE)
        val day = policy.monthlyRefreshDay.coerceIn(1, 28)
        val hour = policy.monthlyRefreshHour.coerceIn(0, 23)
        if (current.dayOfMonth < day || (current.dayOfMonth == day && current.hour < hour)) return false
        return automaticRefreshMonth != monthKey(instant)
    }

    private fun monthKey(instant: Instant): String = MONTH_FORMATTER.format(instant.atZone(CHINA_ZONE))

    private fun cacheRecord() = RebateAndGiftCacheRecord(
        contractsByScope = contractsByScope,
        gifts = gifts,
        queryTimesByScope = queryTimesByScope,
        giftQueryTime = giftQueryTime,
        lastManualRefreshAt = lastManualRefreshAt,
        automaticRefreshMonth = automaticRefreshMonth,
    )

    private fun isCurrentRequest(requestID: UUID, accountID: UUID, scope: RebateQueryScope): Boolean =
        activeRequestID == requestID && activeRequestAccountID == accountID && activeRequestScope == scope && restoredAccountID == accountID

    private fun finishRequest(requestID: UUID) {
        if (activeRequestID != requestID) return
        activeRequestID = null
        activeRequestAccountID = null
        activeRequestScope = null
    }

    private fun invalidateActiveRequest() {
        activeRequestID = null
        activeRequestAccountID = null
        activeRequestScope = null
    }

    private fun publish(
        loading: Boolean = _state.value.loading,
        loaded: Boolean = _state.value.loaded,
        errorMessage: String? = _state.value.errorMessage,
    ) {
        val contracts = contractsByScope[displayedScope.rawValue].orEmpty()
        _state.value = RebateAndGiftStoreState(
            accountID = restoredAccountID,
            displayedScope = displayedScope,
            contracts = contracts,
            gifts = gifts,
            queryTime = queryTimesByScope[displayedScope.rawValue] ?: giftQueryTime,
            giftQueryTime = giftQueryTime,
            lastManualRefreshAt = lastManualRefreshAt,
            loading = loading,
            loaded = loaded,
            errorMessage = errorMessage,
        )
    }

    companion object {
        private val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA)
    }
}
