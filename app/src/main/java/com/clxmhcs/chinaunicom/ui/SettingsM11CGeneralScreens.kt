package com.clxmhcs.chinaunicom.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo
import com.clxmhcs.chinaunicom.data.refresh.AndroidDailyUsageBaselineStore
import kotlinx.coroutines.launch

@Composable
internal fun M11CPageHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun M11CCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun CarrierCorrectionSettingsScreen(
    mobileAccounts: List<UnicomAccount>,
    broadbandAccounts: List<BroadbandAccountInfo>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val attribution by viewModel.attributionState.collectAsState()
    val message by viewModel.operationMessage.collectAsState()
    val settings = LocalAppSettings.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("号码归属纠正", onBack) }
        item {
            M11CCard {
                Text("仅修正 App / Widget 的运营商显示，不改变登录、查询、刷新和套餐数据。", style = MaterialTheme.typography.bodySmall)
                if (message != null) Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::resetCorrections) { Text("全部恢复自动识别") }
            }
        }
        mobileAccounts.sortedBy { it.sortOrder }.forEach { account ->
            item(key = "mobile-${account.id}") {
                val correction = attribution.corrections[account.mobile.filter(Char::isDigit)] ?: PhoneCarrierCorrection.AUTOMATIC
                M11CCard {
                    Text(account.displayName.ifBlank { displayMobileNumber(account.mobile, settings) }, fontWeight = FontWeight.SemiBold)
                    Text(displayMobileNumber(account.mobile, settings), style = MaterialTheme.typography.bodySmall)
                    Text("自动识别：${viewModel.automaticCarrierTitle(account.mobile)}")
                    Text("当前显示：${viewModel.resolvedCarrierTitle(account.mobile)}")
                    viewModel.cachedLocation(account.mobile)?.let { Text("缓存归属地：$it") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.cycleCorrection(account.mobile) }) {
                            Text("修正：${correction.displayName}")
                        }
                        OutlinedButton(onClick = { viewModel.refreshLocation(account.mobile) }) { Text("更新归属地") }
                    }
                }
            }
        }
        broadbandAccounts.forEach { account ->
            item(key = "broadband-${account.id}") {
                val number = account.serviceNumber
                val correction = attribution.corrections[number.filter(Char::isDigit)] ?: PhoneCarrierCorrection.AUTOMATIC
                M11CCard {
                    Text(account.displayName.ifBlank { "宽带号码" }, fontWeight = FontWeight.SemiBold)
                    Text(displayBroadbandNumber(number, settings), style = MaterialTheme.typography.bodySmall)
                    Text("当前显示：${viewModel.resolvedCarrierTitle(number)}")
                    if (account.areaCode.isNotBlank()) Text("区号：${account.areaCode}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { viewModel.cycleCorrection(number) }) { Text("修正：${correction.displayName}") }
                }
            }
        }
        if (mobileAccounts.isEmpty() && broadbandAccounts.isEmpty()) {
            item { Text("暂无已保存号码。") }
        }
    }
}

@Composable
fun PhoneSegmentSettingsScreen(
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.attributionState.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { M11CPageHeader("运营商号段", onBack) }
        item {
            M11CCard {
                Text("远端号段更新失败时会保留/回退到源码内置的运营商号段，不影响联通业务查询。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !state.isUpdatingSegments, onClick = viewModel::updatePhoneSegments) {
                    Text(if (state.isUpdatingSegments) "正在更新…" else "号段更新")
                }
                state.updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("已保存 ${state.segments.size} 个运营商号段")
            }
        }
        items(state.segments, key = { it.prefix }) { record ->
            M11CCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(record.prefix, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(record.carrier.displayName)
                }
                record.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun DailyUsageBaselineSettingsScreen(
    accounts: List<UnicomAccount>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val dateKey = remember { AndroidDailyUsageBaselineStore.todayDateKey() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { M11CPageHeader("每日用量基准", onBack) }
        item {
            M11CCard {
                Text("日期：$dateKey")
                Text("此页面只查看基准，不会主动联网或在白天补写午夜基准。Widget 与快捷通知共用同一账号/日期基准；语音不使用 0:00 流量基准。", style = MaterialTheme.typography.bodySmall)
            }
        }
        accounts.sortedBy { it.sortOrder }.forEach { account ->
            item(key = account.id) {
                val baseline = viewModel.dailyUsageBaseline(account.id, dateKey)
                M11CCard {
                    Text(account.displayName.ifBlank { displayMobileNumber(account.mobile, LocalAppSettings.current) }, fontWeight = FontWeight.SemiBold)
                    if (baseline == null) {
                        Text("未记录")
                    } else {
                        Text("已记录 · ${baseline.packages.size} 个流量资源")
                        Text("采集时间：${baseline.capturedAt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (accounts.isEmpty()) item { Text("暂无手机账号。") }
    }
}

@Composable
fun ElectronicReceiptDirectorySettingsScreen(
    viewModel: ElectronicReceiptViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setExportDirectory(uri)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("电子受理单保存目录", onBack) }
        item {
            M11CCard {
                Text(if (state.exportDirectoryUri.isNullOrBlank()) "未设置自动导出目录" else "已设置自动导出目录")
                Text("PDF 本体仍先写入 App 私有受理单存储；选择目录后，保存受理单时会复用现有 SAF 持久权限自动导出。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { launcher.launch(null) }) { Text("选择目录") }
                if (!state.exportDirectoryUri.isNullOrBlank()) {
                    OutlinedButton(onClick = viewModel::clearExportDirectory) { Text("取消自动导出目录") }
                }
                state.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun CaptureToolSettingsEntryScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("抓包工具", onBack) }
        item {
            M11CCard {
                Text("抓包工具入口已纳入设置迁移。")
                Text("VPN / HTTPS 解密 / 证书 / 会话记录等抓包主体按迁移总纲属于 Android-M14，本阶段不伪造不可用的抓包实现。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class ManualLine(val sourceIndex: Int, val text: String)

@Composable
fun AppManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("chinaunicom.app.manual.v1", 0) }
    val allLines = remember {
        runCatching { context.assets.open("AppManual.txt").bufferedReader().use { it.readText() } }
            .getOrElse { "# App 使用说明书\n\n说明书资源暂不可读取。" }
            .lines()
            .mapIndexed { index, text -> ManualLine(index, text) }
    }
    val headings = remember(allLines) { allLines.filter { it.text.startsWith("# ") || it.text.startsWith("## ") } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showToc by remember { mutableStateOf(false) }
    val savedIndex = remember { preferences.getInt("lastLine", 0).coerceIn(0, (allLines.size - 1).coerceAtLeast(0)) }

    LaunchedEffect(Unit) {
        if (savedIndex > 0) listState.scrollToItem(savedIndex)
    }
    DisposableEffect(listState) {
        onDispose { preferences.edit().putInt("lastLine", listState.firstVisibleItemIndex).apply() }
    }

    val visibleLines = remember(query, allLines) {
        if (query.isBlank()) allLines
        else allLines.filter { it.text.contains(query.trim(), ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            "中国联通",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { M11CPageHeader("App使用说明书", onBack) }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索说明书") },
                    singleLine = true,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showToc = !showToc }) { Text(if (showToc) "关闭目录" else "目录") }
                    OutlinedButton(onClick = {
                        query = ""
                        showToc = false
                        scope.launch { listState.scrollToItem(savedIndex) }
                    }) { Text("继续阅读") }
                }
            }
            if (showToc) {
                items(headings, key = { "toc-${it.sourceIndex}" }) { heading ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showToc = false
                            query = ""
                            scope.launch { listState.scrollToItem(heading.sourceIndex) }
                        },
                    ) {
                        Text(heading.text.trimStart('#', ' '), modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                items(visibleLines, key = { "manual-${it.sourceIndex}" }) { line ->
                    val text = line.text
                    when {
                        text.startsWith("# ") -> Text(text.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        text.startsWith("## ") -> Text(text.removePrefix("## "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        text.startsWith("### ") -> Text(text.removePrefix("### "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        text == "---" -> Text("────────", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray)
                        else -> Text(text)
                    }
                }
            }
        }
    }
}
