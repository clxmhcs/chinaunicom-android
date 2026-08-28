package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.data.balance.AndroidSharedBalanceCacheStores
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshIntervalSynchronizer
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderRefreshPolicy
import kotlinx.coroutines.flow.StateFlow

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

internal fun displayMobileNumber(number: String, settings: AppSettings): String {
    val value = number.trim()
    if (!settings.hideMobileMiddleDigits || value.length < 7) return value
    return value.take(3) + "****" + value.takeLast(4)
}

internal fun displayBroadbandNumber(number: String, settings: AppSettings): String {
    val value = number.trim()
    if (!settings.hideBroadbandMiddleDigits || value.length <= 8) return value
    return value.take(4) + "****" + value.takeLast(4)
}

class SettingsRootViewModel(application: Application) : AndroidViewModel(application) {
    private val appSettingsRepository = AndroidSettingsRepositories.appSettings(application)
    private val sharedBalanceCache = AndroidSharedBalanceCacheStores.create(application)
    private val refreshRepository = AndroidSettingsRepositories.refreshLogic(
        application,
        BalanceRefreshIntervalSynchronizer(sharedBalanceCache::setRefreshIntervalMinutes),
    )

    val appSettings: StateFlow<AppSettings> = appSettingsRepository.settings
    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = refreshRepository.quotaRefreshPolicy
    val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy> = refreshRepository.balanceRefreshPolicy
    val orderRefreshPolicy: StateFlow<OrderRefreshPolicy> = refreshRepository.orderRefreshPolicy

    fun setHideMobileMiddleDigits(value: Boolean) = saveAppSettings { copy(hideMobileMiddleDigits = value) }
    fun setHideBroadbandMiddleDigits(value: Boolean) = saveAppSettings { copy(hideBroadbandMiddleDigits = value) }

    fun cycleDisplayUnit() = saveAppSettings {
        copy(
            displayUnit = when (displayUnit) {
                DisplayUnit.AUTOMATIC -> DisplayUnit.MEGABYTES
                DisplayUnit.MEGABYTES -> DisplayUnit.GIGABYTES
                DisplayUnit.GIGABYTES -> DisplayUnit.AUTOMATIC
            },
        )
    }

    fun setQuotaAutomaticRefresh(value: Boolean) {
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(automaticRefreshEnabled = value))
    }

    fun setQuotaColdLaunch(value: Boolean) {
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(refreshOnColdLaunch = value))
    }

    fun setQuotaForeground(value: Boolean) {
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(refreshOnForeground = value))
    }

    fun changeQuotaInterval(deltaMinutes: Int) {
        val current = quotaRefreshPolicy.value
        refreshRepository.saveQuotaRefreshPolicy(
            current.copy(minimumIntervalMinutes = (current.minimumIntervalMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )
    }

    fun setBalanceAutomaticRefresh(value: Boolean) {
        refreshRepository.saveBalanceRefreshPolicy(balanceRefreshPolicy.value.copy(automaticRefreshEnabled = value))
    }

    fun setBalanceForeground(value: Boolean) {
        refreshRepository.saveBalanceRefreshPolicy(balanceRefreshPolicy.value.copy(checkOnForeground = value))
    }

    fun changeBalanceInterval(deltaMinutes: Int) {
        val current = balanceRefreshPolicy.value
        refreshRepository.saveBalanceRefreshPolicy(
            current.copy(intervalMinutes = (current.intervalMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )
    }

    fun setOrderRefreshOnEntry(value: Boolean) {
        refreshRepository.saveOrderRefreshPolicy(orderRefreshPolicy.value.copy(refreshOnEntry = value))
    }

    private inline fun saveAppSettings(transform: AppSettings.() -> AppSettings) {
        appSettingsRepository.save(appSettings.value.transform())
    }
}

@Composable
fun SettingsRootScreen(
    settingsViewModel: SettingsRootViewModel = viewModel(),
    onOpenCredentials: () -> Unit,
    onOpenRefreshLogic: () -> Unit,
) {
    val settings by settingsViewModel.appSettings.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            SettingsSection("显示") {
                SettingsSwitchRow("隐藏手机号中间四位", settings.hideMobileMiddleDigits, settingsViewModel::setHideMobileMiddleDigits)
                SettingsSwitchRow("宽带号码只显示前后4位", settings.hideBroadbandMiddleDigits, settingsViewModel::setHideBroadbandMiddleDigits)
                SettingsActionRow("流量单位", settings.displayUnit.title, settingsViewModel::cycleDisplayUnit)
                SettingsDeferredRow("自定义排序", "M11-B")
                SettingsDeferredRow("号码归属纠正", "M11-B")
            }
        }
        item {
            SettingsSection("桌面组件") {
                SettingsDeferredRow("单号码组件信息编辑", "配置 M11-B / 组件 M12")
                SettingsDeferredRow("双号码组件信息编辑", "配置 M11-B / 组件 M12")
                SettingsDeferredRow("组件刷新编辑", "配置 M11-B / 组件 M12")
            }
        }
        item {
            SettingsSection("数据刷新") {
                SettingsActionRow("App刷新逻辑编辑", "已接入 schema-3 authority", onOpenRefreshLogic)
                SettingsDeferredRow("每日用量基准", "M11-B")
                SettingsDeferredRow("合账号码选择", "M11-B")
                SettingsDeferredRow("余额/账单 刷新号码编辑", "M11-B")
            }
        }
        item {
            SettingsSection("运营商号段") {
                SettingsDeferredRow("号段更新 / 已保存号段", "M11-B")
            }
        }
        item {
            SettingsSection("数据与安全") {
                SettingsActionRow("凭据信息新增 / 编辑", "本机 CredentialStore", onOpenCredentials)
                SettingsDeferredRow("电子受理单保存目录", "M11-B")
            }
        }
        item {
            SettingsSection("工具") {
                SettingsDeferredRow("快捷指令余量通知", "M11-B")
                SettingsDeferredRow("抓包工具", "入口 M11 / 主体 M14")
            }
        }
        item {
            SettingsSection("关于") {
                SettingsDeferredRow("App使用说明书", "M11-B")
                Text("Android 最低支持版本：Android 11 / API 30", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AppRefreshLogicSettingsScreen(
    settingsViewModel: SettingsRootViewModel,
    onBack: () -> Unit,
) {
    val quota by settingsViewModel.quotaRefreshPolicy.collectAsState()
    val balance by settingsViewModel.balanceRefreshPolicy.collectAsState()
    val orders by settingsViewModel.orderRefreshPolicy.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("App刷新逻辑", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            SettingsSection("首页与基础数据 · 流量/语音") {
                SettingsSwitchRow("启用自动刷新", quota.automaticRefreshEnabled, settingsViewModel::setQuotaAutomaticRefresh)
                SettingsSwitchRow("冷启动时检查", quota.refreshOnColdLaunch, settingsViewModel::setQuotaColdLaunch)
                SettingsSwitchRow("回到前台时检查", quota.refreshOnForeground, settingsViewModel::setQuotaForeground)
                IntervalEditor("最小刷新间隔", quota.minimumIntervalMinutes, 5, settingsViewModel::changeQuotaInterval)
            }
        }
        item {
            SettingsSection("余额") {
                SettingsSwitchRow("启用自动刷新", balance.automaticRefreshEnabled, settingsViewModel::setBalanceAutomaticRefresh)
                SettingsSwitchRow("回到前台时检查", balance.checkOnForeground, settingsViewModel::setBalanceForeground)
                IntervalEditor("刷新间隔", balance.intervalMinutes, 15, settingsViewModel::changeBalanceInterval)
                Text("失败重试：${balance.failureRetryMinutes} 分钟", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsSection("我的订单") {
                SettingsSwitchRow("进入页面自动刷新", orders.refreshOnEntry, settingsViewModel::setOrderRefreshOnEntry)
            }
        }
        item {
            SettingsSection("其它业务") {
                Text("已订业务、我的套餐、话费/账单、积分、返费/赠费、视频彩铃仍使用现有统一 schema-3 authority；详细编辑控件在 M11-B 补齐。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable Column.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(label: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label)
            Text(detail, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsDeferredRow(label: String, stage: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(stage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntervalEditor(label: String, minutes: Int, step: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text("$minutes 分钟", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { onChange(-step) }) { Text("-$step") }
        TextButton(onClick = { onChange(step) }) { Text("+$step") }
    }
}
