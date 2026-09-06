package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.data.AndroidDashboardRefreshSettings
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy

private val QuotaPageBackground = Color(0xFFF2F2F7)
private val QuotaSecondary = Color(0xFF8E8E93)
private val QuotaSeparator = Color(0xFFE5E5EA)
private val QuotaTertiary = Color(0xFFC7C7CC)
private val QuotaAccent = Color(0xFF3478F6)

/**
 * Current-iOS parity for AppRefreshLogicSettingsView -> QuotaRefreshLogicEditorView.
 *
 * The schema-3 quota policy still owns automatic/cold-launch/foreground/minimum/gap settings.
 * Dashboard-only manual/failure timing remains a separate app-private authority, matching iOS.
 */
@Composable
internal fun QuotaRefreshDraftEditor(
    policy: QuotaRefreshPolicy,
    onChange: (QuotaRefreshPolicy) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    hasUnsavedChanges: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val dashboardSettings = remember(context) { AndroidDashboardRefreshSettings(context) }
    val loadedTiming = remember(dashboardSettings) { dashboardSettings.load() }

    var singleManualMinutes by remember { mutableStateOf(loadedTiming.singleManualIntervalMinutes) }
    var savedSingleManualMinutes by remember { mutableStateOf(loadedTiming.singleManualIntervalMinutes) }
    var globalManualMinutes by remember { mutableStateOf(loadedTiming.globalManualIntervalMinutes) }
    var savedGlobalManualMinutes by remember { mutableStateOf(loadedTiming.globalManualIntervalMinutes) }
    var failureRetryMinutes by remember { mutableStateOf(loadedTiming.automaticFailureRetryMinutes) }
    var savedFailureRetryMinutes by remember { mutableStateOf(loadedTiming.automaticFailureRetryMinutes) }

    val timingChanged = singleManualMinutes != savedSingleManualMinutes ||
        globalManualMinutes != savedGlobalManualMinutes ||
        failureRetryMinutes != savedFailureRetryMinutes
    val saveEnabled = hasUnsavedChanges || timingChanged

    fun saveAll() {
        onSave()
        val persisted = dashboardSettings.saveSingleManualIntervalMinutes(singleManualMinutes) &&
            dashboardSettings.saveGlobalManualIntervalMinutes(globalManualMinutes) &&
            dashboardSettings.saveAutomaticFailureRetryMinutes(failureRetryMinutes)
        if (persisted) {
            val normalized = dashboardSettings.load()
            singleManualMinutes = normalized.singleManualIntervalMinutes
            savedSingleManualMinutes = normalized.singleManualIntervalMinutes
            globalManualMinutes = normalized.globalManualIntervalMinutes
            savedGlobalManualMinutes = normalized.globalManualIntervalMinutes
            failureRetryMinutes = normalized.automaticFailureRetryMinutes
            savedFailureRetryMinutes = normalized.automaticFailureRetryMinutes
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(QuotaPageBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            QuotaPageHeader(
                title = "首页余量",
                onBack = onBack,
                onSave = ::saveAll,
                saveEnabled = saveEnabled,
            )
            Spacer(Modifier.height(23.dp))
        }

        item {
            QuotaSectionTitle("自动刷新")
            QuotaCard {
                QuotaSwitchRow(
                    label = "启用自动刷新",
                    checked = policy.automaticRefreshEnabled,
                    onChange = { onChange(policy.copy(automaticRefreshEnabled = it)) },
                )
                QuotaDivider()
                QuotaSwitchRow(
                    label = "App 冷启动时检查",
                    checked = policy.refreshOnColdLaunch,
                    enabled = policy.automaticRefreshEnabled,
                    onChange = { onChange(policy.copy(refreshOnColdLaunch = it)) },
                )
                QuotaDivider()
                QuotaSwitchRow(
                    label = "重新进入前台时检查",
                    checked = policy.refreshOnForeground,
                    enabled = policy.automaticRefreshEnabled,
                    onChange = { onChange(policy.copy(refreshOnForeground = it)) },
                )
                QuotaDivider()
                QuotaPickerRow(
                    label = "最短刷新间隔",
                    value = quotaMinutesText(policy.minimumIntervalMinutes),
                    enabled = policy.automaticRefreshEnabled,
                    choices = AndroidDashboardRefreshSettings.ALLOWED_AUTOMATIC_MINIMUM_INTERVAL_MINUTES,
                    choiceTitle = ::quotaMinutesText,
                    onSelected = { onChange(policy.copy(minimumIntervalMinutes = it)) },
                )
                QuotaDivider()
                QuotaPickerRow(
                    label = "失败后重试间隔",
                    value = quotaMinutesText(failureRetryMinutes),
                    enabled = policy.automaticRefreshEnabled,
                    choices = AndroidDashboardRefreshSettings.ALLOWED_AUTOMATIC_FAILURE_RETRY_MINUTES,
                    choiceTitle = ::quotaMinutesText,
                    onSelected = { failureRetryMinutes = it },
                )
                QuotaDivider()
                QuotaPickerRow(
                    label = "账号刷新间隔",
                    value = "${policy.accountGapSeconds}秒",
                    choices = AndroidDashboardRefreshSettings.ALLOWED_ACCOUNT_GAP_SECONDS,
                    choiceTitle = { "${it}秒" },
                    onSelected = { onChange(policy.copy(accountGapSeconds = it)) },
                )
            }
            QuotaSectionFooter(
                "自动刷新按号码分别计时：每个号码都从自己的最近一次成功刷新开始计算。单号码手动、刷新全部或自动刷新只要该号码成功，就只重置该号码的自动刷新时间；某号码失败也只限制该号码下一次自动尝试。",
            )
            Spacer(Modifier.height(22.dp))
        }

        item {
            QuotaSectionTitle("手动刷新")
            QuotaCard {
                QuotaPickerRow(
                    label = "单号码手动刷新间隔",
                    value = quotaMinutesText(singleManualMinutes),
                    choices = AndroidDashboardRefreshSettings.ALLOWED_SINGLE_MANUAL_INTERVAL_MINUTES,
                    choiceTitle = ::quotaMinutesText,
                    onSelected = { singleManualMinutes = it },
                )
                QuotaDivider()
                QuotaPickerRow(
                    label = "全局手动刷新间隔",
                    value = quotaMinutesText(globalManualMinutes),
                    choices = AndroidDashboardRefreshSettings.ALLOWED_GLOBAL_MANUAL_INTERVAL_MINUTES,
                    choiceTitle = ::quotaMinutesText,
                    onSelected = { globalManualMinutes = it },
                )
            }
            QuotaSectionFooter(
                "单号码只受该号码自身间隔限制。全局手动刷新会逐个检查每个启用号码自己的全局手动成功时间，只刷新已到间隔的号码；单号码成功只重置该号码的“刷新全部”和自动刷新时间。任何号码失败或取消都不会影响其它号码已经保存的成功时间。",
            )
            Spacer(Modifier.height(22.dp))
        }

        item {
            QuotaSectionTitle("固定数据关系")
            QuotaCard {
                QuotaValueRow("刷新内容", "流量 + 语音 + 余量详情")
                QuotaDivider()
                Text(
                    "同一份套餐响应继续同时更新流量、语音和完整余量快照，不允许拆成多个独立网络请求。",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    color = QuotaSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            QuotaCard {
                QuotaValueRow(
                    label = "设置状态",
                    value = if (saveEnabled) "有未保存修改" else "已保存",
                )
            }
            QuotaSectionFooter("保存后，仅对应业务重新判定刷新规则；其它业务不会因本项设置变化而额外联网。")
        }
    }
}

@Composable
private fun QuotaPageHeader(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = Color.White,
        ) {
            Box(
                modifier = Modifier.clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                QuotaBackChevron()
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Color.Black,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.width(68.dp).height(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
        ) {
            Box(
                modifier = Modifier.clickable(enabled = saveEnabled, onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "保存",
                    color = if (saveEnabled) QuotaAccent else QuotaTertiary,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun QuotaBackChevron() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.7.dp.toPx()
        drawLine(
            Color(0xFF1C1C1E),
            androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .18f),
            androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .50f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            Color(0xFF1C1C1E),
            androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .50f),
            androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .82f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun QuotaSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 18.dp, bottom = 9.dp),
        color = QuotaSecondary,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun QuotaSectionFooter(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 9.dp),
        color = QuotaSecondary,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun QuotaCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun QuotaSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) Color.Black else QuotaTertiary,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = QuotaAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE5E5EA),
                uncheckedBorderColor = Color(0xFFD1D1D6),
                disabledCheckedThumbColor = Color.White.copy(alpha = .72f),
                disabledCheckedTrackColor = QuotaAccent.copy(alpha = .28f),
                disabledUncheckedThumbColor = Color.White.copy(alpha = .72f),
                disabledUncheckedTrackColor = Color(0xFFE5E5EA).copy(alpha = .55f),
            ),
        )
    }
}

@Composable
private fun QuotaPickerRow(
    label: String,
    value: String,
    choices: List<Int>,
    choiceTitle: (Int) -> String,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (enabled) Color.Black else QuotaTertiary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                value,
                color = if (enabled) QuotaAccent else QuotaTertiary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.width(5.dp))
            QuotaPickerChevron(enabled)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choiceTitle(choice)) },
                    onClick = {
                        expanded = false
                        onSelected(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun QuotaPickerChevron(enabled: Boolean) {
    val color = if (enabled) QuotaAccent else QuotaTertiary
    Canvas(Modifier.size(width = 14.dp, height = 18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .35f),
            androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .17f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .17f),
            androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .35f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .65f),
            androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .83f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .83f),
            androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .65f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun QuotaValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Color.Black, fontSize = 16.sp, lineHeight = 20.sp)
        Text(value, color = QuotaSecondary, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun QuotaDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = QuotaSeparator,
        thickness = .7.dp,
    )
}

private fun quotaMinutesText(value: Int): String = when {
    value % 60 == 0 -> "${value / 60}小时"
    else -> "${value}分钟"
}
