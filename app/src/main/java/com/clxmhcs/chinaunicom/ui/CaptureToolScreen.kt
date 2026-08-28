package com.clxmhcs.chinaunicom.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.capture.CaptureCertificateSnapshot
import com.clxmhcs.chinaunicom.capture.CaptureConfiguration
import com.clxmhcs.chinaunicom.capture.CaptureHarExporter
import com.clxmhcs.chinaunicom.capture.CaptureHistoryStore
import com.clxmhcs.chinaunicom.capture.CaptureHttpMessage
import com.clxmhcs.chinaunicom.capture.CaptureHttpMessageKind
import com.clxmhcs.chinaunicom.capture.CaptureHttpSessionSnapshot
import com.clxmhcs.chinaunicom.capture.CaptureMitmConfiguration
import com.clxmhcs.chinaunicom.capture.CaptureMitmProxySnapshot
import com.clxmhcs.chinaunicom.capture.CapturePacketSessionSnapshot
import com.clxmhcs.chinaunicom.capture.CaptureStateSnapshot
import com.clxmhcs.chinaunicom.capture.CaptureToolFacade
import com.clxmhcs.chinaunicom.capture.CaptureTunnelState
import com.clxmhcs.chinaunicom.capture.CaptureVpnController
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_IMPORTED_CERTIFICATE_BYTES = 512 * 1024

/** M14-E functional CaptureTool UI. Visual parity is intentionally deferred to the later UI pass. */
@Composable
fun CaptureToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedMessage by remember { mutableStateOf<CaptureHttpMessage?>(null) }
    if (selectedMessage != null) {
        CaptureHttpMessageDetailScreen(
            message = selectedMessage!!,
            onBack = { selectedMessage = null },
        )
        return
    }

    var tunnelState by remember { mutableStateOf(CaptureVpnController.readState(context)) }
    var packetSession by remember { mutableStateOf(CaptureVpnController.readPacketSession()) }
    var httpSession by remember { mutableStateOf(CaptureVpnController.readHttpSession()) }
    var records by remember { mutableStateOf(CaptureToolFacade.readHistory()) }
    var certificateSnapshot by remember { mutableStateOf(CaptureVpnController.readCertificateSnapshot()) }
    var proxySnapshot by remember { mutableStateOf(CaptureVpnController.readMitmProxySnapshot()) }

    val initialConfiguration = remember { CaptureVpnController.readConfiguration(context) }
    var targetHost by remember { mutableStateOf(initialConfiguration.targetHost.orEmpty()) }
    var targetPath by remember { mutableStateOf(initialConfiguration.targetPath.orEmpty()) }
    var captureAllHosts by remember { mutableStateOf(initialConfiguration.captureAllHosts) }
    var additionalHosts by remember { mutableStateOf(initialConfiguration.additionalHosts.joinToString(", ")) }

    val initialMitm = remember { CaptureVpnController.readMitmConfiguration(context) }
    var mitmEnabled by remember { mutableStateOf(initialMitm.enabled) }
    var interceptHttps by remember { mutableStateOf(initialMitm.interceptHttps) }
    var includedHosts by remember { mutableStateOf(initialMitm.includedHosts.joinToString(", ")) }
    var excludedHosts by remember { mutableStateOf(initialMitm.excludedHosts.joinToString(", ")) }

    var searchText by remember { mutableStateOf("") }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var pendingHarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCertificateBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun refresh() {
        tunnelState = CaptureVpnController.readState(context)
        packetSession = CaptureVpnController.readPacketSession()
        httpSession = CaptureVpnController.readHttpSession()
        records = CaptureToolFacade.readHistory()
        certificateSnapshot = CaptureVpnController.readCertificateSnapshot()
        proxySnapshot = CaptureVpnController.readMitmProxySnapshot()
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (CaptureVpnController.isPermissionGranted(context)) {
            CaptureVpnController.start(context)
            operationMessage = "VPN 授权已完成，正在启动抓包服务"
        } else {
            operationMessage = "未获得系统 VPN 授权"
        }
        refresh()
    }

    val harExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val bytes = pendingHarBytes
        pendingHarBytes = null
        if (uri != null && bytes != null) {
            operationMessage = runCatching {
                writeBytes(context, uri, bytes)
                "HAR 已导出"
            }.getOrElse { "HAR 导出失败：${it.message ?: "未知错误"}" }
        }
    }

    val certificateImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            operationMessage = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    readLimitedBytes(it, MAX_IMPORTED_CERTIFICATE_BYTES)
                } ?: error("无法读取证书文件")
                CaptureVpnController.registerRootCertificate(bytes)
                refresh()
                "根证书已导入；如需 HTTPS 架构测试，请按系统设置手动安装"
            }.getOrElse { "证书导入失败：${it.message ?: "未知错误"}" }
        }
    }

    val certificateExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-x509-ca-cert"),
    ) { uri ->
        val bytes = pendingCertificateBytes
        pendingCertificateBytes = null
        if (uri != null && bytes != null) {
            operationMessage = runCatching {
                writeBytes(context, uri, bytes)
                "根证书已导出，请在 Android 系统设置中手动安装 CA"
            }.getOrElse { "证书导出失败：${it.message ?: "未知错误"}" }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(1_000)
        }
    }

    val keyword = searchText.trim()
    val filteredRecords = remember(records, keyword) {
        if (keyword.isEmpty()) {
            records
        } else {
            records.filter { message ->
                listOfNotNull(
                    message.method,
                    message.target,
                    message.host,
                    message.statusCode?.toString(),
                    message.streamID,
                ).any { it.contains(keyword, ignoreCase = true) } ||
                    message.headers.any { (name, value) ->
                        name.contains(keyword, ignoreCase = true) || value.contains(keyword, ignoreCase = true)
                    }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("抓包工具", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.padding(20.dp))
            }
        }

        item {
            CaptureStatusCard(
                tunnelState = tunnelState,
                packetSession = packetSession,
                httpSession = httpSession,
                onStart = {
                    val permissionIntent = CaptureVpnController.permissionIntent(context)
                    if (permissionIntent != null) {
                        vpnPermissionLauncher.launch(permissionIntent)
                    } else {
                        CaptureVpnController.start(context)
                        operationMessage = "正在启动抓包服务"
                        refresh()
                    }
                },
                onStop = {
                    CaptureVpnController.stop(context)
                    operationMessage = "已请求停止抓包服务"
                    refresh()
                },
            )
        }

        item {
            CaptureConfigurationCard(
                targetHost = targetHost,
                onTargetHostChange = { targetHost = it },
                targetPath = targetPath,
                onTargetPathChange = { targetPath = it },
                captureAllHosts = captureAllHosts,
                onCaptureAllHostsChange = { captureAllHosts = it },
                additionalHosts = additionalHosts,
                onAdditionalHostsChange = { additionalHosts = it },
                onSave = {
                    CaptureVpnController.saveConfiguration(
                        context,
                        CaptureConfiguration(
                            targetHost = targetHost,
                            targetPath = targetPath,
                            captureAllHosts = captureAllHosts,
                            additionalHosts = parseHostList(additionalHosts),
                        ),
                    )
                    operationMessage = "抓包过滤配置已保存"
                },
            )
        }

        item {
            CaptureTlsCard(
                enabled = mitmEnabled,
                onEnabledChange = { mitmEnabled = it },
                interceptHttps = interceptHttps,
                onInterceptHttpsChange = { interceptHttps = it },
                includedHosts = includedHosts,
                onIncludedHostsChange = { includedHosts = it },
                excludedHosts = excludedHosts,
                onExcludedHostsChange = { excludedHosts = it },
                certificateSnapshot = certificateSnapshot,
                proxySnapshot = proxySnapshot,
                onSave = {
                    CaptureVpnController.saveMitmConfiguration(
                        context,
                        CaptureMitmConfiguration(
                            enabled = mitmEnabled,
                            interceptHttps = interceptHttps,
                            includedHosts = parseHostList(includedHosts),
                            excludedHosts = parseHostList(excludedHosts),
                        ),
                    )
                    operationMessage = "HTTPS/MITM 配置已保存；当前真实 TLS 解密能力仍为关闭"
                    refresh()
                },
                onImportCertificate = {
                    certificateImportLauncher.launch(
                        arrayOf(
                            "application/x-x509-ca-cert",
                            "application/pkix-cert",
                            "application/octet-stream",
                        ),
                    )
                },
                onExportCertificate = {
                    val bytes = CaptureToolFacade.readRootCertificateData()
                    if (bytes == null) {
                        operationMessage = "尚未导入根证书"
                    } else {
                        pendingCertificateBytes = bytes
                        certificateExportLauncher.launch("CaptureTool-RootCA.cer")
                    }
                },
                onConfirmTrust = {
                    CaptureVpnController.confirmRootCertificateTrustByUser()
                    operationMessage = "已记录用户确认：证书已在系统设置中安装"
                    refresh()
                },
                onRevokeTrust = {
                    CaptureVpnController.revokeRootCertificateTrustConfirmation()
                    operationMessage = "已撤销用户信任确认"
                    refresh()
                },
                onResetCertificate = {
                    CaptureVpnController.resetRootCertificate(context)
                    operationMessage = "证书状态已重置"
                    refresh()
                },
            )
        }

        item {
            operationMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("抓包记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅显示当前进程内最多 128 条结构化 HTTP Header 记录；不保存 HTTP Body，也不写数据库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("搜索 Host / URL / Method / 状态码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                CaptureHistoryStore.clear()
                                refresh()
                                operationMessage = "当前抓包记录已清空"
                            },
                            enabled = records.isNotEmpty(),
                        ) { Text("清空") }
                        Button(
                            onClick = {
                                operationMessage = runCatching {
                                    pendingHarBytes = CaptureToolFacade.makeHarExport()
                                    harExportLauncher.launch(CaptureHarExporter.defaultFileName())
                                    "正在选择 HAR 导出位置"
                                }.getOrElse { "HAR 导出失败：${it.message ?: "未知错误"}" }
                            },
                            enabled = records.isNotEmpty(),
                        ) { Text("导出 HAR") }
                    }
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            item {
                Text(
                    text = if (records.isEmpty()) "暂无请求记录" else "没有匹配的抓包记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            items(filteredRecords, key = { it.messageID }) { message ->
                CaptureHttpMessageRow(
                    message = message,
                    onClick = { selectedMessage = message },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CaptureStatusCard(
    tunnelState: CaptureStateSnapshot,
    packetSession: CapturePacketSessionSnapshot,
    httpSession: CaptureHttpSessionSnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("运行状态", style = MaterialTheme.typography.titleMedium)
            Text("VPN：${tunnelState.state.name}")
            tunnelState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Packet：${packetSession.packetCount}  ·  TCP：${packetSession.tcpPacketCount}  ·  UDP：${packetSession.udpPacketCount}")
            Text("HTTP：${httpSession.messageCount}  ·  请求：${httpSession.requestCount}  ·  响应：${httpSession.responseCount}")
            Text(
                "当前仍使用 192.0.2.0/24 TEST-NET 安全路由，不接管设备默认网络。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = tunnelState.state != CaptureTunnelState.RUNNING && tunnelState.state != CaptureTunnelState.STARTING,
                ) { Text("开始抓包") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = tunnelState.state != CaptureTunnelState.STOPPED,
                ) { Text("停止") }
            }
        }
    }
}

@Composable
private fun CaptureConfigurationCard(
    targetHost: String,
    onTargetHostChange: (String) -> Unit,
    targetPath: String,
    onTargetPathChange: (String) -> Unit,
    captureAllHosts: Boolean,
    onCaptureAllHostsChange: (Boolean) -> Unit,
    additionalHosts: String,
    onAdditionalHostsChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("抓包过滤", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("抓取全部配置主机")
                Switch(checked = captureAllHosts, onCheckedChange = onCaptureAllHostsChange)
            }
            OutlinedTextField(
                value = targetHost,
                onValueChange = onTargetHostChange,
                label = { Text("目标 Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = targetPath,
                onValueChange = onTargetPathChange,
                label = { Text("目标 Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = additionalHosts,
                onValueChange = onAdditionalHostsChange,
                label = { Text("附加 Host，逗号分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave) { Text("保存过滤配置") }
        }
    }
}

@Composable
private fun CaptureTlsCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    interceptHttps: Boolean,
    onInterceptHttpsChange: (Boolean) -> Unit,
    includedHosts: String,
    onIncludedHostsChange: (String) -> Unit,
    excludedHosts: String,
    onExcludedHostsChange: (String) -> Unit,
    certificateSnapshot: CaptureCertificateSnapshot,
    proxySnapshot: CaptureMitmProxySnapshot,
    onSave: () -> Unit,
    onImportCertificate: () -> Unit,
    onExportCertificate: () -> Unit,
    onConfirmTrust: () -> Unit,
    onRevokeTrust: () -> Unit,
    onResetCertificate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("HTTPS / 证书", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前仅迁移 TLS/MITM 配置与证书生命周期。动态站点证书签名和真实 TLS 解密仍未启用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("启用 MITM 配置")
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("HTTPS 规则")
                Switch(checked = interceptHttps, onCheckedChange = onInterceptHttpsChange)
            }
            OutlinedTextField(
                value = includedHosts,
                onValueChange = onIncludedHostsChange,
                label = { Text("包含 Host，逗号分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = excludedHosts,
                onValueChange = onExcludedHostsChange,
                label = { Text("排除 Host，逗号分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave) { Text("保存 HTTPS 配置") }
            HorizontalDivider()
            Text("证书状态：${certificateSnapshot.state.name}")
            Text("MITM 状态：${proxySnapshot.state.name}")
            Text("动态证书：${if (certificateSnapshot.hostCertificateGenerationAvailable) "可用" else "不可用"}")
            Text("TLS 解密：${if (certificateSnapshot.activeTlsDecryptionAvailable) "可用" else "不可用"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportCertificate) { Text("导入根证书") }
                OutlinedButton(
                    onClick = onExportCertificate,
                    enabled = certificateSnapshot.hasRootCertificate,
                ) { Text("导出证书") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onConfirmTrust,
                    enabled = certificateSnapshot.hasRootCertificate && !certificateSnapshot.userConfirmedTrusted,
                ) { Text("确认已安装") }
                OutlinedButton(
                    onClick = onRevokeTrust,
                    enabled = certificateSnapshot.userConfirmedTrusted,
                ) { Text("撤销确认") }
            }
            TextButton(
                onClick = onResetCertificate,
                enabled = certificateSnapshot.hasRootCertificate,
            ) { Text("重置证书") }
        }
    }
}

@Composable
private fun CaptureHttpMessageRow(message: CaptureHttpMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (message.kind == CaptureHttpMessageKind.REQUEST) message.method.orEmpty() else "HTTP",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    message.statusCode?.toString() ?: message.kind.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = message.host ?: message.target ?: message.streamID,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            message.target?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                formatTime(message.capturedAtEpochMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptureHttpMessageDetailScreen(message: CaptureHttpMessage, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("请求详情", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.padding(20.dp))
            }
        }
        item {
            DetailCard("Overview") {
                DetailLine("类型", message.kind.name)
                DetailLine("方法", message.method ?: "—")
                DetailLine("状态码", message.statusCode?.toString() ?: "—")
                DetailLine("Host", message.host ?: "—")
                DetailLine("Target", message.target ?: "—")
                DetailLine("时间", formatTime(message.capturedAtEpochMillis))
            }
        }
        item {
            DetailCard("Stream") {
                Text(message.streamID, fontFamily = FontFamily.Monospace)
            }
        }
        item {
            DetailCard("Headers") {
                if (message.headers.isEmpty()) {
                    Text("无 Header", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    message.headers.entries.sortedBy { it.key.lowercase() }.forEach { (name, value) ->
                        Text("$name: $value", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        item {
            DetailCard("Body") {
                Text(
                    "M14-C/M14-E 安全边界不发布或保存 HTTP Body。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable Column.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

private fun parseHostList(value: String): List<String> = value
    .split(',', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(epochMillis))

private fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
        ?: error("无法打开导出文件")
}

private fun readLimitedBytes(input: InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "证书文件超过 ${maxBytes / 1024} KiB 限制" }
        output.write(buffer, 0, count)
    }
    val result = output.toByteArray()
    require(result.isNotEmpty()) { "证书文件为空" }
    return result
}
