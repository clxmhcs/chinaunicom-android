package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.BuildConfig
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceAccountGroup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val SettingsIosBlue = Color(0xFF3478F6)
private val SettingsIosSecondary = Color(0xFF8E8E93)
private val SettingsIosSeparator = Color(0xFFD1D1D6).copy(alpha = 0.62f)
private val SettingsIosRed = Color(0xFFFF3B30)
private val SettingsIosCard = Color(0xFFFFFFFF)
private val SettingsIosBackground = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF2F7FF),
        Color(0xFFF2F2F7),
        Color(0xFFF2F2F7),
    ),
)
private const val SETTINGS_UNICOM_CLIENT_VERSION = "12.15"

private enum class SettingsVisualPage {
    ACCOUNT_ORDER,
    CARRIER_CORRECTION,
    WIDGET_SINGLE,
    WIDGET_DUAL,
    WIDGET_REFRESH,
    DAILY_BASELINE,
    BALANCE_GROUPING,
    FINANCIAL_REFRESH,
    PHONE_SEGMENTS,
    INTERFACE_CONFIG,
    SHORTCUT_NOTIFICATION,
    APP_MANUAL,
    CLEAR_ACCOUNTS,
}

/**
 * Root settings page styled from the current iOS SettingsView.
 * Business/storage authority remains in the existing Android view-models and repositories.
 */
@Composable
fun SettingsRootVisualScreen(
    settingsViewModel: SettingsRootViewModel = viewModel(),
    onOpenCredentials: () -> Unit,
    onOpenRefreshLogic: () -> Unit,
) {
    val settings by settingsViewModel.appSettings.collectAsState()
    val appState by settingsViewModel.appState.collectAsState()
    val balanceState by settingsViewModel.balanceState.collectAsState()
    val accounts = appState.accounts.sortedBy { it.sortOrder }

    var maintenanceGeneration by remember { mutableStateOf(0) }
    var clearSession by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<SettingsVisualPage?>(null) }
    var showingDonationMessage by remember { mutableStateOf(false) }

    val m11cViewModel: SettingsM11CViewModel = viewModel(key = "settings-visual-m11c-$maintenanceGeneration")
    val broadbandViewModel: BroadbandAccountViewModel = viewModel()
    val broadbandState by broadbandViewModel.state.collectAsState()
    val attributionState by m11cViewModel.attributionState.collectAsState()

    LaunchedEffect(accounts.map { it.id }, maintenanceGeneration) {
        m11cViewModel.reconcileMobileAccounts(accounts.map { it.id }.toSet())
    }

    val closePage = { page = null }
    when (page) {
        SettingsVisualPage.ACCOUNT_ORDER -> {
            IosAccountOrderScreen(
                accounts = accounts,
                settings = settings,
                onMove = settingsViewModel::moveAccount,
                onBack = closePage,
            )
            return
        }
        SettingsVisualPage.CARRIER_CORRECTION -> {
            CarrierCorrectionSettingsScreen(accounts, broadbandState.accounts, m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.WIDGET_SINGLE -> {
            SingleWidgetSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.WIDGET_DUAL -> {
            DualWidgetSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.WIDGET_REFRESH -> {
            WidgetRefreshSettingsScreen(m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.DAILY_BASELINE -> {
            DailyUsageBaselineSettingsScreen(accounts, m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.BALANCE_GROUPING -> {
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
        SettingsVisualPage.FINANCIAL_REFRESH -> {
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
        SettingsVisualPage.PHONE_SEGMENTS -> {
            PhoneSegmentSettingsScreen(m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.INTERFACE_CONFIG -> {
            IosInterfaceConfigurationSummaryScreen(closePage)
            return
        }
        SettingsVisualPage.SHORTCUT_NOTIFICATION -> {
            ShortcutNotificationSettingsScreen(accounts.filter { it.isEnabled }, m11cViewModel, closePage)
            return
        }
        SettingsVisualPage.APP_MANUAL -> {
            AppManualScreen(closePage)
            return
        }
        SettingsVisualPage.CLEAR_ACCOUNTS -> {
            SettingsClearAccountsScreen(
                viewModel = viewModel(key = "settings-visual-clear-$clearSession"),
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
    val latestSegmentText = latestSegmentUpdate?.let(SettingsSegmentDateFormatter::format)
    val compactAppVersion = "${BuildConfig.VERSION_NAME.substringBefore('-')} (${BuildConfig.VERSION_CODE})"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsIosBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "设置",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, top = 2.dp, bottom = 16.dp),
                    fontSize = 36.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }

            item {
                IosSettingsSection("显示") {
                    IosSwitchRow("隐藏手机号中间四位", settings.hideMobileMiddleDigits, settingsViewModel::setHideMobileMiddleDigits)
                    IosSettingsDivider()
                    IosSwitchRow("宽带号码只显示前后4位", settings.hideBroadbandMiddleDigits, settingsViewModel::setHideBroadbandMiddleDigits)
                    IosSettingsDivider()
                    IosPickerRow("流量单位", settings.displayUnit.title, settingsViewModel::cycleDisplayUnit)
                    IosSettingsDivider()
                    IosActionRow(
                        label = "自定义排序",
                        glyph = SettingsIosGlyph.SORT,
                        enabled = accounts.size >= 2,
                        onClick = { page = SettingsVisualPage.ACCOUNT_ORDER },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "号码归属纠正",
                        glyph = SettingsIosGlyph.CORRECTION,
                        trailingText = attributionState.corrections.size.takeIf { it > 0 }?.let { "已纠正 $it" },
                        enabled = accounts.isNotEmpty() || broadbandState.accounts.isNotEmpty(),
                        onClick = { page = SettingsVisualPage.CARRIER_CORRECTION },
                    )
                }
            }

            item {
                IosSettingsSection("桌面组件") {
                    IosActionRow(
                        label = "单号码组件信息编辑",
                        glyph = SettingsIosGlyph.WIDGET_SINGLE,
                        enabled = accounts.isNotEmpty(),
                        onClick = { page = SettingsVisualPage.WIDGET_SINGLE },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "双号码组件信息编辑",
                        glyph = SettingsIosGlyph.WIDGET_DUAL,
                        enabled = accounts.size >= 2,
                        onClick = { page = SettingsVisualPage.WIDGET_DUAL },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "组件刷新编辑",
                        glyph = SettingsIosGlyph.REFRESH,
                        onClick = { page = SettingsVisualPage.WIDGET_REFRESH },
                    )
                }
            }

            item {
                IosSettingsSection("数据刷新") {
                    IosActionRow(
                        label = "App刷新逻辑编辑",
                        glyph = SettingsIosGlyph.REFRESH,
                        onClick = onOpenRefreshLogic,
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "每日用量基准",
                        glyph = SettingsIosGlyph.CLOCK,
                        enabled = accounts.isNotEmpty(),
                        onClick = { page = SettingsVisualPage.DAILY_BASELINE },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "合账号码选择",
                        glyph = SettingsIosGlyph.GROUP,
                        trailingText = if (activeBalanceGroups == 0) "未设置" else "已设置 $activeBalanceGroups 组",
                        enabled = accounts.size >= 2,
                        onClick = { page = SettingsVisualPage.BALANCE_GROUPING },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "余额/账单 刷新号码编辑",
                        glyph = SettingsIosGlyph.FINANCIAL,
                        enabled = accounts.any { it.isEnabled },
                        onClick = { page = SettingsVisualPage.FINANCIAL_REFRESH },
                    )
                }
            }

            item {
                IosSettingsSection("运营商号段") {
                    IosActionRow(
                        label = "号段更新",
                        glyph = SettingsIosGlyph.SEGMENTS,
                        trailingText = latestSegmentText,
                        trailingColor = SettingsIosBlue.copy(alpha = 0.50f),
                        showChevron = false,
                        trailingProgress = attributionState.isUpdatingSegments,
                        enabled = !attributionState.isUpdatingSegments,
                        onClick = m11cViewModel::updatePhoneSegments,
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "已保存号段",
                        glyph = SettingsIosGlyph.LIST,
                        trailingText = attributionState.segments.size.toString(),
                        trailingColor = SettingsIosBlue.copy(alpha = 0.58f),
                        onClick = { page = SettingsVisualPage.PHONE_SEGMENTS },
                    )
                }
            }

            item {
                IosSettingsSection("数据与安全", showQuestion = true) {
                    IosCredentialsRow(
                        savedCredentialCount = savedCredentialCount,
                        onClick = onOpenCredentials,
                    )
                    IosSettingsDivider(start = 18.dp)
                    IosInfoRow(
                        text = "手动凭据存储在本机 [密钥库] 中",
                        glyph = SettingsIosGlyph.KEY,
                    )
                }
            }

            item {
                IosSettingsSection("网络与接口") {
                    IosActionRow(
                        label = "联通接口配置",
                        glyph = SettingsIosGlyph.SERVER,
                        trailingText = SETTINGS_UNICOM_CLIENT_VERSION,
                        onClick = { page = SettingsVisualPage.INTERFACE_CONFIG },
                    )
                }
            }

            item {
                IosSettingsSection("工具") {
                    IosActionRow(
                        label = "快捷指令余量通知",
                        glyph = SettingsIosGlyph.BELL,
                        onClick = { page = SettingsVisualPage.SHORTCUT_NOTIFICATION },
                    )
                }
            }

            item {
                IosSettingsSection("关于") {
                    IosActionRow(
                        label = "App使用说明书",
                        glyph = SettingsIosGlyph.BOOK,
                        onClick = { page = SettingsVisualPage.APP_MANUAL },
                    )
                    IosSettingsDivider(start = 64.dp)
                    IosActionRow(
                        label = "我要打赏",
                        glyph = SettingsIosGlyph.HEART,
                        labelColor = SettingsIosRed,
                        glyphColor = SettingsIosRed,
                        showChevron = false,
                        onClick = { showingDonationMessage = true },
                    )
                    IosSettingsDivider(start = 18.dp)
                    IosStaticValueRow("版本、版权", compactAppVersion)
                    IosSettingsDivider(start = 18.dp)
                    Text(
                        text = "©️夢幻傳說 - Gmail：clxmhcs",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = SettingsIosSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = {
                            clearSession += 1
                            page = SettingsVisualPage.CLEAR_ACCOUNTS
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = SettingsIosRed.copy(alpha = 0.075f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsIosRed.copy(alpha = 0.28f)),
                    ) {
                        Text(
                            text = "清空账户与凭据",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SettingsIosRed,
                        )
                    }
                }
            }
        }

        // iOS SettingsPageWatermark: the original knot asset is drawn above Form content,
        // fixed at 380×380 pt with 0.027 opacity and no tint.
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(380.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.027f,
        )
    }

    if (showingDonationMessage) {
        AlertDialog(
            onDismissRequest = { showingDonationMessage = false },
            title = { Text("感谢支持") },
            text = { Text("感谢你对联通余量项目的支持。") },
            confirmButton = {
                TextButton(onClick = { showingDonationMessage = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun IosSettingsSection(
    title: String,
    showQuestion: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = SettingsIosSecondary,
            )
            if (showQuestion) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                    color = SettingsIosSecondary.copy(alpha = 0.66f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = SettingsIosCard.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun IosSettingsDivider(start: androidx.compose.ui.unit.Dp = 18.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 18.dp),
        thickness = 0.65.dp,
        color = SettingsIosSeparator,
    )
}

@Composable
private fun IosSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = Color.Black,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsIosBlue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE9E9EA),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun IosPickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, lineHeight = 21.sp, color = Color.Black)
        Text(value, fontSize = 16.sp, lineHeight = 21.sp, color = SettingsIosBlue)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(width = 9.dp, height = 18.dp)) {
            val stroke = 1.7.dp.toPx()
            drawLine(SettingsIosBlue, Offset(size.width * 0.18f, size.height * 0.36f), Offset(size.width * 0.50f, size.height * 0.18f), stroke, StrokeCap.Round)
            drawLine(SettingsIosBlue, Offset(size.width * 0.50f, size.height * 0.18f), Offset(size.width * 0.82f, size.height * 0.36f), stroke, StrokeCap.Round)
            drawLine(SettingsIosBlue, Offset(size.width * 0.18f, size.height * 0.64f), Offset(size.width * 0.50f, size.height * 0.82f), stroke, StrokeCap.Round)
            drawLine(SettingsIosBlue, Offset(size.width * 0.50f, size.height * 0.82f), Offset(size.width * 0.82f, size.height * 0.64f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun IosActionRow(
    label: String,
    glyph: SettingsIosGlyph? = null,
    trailingText: String? = null,
    trailingColor: Color = SettingsIosSecondary,
    labelColor: Color = Color.Black,
    glyphColor: Color = SettingsIosBlue,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailingProgress: Boolean = false,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.34f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            SettingsIosGlyphView(glyph = glyph, color = glyphColor.copy(alpha = contentAlpha))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = labelColor.copy(alpha = contentAlpha),
        )
        if (trailingProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = SettingsIosBlue,
            )
        } else if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = trailingColor.copy(alpha = contentAlpha),
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "›",
                fontSize = 29.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFC7C7CC).copy(alpha = contentAlpha),
            )
        }
    }
}

@Composable
private fun IosCredentialsRow(savedCredentialCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "凭据信息新增 / 编辑",
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = Color.Black,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已保存 ", fontSize = 12.5.sp, color = SettingsIosSecondary)
            Text(savedCredentialCount.toString(), fontSize = 12.5.sp, color = SettingsIosRed)
            Text(" 个号码凭据", fontSize = 12.5.sp, color = SettingsIosSecondary)
        }
        Spacer(Modifier.width(7.dp))
        Text("›", fontSize = 29.sp, lineHeight = 29.sp, fontWeight = FontWeight.Light, color = Color(0xFFC7C7CC))
    }
}

@Composable
private fun IosInfoRow(text: String, glyph: SettingsIosGlyph) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIosGlyphView(glyph = glyph, color = SettingsIosSecondary)
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = SettingsIosSecondary,
        )
    }
}

@Composable
private fun IosStaticValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, lineHeight = 21.sp, color = Color.Black)
        Text(value, fontSize = 15.sp, lineHeight = 20.sp, color = SettingsIosSecondary)
    }
}

@Composable
private fun IosSubpageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsIosBackground),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回", color = SettingsIosBlue) }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Spacer(Modifier.width(64.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Column(content = content) }
            }
        }
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(380.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.027f,
        )
    }
}

@Composable
private fun IosAccountOrderScreen(
    accounts: List<UnicomAccount>,
    settings: AppSettings,
    onMove: (UUID, Int) -> Unit,
    onBack: () -> Unit,
) {
    IosSubpageScaffold("自定义排序", onBack) {
        IosSettingsSection("账户卡片排序") {
            if (accounts.isEmpty()) {
                Text("暂无手机账号", modifier = Modifier.padding(18.dp), color = SettingsIosSecondary)
            } else {
                accounts.forEachIndexed { index, account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            displayMobileNumber(account.mobile, settings),
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                        )
                        TextButton(enabled = index > 0, onClick = { onMove(account.id, -1) }) { Text("上移") }
                        TextButton(enabled = index < accounts.lastIndex, onClick = { onMove(account.id, 1) }) { Text("下移") }
                    }
                    if (index < accounts.lastIndex) IosSettingsDivider(start = 18.dp)
                }
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
    IosSubpageScaffold("合账号码选择", onBack) {
        IosSettingsSection("合账号码") {
            Text(
                "同一号码只能属于一个合账组；至少 2 个成员时该组才参与统一余额/账单代表规则。",
                modifier = Modifier.padding(18.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = SettingsIosSecondary,
            )
            IosSettingsDivider()
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onAddGroup) { Text("新增合账组") }
        }
        Spacer(Modifier.height(14.dp))
        if (groups.isEmpty()) {
            IosSettingsSection("已保存") {
                Text("暂无合账组", modifier = Modifier.padding(18.dp), color = SettingsIosSecondary)
            }
        } else {
            groups.forEach { group ->
                IosSettingsSection(group.name) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (group.memberAccountIDs.size >= 2) "有效 · ${group.memberAccountIDs.size} 个号码" else "未生效 · 至少选择 2 个号码",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = SettingsIosSecondary,
                        )
                        TextButton(onClick = { onDeleteGroup(group.id) }) { Text("删除", color = SettingsIosRed) }
                    }
                    accounts.forEach { account ->
                        IosSettingsDivider()
                        IosSwitchRow(
                            displayMobileNumber(account.mobile, settings),
                            account.id in group.memberAccountIDs,
                        ) { onToggleMember(account.id, group.id) }
                    }
                }
                Spacer(Modifier.height(14.dp))
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
    IosSubpageScaffold("余额/账单 刷新号码编辑", onBack) {
        IosSettingsSection("首页余额显示 / 刷新号码") {
            accounts.forEachIndexed { index, account ->
                IosActionRow(
                    label = displayMobileNumber(account.mobile, settings),
                    trailingText = if (account.id == homeAccountID) "当前" else null,
                    showChevron = false,
                    onClick = { onSetHome(account.id) },
                )
                if (index < accounts.lastIndex) IosSettingsDivider()
            }
            if (homeAccountID != null) {
                if (accounts.isNotEmpty()) IosSettingsDivider()
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { onSetHome(null) }) { Text("取消首页余额号码") }
            }
        }
        groups.filter { it.memberAccountIDs.size >= 2 }.forEach { group ->
            Spacer(Modifier.height(14.dp))
            IosSettingsSection("${group.name} · 默认财务号码") {
                IosActionRow(
                    label = "自动选择",
                    trailingText = if (group.defaultAccountID == null) "当前" else null,
                    showChevron = false,
                    onClick = { onSetGroupDefault(null, group.id) },
                )
                group.memberAccountIDs.mapNotNull { id -> accounts.firstOrNull { it.id == id } }.forEach { account ->
                    IosSettingsDivider()
                    IosActionRow(
                        label = displayMobileNumber(account.mobile, settings),
                        trailingText = if (group.defaultAccountID == account.id) "当前" else null,
                        showChevron = false,
                        onClick = { onSetGroupDefault(account.id, group.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IosInterfaceConfigurationSummaryScreen(onBack: () -> Unit) {
    IosSubpageScaffold("联通接口配置", onBack) {
        IosSettingsSection("联通客户端") {
            IosStaticValueRow("联通客户端版本", SETTINGS_UNICOM_CLIENT_VERSION)
        }
        Spacer(Modifier.height(14.dp))
        IosSettingsSection("接口地址") {
            Text(
                text = "Android 当前业务接口仍由 M4 网络层统一管理；此入口先与 iOS 设置首页保持一致，后续继续迁移分组 URL 编辑与恢复默认值。",
                modifier = Modifier.padding(18.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = SettingsIosSecondary,
            )
        }
    }
}

private val SettingsSegmentDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
