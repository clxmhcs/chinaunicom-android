package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount

@Composable
fun MyOrderScreen(
    accounts: List<UnicomAccount>,
    viewModel: MyOrderViewModel,
    onBack: () -> Unit,
    onOpenDetail: (UnicomAccount, MyOrder) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedAccountID by rememberSaveable { mutableStateOf<String?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedKindRaw by rememberSaveable { mutableStateOf(MyOrderKind.ALL.rawValue) }
    val enabledAccounts = accounts.filter { it.isEnabled }
    val selectedAccount = enabledAccounts.firstOrNull { it.id.toString() == selectedAccountID }
    val selectedKind = MyOrderKind.entries.firstOrNull { it.rawValue == selectedKindRaw } ?: MyOrderKind.ALL

    LaunchedEffect(selectedAccount?.id) {
        selectedAccount?.let { viewModel.load(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onBack) { Text("返回") }
            Text("我的订单", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        }

        if (enabledAccounts.isEmpty()) {
            Text("暂无可用联通号码", modifier = Modifier.padding(16.dp))
            return@Column
        }

        if (selectedAccount == null) {
            Text("请选择查询号码", modifier = Modifier.padding(16.dp))
            enabledAccounts.forEach { account ->
                Button(
                    onClick = { selectedAccountID = account.id.toString() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(account.displayTitle)
                }
            }
            return@Column
        }

        OrderToolbar(
            account = selectedAccount,
            accounts = enabledAccounts,
            searchText = searchText,
            selectedKind = selectedKind,
            onAccountChange = { selectedAccountID = it.id.toString() },
            onSearchChange = { searchText = it },
            onKindChange = { selectedKindRaw = it.rawValue },
            onRefresh = { viewModel.refresh(selectedAccount) },
        )

        val normalizedQuery = searchText.trim()
        val filtered = state.orders.filter { order ->
            val kindMatches = selectedKind == MyOrderKind.ALL || order.kind == selectedKind
            val searchMatches = normalizedQuery.isEmpty() || listOf(
                order.categoryTitle,
                order.primaryTitle,
                order.statusName,
                order.channelName,
                order.createdAtText,
                order.displayServiceNumber,
                order.address,
            ).filterNotNull().any { it.contains(normalizedQuery, ignoreCase = true) }
            kindMatches && searchMatches
        }

        when {
            state.isLoadingInitial && state.orders.isEmpty() -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null && state.orders.isEmpty() -> {
                Text("查询失败：${state.errorMessage}", modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.refresh(selectedAccount) }, modifier = Modifier.padding(16.dp)) { Text("重试") }
            }
            filtered.isEmpty() -> Text("没有符合条件的订单", modifier = Modifier.padding(16.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { order ->
                    MyOrderCard(order) { onOpenDetail(selectedAccount, order) }
                    if (order.id == state.orders.lastOrNull()?.id) {
                        LaunchedEffect(order.id, state.hasMore) {
                            if (state.hasMore) viewModel.loadMoreIfNeeded(order, selectedAccount)
                        }
                    }
                }
                item {
                    when {
                        state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                        state.hasMore -> Button(onClick = { viewModel.loadMore(selectedAccount) }) { Text("加载更多") }
                    }
                    state.errorMessage?.let { Text("加载提示：$it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun OrderToolbar(
    account: UnicomAccount,
    accounts: List<UnicomAccount>,
    searchText: String,
    selectedKind: MyOrderKind,
    onAccountChange: (UnicomAccount) -> Unit,
    onSearchChange: (String) -> Unit,
    onKindChange: (MyOrderKind) -> Unit,
    onRefresh: () -> Unit,
) {
    var accountMenu by rememberSaveable { mutableStateOf(false) }
    var kindMenu by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Button(onClick = { accountMenu = true }) { Text(account.displayTitle) }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { value ->
                        DropdownMenuItem(text = { Text(value.displayTitle) }, onClick = {
                            accountMenu = false
                            onAccountChange(value)
                        })
                    }
                }
            }
            Column {
                Button(onClick = { kindMenu = true }) { Text(selectedKind.title) }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    MyOrderKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(kind.title) }, onClick = {
                            kindMenu = false
                            onKindChange(kind)
                        })
                    }
                }
            }
            Button(onClick = onRefresh) { Text("刷新") }
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            label = { Text("搜索订单") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MyOrderCard(order: MyOrder, onOpenDetail: () -> Unit) {
    val hasDetail = order.detailAction != null
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasDetail, onClick = onOpenDetail)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${order.categoryTitle} · ${order.statusName}", style = MaterialTheme.typography.labelLarge)
            Text(order.primaryTitle, style = MaterialTheme.typography.titleMedium)
            order.operationType?.let { Text("操作：$it") }
            order.displayServiceNumber?.let { Text("业务号码：$it") }
            order.channelName?.let { Text("渠道：$it") }
            if (order.createdAtText.isNotBlank()) Text("时间：${order.createdAtText}")
            order.displayPrice?.let { Text("金额：$it") }
            order.displayPoints?.let { Text("积分：$it") }
            if (order.showsCancellationNotice) Text("订单存在退订/取消状态提示", color = MaterialTheme.colorScheme.error)
            Text(if (hasDetail) "查看详情" else "暂无可用详情", color = MaterialTheme.colorScheme.primary)
        }
    }
}
