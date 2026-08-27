package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessItem
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.orderedbusiness.OrderedBusinessRefreshState
import kotlinx.coroutines.launch

/**
 * M9-C1 functional-only Other Business ordered-business destination.
 * It deliberately reuses the M8 OrderedBusinessStore/Client/cache/refresh-policy authority.
 */
@Composable
fun OtherOrderedBusinessScreen(
    accounts: List<UnicomAccount>,
    businessViewModel: ComprehensiveBusinessViewModel,
    onBack: () -> Unit,
) {
    val state by businessViewModel.orderedState.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedAccountID by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAccount = accounts.firstOrNull { it.id.toString() == selectedAccountID }
    val accountKey = accounts.joinToString("|") { it.id.toString() }

    LaunchedEffect(accountKey) {
        businessViewModel.orderedBusinessStore.reconcileAccounts(accounts.map { it.id })
    }

    if (selectedAccount != null) {
        OrderedBusinessTargetDetail(
            account = selectedAccount,
            businessViewModel = businessViewModel,
            onBack = { selectedAccountID = null },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("已订业务", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "进入页面是否联网由现有 App 刷新逻辑决定；手动刷新始终重新查询。手机账号与独立宽带账号共用 M8 已订业务数据层。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        enabled = accounts.isNotEmpty() && !state.isRefreshingAll,
                        onClick = { scope.launch { businessViewModel.orderedBusinessStore.refreshAll(accounts) } },
                    ) {
                        if (state.isRefreshingAll) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text(if (state.isRefreshingAll) "查询中" else "全部刷新")
                    }
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                FunctionalOrderedCard {
                    Text("还没有可查询的号码", style = MaterialTheme.typography.titleMedium)
                    Text("请先保存联通手机账号，或在设置中添加独立宽带账号。")
                }
            }
        } else {
            items(accounts, key = { it.id }) { account ->
                val snapshot = state.snapshots[account.id]
                val refreshState = state.refreshStates[account.id] ?: OrderedBusinessRefreshState.Idle
                FunctionalOrderedCard(
                    modifier = Modifier.clickable { selectedAccountID = account.id.toString() },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(maskOrderedBusinessNumber(account), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (account.isBroadbandOrderedTarget()) "独立宽带账号" else account.displayName.ifBlank { "联通手机号" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (snapshot != null) {
                                Text(snapshot.title ?: "已订业务")
                                Text("共 ${snapshot.totalCount} 项", style = MaterialTheme.typography.bodySmall)
                                snapshot.queryTime?.takeIf(String::isNotBlank)?.let {
                                    Text("查询时间：$it", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Text("暂无已订业务缓存", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(
                            enabled = refreshState !is OrderedBusinessRefreshState.Loading,
                            onClick = { scope.launch { businessViewModel.orderedBusinessStore.refresh(account) } },
                        ) {
                            Text(if (refreshState is OrderedBusinessRefreshState.Loading) "查询中" else "刷新")
                        }
                    }
                    when (refreshState) {
                        is OrderedBusinessRefreshState.Failed -> Text(refreshState.message, color = MaterialTheme.colorScheme.error)
                        is OrderedBusinessRefreshState.Warning -> Text(refreshState.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }
            item {
                Text(
                    "共 ${accounts.size} 个号码",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OrderedBusinessTargetDetail(
    account: UnicomAccount,
    businessViewModel: ComprehensiveBusinessViewModel,
    onBack: () -> Unit,
) {
    val state by businessViewModel.orderedState.collectAsState()
    val scope = rememberCoroutineScope()
    val snapshot = state.snapshots[account.id]
    val refreshState = state.refreshStates[account.id] ?: OrderedBusinessRefreshState.Idle

    LaunchedEffect(account.id) {
        businessViewModel.orderedBusinessStore.loadCachedOrRefreshIfMissing(account)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("已订业务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(maskOrderedBusinessNumber(account), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = refreshState !is OrderedBusinessRefreshState.Loading,
                    onClick = { scope.launch { businessViewModel.orderedBusinessStore.refresh(account) } },
                ) {
                    Text(if (refreshState is OrderedBusinessRefreshState.Loading) "查询中" else "手动刷新")
                }
            }
        }

        when (refreshState) {
            is OrderedBusinessRefreshState.Failed -> item { OrderedStatusCard(refreshState.message) }
            is OrderedBusinessRefreshState.Warning -> item { OrderedStatusCard(refreshState.message) }
            else -> Unit
        }

        if (snapshot == null) {
            item {
                FunctionalOrderedCard {
                    if (refreshState is OrderedBusinessRefreshState.Loading) {
                        CircularProgressIndicator()
                        Text("正在读取已订业务")
                    } else {
                        Text("尚未取得已订业务")
                        Text("当前刷新策略可能要求手动查询。")
                    }
                }
            }
        } else {
            item {
                FunctionalOrderedCard {
                    snapshot.title?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("保存时间：${snapshot.fetchedAt}", style = MaterialTheme.typography.bodySmall)
                    snapshot.queryTime?.takeIf(String::isNotBlank)?.let {
                        Text("查询时间：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("共 ${snapshot.totalCount} 项", style = MaterialTheme.typography.bodySmall)
                }
            }

            snapshot.sections.forEach { section ->
                item(key = section.id) {
                    FunctionalOrderedCard {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (section.items.isEmpty()) {
                            Text("本分类暂无项目")
                        } else {
                            section.items.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider()
                                OrderedBusinessItemContent(item)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OrderedBusinessItemContent(item: OrderedBusinessItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            item.fee?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        item.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(orderedBusinessDateText(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OrderedStatusCard(message: String) {
    FunctionalOrderedCard { Text(message, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun FunctionalOrderedCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private fun UnicomAccount.isBroadbandOrderedTarget(): Boolean = packageName == "宽带账号"

private fun maskOrderedBusinessNumber(account: UnicomAccount): String {
    val value = account.mobile.trim()
    if (account.isBroadbandOrderedTarget()) {
        return if (value.length > 8) value.take(4) + "****" + value.takeLast(4) else value
    }
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 7) digits.take(3) + "****" + digits.takeLast(4) else digits.ifBlank { value }
}

private fun orderedBusinessDateText(item: OrderedBusinessItem): String {
    val start = item.startDate?.trim().orEmpty().takeIf(String::isNotEmpty)
    val end = item.endDate?.trim().orEmpty().takeIf(String::isNotEmpty)
    return when {
        start != null && end != null -> "有效期：$start - $end"
        end != null -> "有效期至：$end"
        else -> "有效期：长期有效"
    }
}
