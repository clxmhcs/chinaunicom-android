package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.TariffZoneDetail
import com.clxmhcs.chinaunicom.core.model.TariffZoneRegion
import com.clxmhcs.chinaunicom.core.model.TariffZoneScope
import com.clxmhcs.chinaunicom.core.model.TariffZoneSearchResult
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.tariffzone.TariffZoneStoreState

/** M9-G rough functional UI. Final visual parity is intentionally deferred. */
@Composable
fun TariffZoneScreen(
    accounts: List<UnicomAccount>,
    viewModel: TariffZoneViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val targets = accounts.filter(UnicomAccount::isEnabled)
        .sortedWith(compareBy<UnicomAccount> { it.sortOrder }.thenBy { it.mobile })
    var selectedAccountID by rememberSaveable { mutableStateOf<String?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val selectedAccount = targets.firstOrNull { it.id.toString() == selectedAccountID }

    LaunchedEffect(targets.map { it.id }) {
        if (targets.isEmpty()) {
            selectedAccountID = null
            viewModel.clear()
        } else if (selectedAccount == null) {
            selectedAccountID = targets.first().id.toString()
        }
    }
    LaunchedEffect(selectedAccount?.id) {
        val account = selectedAccount ?: return@LaunchedEffect
        searchText = ""
        viewModel.load(account)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("资费专区", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = { selectedAccount?.let(viewModel::reload) },
                enabled = selectedAccount != null && !state.loading,
            ) { Text("刷新") }
        }

        if (targets.isEmpty()) {
            Surface(modifier = Modifier.padding(16.dp).fillMaxWidth(), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("暂无联通号码", fontWeight = FontWeight.SemiBold)
                    Text("请先在设置中保存可用的联通手机号码凭据。", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(targets, key = { it.id }) { account ->
                    FilterChip(
                        selected = account.id.toString() == selectedAccountID,
                        onClick = { selectedAccountID = account.id.toString() },
                        label = { Text(maskTariffMobile(account.mobile)) },
                    )
                }
            }

            selectedAccount?.let { account ->
                TariffControls(
                    state = state,
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    onSearch = { viewModel.search(account, searchText) },
                    onClearSearch = { searchText = ""; viewModel.endSearch() },
                    onScope = { viewModel.setScope(account, it) },
                    onRegion = { viewModel.selectRegion(account, it) },
                    onFirstLevel = { viewModel.selectFirstLevel(account, it) },
                    onSecondLevel = { viewModel.selectSecondLevel(account, it) },
                    onProducts = { viewModel.selectProducts(account, it) },
                )
                if (state.searchQuery.isNotEmpty()) {
                    SearchResults(
                        modifier = Modifier.weight(1f),
                        state = state,
                        onOpen = { viewModel.openSearchResult(account, it) },
                        onCloseDetail = viewModel::closeSearchDetail,
                    )
                } else {
                    BrowseResults(
                        modifier = Modifier.weight(1f),
                        state = state,
                        onLoadMore = { viewModel.loadMore(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TariffControls(
    state: TariffZoneStoreState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onScope: (TariffZoneScope) -> Unit,
    onRegion: (TariffZoneRegion) -> Unit,
    onFirstLevel: (String) -> Unit,
    onSecondLevel: (String) -> Unit,
    onProducts: (Set<String>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("名称/关键词/方案编号") },
            )
            Button(onClick = onSearch, enabled = searchText.trim().isNotEmpty() && !state.searchLoading) { Text("搜索") }
            if (state.searchQuery.isNotEmpty()) OutlinedButton(onClick = onClearSearch) { Text("清除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.scope == TariffZoneScope.LOCAL,
                onClick = { onScope(TariffZoneScope.LOCAL) },
                label = { Text("本地资费") },
            )
            FilterChip(
                selected = state.scope == TariffZoneScope.NATIONAL,
                onClick = { onScope(TariffZoneScope.NATIONAL) },
                label = { Text("全国资费") },
            )
            if (state.scope == TariffZoneScope.LOCAL) RegionMenu(state, onRegion)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimpleMenu(
                state.selectedFirstLevel?.name ?: "一级分类",
                state.levels.map { it.id to it.name },
                onFirstLevel,
            )
            SimpleMenu(
                state.selectedSecondLevel?.name ?: "二级分类",
                state.selectedFirstLevel?.secondLevels.orEmpty().map { it.id to it.name },
                onSecondLevel,
            )
        }
        if (state.searchQuery.isEmpty() && state.productReferences.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedProductIDs.isEmpty(),
                        onClick = { onProducts(emptySet()) },
                        label = { Text("全部资费") },
                    )
                }
                items(state.productReferences, key = { it.id }) { product ->
                    val selected = product.id in state.selectedProductIDs
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = state.selectedProductIDs.toMutableSet().apply {
                                if (!add(product.id)) remove(product.id)
                            }
                            onProducts(next)
                        },
                        label = { Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionMenu(state: TariffZoneStoreState, onRegion: (TariffZoneRegion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(state.selectedRegion?.cityName ?: "地区") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.regionGroups.forEach { group ->
                DropdownMenuItem(text = { Text(group.provinceName, fontWeight = FontWeight.Bold) }, onClick = {}, enabled = false)
                group.regions.forEach { region ->
                    DropdownMenuItem(
                        text = { Text("  ${region.cityName}") },
                        onClick = { expanded = false; onRegion(region) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleMenu(title: String, entries: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = entries.isNotEmpty()) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun BrowseResults(modifier: Modifier, state: TariffZoneStoreState, onLoadMore: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.updatedAtText?.let { item { Text("截至：$it", style = MaterialTheme.typography.bodySmall) } }
        state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.loading && state.details.isEmpty()) item { Text("正在查询资费…") }
        else if (!state.loading && state.details.isEmpty()) item { Text("暂无资费信息") }
        items(state.details, key = { it.id }) { TariffDetailCard(it) }
        if (state.loadingMore) item { Text("正在加载更多…") }
        if (state.hasMore && !state.loadingMore) item { Button(onClick = onLoadMore) { Text("加载更多") } }
    }
}

@Composable
private fun SearchResults(
    modifier: Modifier,
    state: TariffZoneStoreState,
    onOpen: (TariffZoneSearchResult) -> Unit,
    onCloseDetail: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.searchDetailLoading) item { Text("正在加载资费详情…") }
        state.searchDetail?.let { detail ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onCloseDetail) { Text("返回搜索结果") }
                    TariffDetailCard(detail)
                }
            }
        }
        if (state.searchDetail == null) {
            if (state.searchLoading && state.searchResults.isEmpty()) item { Text("正在搜索资费名称…") }
            else if (!state.searchLoading && state.searchResults.isEmpty()) item { Text("未找到相关资费") }
            items(state.searchResults, key = { it.id }) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(result) },
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${result.firstLevelName} / ${result.secondLevelName} · ${result.reference.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.searchLoading && state.searchResults.isNotEmpty()) item { Text("继续建立搜索索引…") }
        }
    }
}

@Composable
private fun TariffDetailCard(detail: TariffZoneDetail) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(detail.name.ifBlank { "资费详情" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TariffDetailRow("方案编号", detail.reportNo)
            TariffDetailRow("资费标准", detail.standardFeeText)
            TariffDetailRow("其他费用", detail.extraFees.ifBlank { detail.otherFees })
            TariffDetailRow("语音(分钟)", detail.minute)
            TariffDetailRow("通用流量", detail.commonDataText)
            TariffDetailRow("短信(条)", detail.sms)
            TariffDetailRow("定向流量", detail.orientTrafficText)
            HorizontalDivider(Modifier.padding(vertical = 3.dp))
            TariffDetailRow("宽带", detail.broadBand)
            TariffDetailRow("IPTV", detail.iptv)
            TariffDetailRow("权益", detail.equityCoupon)
            TariffDetailRow("服务内容", detail.serviceContent)
            TariffDetailRow("适用范围", detail.useScope)
            TariffDetailRow("有效期", detail.validPeriod)
            TariffDetailRow("在售期", detail.onlinePeriod)
            TariffDetailRow("销售渠道", detail.saleChnl)
            TariffDetailRow("退订方式", detail.unsubscribe)
            TariffDetailRow("开始日期", detail.startDate)
            TariffDetailRow("结束日期", detail.endDate)
            TariffDetailRow("合约责任", detail.contractDuty)
            TariffDetailRow("其他说明", detail.otherDesc)
        }
    }
}

@Composable
private fun TariffDetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private fun maskTariffMobile(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.length >= 7) trimmed.replaceRange(3, 7, "****") else trimmed
}
