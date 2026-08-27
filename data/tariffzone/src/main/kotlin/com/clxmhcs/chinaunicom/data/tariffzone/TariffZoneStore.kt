package com.clxmhcs.chinaunicom.data.tariffzone

import com.clxmhcs.chinaunicom.core.login.TariffZoneRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetail
import com.clxmhcs.chinaunicom.core.model.TariffZoneFirstLevel
import com.clxmhcs.chinaunicom.core.model.TariffZoneProductReference
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegionGroup
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.model.TariffZoneSearchResult
import com.clxmhcs.chinaunicom.core.model.TariffZoneSecondLevel
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class TariffZoneStoreState(
    val accountID: UUID? = null,
    val scope: TariffZoneScope = TariffZoneScope.LOCAL,
    val regions: List<TariffZoneRegion> = emptyList(),
    val levels: List<TariffZoneFirstLevel> = TariffZoneFirstLevel.FALLBACK,
    val selectedRegion: TariffZoneRegion? = null,
    val selectedFirstLevelID: String = "1",
    val selectedSecondLevelID: String = "1001",
    val productReferences: List<TariffZoneProductReference> = emptyList(),
    val selectedProductIDs: Set<String> = emptySet(),
    val details: List<TariffZoneDetail> = emptyList(),
    val updatedAtText: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<TariffZoneSearchResult> = emptyList(),
    val searchLoading: Boolean = false,
    val searchDetail: TariffZoneDetail? = null,
    val searchDetailLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedFirstLevel: TariffZoneFirstLevel?
        get() = levels.firstOrNull { it.id == selectedFirstLevelID }

    val selectedSecondLevel: TariffZoneSecondLevel?
        get() = selectedFirstLevel?.secondLevels?.firstOrNull { it.id == selectedSecondLevelID }

    val selectedProductName: String?
        get() = when {
            selectedProductIDs.isEmpty() -> null
            selectedProductIDs.size == 1 -> productReferences.firstOrNull { it.id in selectedProductIDs }?.name
            else -> "已选${selectedProductIDs.size}项"
        }

    val regionGroups: List<TariffZoneRegionGroup>
        get() {
            val order = mutableListOf<String>()
            val grouped = linkedMapOf<String, MutableList<TariffZoneRegion>>()
            regions.forEach { region ->
                if (region.provinceCode !in grouped) order += region.provinceCode
                grouped.getOrPut(region.provinceCode) { mutableListOf() } += region
            }
            return order.mapNotNull { code ->
                val serverRegions = grouped[code].orEmpty()
                val first = serverRegions.firstOrNull() ?: return@mapNotNull null
                val complete = TariffZoneRegionCatalog.regions(code, first.provinceName).toMutableList()
                serverRegions.forEach { region ->
                    if (complete.none { it.cityCode == region.cityCode }) complete += region
                }
                TariffZoneRegionGroup(code, first.provinceName, if (complete.isEmpty()) serverRegions else complete)
            }
        }
}

interface TariffZoneStore {
    val state: StateFlow<TariffZoneStoreState>
    suspend fun load(account: UnicomAccount)
    suspend fun reload(account: UnicomAccount)
    suspend fun setScope(account: UnicomAccount, scope: TariffZoneScope)
    suspend fun selectRegion(account: UnicomAccount, region: TariffZoneRegion)
    suspend fun selectFirstLevel(account: UnicomAccount, id: String)
    suspend fun selectSecondLevel(account: UnicomAccount, id: String)
    suspend fun selectProducts(account: UnicomAccount, ids: Set<String>)
    suspend fun loadMore(account: UnicomAccount)
    suspend fun search(account: UnicomAccount, query: String)
    fun endSearch()
    suspend fun openSearchResult(account: UnicomAccount, result: TariffZoneSearchResult)
    fun closeSearchDetail()
    fun clear()
}

/** In-memory source-equivalent M9-G store. iOS TariffZoneStore has no disk cache/settings authority. */
class DefaultTariffZoneStore(
    private val lifecycle: TariffZoneRequestLifecycle,
    private val now: () -> Instant = Instant::now,
) : TariffZoneStore {
    private val _state = MutableStateFlow(TariffZoneStoreState())
    override val state: StateFlow<TariffZoneStoreState> = _state.asStateFlow()

    private var activeAccountID: UUID? = null
    private var requestGeneration: UUID = UUID.randomUUID()
    private var filteredReferences: List<TariffZoneProductReference> = emptyList()
    private var nextReferenceIndex = 0
    private var searchIndex: List<TariffZoneSearchResult> = emptyList()
    private var searchIndexKey: String? = null

    override fun clear() {
        requestGeneration = UUID.randomUUID()
        activeAccountID = null
        filteredReferences = emptyList()
        nextReferenceIndex = 0
        searchIndex = emptyList()
        searchIndexKey = null
        _state.value = TariffZoneStoreState()
    }

    override suspend fun load(account: UnicomAccount) {
        val generation = newGeneration()
        activeAccountID = account.id
        filteredReferences = emptyList()
        nextReferenceIndex = 0
        searchIndex = emptyList()
        searchIndexKey = null
        _state.value = TariffZoneStoreState(accountID = account.id, scope = TariffZoneScope.LOCAL, loading = true)
        if (!lifecycle.hasCredentials(account.id)) {
            publish(loading = false, errorMessage = "当前号码缺少可用凭据")
            return
        }
        try {
            val result = withContext(Dispatchers.IO) { lifecycle.fetchIndexValidated(account.id) }
            if (!isCurrent(generation, account.id)) return
            val levels = result.index.levels.ifEmpty { TariffZoneFirstLevel.FALLBACK }
            val regions = result.index.regions
            val selectedRegion = regions.firstOrNull {
                it.provinceCode == result.index.userProvinceCode && it.cityCode == result.index.userCityCode
            } ?: regions.firstOrNull { it.provinceCode == result.index.userProvinceCode } ?: regions.firstOrNull()
            val firstID = levels.firstOrNull { it.id == "1" }?.id ?: levels.firstOrNull()?.id.orEmpty()
            val first = levels.firstOrNull { it.id == firstID }
            val secondID = first?.secondLevels?.firstOrNull { it.id == "1001" }?.id
                ?: first?.secondLevels?.firstOrNull()?.id.orEmpty()
            _state.value = _state.value.copy(
                regions = regions,
                levels = levels,
                selectedRegion = selectedRegion,
                selectedFirstLevelID = firstID,
                selectedSecondLevelID = secondID,
                errorMessage = null,
            )
            if (selectedRegion == null || secondID.isEmpty()) {
                publish(loading = false, updatedAtText = timestamp(), errorMessage = null)
                return
            }
            reloadInternal(account, generation, preserveLoading = true)
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) publish(loading = false, errorMessage = error.message ?: error::class.java.simpleName)
        }
    }

    override suspend fun reload(account: UnicomAccount) {
        if (activeAccountID != account.id) {
            load(account)
            return
        }
        reloadInternal(account, newGeneration(), preserveLoading = false)
    }

    override suspend fun setScope(account: UnicomAccount, scope: TariffZoneScope) {
        if (activeAccountID != account.id) {
            load(account)
            if (activeAccountID != account.id) return
        }
        if (_state.value.scope == scope) return
        _state.value = _state.value.copy(scope = scope, selectedProductIDs = emptySet())
        searchIndex = emptyList()
        searchIndexKey = null
        reload(account)
    }

    override suspend fun selectRegion(account: UnicomAccount, region: TariffZoneRegion) {
        if (activeAccountID != account.id) {
            load(account)
            if (activeAccountID != account.id) return
        }
        _state.value = _state.value.copy(selectedRegion = region, selectedProductIDs = emptySet())
        searchIndex = emptyList()
        searchIndexKey = null
        reload(account)
    }

    override suspend fun selectFirstLevel(account: UnicomAccount, id: String) {
        val level = _state.value.levels.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            selectedFirstLevelID = level.id,
            selectedSecondLevelID = level.secondLevels.firstOrNull()?.id.orEmpty(),
            selectedProductIDs = emptySet(),
        )
        reload(account)
    }

    override suspend fun selectSecondLevel(account: UnicomAccount, id: String) {
        val second = _state.value.selectedFirstLevel?.secondLevels?.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(selectedSecondLevelID = second.id, selectedProductIDs = emptySet())
        reload(account)
    }

    override suspend fun selectProducts(account: UnicomAccount, ids: Set<String>) {
        if (activeAccountID != account.id) {
            load(account)
            return
        }
        val valid = ids.intersect(_state.value.productReferences.mapTo(mutableSetOf()) { it.id })
        if (valid.isEmpty()) {
            _state.value = _state.value.copy(selectedProductIDs = emptySet())
            reload(account)
            return
        }
        val generation = newGeneration()
        _state.value = _state.value.copy(
            selectedProductIDs = valid,
            details = emptyList(),
            loading = true,
            loadingMore = false,
            hasMore = false,
            updatedAtText = null,
            errorMessage = null,
        )
        try {
            val region = _state.value.selectedRegion ?: return
            val products = _state.value.productReferences.filter { it.id in valid }
            val collected = mutableListOf<TariffZoneDetail>()
            var latestTime: String? = null
            products.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                if (!isCurrent(generation, account.id)) return
                val result = withContext(Dispatchers.IO) {
                    lifecycle.fetchDetailsValidated(account.id, batch, index + 1, region)
                }
                if (!isCurrent(generation, account.id)) return
                result.details.forEach { detail -> if (collected.none { it.id == detail.id }) collected += detail }
                latestTime = result.timeText ?: latestTime
                _state.value = _state.value.copy(details = collected.toList())
            }
            publish(loading = false, details = collected, updatedAtText = latestTime ?: timestamp(), errorMessage = null)
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) publish(loading = false, errorMessage = error.message ?: error::class.java.simpleName)
        }
    }

    override suspend fun loadMore(account: UnicomAccount) {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore || activeAccountID != account.id) return
        loadNextBatch(account, requestGeneration)
    }

    override suspend fun search(account: UnicomAccount, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            endSearch()
            return
        }
        if (activeAccountID != account.id) load(account)
        val region = _state.value.selectedRegion ?: return
        if (activeAccountID != account.id) return
        val generation = newGeneration()
        _state.value = _state.value.copy(
            searchQuery = trimmed,
            searchResults = emptyList(),
            searchLoading = true,
            searchDetail = null,
            searchDetailLoading = false,
            errorMessage = null,
        )
        val key = listOf(account.id.toString(), _state.value.scope.rawValue, region.provinceCode, region.cityCode).joinToString("|")
        if (searchIndexKey == key) {
            publishSearch(matchingSearchResults(searchIndex, trimmed), loading = false)
            return
        }
        try {
            val collected = mutableListOf<TariffZoneSearchResult>()
            val seen = mutableSetOf<String>()
            _state.value.levels.forEach { firstLevel ->
                firstLevel.secondLevels.forEach { secondLevel ->
                    if (!isCurrent(generation, account.id)) return
                    val result = withContext(Dispatchers.IO) {
                        lifecycle.fetchProductReferencesValidated(
                            account.id,
                            _state.value.scope,
                            firstLevel.id,
                            secondLevel.id,
                            region,
                        )
                    }
                    if (!isCurrent(generation, account.id)) return
                    result.references.forEach { reference ->
                        if (seen.add(reference.id)) {
                            collected += TariffZoneSearchResult(
                                reference,
                                firstLevel.id,
                                firstLevel.name,
                                secondLevel.id,
                                secondLevel.name,
                            )
                        }
                    }
                    publishSearch(matchingSearchResults(collected, trimmed), loading = true)
                }
            }
            if (!isCurrent(generation, account.id)) return
            searchIndex = collected
            searchIndexKey = key
            publishSearch(matchingSearchResults(collected, trimmed), loading = false)
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) {
                _state.value = _state.value.copy(searchLoading = false, errorMessage = error.message ?: error::class.java.simpleName)
            }
        }
    }

    override fun endSearch() {
        requestGeneration = UUID.randomUUID()
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            searchLoading = false,
            searchDetail = null,
            searchDetailLoading = false,
            errorMessage = null,
        )
    }

    override suspend fun openSearchResult(account: UnicomAccount, result: TariffZoneSearchResult) {
        if (activeAccountID != account.id) return
        val region = _state.value.selectedRegion ?: return
        val generation = requestGeneration
        _state.value = _state.value.copy(searchDetailLoading = true, searchDetail = null, errorMessage = null)
        try {
            val response = withContext(Dispatchers.IO) {
                lifecycle.fetchDetailsValidated(account.id, listOf(result.reference), 1, region)
            }
            if (!isCurrent(generation, account.id)) return
            _state.value = _state.value.copy(
                searchDetailLoading = false,
                searchDetail = response.details.firstOrNull(),
                errorMessage = null,
            )
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) {
                _state.value = _state.value.copy(searchDetailLoading = false, errorMessage = error.message ?: error::class.java.simpleName)
            }
        }
    }

    override fun closeSearchDetail() {
        _state.value = _state.value.copy(searchDetail = null, searchDetailLoading = false)
    }

    private suspend fun reloadInternal(account: UnicomAccount, generation: UUID, preserveLoading: Boolean) {
        val region = _state.value.selectedRegion ?: run { publish(loading = false, errorMessage = null); return }
        val second = _state.value.selectedSecondLevel ?: run { publish(loading = false, errorMessage = null); return }
        filteredReferences = emptyList()
        nextReferenceIndex = 0
        _state.value = _state.value.copy(
            selectedProductIDs = emptySet(),
            productReferences = emptyList(),
            details = emptyList(),
            updatedAtText = null,
            loading = true,
            loadingMore = false,
            hasMore = false,
            searchQuery = "",
            searchResults = emptyList(),
            searchLoading = false,
            searchDetail = null,
            searchDetailLoading = false,
            errorMessage = null,
        )
        try {
            val result = withContext(Dispatchers.IO) {
                lifecycle.fetchProductReferencesValidated(
                    account.id,
                    _state.value.scope,
                    _state.value.selectedFirstLevelID,
                    second.id,
                    region,
                )
            }
            if (!isCurrent(generation, account.id)) return
            filteredReferences = result.references
            _state.value = _state.value.copy(productReferences = result.references, hasMore = result.references.isNotEmpty())
            if (filteredReferences.isEmpty()) {
                publish(loading = false, updatedAtText = timestamp(), hasMore = false, errorMessage = null)
                return
            }
            loadNextBatch(account, generation, initial = preserveLoading)
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) publish(loading = false, errorMessage = error.message ?: error::class.java.simpleName)
        }
    }

    private suspend fun loadNextBatch(account: UnicomAccount, generation: UUID, initial: Boolean = false) {
        if (!isCurrent(generation, account.id) || nextReferenceIndex >= filteredReferences.size) return
        val start = nextReferenceIndex
        val upper = minOf(start + BATCH_SIZE, filteredReferences.size)
        val batch = filteredReferences.subList(start, upper)
        _state.value = _state.value.copy(
            loading = initial || _state.value.details.isEmpty(),
            loadingMore = !initial && _state.value.details.isNotEmpty(),
            errorMessage = null,
        )
        try {
            val region = _state.value.selectedRegion ?: return
            val result = withContext(Dispatchers.IO) {
                lifecycle.fetchDetailsValidated(account.id, batch, start / BATCH_SIZE + 1, region)
            }
            if (!isCurrent(generation, account.id)) return
            val merged = _state.value.details.toMutableList()
            result.details.forEach { detail -> if (merged.none { it.id == detail.id }) merged += detail }
            nextReferenceIndex = upper
            val more = _state.value.selectedProductIDs.isEmpty() && nextReferenceIndex < filteredReferences.size
            publish(
                loading = false,
                loadingMore = false,
                details = merged,
                hasMore = more,
                updatedAtText = result.timeText ?: _state.value.updatedAtText ?: timestamp(),
                errorMessage = null,
            )
            if (result.details.isEmpty() && more) loadNextBatch(account, generation)
        } catch (error: Exception) {
            if (isCurrent(generation, account.id)) {
                publish(loading = false, loadingMore = false, errorMessage = error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun newGeneration(): UUID = UUID.randomUUID().also { requestGeneration = it }

    private fun isCurrent(generation: UUID, accountID: UUID): Boolean =
        requestGeneration == generation && activeAccountID == accountID && _state.value.accountID == accountID

    private fun matchingSearchResults(index: List<TariffZoneSearchResult>, query: String): List<TariffZoneSearchResult> =
        index.filter { it.name.contains(query, ignoreCase = true) || it.reference.id.contains(query, ignoreCase = true) }

    private fun publishSearch(results: List<TariffZoneSearchResult>, loading: Boolean) {
        _state.value = _state.value.copy(searchResults = results, searchLoading = loading, errorMessage = null)
    }

    private fun publish(
        loading: Boolean = _state.value.loading,
        loadingMore: Boolean = _state.value.loadingMore,
        details: List<TariffZoneDetail> = _state.value.details,
        hasMore: Boolean = _state.value.hasMore,
        updatedAtText: String? = _state.value.updatedAtText,
        errorMessage: String? = _state.value.errorMessage,
    ) {
        _state.value = _state.value.copy(
            loading = loading,
            loadingMore = loadingMore,
            details = details,
            hasMore = hasMore,
            updatedAtText = updatedAtText,
            errorMessage = errorMessage,
        )
    }

    private fun timestamp(): String = TIMESTAMP.format(now().atZone(CHINA_ZONE))

    companion object {
        private const val BATCH_SIZE = 5
        private val CHINA_ZONE = ZoneId.of("Asia/Shanghai")
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.CHINA)
    }
}
