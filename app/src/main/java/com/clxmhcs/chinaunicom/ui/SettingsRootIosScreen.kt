package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceAccountGroup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val SettingsAccent = Color(0xFF3478F6)
private val SettingsGroupedBackground = Color(0xFFF2F2F7)
private val SettingsSecondary = Color(0xFF8E8E93)
private val SettingsSeparator = Color(0xFFE5E5EA)
private val SettingsChevron = Color(0xFFC7C7CC)
private val SettingsDanger = Color(0xFFFF3B30)

private enum class SettingsIosPage {
    ACCOUNT_ORDER,
    CARRIER_CORRECTION,
    WIDGET_SINGLE,
    WIDGET_DUAL,
    WIDGET_REFRESH,
    DAILY_BASELINE,
    BALANCE_GROUPING,
    FINANCIAL_REFRESH,
    PHONE_SEGMENTS,
    SHORTCUT_NOTIFICATION,
    APP_MANUAL,
    INTERFACE_CONFIGURATION,
    CLEAR_ACCOUNTS,
}

private enum class SettingsGlyph {
    SORT,
    CARRIER,
    WIDGET_SINGLE,
    WIDGET_DUAL,
    REFRESH,
    APP_REFRESH,
    CLOCK,
    GROUP,
    FINANCIAL,
    SEGMENTS,
    LIST,
    KEY,
    SERVER,
    BELL,
    BOOK,
    HEART,
}

@Composable
fun SettingsRootIosScreen(
    settingsViewModel: SettingsRootViewModel,
    onOpenCredentials: () -> Unit,
    onOpenRefreshLogic: () -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember(context.packageName) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val installedAppVersion = "${packageInfo.versionName ?: "1.0"} (${packageInfo.longVersionCode})"

    val settings by settingsViewModel.appSettings.collectAsState()
    val appState by settingsViewModel.appState.collectAsState()
    val balanceState by settingsViewModel.balanceState.collectAsState()
    val accounts = appState.accounts.sortedBy { it.sortOrder }

    var maintenanceGeneration by remember { mutableStateOf(0) }
    var clearSession by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<SettingsIosPage?>(null) }
    var showCredentialHelp by remember { mutableStateOf(false) }
    var showDonationMessage by remember { mutableStateOf(false) }

    val m11cViewModel: SettingsM11CViewModel = viewModel(key = "settings-ios-m11c-$maintenanceGeneration")
    val broadbandViewModel: BroadbandAccountViewModel = viewModel()
    val broadbandState by broadbandViewModel.state.collectAsState()
    val attributionState by m11cViewModel.attributionState.collectAsState()

    LaunchedEffect(accounts.map { it.id }, maintenanceGeneration) {
        m11cViewModel.reconcileMobileAccounts(accounts.map { it.id }.toSet())
    }

    val closePage = { page = null }
    when (page) {
        SettingsIosPage.ACCOUNT_ORDER -> {
            IosAccountOrderScreen(
                accounts = accounts,
                settings = settings,
                onMove = settingsViewModel::moveAccount,
                onBack = closePage,
            )
            return
        }
        SettingsIosPage.CARRIER_CORRECTION -> {
            CarrierCorrectionSettingsScreen(accounts, broadbandState.accounts, m11cViewModel, closePage)
            return
        }
        SettingsIosPage.WIDGET_SINGLE -> {
            SingleWidgetSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsIosPage.WIDGET_DUAL -> {
            DualWidgetSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsIosPage.WIDGET_REFRESH -> {
            WidgetRefreshSettingsScreen(m11cViewModel, closePage)
            return
        }
        SettingsIosPage.DAILY_BASELINE -> {
            DailyUsageBaselineSettingsScreen(accounts, m11cViewModel, closePage)
            return
        }
        SettingsIosPage.BALANCE_GROUPING -> {
            IosBalanceGroupingScreen(
                accounts = accounts,
                groups = balanceState.balanceAccountGroups,
                settings = settings,
                onAddGroup = settingsViewModel::addBalanceGroup,
                onDeleteGroup = settingsViewModel::deleteBalanceGroup,
                onToggleMember = settingsViewModel::toggleBalanceGroupMember,
                onBack = closePage,
            )
            return
        }
        SettingsIosPage.FINANCIAL_REFRESH -> {
            IosFinancialRefreshScreen(
                accounts = accounts.filter { it.isEnabled },
                groups = balanceState.balanceAccountGroups,
                homeAccountID = balanceState.homeBalanceAccountID,
                settings = settings,
                onSetHome = settingsViewModel::setHomeBalanceAccount,
                onSetGroupDefault = settingsViewModel::setDefaultFinancialAccount,
                onBack = closePage,
            )
            return
        }
        SettingsIosPage.PHONE_SEGMENTS -> {
            PhoneSegmentSettingsScreen(m11cViewModel, closePage)
            return
        }
        SettingsIosPage.SHORTCUT_NOTIFICATION -> {
            ShortcutNotificationSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsIosPage.APP_MANUAL -> {
            AppManualScreen(closePage)
            return
        }
        SettingsIosPage.INTERFACE_CONFIGURATION -> {
            IosInterfaceConfigurationScreen(closePage)
            return
        }
        SettingsIosPage.CLEAR_ACCOUNTS -> {
            SettingsClearAccountsScreen(
                viewModel = viewModel(key = "settings-ios-clear-$clearSession"),
                onAccountsChanged = { clearedAll ->
                    broadbandViewModel.reload()
                    if (clearedAll) settingsViewModel.reloadAfterMaintenance()
                    maintenanceGeneration += 1
                },
                onBack = closePage,
            )
            return
        }
        null -> Unit
    }

    val activeBalanceGroups = balanceState.balanceAccountGroups.count { it.memberAccountIDs.size >= 2 }
    val savedCredentialCount = accounts.size + broadbandState.accounts.size
    val latestSegmentUpdate = attributionState.segments.maxByOrNull { it.updatedAt }?.updatedAt
    val latestSegmentText = latestSegmentUpdate?.atZone(ZoneId.systemDefault())?.format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F7FF), SettingsGroupedBackground, SettingsGroupedBackground),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = "设置",
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 0.dp, top = 0.dp, bottom = 2.dp),
                )
            }

            item {
                IosSettingsSection(title = "显示") {
                    IosSwitchRow(
                        label = "隐藏手机号中间四位",
                        checked = settings.hideMobileMiddleDigits,
                        divider = true,
                        onCheckedChange = settingsViewModel::setHideMobileMiddleDigits,
                    )
                    IosSwitchRow(
                        label = "宽带号码只显示前后4位",
                        checked = settings.hideBroadbandMiddleDigits,
                        divider = true,
                        onCheckedChange = settingsViewModel::setHideBroadbandMiddleDigits,
                    )
                    IosActionRow(
                        label = "流量单位",
                        detail = settings.displayUnit.title,
                        detailColor = SettingsAccent,
                        showsPicker = true,
                        divider = true,
                        onClick = settingsViewModel::cycleDisplayUnit,
                    )
                    IosActionRow(
                        label = "自定义排序",
                        icon = SettingsGlyph.SORT,
                        divider = true,
                        enabled = accounts.size >= 2,
                        onClick = { page = SettingsIosPage.ACCOUNT_ORDER },
                    )
                    IosActionRow(
                        label = "号码归属纠正",
                        detail = attributionState.corrections.size.takeIf { it > 0 }?.let { "已纠正 $it" },
                        icon = SettingsGlyph.CARRIER,
                        onClick = { page = SettingsIosPage.CARRIER_CORRECTION },
                    )
                }
            }

            item {
                IosSettingsSection(title = "桌面组件") {
                    IosActionRow("单号码组件信息编辑", icon = SettingsGlyph.WIDGET_SINGLE, divider = true) {
                        page = SettingsIosPage.WIDGET_SINGLE
                    }
                    IosActionRow("双号码组件信息编辑", icon = SettingsGlyph.WIDGET_DUAL, divider = true, enabled = accounts.size >= 2) {
                        page = SettingsIosPage.WIDGET_DUAL
                    }
                    IosActionRow("组件刷新编辑", icon = SettingsGlyph.REFRESH) {
                        page = SettingsIosPage.WIDGET_REFRESH
                    }
                }
            }

            item {
                IosSettingsSection(title = "数据刷新") {
                    IosActionRow("App刷新逻辑编辑", icon = SettingsGlyph.APP_REFRESH, divider = true, onClick = onOpenRefreshLogic)
                    IosActionRow("每日用量基准", icon = SettingsGlyph.CLOCK, divider = true, enabled = accounts.isNotEmpty()) {
                        page = SettingsIosPage.DAILY_BASELINE
                    }
                    IosActionRow(
                        label = "合账号码选择",
                        detail = if (activeBalanceGroups == 0) "未设置" else "已设置 ${activeBalanceGroups} 组",
                        icon = SettingsGlyph.GROUP,
                        divider = true,
                        enabled = accounts.size >= 2,
                    ) { page = SettingsIosPage.BALANCE_GROUPING }
                    IosActionRow(
                        label = "余额/账单 刷新号码编辑",
                        icon = SettingsGlyph.FINANCIAL,
                        enabled = accounts.any { it.isEnabled },
                    ) { page = SettingsIosPage.FINANCIAL_REFRESH }
                }
            }

            item {
                IosSettingsSection(title = "运营商号段") {
                    IosActionRow(
                        label = if (attributionState.isUpdatingSegments) "号段更新中…" else "号段更新",
                        detail = latestSegmentText,
                        detailColor = SettingsAccent.copy(alpha = 0.55f),
                        icon = SettingsGlyph.SEGMENTS,
                        divider = true,
                        showsChevron = false,
                        enabled = !attributionState.isUpdatingSegments,
                        onClick = m11cViewModel::updatePhoneSegments,
                    )
                    IosActionRow(
                        label = "已保存号段",
                        detail = attributionState.segments.size.toString(),
                        detailColor = SettingsAccent.copy(alpha = 0.55f),
                        icon = SettingsGlyph.LIST,
                    ) { page = SettingsIosPage.PHONE_SEGMENTS }
                }
            }

            item {
                IosSettingsSection(
                    title = "数据与安全",
                    titleAccessory = {
                        Surface(
                            onClick = { showCredentialHelp = true },
                            shape = CircleShape,
                            color = SettingsSecondary.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("?", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                ) {
                    IosActionRow(
                        label = "凭据信息新增 / 编辑",
                        detailContent = {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = SettingsSecondary)) { append("已保存 ") }
                                    withStyle(SpanStyle(color = SettingsDanger)) { append(savedCredentialCount.toString()) }
                                    withStyle(SpanStyle(color = SettingsSecondary)) { append(" 个号码凭据") }
                                },
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                            )
                        },
                        divider = true,
                        onClick = onOpenCredentials,
                    )
                    IosInformationRow(
                        label = "手动凭据存储在本机 [Keystore] 中",
                        icon = SettingsGlyph.KEY,
                    )
                }
            }

            item {
                IosSettingsSection(title = "网络与接口") {
                    IosActionRow(
                        label = "联通接口配置",
                        detail = "12.15",
                        icon = SettingsGlyph.SERVER,
                    ) { page = SettingsIosPage.INTERFACE_CONFIGURATION }
                }
            }

            item {
                IosSettingsSection(title = "工具") {
                    IosActionRow("快捷指令余量通知", icon = SettingsGlyph.BELL) {
                        page = SettingsIosPage.SHORTCUT_NOTIFICATION
                    }
                }
            }

            item {
                IosSettingsSection(title = "关于") {
                    IosActionRow("App使用说明书", icon = SettingsGlyph.BOOK, divider = true) {
                        page = SettingsIosPage.APP_MANUAL
                    }
                    IosActionRow(
                        label = "我要打赏",
                        icon = SettingsGlyph.HEART,
                        labelColor = SettingsDanger,
                        divider = true,
                        showsChevron = false,
                    ) { showDonationMessage = true }
                    IosInformationRow(
                        label = "版本、版权",
                        detail = installedAppVersion,
                        divider = true,
                        primaryColor = Color.Black,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "©️夢幻傳說 - Gmail：clxmhcs",
                            fontSize = 11.sp,
                            color = SettingsSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = {
                            clearSession += 1
                            page = SettingsIosPage.CLEAR_ACCOUNTS
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SettingsDanger),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsDanger.copy(alpha = 0.28f)),
                    ) {
                        Text(
                            "清空账户与凭据",
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(380.dp)
                .alpha(0.027f),
        )
    }

    if (showCredentialHelp) {
        AlertDialog(
            onDismissRequest = { showCredentialHelp = false },
            title = { Text("凭据失效处理办法") },
            text = { Text("凭据失效时，可进入“凭据信息新增 / 编辑”重新验证并保存；清空账户与凭据属于独立的受保护维护操作。") },
            confirmButton = { TextButton(onClick = { showCredentialHelp = false }) { Text("知道了") } },
        )
    }

    if (showDonationMessage) {
        AlertDialog(
            onDismissRequest = { showDonationMessage = false },
            title = { Text("我要打赏") },
            text = { Text("感谢支持。Android 版当前仅保留与 iOS 一致的设置入口，支付方式不在本页面内处理。") },
            confirmButton = { TextButton(onClick = { showDonationMessage = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun IosSettingsSection(
    title: String,
    titleAccessory: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.padding(start = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = SettingsSecondary,
            )
            titleAccessory?.invoke()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.97f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun IosSwitchRow(
    label: String,
    checked: Boolean,
    divider: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 17.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 18.sp, color = Color.Black)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.83f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SettingsAccent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE9E9EA),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        if (divider) IosDivider(start = 17.dp)
    }
}

@Composable
private fun IosActionRow(
    label: String,
    detail: String? = null,
    icon: SettingsGlyph? = null,
    divider: Boolean = false,
    enabled: Boolean = true,
    detailColor: Color = SettingsSecondary,
    labelColor: Color = Color.Black,
    showsChevron: Boolean = true,
    showsPicker: Boolean = false,
    detailContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column {
        Surface(
            onClick = onClick,
            enabled = enabled,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(start = 17.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    SettingsGlyphIcon(icon, enabled)
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = if (enabled) labelColor else SettingsSecondary.copy(alpha = 0.45f),
                )
                if (detailContent != null) {
                    detailContent()
                } else if (detail != null) {
                    Text(
                        detail,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (enabled) detailColor else SettingsSecondary.copy(alpha = 0.4f),
                    )
                }
                if (showsPicker) {
                    Spacer(Modifier.size(5.dp))
                    IosPickerChevronPair()
                } else if (showsChevron) {
                    Spacer(Modifier.size(6.dp))
                    Text("›", fontSize = 24.sp, lineHeight = 24.sp, color = SettingsChevron)
                }
            }
        }
        if (divider) IosDivider(start = if (icon == null) 17.dp else 57.dp)
    }
}

@Composable
private fun IosPickerChevronPair() {
    Canvas(modifier = Modifier.size(width = 10.dp, height = 14.dp)) {
        val strokeWidth = 1.45.dp.toPx()
        val centerX = size.width / 2f
        val halfWidth = size.width * 0.28f
        val topCenterY = size.height * 0.30f
        val bottomCenterY = size.height * 0.70f
        val halfHeight = size.height * 0.12f

        drawLine(
            color = SettingsAccent,
            start = androidx.compose.ui.geometry.Offset(centerX - halfWidth, topCenterY + halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX, topCenterY - halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SettingsAccent,
            start = androidx.compose.ui.geometry.Offset(centerX, topCenterY - halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX + halfWidth, topCenterY + halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SettingsAccent,
            start = androidx.compose.ui.geometry.Offset(centerX - halfWidth, bottomCenterY - halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX, bottomCenterY + halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SettingsAccent,
            start = androidx.compose.ui.geometry.Offset(centerX, bottomCenterY + halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX + halfWidth, bottomCenterY - halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun IosInformationRow(
    label: String,
    detail: String? = null,
    icon: SettingsGlyph? = null,
    divider: Boolean = false,
    primaryColor: Color = SettingsSecondary,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 17.dp, end = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsGlyphIcon(icon, enabled = false, secondary = true)
                Spacer(Modifier.size(12.dp))
            }
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = primaryColor)
            detail?.let { Text(it, fontSize = 14.sp, color = SettingsSecondary) }
        }
        if (divider) IosDivider(start = 17.dp)
    }
}

@Composable
private fun IosDivider(start: androidx.compose.ui.unit.Dp) {
    Divider(
        modifier = Modifier.padding(start = start, end = 17.dp),
        thickness = 0.7.dp,
        color = SettingsSeparator,
    )
}

@Composable
private fun SettingsGlyphIcon(
    glyph: SettingsGlyph,
    enabled: Boolean,
    secondary: Boolean = false,
) {
    val color = when {
        secondary -> SettingsSecondary
        !enabled -> SettingsAccent.copy(alpha = 0.35f)
        glyph == SettingsGlyph.HEART -> SettingsDanger
        else -> SettingsAccent
    }
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
        fun arrowHead(tipX: Float, tipY: Float, leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
            val path = Path().apply {
                moveTo(leftX, leftY)
                lineTo(tipX, tipY)
                lineTo(rightX, rightY)
            }
            drawPath(path, color, style = Stroke(width = stroke.width, cap = StrokeCap.Round))
        }
        when (glyph) {
            SettingsGlyph.SORT -> {
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .32f, h * .16f), end = androidx.compose.ui.geometry.Offset(w * .32f, h * .84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .18f, h * .30f), end = androidx.compose.ui.geometry.Offset(w * .32f, h * .16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .46f, h * .30f), end = androidx.compose.ui.geometry.Offset(w * .32f, h * .16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .68f, h * .16f), end = androidx.compose.ui.geometry.Offset(w * .68f, h * .84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .54f, h * .70f), end = androidx.compose.ui.geometry.Offset(w * .68f, h * .84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, start = androidx.compose.ui.geometry.Offset(w * .82f, h * .70f), end = androidx.compose.ui.geometry.Offset(w * .68f, h * .84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            SettingsGlyph.CARRIER -> {
                drawCircle(color, radius = w * .38f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .22f, h * .42f), androidx.compose.ui.geometry.Offset(w * .78f, h * .42f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .22f, h * .42f), androidx.compose.ui.geometry.Offset(w * .34f, h * .30f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .78f, h * .58f), androidx.compose.ui.geometry.Offset(w * .22f, h * .58f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .78f, h * .58f), androidx.compose.ui.geometry.Offset(w * .66f, h * .70f), stroke.width, StrokeCap.Round)
            }
            SettingsGlyph.WIDGET_SINGLE -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .12f, h * .20f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .22f, h * .29f), size = androidx.compose.ui.geometry.Size(w * .56f, h * .42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()), style = stroke)
            }
            SettingsGlyph.WIDGET_DUAL -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .08f, h * .22f), size = androidx.compose.ui.geometry.Size(w * .84f, h * .56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .50f, h * .22f), androidx.compose.ui.geometry.Offset(w * .50f, h * .78f), stroke.width)
            }
            SettingsGlyph.REFRESH -> {
                drawCircle(color, radius = w * .37f, center = center, style = stroke)
                drawArc(
                    color = color,
                    startAngle = -36f,
                    sweepAngle = 282f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .27f, h * .27f),
                    size = androidx.compose.ui.geometry.Size(w * .46f, h * .46f),
                    style = stroke,
                )
                arrowHead(
                    tipX = w * .70f,
                    tipY = h * .32f,
                    leftX = w * .57f,
                    leftY = h * .30f,
                    rightX = w * .68f,
                    rightY = h * .45f,
                )
            }
            SettingsGlyph.APP_REFRESH -> {
                drawCircle(color, radius = w * .37f, center = center, style = stroke)
                drawArc(
                    color = color,
                    startAngle = 202f,
                    sweepAngle = 112f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .27f, h * .27f),
                    size = androidx.compose.ui.geometry.Size(w * .46f, h * .46f),
                    style = stroke,
                )
                arrowHead(
                    tipX = w * .66f,
                    tipY = h * .31f,
                    leftX = w * .53f,
                    leftY = h * .30f,
                    rightX = w * .64f,
                    rightY = h * .44f,
                )
                drawArc(
                    color = color,
                    startAngle = 22f,
                    sweepAngle = 112f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .27f, h * .27f),
                    size = androidx.compose.ui.geometry.Size(w * .46f, h * .46f),
                    style = stroke,
                )
                arrowHead(
                    tipX = w * .34f,
                    tipY = h * .69f,
                    leftX = w * .47f,
                    leftY = h * .70f,
                    rightX = w * .36f,
                    rightY = h * .56f,
                )
            }
            SettingsGlyph.SEGMENTS -> {
                drawArc(
                    color = color,
                    startAngle = 202f,
                    sweepAngle = 112f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .17f, h * .17f),
                    size = androidx.compose.ui.geometry.Size(w * .66f, h * .66f),
                    style = stroke,
                )
                arrowHead(
                    tipX = w * .76f,
                    tipY = h * .24f,
                    leftX = w * .59f,
                    leftY = h * .22f,
                    rightX = w * .72f,
                    rightY = h * .40f,
                )
                drawArc(
                    color = color,
                    startAngle = 22f,
                    sweepAngle = 112f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .17f, h * .17f),
                    size = androidx.compose.ui.geometry.Size(w * .66f, h * .66f),
                    style = stroke,
                )
                arrowHead(
                    tipX = w * .24f,
                    tipY = h * .76f,
                    leftX = w * .41f,
                    leftY = h * .78f,
                    rightX = w * .28f,
                    rightY = h * .60f,
                )
            }
            SettingsGlyph.CLOCK -> {
                drawCircle(color, radius = w * .36f, center = center, style = stroke)
                drawLine(color, center, androidx.compose.ui.geometry.Offset(w * .50f, h * .28f), stroke.width, StrokeCap.Round)
                drawLine(color, center, androidx.compose.ui.geometry.Offset(w * .34f, h * .50f), stroke.width, StrokeCap.Round)
            }
            SettingsGlyph.GROUP -> {
                drawCircle(color, radius = w * .12f, center = androidx.compose.ui.geometry.Offset(w * .40f, h * .40f), style = stroke)
                drawCircle(color, radius = w * .10f, center = androidx.compose.ui.geometry.Offset(w * .68f, h * .44f), style = stroke)
                drawArc(color, 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(w * .18f, h * .40f), size = androidx.compose.ui.geometry.Size(w * .45f, h * .42f), style = stroke)
                drawArc(color, 210f, 120f, false, topLeft = androidx.compose.ui.geometry.Offset(w * .49f, h * .46f), size = androidx.compose.ui.geometry.Size(w * .34f, h * .32f), style = stroke)
            }
            SettingsGlyph.FINANCIAL -> {
                drawCircle(color, radius = w * .34f, center = center, style = stroke)
                drawCircle(color, radius = w * .11f, center = androidx.compose.ui.geometry.Offset(w * .61f, h * .38f), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .24f, h * .67f), androidx.compose.ui.geometry.Offset(w * .43f, h * .49f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .43f, h * .49f), androidx.compose.ui.geometry.Offset(w * .54f, h * .61f), stroke.width, StrokeCap.Round)
            }
            SettingsGlyph.LIST, SettingsGlyph.SERVER -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .12f, h * .18f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                repeat(3) { index ->
                    val y = h * (.32f + index * .18f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(w * .24f, y), androidx.compose.ui.geometry.Offset(w * .72f, y), stroke.width, StrokeCap.Round)
                }
            }
            SettingsGlyph.KEY -> {
                drawCircle(color, radius = w * .14f, center = androidx.compose.ui.geometry.Offset(w * .38f, h * .34f), style = Stroke(width = 3.dp.toPx()))
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .44f, h * .45f), androidx.compose.ui.geometry.Offset(w * .70f, h * .72f), 3.dp.toPx(), StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .61f, h * .63f), androidx.compose.ui.geometry.Offset(w * .72f, h * .52f), 3.dp.toPx(), StrokeCap.Round)
            }
            SettingsGlyph.BELL -> {
                drawArc(color, 190f, 160f, false, topLeft = androidx.compose.ui.geometry.Offset(w * .22f, h * .18f), size = androidx.compose.ui.geometry.Size(w * .56f, h * .58f), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .20f, h * .67f), androidx.compose.ui.geometry.Offset(w * .80f, h * .67f), stroke.width, StrokeCap.Round)
                drawCircle(color = color, radius = w * .05f, center = androidx.compose.ui.geometry.Offset(w * .50f, h * .78f))
                drawCircle(color = color, radius = w * .06f, center = androidx.compose.ui.geometry.Offset(w * .70f, h * .20f))
            }
            SettingsGlyph.BOOK -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .20f, h * .14f), size = androidx.compose.ui.geometry.Size(w * .58f, h * .72f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .28f, h * .14f), androidx.compose.ui.geometry.Offset(w * .28f, h * .86f), stroke.width)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .28f, h * .76f), androidx.compose.ui.geometry.Offset(w * .72f, h * .76f), stroke.width)
            }
            SettingsGlyph.HEART -> {
                val path = Path().apply {
                    moveTo(w * .50f, h * .82f)
                    cubicTo(w * .10f, h * .56f, w * .18f, h * .22f, w * .36f, h * .22f)
                    cubicTo(w * .45f, h * .22f, w * .49f, h * .29f, w * .50f, h * .34f)
                    cubicTo(w * .53f, h * .27f, w * .59f, h * .22f, w * .68f, h * .22f)
                    cubicTo(w * .87f, h * .22f, w * .91f, h * .55f, w * .50f, h * .82f)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

@Composable
private fun IosAccountOrderScreen(
    accounts: List<UnicomAccount>,
    settings: AppSettings,
    onMove: (UUID, Int) -> Unit,
    onBack: () -> Unit,
) {
    IosSimpleSettingsPage("自定义排序", onBack) {
        if (accounts.isEmpty()) {
            Text("暂无手机账号", color = SettingsSecondary)
        } else {
            accounts.forEachIndexed { index, account ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        account.displayName.ifBlank { displayMobileNumber(account.mobile, settings) },
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                    )
                    TextButton(onClick = { onMove(account.id, -1) }, enabled = index > 0) { Text("上移") }
                    TextButton(onClick = { onMove(account.id, 1) }, enabled = index < accounts.lastIndex) { Text("下移") }
                }
                if (index < accounts.lastIndex) IosDivider(start = 0.dp)
            }
        }
    }
}

@Composable
private fun IosBalanceGroupingScreen(
    accounts: List<UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    settings: AppSettings,
    onAddGroup: () -> Unit,
    onDeleteGroup: (UUID) -> Unit,
    onToggleMember: (UUID, UUID) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SettingsGroupedBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { IosSubpageHeader("合账号码选择", onBack) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("同一号码只能属于一个合账组；至少选择 2 个成员后参与余额/账单统一刷新。", fontSize = 12.sp, color = SettingsSecondary)
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAddGroup) { Text("新增合账组") }
                }
            }
        }
        if (groups.isEmpty()) item { Text("暂无合账组", color = SettingsSecondary, modifier = Modifier.padding(16.dp)) }
        groups.forEach { group ->
            item(key = group.id) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(group.name, fontWeight = FontWeight.SemiBold)
                                Text(if (group.memberAccountIDs.size >= 2) "已设置 ${group.memberAccountIDs.size} 个号码" else "至少选择 2 个号码", fontSize = 11.sp, color = SettingsSecondary)
                            }
                            TextButton(onClick = { onDeleteGroup(group.id) }) { Text("删除", color = SettingsDanger) }
                        }
                        accounts.forEach { account ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = account.id in group.memberAccountIDs, onCheckedChange = { onToggleMember(account.id, group.id) })
                                Text(displayMobileNumber(account.mobile, settings), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosFinancialRefreshScreen(
    accounts: List<UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    homeAccountID: UUID?,
    settings: AppSettings,
    onSetHome: (UUID?) -> Unit,
    onSetGroupDefault: (UUID?, UUID) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SettingsGroupedBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { IosSubpageHeader("余额/账单 刷新号码编辑", onBack) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("首页余额显示 / 刷新号码", fontWeight = FontWeight.SemiBold)
                    accounts.forEach { account ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = account.id == homeAccountID, onCheckedChange = { checked -> onSetHome(if (checked) account.id else null) })
                            Text(displayMobileNumber(account.mobile, settings), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        groups.filter { it.memberAccountIDs.size >= 2 }.forEach { group ->
            item(key = "financial-${group.id}") {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${group.name} · 默认财务号码", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onSetGroupDefault(null, group.id) }) { Text("自动选择") }
                        group.memberAccountIDs.mapNotNull { id -> accounts.firstOrNull { it.id == id } }.forEach { account ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = group.defaultAccountID == account.id, onCheckedChange = { checked -> onSetGroupDefault(if (checked) account.id else null, group.id) })
                                Text(displayMobileNumber(account.mobile, settings), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosInterfaceConfigurationScreen(onBack: () -> Unit) {
    IosSimpleSettingsPage("联通接口配置", onBack) {
        IosInformationRow(label = "联通客户端版本", detail = "12.15", primaryColor = Color.Black)
        IosDivider(start = 0.dp)
        Text(
            "当前 Android 网络层使用 iphone_c@12.1500。接口地址仍由已迁移网络模块统一维护，本页先与 iOS 设置入口保持一致，不创建第二套接口 authority。",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = SettingsSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun IosSimpleSettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(SettingsGroupedBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IosSubpageHeader(title, onBack)
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun IosSubpageHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 34.sp, lineHeight = 34.sp, color = Color.Black) }
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(44.dp))
    }
}
