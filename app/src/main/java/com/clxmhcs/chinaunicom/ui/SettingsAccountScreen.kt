package com.clxmhcs.chinaunicom.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountDraft

/** Functional-only account/settings entry. Visual parity is intentionally deferred. */
@Composable
internal fun SettingsAccountScreen(
    flowViewModel: FlowViewModel = viewModel(),
    broadbandViewModel: BroadbandAccountViewModel = viewModel(),
) {
    val flowState by flowViewModel.uiState.collectAsState()
    val onboarding by flowViewModel.accountOnboardingState.collectAsState()
    val broadbandState by broadbandViewModel.state.collectAsState()
    val accounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()

    var mobile by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    // Broadband credential fields are deliberately remember-only, never rememberSaveable.
    var broadbandNumber by remember { mutableStateOf("") }
    var broadbandDisplayName by remember { mutableStateOf("") }
    var broadbandIDCardLastSix by remember { mutableStateOf("") }
    var broadbandLocation by remember { mutableStateOf("") }
    var broadbandProvinceCode by remember { mutableStateOf("") }
    var broadbandCityCode by remember { mutableStateOf("") }
    var broadbandAreaCode by remember { mutableStateOf("") }
    var broadbandCookie by remember { mutableStateOf("") }
    var broadbandAppID by remember { mutableStateOf("") }
    var broadbandTokenOnline by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) flowViewModel.importIOSCredentialArchive(uri)
    }

    LaunchedEffect(onboarding.loginSucceeded) {
        if (onboarding.loginSucceeded) {
            verificationCode = ""
            flowViewModel.consumeLoginSuccess()
        }
    }

    LaunchedEffect(broadbandState.operationSerial) {
        if (broadbandState.operationSerial > 0 && broadbandState.lastSaveSucceeded) {
            broadbandNumber = ""
            broadbandDisplayName = ""
            broadbandIDCardLastSix = ""
            broadbandLocation = ""
            broadbandProvinceCode = ""
            broadbandCityCode = ""
            broadbandAreaCode = ""
            broadbandCookie = ""
            broadbandAppID = ""
            broadbandTokenOnline = ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(text = "设置", style = MaterialTheme.typography.displayLarge)
        }

        item {
            FunctionalCard(title = "联通号码") {
                if (accounts.isEmpty()) Text("当前没有正式账号。新增成功后会立即同步到首页、语音和综合业务。")
                else Text("已保存 ${accounts.size} 个号码")
            }
        }

        if (accounts.isNotEmpty()) {
            items(accounts, key = { it.id }) { account ->
                FunctionalCard(title = account.displayName) {
                    Text(account.mobile)
                    Text(account.packageName.ifBlank { "套餐信息暂无" })
                }
            }
        }

        item {
            FunctionalCard(title = "从 iOS 导入凭据") {
                Text("文件只在本机读取；Cookie、appId、token_online 会经过真实余量验证后写入 Android Keystore，不写入普通账号文件。")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !onboarding.isImporting && !onboarding.isLoggingIn,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                ) {
                    if (onboarding.isImporting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(if (onboarding.isImporting) "正在验证并导入" else "选择 iOS 凭据 JSON")
                }
            }
        }

        item {
            FunctionalCard(title = "短信验证码登录") {
                Text("验证码只用于本次请求，不会保存。若联通触发风险安全验证，会自动打开官方验证页面；密码登录 UI 仍按 iOS 当前状态保持关闭。")
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = mobile,
                    onValueChange = { mobile = it.filter(Char::isDigit).take(13) },
                    label = { Text("联通手机号") },
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !onboarding.isSendingCode && !onboarding.isLoggingIn && !onboarding.isImporting,
                    onClick = { flowViewModel.sendSMSCode(mobile) },
                ) {
                    if (onboarding.isSendingCode) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(if (onboarding.isSendingCode) "正在发送" else "获取验证码")
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = verificationCode,
                    onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位验证码") },
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !onboarding.isLoggingIn && !onboarding.isSendingCode && !onboarding.isImporting,
                    onClick = { flowViewModel.loginWithSMS(mobile, verificationCode) },
                ) {
                    if (onboarding.isLoggingIn) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(if (onboarding.isLoggingIn) "正在登录并验证余量" else "登录并添加号码")
                }
            }
        }

        if (onboarding.statusTitle != null || onboarding.statusMessage != null) {
            item {
                FunctionalCard(title = onboarding.statusTitle ?: "状态") {
                    onboarding.statusMessage?.let { Text(it) }
                    Button(onClick = flowViewModel::clearOnboardingStatus) { Text("清除状态") }
                }
            }
        }

        item {
            FunctionalCard(title = "独立宽带账号") {
                Text("宽带账号只供支持宽带目标的业务页面使用，不会加入首页流量、语音或余额卡片。普通元数据保存在本机；Cookie/appId/token_online 只进入 M5 Android Keystore。")
                if (broadbandState.accounts.isEmpty()) Text("当前没有独立宽带账号。")
                else Text("已保存 ${broadbandState.accounts.size} 个宽带账号")
            }
        }

        if (broadbandState.accounts.isNotEmpty()) {
            items(broadbandState.accounts, key = { "broadband-${it.id}" }) { account ->
                FunctionalCard(title = account.displayName.ifBlank { account.locationName.ifBlank { "宽带号码" } }) {
                    Text(account.maskedServiceNumber())
                    if (account.locationName.isNotBlank()) Text(account.locationName)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !broadbandState.isWorking,
                        onClick = { broadbandViewModel.remove(account.id) },
                    ) { Text("删除宽带账号") }
                }
            }
        }

        item {
            FunctionalCard(title = "添加 / 覆盖宽带账号") {
                Text("同一宽带号码再次验证会保留原账号 UUID 并更新凭据。下面字段不写入 Compose 保存状态；请勿通过截图分享 Cookie 或 token。")
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandNumber,
                    onValueChange = { broadbandNumber = it.trim().take(32) },
                    label = { Text("联通宽带号码") }, singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandDisplayName,
                    onValueChange = { broadbandDisplayName = it.take(40) },
                    label = { Text("显示名称（可选）") }, singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandIDCardLastSix,
                    onValueChange = { broadbandIDCardLastSix = it.filter(Char::isDigit).take(6) },
                    label = { Text("身份证后 6 位") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandLocation,
                    onValueChange = { broadbandLocation = it.take(30) },
                    label = { Text("号码归属地（可选）") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f), value = broadbandProvinceCode,
                        onValueChange = { broadbandProvinceCode = it.filter(Char::isDigit).take(8) },
                        label = { Text("省编码") }, singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f), value = broadbandCityCode,
                        onValueChange = { broadbandCityCode = it.filter(Char::isDigit).take(8) },
                        label = { Text("市编码") }, singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f), value = broadbandAreaCode,
                        onValueChange = { broadbandAreaCode = it.filter(Char::isDigit).take(8) },
                        label = { Text("区号") }, singleLine = true,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandCookie,
                    onValueChange = { broadbandCookie = it },
                    label = { Text("Cookie") }, minLines = 3,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandAppID,
                    onValueChange = { broadbandAppID = it },
                    label = { Text("appId（可留空）") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = broadbandTokenOnline,
                    onValueChange = { broadbandTokenOnline = it },
                    label = { Text("token_online（可留空）") }, minLines = 2,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !broadbandState.isWorking && broadbandNumber.isNotBlank() &&
                        broadbandIDCardLastSix.length == 6 && broadbandCookie.isNotBlank(),
                    onClick = {
                        broadbandViewModel.validateAndSave(
                            draft = BroadbandAccountDraft(
                                serviceNumber = broadbandNumber,
                                displayName = broadbandDisplayName,
                                idCardLastSix = broadbandIDCardLastSix,
                                locationName = broadbandLocation,
                                provinceCode = broadbandProvinceCode,
                                cityCode = broadbandCityCode,
                                areaCode = broadbandAreaCode,
                            ),
                            cookie = broadbandCookie,
                            appID = broadbandAppID,
                            tokenOnline = broadbandTokenOnline,
                        )
                    },
                ) {
                    if (broadbandState.isWorking) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(if (broadbandState.isWorking) "正在验证" else "验证并保存宽带账号")
                }
            }
        }

        if (broadbandState.statusTitle != null || broadbandState.statusMessage != null) {
            item {
                FunctionalCard(title = broadbandState.statusTitle ?: "宽带账号状态") {
                    broadbandState.statusMessage?.let { Text(it) }
                    Button(onClick = broadbandViewModel::clearStatus) { Text("清除状态") }
                }
            }
        }

        item {
            FunctionalCard(title = "其它设置") {
                Text("刷新策略、Widget、自动化和最终设置页视觉布局继续按迁移计划后续接入。")
            }
        }
    }

    onboarding.captchaChallenge?.let { challenge ->
        UnicomCaptchaVerificationDialog(
            challenge = challenge,
            cookieHeader = flowViewModel.captchaCookieHeader(),
            userAgent = flowViewModel.captchaUserAgent(),
            systemInfo = flowViewModel.captchaSystemInfo(),
            onResultToken = flowViewModel::continueSMSCaptcha,
            onDismiss = flowViewModel::dismissSMSCaptcha,
        )
    }
}

@Composable
private fun FunctionalCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
