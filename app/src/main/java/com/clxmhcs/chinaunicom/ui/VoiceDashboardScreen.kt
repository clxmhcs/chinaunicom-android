package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage

/** M7-A functional voice dashboard; voice data is read-only and refreshed with the flow domain. */
@Composable
fun VoiceDashboardScreen(
    flowViewModel: FlowViewModel,
) {
    val uiState by flowViewModel.uiState.collectAsState()
    var detailAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        FlowUiState.Loading -> VoiceMessage("加载中…")
        is FlowUiState.Error -> VoiceMessage(state.message)
        is FlowUiState.Content -> {
            val selected = detailAccountID?.let { id ->
                state.accounts.firstOrNull { it.id.toString() == id }
            }
            if (selected != null) {
                VoiceAccountDetail(
                    account = selected,
                    accounts = state.accounts,
                    refreshState = state.appState.refreshState(selected.id),
                    onBack = { detailAccountID = null },
                    onSelectAccount = { detailAccountID = it.id.toString() },
                )
            } else {
                IosVoiceDashboardContent(
                    state = state,
                    onOpenAccount = { detailAccountID = it.id.toString() },
                )
            }
        }
    }
}

@Composable
private fun VoiceDashboardContent(
    state: FlowUiState.Content,
    onOpenAccount: (UnicomAccount) -> Unit,
) {
    val latest = state.accounts.mapNotNull { it.lastUpdatedAt }.maxOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("语音", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        latest?.let { "已更新：${formatTime(it)}" } ?: "尚未更新",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "语音相关数据不单独执行刷新，其数据会随流量同步刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.accounts.isEmpty()) {
            item { VoiceMessageCard("还没有联通号码。请先完成账号登录/添加，流量刷新时会同时读取语音余量。") }
        } else {
            items(state.accounts, key = { it.id }) { account ->
                VoiceAccountCard(
                    account = account,
                    refreshState = state.appState.refreshState(account.id),
                    onOpen = { onOpenAccount(account) },
                )
            }
            item {
                Text("共 ${state.accounts.size} 个号码", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VoiceAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    onOpen: () -> Unit,
) {
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
                    if (account.packageName.isNotBlank()) Text(account.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    when (refreshState) {
                        is RefreshState.Loading -> "随流量刷新中"
                        is RefreshState.Failed -> "刷新失败"
                        else -> account.lastUpdatedAt?.let(::formatTime) ?: "--"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (account.resolvedVoicePackages.isEmpty()) {
                Text("未识别到语音余量", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "当前联通余量接口没有为此号码返回可识别的语音资源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (account.visibleVoicePackages.isEmpty()) {
                Text("所有语音权益均已隐藏", style = MaterialTheme.typography.bodySmall)
            } else {
                account.visibleVoicePackages.take(3).forEach { VoicePackageRow(it) }
                if (account.visibleVoicePackages.size > 3) {
                    Text("另有 ${account.visibleVoicePackages.size - 3} 项语音权益", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (refreshState is RefreshState.Failed) {
                Text(refreshState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看语音详情") }
        }
    }
}

@Composable
private fun VoiceAccountDetail(
    account: UnicomAccount,
    accounts: List<UnicomAccount>,
    refreshState: RefreshState,
    onBack: () -> Unit,
    onSelectAccount: (UnicomAccount) -> Unit,
) {
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
                    Text("语音详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(account.mobile, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { AccountSelector(accounts = accounts, selected = account, onSelect = onSelectAccount) }
        item {
            Text(
                "语音不提供单独刷新按钮；请在“流量”页刷新此号码或刷新全部。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (account.visibleVoicePackages.isEmpty()) {
            item { VoiceMessageCard(if (account.resolvedVoicePackages.isEmpty()) "未识别到语音余量。" else "所有语音权益均已隐藏。") }
        } else {
            items(account.visibleVoicePackages, key = { it.id }) { packageValue ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) { VoicePackageRow(packageValue) }
                }
            }
        }

        account.remainingQuerySnapshot?.voice?.let { voice ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("联通余量快照", fontWeight = FontWeight.SemiBold)
                        Text("剩余 ${formatMinutes(voice.remainingMinutes)}", style = MaterialTheme.typography.bodyMedium)
                        Text("已用 ${formatMinutes(voice.usedMinutes)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (refreshState is RefreshState.Failed) {
            item { VoiceMessageCard("最近一次流量/语音同步刷新失败：${refreshState.message}") }
        }
    }
}

@Composable
private fun VoicePackageRow(packageValue: VoicePackage) {
    val remainingText = if (packageValue.isUnlimited) {
        "不限量"
    } else {
        packageValue.totalMinutes?.let {
            "剩余 ${formatMinutes(packageValue.remainingMinutes)} / 共 ${formatMinutes(it)}"
        } ?: "剩余 ${formatMinutes(packageValue.remainingMinutes)}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(packageValue.originalName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(remainingText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "已用 ${formatMinutes(packageValue.usedMinutes)} · ${if (packageValue.isShared) "共享" else "非共享"}",
            style = MaterialTheme.typography.labelSmall,
        )
        packageValue.endDateText?.takeIf { it.isNotBlank() }?.let {
            Text("有效期至 $it", style = MaterialTheme.typography.labelSmall)
        }
        packageValue.usedFraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoiceMessage(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun VoiceMessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
