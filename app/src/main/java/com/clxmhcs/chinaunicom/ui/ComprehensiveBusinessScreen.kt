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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralSection
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import com.clxmhcs.chinaunicom.data.integral.IntegralLoadState
import com.clxmhcs.chinaunicom.data.orderedbusiness.OrderedBusinessRefreshState
import java.util.Locale
import java.util.UUID

/** M8-E functional wiring only. Final visual parity is intentionally deferred. */
@Composable
fun ComprehensiveBusinessScreen(
    flowViewModel: FlowViewModel,
    businessViewModel: ComprehensiveBusinessViewModel,
    onOpenOrderedBusiness: (UUID) -> Unit,
    onOpenPhoneBill: (UUID) -> Unit,
    onOpenFlow: (UUID) -> Unit,
    onOpenVoice: (UUID) -> Unit,
    onOpenIntegral: (UUID) -> Unit,
) {
    val flowState by flowViewModel.uiState.collectAsState()
    val rootState by businessViewModel.rootState.collectAsState()
    val accounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
    val accountIDs = accounts.map { it.id }
    val accountKey = accountIDs.joinToString("|")
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(accountKey) { businessViewModel.loadCachedPoints(accountIDs) }
    DisposableEffect(lifecycleOwner, accountKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) businessViewModel.loadCachedPoints(accountIDs)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BusinessList("综合业务", "展示话费、流量、语音和积分缓存，本页面不会主动联网刷新。") {
        when (val state = flowState) {
            FlowUiState.Loading -> item { MessageCard("加载中…") }
            is FlowUiState.Error -> item { MessageCard(state.message) }
            is FlowUiState.Content -> if (state.accounts.isEmpty()) {
                item { MessageCard("还没有联通号码。") }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(account.mobile, fontWeight = FontWeight.SemiBold)
                            Text(
                                account.packageName.ifBlank { "联通套餐" },
                                modifier = Modifier.clickable { onOpenOrderedBusiness(account.id) },
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Metric("剩余话费", balanceText(account), Modifier.weight(1f)) {
                                    onOpenPhoneBill(flowViewModel.financialRepresentativeAccountID(account.id) ?: account.id)
                                }
                                Metric("剩余流量", flowText(account), Modifier.weight(1f)) { onOpenFlow(account.id) }
                                Metric("剩余语音", voiceText(account), Modifier.weight(1f)) { onOpenVoice(account.id) }
                                Metric("可用积分", rootState.pointsByAccountID[account.id]?.toString() ?: "--", Modifier.weight(1f)) {
                                    onOpenIntegral(account.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun OrderedBusinessEntryScreen(account: UnicomAccount, businessViewModel: ComprehensiveBusinessViewModel, onBack: () -> Unit) {
    val state by businessViewModel.orderedState.collectAsState()
    val snapshot = state.snapshots[account.id]
    val refreshState = state.refreshStates[account.id] ?: OrderedBusinessRefreshState.Idle
    LaunchedEffect(account.id) { businessViewModel.loadOrderedBusiness(account) }
    EntryList("已订业务", account.mobile, onBack) {
        item {
            TextButton(onClick = { businessViewModel.refreshOrderedBusiness(account) }) {
                Text(if (refreshState is OrderedBusinessRefreshState.Loading) "查询中" else "刷新")
            }
        }
        when (refreshState) {
            is OrderedBusinessRefreshState.Failed -> item { MessageCard(refreshState.message) }
            is OrderedBusinessRefreshState.Warning -> item { MessageCard(refreshState.message) }
            else -> Unit
        }
        if (snapshot == null) item { MessageCard("暂无已订业务缓存；自动查询由刷新策略决定。") }
        else snapshot.sections.forEach { section ->
            item(key = section.id) { MessageCard("${section.title}\n${section.items.joinToString("\n") { it.name }}") }
        }
    }
}

@Composable
fun PhoneBillEntryScreen(account: UnicomAccount, businessViewModel: ComprehensiveBusinessViewModel, onBack: () -> Unit) {
    IosPhoneBillScreen(
        account = account,
        businessViewModel = businessViewModel,
        onBack = onBack,
    )
}

@Composable
fun ComprehensiveRemainingEntryScreen(
    initialAccount: UnicomAccount,
    accounts: List<UnicomAccount>,
    initialVoice: Boolean,
    flowViewModel: FlowViewModel,
    onBack: () -> Unit,
) {
    var selectedID by rememberSaveable(initialAccount.id.toString()) { mutableStateOf(initialAccount.id.toString()) }
    val selected = accounts.firstOrNull { it.id.toString() == selectedID } ?: initialAccount
    LaunchedEffect(selected.id, selected.remainingQuerySnapshot) {
        if (selected.remainingQuerySnapshot == null) flowViewModel.refreshAccount(selected.id)
    }
    EntryList(if (initialVoice) "语音余量" else "流量余量", selected.mobile, onBack) {
        item { AccountSelector(accounts, selected) { selectedID = it.id.toString() } }
        item { TextButton(onClick = { flowViewModel.refreshAccount(selected.id) }) { Text("刷新此号码") } }
        if (initialVoice) {
            if (selected.resolvedVoicePackages.isEmpty()) item { MessageCard("暂无语音资源。") }
            else selected.resolvedVoicePackages.forEach { value ->
                item(key = value.id) { MessageCard("${value.originalName}：剩余 ${formatMinutes(value.remainingMinutes)}") }
            }
        } else {
            if (selected.visibleSummaryGroups.isEmpty() && selected.remainingQuerySnapshot == null) item { MessageCard("正在复用 M6/M7 刷新链路补齐余量快照。") }
            selected.visibleSummaryGroups.forEach { group ->
                val summary = selected.summary(group)
                item(key = group.id) { MessageCard("${group.name}：${if (summary.isUnlimited) "不限量" else FlowFormatter(DisplayUnit.AUTOMATIC).string(summary.remainingMB)}") }
            }
        }
    }
}

@Composable
fun IntegralEntryScreen(
    account: UnicomAccount,
    allAccountIDs: Collection<UUID>,
    businessViewModel: ComprehensiveBusinessViewModel,
    onBack: () -> Unit,
) {
    val state by businessViewModel.integralState.collectAsState()
    var selectedQuery by remember { mutableStateOf<IntegralDetailQuery?>(null) }
    LaunchedEffect(account.id) { businessViewModel.loadIntegral(account) }
    EntryList("积分查询", account.mobile, onBack) {
        item { TextButton(onClick = { businessViewModel.refreshIntegral(account, allAccountIDs) }) { Text(if (state.isRefreshing) "刷新中" else "刷新") } }
        when (val load = state.loadState) {
            is IntegralLoadState.Failed -> item { MessageCard(load.message) }
            IntegralLoadState.ManualRequired -> item { MessageCard("当前策略要求手动查询积分。") }
            else -> Unit
        }
        state.errorMessage?.let { item { MessageCard(it) } }
        val snapshot = state.snapshot
        if (snapshot == null) item { MessageCard("暂无积分缓存。") }
        else {
            item { MessageCard("可用积分 ${snapshot.totalAvailable}\n通信 ${snapshot.communication} · 奖励 ${snapshot.reward} · 本月到期 ${snapshot.expiringThisMonth}") }
            item { Row { IntegralSection.entries.forEach { section ->
                val query = section.detailQuery
                TextButton(onClick = {
                    if (query != null) { selectedQuery = query; businessViewModel.loadIntegralDetails(query, account) }
                }, enabled = query != null) { Text(section.title) }
            } } }
            snapshot.months.forEach { month -> item(key = month.id) {
                Column {
                    Text(month.cycleID, fontWeight = FontWeight.SemiBold)
                    Row { listOf("0" to "获得积分", "1" to "消耗积分", "2" to "到期积分").forEach { (typeChar, title) ->
                        TextButton(onClick = { IntegralDetailQuery.month(month, typeChar, title)?.let { q -> selectedQuery = q; businessViewModel.loadIntegralDetails(q, account) } }) { Text(title) }
                    } }
                }
            } }
            selectedQuery?.let { query ->
                val details = businessViewModel.integralStore.details(query)
                item { MessageCard(details?.joinToString("\n") { "${it.title}：${it.scoreValue}" } ?: "正在读取积分明细…") }
            }
        }
    }
}

@Composable
private fun BusinessList(title: String, subtitle: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) } }
        content()
    }
}

@Composable
private fun EntryList(title: String, subtitle: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column { TextButton(onClick = onBack) { Text("返回") }; Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle) } }
        content()
    }
}

@Composable
private fun MessageCard(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) { Text(message, Modifier.padding(14.dp)) }
}

private fun balanceText(account: UnicomAccount) = account.balanceYuan?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f元", it) } ?: "--"
private fun flowText(account: UnicomAccount): String {
    val group = account.visibleSummaryGroups.firstOrNull() ?: return "--"
    val summary = account.summary(group)
    return if (summary.isUnlimited) "不限量" else FlowFormatter(DisplayUnit.AUTOMATIC).string(summary.remainingMB).replace(" ", "")
}
private fun voiceText(account: UnicomAccount): String {
    if (account.resolvedVoicePackages.any { it.isUnlimited }) return "不限量"
    val values = account.resolvedVoicePackages.mapNotNull { it.remainingMinutes?.takeIf { v -> v.isFinite() } }
    return if (values.isEmpty()) "--" else formatMinutes(values.sum()).replace(" ", "")
}
