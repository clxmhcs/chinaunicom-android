package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.BalanceRefreshState
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import com.clxmhcs.chinaunicom.core.parser.flowPackageDisplayText
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * M7-A functional flow dashboard.
 *
 * Visual parity is intentionally deferred. This screen consumes the production M6 AppState and
 * exposes the source-equivalent flow actions/data without introducing a second refresh authority.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel,
) {
    val uiState by flowViewModel.uiState.collectAsState()
    var detailAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        FlowUiState.Loading -> DashboardMessage("加载中…")
        is FlowUiState.Error -> DashboardMessage(state.message)
        is FlowUiState.Content -> {
            val detailAccount = detailAccountID?.let { id ->
                state.accounts.firstOrNull { it.id.toString() == id }
            }
            if (detailAccount != null) {
                FlowRemainingDetail(
                    account = detailAccount,
                    accounts = state.accounts,
                    onBack = { detailAccountID = null },
                    onSelectAccount = { detailAccountID = it.id.toString() },
                    onRefreshAccount = { flowViewModel.refreshAccount(detailAccount.id) },
                )
            } else {
                FlowDashboardContent(
                    state = state,
                    onRefreshAll = flowViewModel::refresh,
                    onRefreshBalance = flowViewModel::refreshHomeBalanceManually,
                    onRefreshAccount = flowViewModel::refreshAccount,
                    onOpenAccount = { detailAccountID = it.id.toString() },
                )
            }
        }
    }
}

@Composable
private fun FlowDashboardContent(
    state: FlowUiState.Content,
    onRefreshAll: () -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshAccount: (java.util.UUID) -> Unit,
    onOpenAccount: (UnicomAccount) -> Unit,
) {
    val latest = state.accounts.mapNotNull { it.lastUpdatedAt }.maxOrNull()
    val homeBalance = state.homeBalanceAccount?.balanceYuan?.takeIf { it.isFinite() }
    val balanceLoading = state.balanceState.balanceRefreshState == BalanceRefreshState.LOADING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("流量", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "流量数据刷新时，会同步刷新语音相关数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        latest?.let { "已更新：${formatTime(it)}" } ?: "尚未更新",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(
                    onClick = onRefreshAll,
                    enabled = state.accounts.isNotEmpty() && !state.appState.isRefreshingAll,
                ) {
                    Text(if (state.appState.isRefreshingAll) "更新中" else "刷新全部")
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("首页余额", style = MaterialTheme.typography.labelMedium)
                        Text(
                            homeBalance?.let { String.format(Locale.US, "%.2f 元", it) } ?: "--",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        state.homeBalanceAccount?.let {
                            Text(it.mobile, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = onRefreshBalance, enabled = !balanceLoading) {
                        Text(if (balanceLoading) "余额更新中" else "刷新余额")
                    }
                }
            }
        }

        if (state.accounts.isEmpty()) {
            item { DashboardMessageCard("还没有联通号码。账号新增入口将在后续设置页面接线；底层账号持久化与登录链路已经完成。") }
        } else {
            items(state.accounts, key = { it.id }) { account ->
                FlowAccountCard(
                    account = account,
                    refreshState = state.appState.refreshState(account.id),
                    onRefresh = { onRefreshAccount(account.id) },
                    onOpen = { onOpenAccount(account) },
                )
            }
            item {
                Text(
                    "共 ${state.accounts.size} 个号码",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlowAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
) {
    val formatter = remember { FlowFormatter(DisplayUnit.AUTOMATIC) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.mobile, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (account.packageName.isNotBlank()) {
                        Text(account.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text(if (refreshState is RefreshState.Loading) "刷新中" else "刷新此号码")
                }
            }

            account.primaryPackage?.let { FlowPackageCompactRow(account, it) }
            account.secondaryPackages.forEach { FlowPackageCompactRow(account, it) }

            if (account.visibleSummaryGroups.isNotEmpty()) {
                Text("套餐分类", style = MaterialTheme.typography.labelMedium)
                account.visibleSummaryGroups.take(4).forEach { group ->
                    val summary = account.summary(group)
                    val value = if (summary.isUnlimited) "不限量" else formatter.string(summary.remainingMB)
                    Text("${summary.name}：剩余 $value（${summary.packageCount} 项）", style = MaterialTheme.typography.bodySmall)
                }
            }

            account.balanceYuan?.takeIf { it.isFinite() }?.let {
                Text("余额：${String.format(Locale.US, "%.2f", it)} 元", style = MaterialTheme.typography.bodySmall)
            }
            account.lastUpdatedAt?.let {
                Text("更新时间：${formatTime(it)}", style = MaterialTheme.typography.labelSmall)
            }
            account.lastErrorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("余量详情")
            }
        }
    }
}

@Composable
private fun FlowPackageCompactRow(account: UnicomAccount, packageValue: FlowPackage) {
    val display = flowPackageDisplayText(account, packageValue, DisplayUnit.AUTOMATIC)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(display.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(display.remainingText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        display.detailText?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        display.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FlowRemainingDetail(
    account: UnicomAccount,
    accounts: List<UnicomAccount>,
    onBack: () -> Unit,
    onSelectAccount: (UnicomAccount) -> Unit,
    onRefreshAccount: () -> Unit,
) {
    val snapshot = account.remainingQuerySnapshot
    val expanded = remember(account.id) { mutableStateMapOf<RemainingFlowCategory, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("余量详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(account.mobile, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRefreshAccount) { Text("刷新") }
            }
        }

        item {
            AccountSelector(accounts = accounts, selected = account, onSelect = onSelectAccount)
        }

        if (account.visibleDetailPackages.isNotEmpty()) {
            item { Text("首页资源", style = MaterialTheme.typography.titleMedium) }
            items(account.visibleDetailPackages, key = { "root:${it.id}" }) { packageValue ->
                val display = flowPackageDisplayText(account, packageValue, DisplayUnit.AUTOMATIC)
                UnicomQuotaCard(
                    title = display.title,
                    subtitle = account.mobile,
                    remaining = display.remainingText,
                    detail = display.detailText,
                    progress = display.progress?.toFloat() ?: 0f,
                )
            }
        }

        if (snapshot == null) {
            item { DashboardMessageCard("当前账号尚无余量查询快照。执行流量刷新后会在这里展示分类余量、套餐和成员数据。") }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("联通余量快照", style = MaterialTheme.typography.titleMedium)
                    Text("更新时间：${formatTime(snapshot.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                    if (snapshot.members.isNotEmpty()) {
                        Text("成员号卡：${snapshot.members.joinToString("、") { it.maskedNumber }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            val categoryOrder = listOf(
                RemainingFlowCategory.GENERAL,
                RemainingFlowCategory.EXCLUSIVE,
                RemainingFlowCategory.OTHER,
            )
            categoryOrder.forEach { category ->
                val packages = snapshot.flowPackages.filter { packageValue ->
                    when (category) {
                        RemainingFlowCategory.GENERAL -> packageValue.category == RemainingFlowCategory.GENERAL
                        RemainingFlowCategory.EXCLUSIVE -> packageValue.category == RemainingFlowCategory.EXCLUSIVE
                        RemainingFlowCategory.OTHER -> packageValue.category == RemainingFlowCategory.OTHER ||
                            packageValue.category == RemainingFlowCategory.UNKNOWN || packageValue.category == null
                        RemainingFlowCategory.UNKNOWN -> false
                    }
                }
                val summary = snapshot.flowSummaries.firstOrNull { it.category == category }
                if (summary != null || packages.isNotEmpty()) {
                    item(key = "category:${category.rawValue}") {
                        RemainingFlowCategorySection(
                            category = category,
                            summaryRemainingMB = summary?.remainingMB,
                            summaryUsedMB = summary?.usedMB,
                            packages = packages,
                            isExpanded = expanded[category] == true,
                            onToggle = { expanded[category] = expanded[category] != true },
                        )
                    }
                }
            }

            item { VoiceSnapshotSummary(account) }
        }
    }
}

@Composable
internal fun AccountSelector(
    accounts: List<UnicomAccount>,
    selected: UnicomAccount,
    onSelect: (UnicomAccount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("当前号码：${selected.mobile}　切换")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.mobile) },
                    onClick = {
                        expanded = false
                        onSelect(account)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemainingFlowCategorySection(
    category: RemainingFlowCategory,
    summaryRemainingMB: Double?,
    summaryUsedMB: Double?,
    packages: List<RemainingFlowPackage>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val formatter = remember { FlowFormatter(DisplayUnit.AUTOMATIC) }
    val visible = if (isExpanded) packages else packages.take(2)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(categoryTitle(category), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                summaryRemainingMB?.let { Text("剩余 ${formatter.string(it)}", style = MaterialTheme.typography.bodySmall) }
            }
            summaryUsedMB?.let { Text("已用 ${formatter.string(it)}", style = MaterialTheme.typography.labelSmall) }
            if (visible.isEmpty()) {
                Text("暂无该类型套餐", style = MaterialTheme.typography.bodySmall)
            } else {
                visible.forEach { packageValue ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(packageValue.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (packageValue.resolvedIsUnlimited) "不限量" else "剩余 ${formatter.string(packageValue.remainingMB)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            listOfNotNull(
                                if (packageValue.isShared) "共享" else "非共享",
                                packageValue.endDateText?.takeIf { it.isNotBlank() }?.let { "有效期 $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (packages.size > 2) {
                TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isExpanded) "收起" else "查看更多（${packages.size}）")
                }
            }
        }
    }
}

@Composable
private fun VoiceSnapshotSummary(account: UnicomAccount) {
    val snapshot = account.remainingQuerySnapshot ?: return
    val voice = snapshot.voice
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("语音余量", fontWeight = FontWeight.SemiBold)
            Text("剩余 ${formatMinutes(voice.remainingMinutes)} · 已用 ${formatMinutes(voice.usedMinutes)}", style = MaterialTheme.typography.bodySmall)
            (voice.packages + voice.unsharedPackages).distinctBy { it.id }.take(6).forEach { packageValue ->
                Text("${packageValue.name}：剩余 ${formatMinutes(packageValue.remainingMinutes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DashboardMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun DashboardMessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun categoryTitle(category: RemainingFlowCategory): String = when (category) {
    RemainingFlowCategory.GENERAL -> "通用流量"
    RemainingFlowCategory.EXCLUSIVE -> "定向流量"
    RemainingFlowCategory.OTHER -> "其他流量"
    RemainingFlowCategory.UNKNOWN -> "未分类流量"
}

internal fun formatMinutes(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val safe = value.coerceAtLeast(0.0)
    return if (kotlin.math.abs(safe - safe.toLong()) < 0.0001) "${safe.toLong()} 分钟"
    else String.format(Locale.US, "%.2f 分钟", safe)
}

private val dashboardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatTime(value: Instant): String = dashboardTimeFormatter.format(value.atZone(ZoneId.systemDefault()))
