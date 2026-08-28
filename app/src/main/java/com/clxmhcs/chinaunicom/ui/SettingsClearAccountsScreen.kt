package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsClearAccountsScreen(
    viewModel: SettingsClearAccountsViewModel = viewModel(),
    onAccountsChanged: (clearedAll: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings = LocalAppSettings.current
    var verificationInput by remember { mutableStateOf("") }
    var showingConfirmation by remember { mutableStateOf(false) }
    var handledSerial by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.operationSerial) {
        if (state.operationSerial <= handledSerial) return@LaunchedEffect
        handledSerial = state.operationSerial
        if (state.errorMessage == null) {
            onAccountsChanged(state.clearedAll)
            if (state.closeRequested) onBack()
        }
    }

    if (state.requiresVerification && !state.isVerified) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { M11CPageHeader("验证身份", onBack) }
            item {
                M11CCard {
                    Text("进入清空账户与凭据前需要先验证身份。", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android 当前没有单独的凭据管理密码时，按 iOS 兼容规则使用排序最靠前的手机号码作为清空页面验证值。验证内容不会保存。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = verificationInput,
                        onValueChange = { verificationInput = it.filter(Char::isDigit).take(13) },
                        label = { Text("验证值") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = verificationInput.isNotBlank(),
                        onClick = {
                            viewModel.verify(verificationInput)
                            verificationInput = ""
                        },
                    ) { Text("验证并继续") }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("清空账户与凭据", onBack) }
        item {
            M11CCard {
                Text("删除号码时会同步删除该 UUID 的本机登录凭据。全部清空还会恢复显示、刷新、号段、Widget 配置和快捷通知设置。电子受理单 PDF 不属于账户凭据，不会随此操作删除。", style = MaterialTheme.typography.bodySmall)
                state.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (!state.hasAccounts) {
            item { M11CCard { Text("没有可清空的号码") } }
        }

        if (state.mobileAccounts.isNotEmpty()) {
            item { Text("手机号码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            state.mobileAccounts.forEach { account ->
                item(key = "clear-mobile-${account.id}") {
                    M11CCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = account.id in state.selectedMobileIDs,
                                enabled = !state.isWorking,
                                onCheckedChange = { viewModel.toggleMobile(account.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.displayName.ifBlank { "联通号码" }, fontWeight = FontWeight.SemiBold)
                                Text(displayMobileNumber(account.mobile, settings), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                enabled = !state.isWorking,
                                onClick = {
                                    viewModel.selectOnlyMobile(account.id)
                                    showingConfirmation = true
                                },
                            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        if (state.broadbandAccounts.isNotEmpty()) {
            item { Text("宽带号码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            state.broadbandAccounts.forEach { account ->
                item(key = "clear-broadband-${account.id}") {
                    M11CCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = account.id in state.selectedBroadbandIDs,
                                enabled = !state.isWorking,
                                onCheckedChange = { viewModel.toggleBroadband(account.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.displayName.ifBlank { account.locationName.ifBlank { "宽带号码" } }, fontWeight = FontWeight.SemiBold)
                                Text(displayBroadbandNumber(account.serviceNumber, settings), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                enabled = !state.isWorking,
                                onClick = {
                                    viewModel.selectOnlyBroadband(account.id)
                                    showingConfirmation = true
                                },
                            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        if (state.hasAccounts) {
            item {
                M11CCard {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isWorking,
                        onClick = viewModel::toggleSelectAll,
                    ) { Text(if (state.allSelected) "取消全选" else "全选") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isWorking && state.selectedCount > 0,
                        onClick = { showingConfirmation = true },
                    ) {
                        Text(
                            if (state.allSelected) "清空全部账户与凭据" else "删除已选 ${state.selectedCount} 个号码",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "删除前会再次确认。此操作无法撤销；全部清空不会删除已保存的电子受理单 PDF。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showingConfirmation) {
        val deletingAll = state.allSelected
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) showingConfirmation = false },
            title = { Text(if (deletingAll) "确认清空全部？" else "确认删除号码？") },
            text = {
                Text(
                    if (deletingAll) {
                        "将删除全部手机号码、宽带号码及其 Cookie/令牌凭据，并恢复账户相关设置。此操作无法撤销。"
                    } else {
                        "将删除选中的 ${state.selectedCount} 个号码及其 Cookie/令牌凭据。此操作无法撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isWorking,
                    onClick = {
                        showingConfirmation = false
                        viewModel.deleteSelected()
                    },
                ) { Text(if (deletingAll) "清空" else "删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !state.isWorking, onClick = { showingConfirmation = false }) { Text("取消") }
            },
        )
    }
}
