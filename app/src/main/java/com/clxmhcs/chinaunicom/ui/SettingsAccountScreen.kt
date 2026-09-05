package com.clxmhcs.chinaunicom.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountDraft

private val CredentialBlue = Color(0xFF3D6CF4)
private val CredentialGroupedBackground = Color(0xFFF2F2F7)
private val CredentialSecondary = Color(0xFF8E8E93)
private val CredentialMutedBlue = Color(0xFF9AB2FF)
private val CredentialDanger = Color(0xFFFF3B30)

private enum class CredentialPage {
    ROOT,
    ADD_MOBILE,
    ADD_BROADBAND,
    LOGIN,
    EDIT,
    TRANSFER,
    CLEAR_ACCOUNTS,
}

private enum class CredentialActionGlyph {
    ADD_MOBILE,
    ADD_BROADBAND,
    LOGIN,
    KEY,
    TRANSFER,
}

@Composable
internal fun SettingsAccountScreen(
    flowViewModel: FlowViewModel = viewModel(),
    broadbandViewModel: BroadbandAccountViewModel = viewModel(),
    onClose: () -> Unit = {},
) {
    val flowState by flowViewModel.uiState.collectAsState()
    val onboarding by flowViewModel.accountOnboardingState.collectAsState()
    val broadbandState by broadbandViewModel.state.collectAsState()
    val accounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()

    var page by remember { mutableStateOf(CredentialPage.ROOT) }
    var clearSession by remember { mutableIntStateOf(0) }
    var mobile by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    // Credential fields remain remember-only so secrets never enter Compose saveable state.
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

    when (page) {
        CredentialPage.ROOT -> CredentialActionsHome(
            onClose = onClose,
            onAddMobile = { page = CredentialPage.ADD_MOBILE },
            onAddBroadband = { page = CredentialPage.ADD_BROADBAND },
            onLogin = { page = CredentialPage.LOGIN },
            onEdit = { page = CredentialPage.EDIT },
            onTransfer = { page = CredentialPage.TRANSFER },
            onOpenClearAccounts = {
                clearSession += 1
                page = CredentialPage.CLEAR_ACCOUNTS
            },
        )

        CredentialPage.ADD_MOBILE -> CredentialImportMobilePage(
            onboarding = onboarding,
            onChooseFile = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            onClearStatus = flowViewModel::clearOnboardingStatus,
            onBack = { page = CredentialPage.ROOT },
        )

        CredentialPage.ADD_BROADBAND -> BroadbandCredentialPage(
            broadbandState = broadbandState,
            broadbandNumber = broadbandNumber,
            onBroadbandNumberChange = { broadbandNumber = it.trim().take(32) },
            broadbandDisplayName = broadbandDisplayName,
            onBroadbandDisplayNameChange = { broadbandDisplayName = it.take(40) },
            broadbandIDCardLastSix = broadbandIDCardLastSix,
            onBroadbandIDCardLastSixChange = { broadbandIDCardLastSix = it.filter(Char::isDigit).take(6) },
            broadbandLocation = broadbandLocation,
            onBroadbandLocationChange = { broadbandLocation = it.take(30) },
            broadbandProvinceCode = broadbandProvinceCode,
            onBroadbandProvinceCodeChange = { broadbandProvinceCode = it.filter(Char::isDigit).take(8) },
            broadbandCityCode = broadbandCityCode,
            onBroadbandCityCodeChange = { broadbandCityCode = it.filter(Char::isDigit).take(8) },
            broadbandAreaCode = broadbandAreaCode,
            onBroadbandAreaCodeChange = { broadbandAreaCode = it.filter(Char::isDigit).take(8) },
            broadbandCookie = broadbandCookie,
            onBroadbandCookieChange = { broadbandCookie = it },
            broadbandAppID = broadbandAppID,
            onBroadbandAppIDChange = { broadbandAppID = it },
            broadbandTokenOnline = broadbandTokenOnline,
            onBroadbandTokenOnlineChange = { broadbandTokenOnline = it },
            onSave = {
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
            onClearStatus = broadbandViewModel::clearStatus,
            onBack = { page = CredentialPage.ROOT },
        )

        CredentialPage.LOGIN -> SMSCredentialLoginPage(
            onboarding = onboarding,
            mobile = mobile,
            onMobileChange = { mobile = it.filter(Char::isDigit).take(13) },
            verificationCode = verificationCode,
            onVerificationCodeChange = { verificationCode = it.filter(Char::isDigit).take(6) },
            onSendCode = { flowViewModel.sendSMSCode(mobile) },
            onLogin = { flowViewModel.loginWithSMS(mobile, verificationCode) },
            onClearStatus = flowViewModel::clearOnboardingStatus,
            onBack = { page = CredentialPage.ROOT },
        )

        CredentialPage.EDIT -> CredentialEditorPage(
            accounts = accounts,
            broadbandState = broadbandState,
            onDeleteBroadband = broadbandViewModel::remove,
            onBack = { page = CredentialPage.ROOT },
        )

        CredentialPage.TRANSFER -> CredentialTransferPage(
            onboarding = onboarding,
            onChooseFile = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            onClearStatus = flowViewModel::clearOnboardingStatus,
            onBack = { page = CredentialPage.ROOT },
        )

        CredentialPage.CLEAR_ACCOUNTS -> SettingsClearAccountsScreen(
            viewModel = viewModel(key = "credential-clear-$clearSession"),
            onAccountsChanged = { broadbandViewModel.reload() },
            onBack = { page = CredentialPage.ROOT },
        )
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
private fun CredentialActionsHome(
    onClose: () -> Unit,
    onAddMobile: () -> Unit,
    onAddBroadband: () -> Unit,
    onLogin: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onOpenClearAccounts: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5D7DC)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            color = CredentialGroupedBackground,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    CredentialRootHeader(onClose)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "选择你要进行的操作：",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = CredentialSecondary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                item {
                    CredentialActionCard(
                        glyph = CredentialActionGlyph.ADD_MOBILE,
                        title = "新增手机号码信息",
                        subtitle = "通过 Cookie、appId、token_online 登录。",
                        onClick = onAddMobile,
                    )
                    Spacer(Modifier.height(28.dp))
                }

                item {
                    CredentialActionCard(
                        glyph = CredentialActionGlyph.ADD_BROADBAND,
                        title = "新增宽带号码信息",
                        subtitle = "通过 Cookie、appId、token_online 登录。",
                        onClick = onAddBroadband,
                    )
                    Spacer(Modifier.height(28.dp))
                }

                item {
                    CredentialActionCard(
                        glyph = CredentialActionGlyph.LOGIN,
                        title = "短信/密码 登录",
                        onClick = onLogin,
                    )
                    Spacer(Modifier.height(28.dp))
                }

                item {
                    CredentialActionCard(
                        glyph = CredentialActionGlyph.KEY,
                        title = "账户凭据信息编辑",
                        onClick = onEdit,
                    )
                    Spacer(Modifier.height(28.dp))
                }

                item {
                    CredentialActionCard(
                        glyph = CredentialActionGlyph.TRANSFER,
                        title = "导出 / 导入凭据",
                        onClick = onTransfer,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        Text(
                            text = "导出和导入需要先通过凭据管理密码或设备身份验证。",
                            color = CredentialSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = CredentialSecondary)) {
                                    append("若要删除某个手机号的相关凭据，请到")
                                }
                                withStyle(SpanStyle(color = CredentialDanger)) {
                                    append("【清空账户与凭据】")
                                }
                                withStyle(SpanStyle(color = CredentialSecondary)) {
                                    append("页面操作。")
                                }
                            },
                            modifier = Modifier.clickable(onClick = onOpenClearAccounts),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialRootHeader(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Surface(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(66.dp)
                .height(44.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "关闭",
                    color = CredentialBlue,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                )
            }
        }
        Text(
            text = "账户凭据",
            modifier = Modifier.align(Alignment.Center),
            color = Color.Black,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CredentialActionCard(
    glyph: CredentialActionGlyph,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 66.dp else 78.dp),
        shape = RoundedCornerShape(25.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CredentialActionIcon(glyph)
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = title,
                        color = CredentialBlue,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = CredentialMutedBlue,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialActionIcon(glyph: CredentialActionGlyph) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.0.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        when (glyph) {
            CredentialActionGlyph.ADD_MOBILE -> {
                drawCircle(color = CredentialBlue, radius = w * 0.36f, center = center)
                drawLine(Color.White, Offset(w * .50f, h * .33f), Offset(w * .50f, h * .67f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(w * .33f, h * .50f), Offset(w * .67f, h * .50f), 2.dp.toPx(), StrokeCap.Round)
            }
            CredentialActionGlyph.ADD_BROADBAND -> {
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .12f, h * .48f),
                    size = Size(w * .76f, h * .30f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                repeat(4) { index ->
                    drawCircle(
                        color = CredentialBlue,
                        radius = w * .025f,
                        center = Offset(w * (.30f + index * .13f), h * .63f),
                    )
                }
                drawArc(
                    color = CredentialBlue,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(w * .27f, h * .12f),
                    size = Size(w * .46f, h * .34f),
                    style = stroke,
                )
                drawArc(
                    color = CredentialBlue,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * .39f, h * .26f),
                    size = Size(w * .22f, h * .16f),
                    style = stroke,
                )
            }
            CredentialActionGlyph.LOGIN -> {
                drawCircle(CredentialBlue, radius = w * .14f, center = Offset(w * .38f, h * .33f))
                drawArc(
                    color = CredentialBlue,
                    startAngle = 198f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = Offset(w * .17f, h * .38f),
                    size = Size(w * .42f, h * .34f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(CredentialBlue, radius = w * .08f, center = Offset(w * .68f, h * .47f), style = stroke)
                drawLine(CredentialBlue, Offset(w * .68f, h * .55f), Offset(w * .68f, h * .76f), strokeWidth, StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .68f, h * .68f), Offset(w * .76f, h * .68f), strokeWidth, StrokeCap.Round)
            }
            CredentialActionGlyph.KEY -> {
                drawCircle(CredentialBlue, radius = w * .12f, center = Offset(w * .42f, h * .30f), style = Stroke(width = 4.dp.toPx()))
                drawLine(CredentialBlue, Offset(w * .42f, h * .42f), Offset(w * .42f, h * .78f), 5.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .42f, h * .61f), Offset(w * .56f, h * .70f), 5.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .42f, h * .72f), Offset(w * .53f, h * .81f), 5.dp.toPx(), StrokeCap.Round)
            }
            CredentialActionGlyph.TRANSFER -> {
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .18f, h * .37f),
                    size = Size(w * .56f, h * .45f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .30f, h * .47f),
                    size = Size(w * .52f, h * .40f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawLine(CredentialBlue, Offset(w * .50f, h * .12f), Offset(w * .50f, h * .57f), strokeWidth, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(w * .36f, h * .27f)
                    lineTo(w * .50f, h * .12f)
                    lineTo(w * .64f, h * .27f)
                }
                drawPath(arrow, CredentialBlue, style = stroke)
            }
        }
    }
}

@Composable
private fun CredentialImportMobilePage(
    onboarding: AccountOnboardingUiState,
    onChooseFile: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    CredentialFunctionalPage(title = "新增手机号码信息", onBack = onBack) {
        FunctionalCard(title = "导入并验证手机凭据") {
            Text(
                "Android 当前复用已闭环的安全导入路径：从本机选择 iOS 凭据 JSON，Cookie、appId、token_online 经真实余量验证后写入 Android Keystore。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !onboarding.isImporting && !onboarding.isLoggingIn,
                onClick = onChooseFile,
            ) {
                if (onboarding.isImporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                if (onboarding.isImporting) Spacer(Modifier.width(8.dp))
                Text(if (onboarding.isImporting) "正在验证并导入" else "选择 iOS 凭据 JSON")
            }
        }
        OnboardingStatusCard(onboarding, onClearStatus)
    }
}

@Composable
private fun SMSCredentialLoginPage(
    onboarding: AccountOnboardingUiState,
    mobile: String,
    onMobileChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    CredentialFunctionalPage(title = "短信/密码 登录", onBack = onBack) {
        FunctionalCard(title = "验证码登录") {
            Text(
                "当前 iOS 基线仍关闭密码提交入口；Android 保持相同产品状态，仅开放已闭环的短信验证码登录。验证码不会保存。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = mobile,
                onValueChange = onMobileChange,
                label = { Text("联通手机号") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !onboarding.isSendingCode && !onboarding.isLoggingIn && !onboarding.isImporting,
                onClick = onSendCode,
            ) {
                if (onboarding.isSendingCode) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                if (onboarding.isSendingCode) Spacer(Modifier.width(8.dp))
                Text(if (onboarding.isSendingCode) "正在发送" else "获取验证码")
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = verificationCode,
                onValueChange = onVerificationCodeChange,
                label = { Text("6 位验证码") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !onboarding.isLoggingIn && !onboarding.isSendingCode && !onboarding.isImporting,
                onClick = onLogin,
            ) {
                if (onboarding.isLoggingIn) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                if (onboarding.isLoggingIn) Spacer(Modifier.width(8.dp))
                Text(if (onboarding.isLoggingIn) "正在登录并验证余量" else "登录并添加号码")
            }
        }
        OnboardingStatusCard(onboarding, onClearStatus)
    }
}

@Composable
private fun CredentialTransferPage(
    onboarding: AccountOnboardingUiState,
    onChooseFile: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    CredentialFunctionalPage(title = "导出 / 导入凭据", onBack = onBack) {
        FunctionalCard(title = "导入凭据") {
            Text(
                "当前 Android 已闭环的是安全导入：文件仅在本机读取，凭据验证成功后只写入 Keystore。导出 authority 未在本次 UI 精修中新增。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !onboarding.isImporting && !onboarding.isLoggingIn,
                onClick = onChooseFile,
            ) {
                if (onboarding.isImporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                if (onboarding.isImporting) Spacer(Modifier.width(8.dp))
                Text(if (onboarding.isImporting) "正在验证并导入" else "选择 iOS 凭据 JSON")
            }
        }
        OnboardingStatusCard(onboarding, onClearStatus)
    }
}

@Composable
private fun CredentialEditorPage(
    accounts: List<com.clxmhcs.chinaunicom.core.model.UnicomAccount>,
    broadbandState: BroadbandAccountUiState,
    onDeleteBroadband: (java.util.UUID) -> Unit,
    onBack: () -> Unit,
) {
    CredentialFunctionalPage(title = "账户凭据信息编辑", onBack = onBack) {
        FunctionalCard(title = "手机号码") {
            if (accounts.isEmpty()) {
                Text("当前没有正式手机账号。", style = MaterialTheme.typography.bodySmall)
            } else {
                accounts.forEach { account ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(account.displayName.ifBlank { "联通号码" }, fontWeight = FontWeight.SemiBold)
                        Text(account.mobile, style = MaterialTheme.typography.bodySmall)
                        Text(account.packageName.ifBlank { "套餐信息暂无" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FunctionalCard(title = "宽带号码") {
            if (broadbandState.accounts.isEmpty()) {
                Text("当前没有独立宽带账号。", style = MaterialTheme.typography.bodySmall)
            } else {
                broadbandState.accounts.forEach { account ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(account.displayName.ifBlank { account.locationName.ifBlank { "宽带号码" } }, fontWeight = FontWeight.SemiBold)
                        Text(account.maskedServiceNumber(), style = MaterialTheme.typography.bodySmall)
                        if (account.locationName.isNotBlank()) Text(account.locationName, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !broadbandState.isWorking,
                            onClick = { onDeleteBroadband(account.id) },
                        ) { Text("删除宽带账号") }
                    }
                }
            }
        }
        Text(
            "手机号码删除请从账户凭据首页进入【清空账户与凭据】。",
            style = MaterialTheme.typography.bodySmall,
            color = CredentialSecondary,
        )
    }
}

@Composable
private fun BroadbandCredentialPage(
    broadbandState: BroadbandAccountUiState,
    broadbandNumber: String,
    onBroadbandNumberChange: (String) -> Unit,
    broadbandDisplayName: String,
    onBroadbandDisplayNameChange: (String) -> Unit,
    broadbandIDCardLastSix: String,
    onBroadbandIDCardLastSixChange: (String) -> Unit,
    broadbandLocation: String,
    onBroadbandLocationChange: (String) -> Unit,
    broadbandProvinceCode: String,
    onBroadbandProvinceCodeChange: (String) -> Unit,
    broadbandCityCode: String,
    onBroadbandCityCodeChange: (String) -> Unit,
    broadbandAreaCode: String,
    onBroadbandAreaCodeChange: (String) -> Unit,
    broadbandCookie: String,
    onBroadbandCookieChange: (String) -> Unit,
    broadbandAppID: String,
    onBroadbandAppIDChange: (String) -> Unit,
    broadbandTokenOnline: String,
    onBroadbandTokenOnlineChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    CredentialFunctionalPage(title = "新增宽带号码信息", onBack = onBack) {
        FunctionalCard(title = "添加 / 覆盖宽带账号") {
            Text(
                "同一宽带号码再次验证会保留原账号 UUID 并更新凭据。Cookie、appId、token_online 只进入 Android Keystore。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandNumber,
                onValueChange = onBroadbandNumberChange, label = { Text("联通宽带号码") }, singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandDisplayName,
                onValueChange = onBroadbandDisplayNameChange, label = { Text("显示名称（可选）") }, singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandIDCardLastSix,
                onValueChange = onBroadbandIDCardLastSixChange, label = { Text("身份证后 6 位") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandLocation,
                onValueChange = onBroadbandLocationChange, label = { Text("号码归属地（可选）") }, singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f), value = broadbandProvinceCode,
                    onValueChange = onBroadbandProvinceCodeChange, label = { Text("省编码") }, singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f), value = broadbandCityCode,
                    onValueChange = onBroadbandCityCodeChange, label = { Text("市编码") }, singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f), value = broadbandAreaCode,
                    onValueChange = onBroadbandAreaCodeChange, label = { Text("区号") }, singleLine = true,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandCookie,
                onValueChange = onBroadbandCookieChange, label = { Text("Cookie") }, minLines = 3,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandAppID,
                onValueChange = onBroadbandAppIDChange, label = { Text("appId（可留空）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(), value = broadbandTokenOnline,
                onValueChange = onBroadbandTokenOnlineChange, label = { Text("token_online（可留空）") }, minLines = 2,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !broadbandState.isWorking && broadbandNumber.isNotBlank() &&
                    broadbandIDCardLastSix.length == 6 && broadbandCookie.isNotBlank(),
                onClick = onSave,
            ) {
                if (broadbandState.isWorking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                if (broadbandState.isWorking) Spacer(Modifier.width(8.dp))
                Text(if (broadbandState.isWorking) "正在验证" else "验证并保存宽带账号")
            }
        }
        if (broadbandState.statusTitle != null || broadbandState.statusMessage != null) {
            FunctionalCard(title = broadbandState.statusTitle ?: "宽带账号状态") {
                broadbandState.statusMessage?.let { Text(it) }
                TextButton(onClick = onClearStatus) { Text("清除状态") }
            }
        }
    }
}

@Composable
private fun OnboardingStatusCard(
    onboarding: AccountOnboardingUiState,
    onClearStatus: () -> Unit,
) {
    if (onboarding.statusTitle != null || onboarding.statusMessage != null) {
        FunctionalCard(title = onboarding.statusTitle ?: "状态") {
            onboarding.statusMessage?.let { Text(it) }
            TextButton(onClick = onClearStatus) { Text("清除状态") }
        }
    }
}

@Composable
private fun CredentialFunctionalPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CredentialGroupedBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { CredentialSubpageHeader(title, onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun CredentialSubpageHeader(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("返回", color = CredentialBlue)
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
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
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
