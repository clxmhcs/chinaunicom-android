package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.balance.AndroidSharedBalanceCacheStores
import com.clxmhcs.chinaunicom.data.balance.BalanceAccountGroup
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshIntervalSynchronizer
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.CachedBusinessEntryMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshCycleMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.MyPackageRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderedBusinessRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.PageEntryRefreshMode
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.RebateGiftRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.VideoRingRefreshPolicy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val unicomRepository = UnicomRepositoryProvider.create(application)
    private val accountRepository = DefaultAccountRepository(AndroidAccountMetadataStores.accounts(application))

    val appSettings: StateFlow<AppSettings> = appSettingsRepository.settings
    val appState = unicomRepository.appState
    val balanceState = unicomRepository.balanceState

    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = refreshRepository.quotaRefreshPolicy
    val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy> = refreshRepository.balanceRefreshPolicy
    val orderedBusinessRefreshPolicy: StateFlow<OrderedBusinessRefreshPolicy> = refreshRepository.orderedBusinessRefreshPolicy
    val myPackageRefreshPolicy: StateFlow<MyPackageRefreshPolicy> = refreshRepository.myPackageRefreshPolicy
    val phoneBillRefreshPolicy: StateFlow<PhoneBillRefreshPolicy> = refreshRepository.phoneBillRefreshPolicy
    val integralRefreshPolicy: StateFlow<IntegralRefreshPolicy> = refreshRepository.integralRefreshPolicy
    val orderRefreshPolicy: StateFlow<OrderRefreshPolicy> = refreshRepository.orderRefreshPolicy
    val rebateGiftRefreshPolicy: StateFlow<RebateGiftRefreshPolicy> = refreshRepository.rebateGiftRefreshPolicy
    val videoRingRefreshPolicy: StateFlow<VideoRingRefreshPolicy> = refreshRepository.videoRingRefreshPolicy

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

    fun moveAccount(accountID: UUID, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = accountRepository.loadAccounts().sortedBy { it.sortOrder }.toMutableList()
            val from = accounts.indexOfFirst { it.id == accountID }
            val to = (from + delta).coerceIn(0, accounts.lastIndex)
            if (from < 0 || from == to) return@launch
            val moved = accounts.removeAt(from)
            accounts.add(to, moved)
            accountRepository.replaceAccounts(accounts.mapIndexed { index, account -> account.copy(sortOrder = index) })
            unicomRepository.reloadAccountsFromPersistence()
        }
    }

    fun addBalanceGroup() {
        viewModelScope.launch { unicomRepository.addBalanceAccountGroup() }
    }

    fun deleteBalanceGroup(groupID: UUID) {
        viewModelScope.launch { unicomRepository.deleteBalanceAccountGroup(groupID) }
    }

    fun toggleBalanceGroupMember(accountID: UUID, groupID: UUID) {
        viewModelScope.launch { unicomRepository.toggleBalanceAccount(accountID, groupID) }
    }

    fun setHomeBalanceAccount(accountID: UUID?) = unicomRepository.setHomeBalanceAccountID(accountID)

    fun setDefaultFinancialAccount(accountID: UUID?, groupID: UUID) =
        unicomRepository.setDefaultFinancialAccountID(accountID, groupID)

    fun setQuotaAutomaticRefresh(value: Boolean) =
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(automaticRefreshEnabled = value))

    fun setQuotaColdLaunch(value: Boolean) =
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(refreshOnColdLaunch = value))

    fun setQuotaForeground(value: Boolean) =
        refreshRepository.saveQuotaRefreshPolicy(quotaRefreshPolicy.value.copy(refreshOnForeground = value))

    fun changeQuotaInterval(deltaMinutes: Int) =
        refreshRepository.saveQuotaRefreshPolicy(
            quotaRefreshPolicy.value.copy(
                minimumIntervalMinutes = (quotaRefreshPolicy.value.minimumIntervalMinutes + deltaMinutes).coerceIn(1, 24 * 60),
            ),
        )

    fun changeQuotaAccountGap(deltaSeconds: Int) =
        refreshRepository.saveQuotaRefreshPolicy(
            quotaRefreshPolicy.value.copy(accountGapSeconds = (quotaRefreshPolicy.value.accountGapSeconds + deltaSeconds).coerceIn(0, 60)),
        )

    fun setBalanceAutomaticRefresh(value: Boolean) =
        refreshRepository.saveBalanceRefreshPolicy(balanceRefreshPolicy.value.copy(automaticRefreshEnabled = value))

    fun setBalanceForeground(value: Boolean) =
        refreshRepository.saveBalanceRefreshPolicy(balanceRefreshPolicy.value.copy(checkOnForeground = value))

    fun changeBalanceInterval(deltaMinutes: Int) =
        refreshRepository.saveBalanceRefreshPolicy(
            balanceRefreshPolicy.value.copy(intervalMinutes = (balanceRefreshPolicy.value.intervalMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )

    fun changeBalanceFailureRetry(deltaMinutes: Int) =
        refreshRepository.saveBalanceRefreshPolicy(
            balanceRefreshPolicy.value.copy(failureRetryMinutes = (balanceRefreshPolicy.value.failureRetryMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )

    fun cycleOrderedBusinessEntryMode() =
        refreshRepository.saveOrderedBusinessRefreshPolicy(
            orderedBusinessRefreshPolicy.value.copy(entryMode = nextCachedMode(orderedBusinessRefreshPolicy.value.entryMode)),
        )

    fun changeOrderedBusinessCache(deltaHours: Int) =
        refreshRepository.saveOrderedBusinessRefreshPolicy(
            orderedBusinessRefreshPolicy.value.copy(cacheValidityHours = (orderedBusinessRefreshPolicy.value.cacheValidityHours + deltaHours).coerceIn(1, 24 * 30)),
        )

    fun setOrderedBusinessNoCacheQuery(value: Boolean) =
        refreshRepository.saveOrderedBusinessRefreshPolicy(orderedBusinessRefreshPolicy.value.copy(noCacheAutoQuery = value))

    fun changeOrderedBusinessGap(deltaSeconds: Int) =
        refreshRepository.saveOrderedBusinessRefreshPolicy(
            orderedBusinessRefreshPolicy.value.copy(
                refreshAllAccountGapSeconds = (orderedBusinessRefreshPolicy.value.refreshAllAccountGapSeconds + deltaSeconds).coerceIn(0, 60),
            ),
        )

    fun cycleMyPackageEntryMode() =
        refreshRepository.saveMyPackageRefreshPolicy(
            myPackageRefreshPolicy.value.copy(entryMode = nextPageEntryMode(myPackageRefreshPolicy.value.entryMode)),
        )

    fun changeMyPackageCache(deltaMinutes: Int) =
        refreshRepository.saveMyPackageRefreshPolicy(
            myPackageRefreshPolicy.value.copy(cacheValidityMinutes = (myPackageRefreshPolicy.value.cacheValidityMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )

    fun changePhoneBillCurrentCache(deltaMinutes: Int) =
        refreshRepository.savePhoneBillRefreshPolicy(
            phoneBillRefreshPolicy.value.copy(
                currentMonthCacheMinutes = (phoneBillRefreshPolicy.value.currentMonthCacheMinutes + deltaMinutes).coerceIn(1, 24 * 60),
            ),
        )

    fun changePhoneBillHistoricalCache(deltaDays: Int) =
        refreshRepository.savePhoneBillRefreshPolicy(
            phoneBillRefreshPolicy.value.copy(historicalCacheDays = (phoneBillRefreshPolicy.value.historicalCacheDays + deltaDays).coerceIn(1, 365)),
        )

    fun changePhoneBillRecheckDay(delta: Int) =
        refreshRepository.savePhoneBillRefreshPolicy(
            phoneBillRefreshPolicy.value.copy(monthlyRecheckDay = (phoneBillRefreshPolicy.value.monthlyRecheckDay + delta).coerceIn(1, 28)),
        )

    fun changePhoneBillRecheckHour(delta: Int) =
        refreshRepository.savePhoneBillRefreshPolicy(
            phoneBillRefreshPolicy.value.copy(monthlyRecheckHour = (phoneBillRefreshPolicy.value.monthlyRecheckHour + delta).coerceIn(0, 23)),
        )

    fun setIntegralAutomatic(value: Boolean) =
        refreshRepository.saveIntegralRefreshPolicy(integralRefreshPolicy.value.copy(automaticRefreshEnabled = value))

    fun cycleIntegralMode() =
        refreshRepository.saveIntegralRefreshPolicy(
            integralRefreshPolicy.value.copy(cycleMode = nextIntegralMode(integralRefreshPolicy.value.cycleMode)),
        )

    fun changeIntegralMonthlyDay(delta: Int) =
        refreshRepository.saveIntegralRefreshPolicy(
            integralRefreshPolicy.value.copy(monthlyRefreshDay = (integralRefreshPolicy.value.monthlyRefreshDay + delta).coerceIn(1, 28)),
        )

    fun changeIntegralMonthlyHour(delta: Int) =
        refreshRepository.saveIntegralRefreshPolicy(
            integralRefreshPolicy.value.copy(monthlyRefreshHour = (integralRefreshPolicy.value.monthlyRefreshHour + delta).coerceIn(0, 23)),
        )

    fun changeIntegralFixedHours(delta: Int) =
        refreshRepository.saveIntegralRefreshPolicy(
            integralRefreshPolicy.value.copy(fixedIntervalHours = (integralRefreshPolicy.value.fixedIntervalHours + delta).coerceIn(1, 24 * 30)),
        )

    fun setIntegralCheckOnEntry(value: Boolean) =
        refreshRepository.saveIntegralRefreshPolicy(integralRefreshPolicy.value.copy(checkOnEntry = value))

    fun setOrderRefreshOnEntry(value: Boolean) =
        refreshRepository.saveOrderRefreshPolicy(orderRefreshPolicy.value.copy(refreshOnEntry = value))

    fun setRebateGiftAutomatic(value: Boolean) =
        refreshRepository.saveRebateGiftRefreshPolicy(rebateGiftRefreshPolicy.value.copy(automaticRefreshEnabled = value))

    fun changeRebateGiftDay(delta: Int) =
        refreshRepository.saveRebateGiftRefreshPolicy(
            rebateGiftRefreshPolicy.value.copy(monthlyRefreshDay = (rebateGiftRefreshPolicy.value.monthlyRefreshDay + delta).coerceIn(1, 28)),
        )

    fun changeRebateGiftHour(delta: Int) =
        refreshRepository.saveRebateGiftRefreshPolicy(
            rebateGiftRefreshPolicy.value.copy(monthlyRefreshHour = (rebateGiftRefreshPolicy.value.monthlyRefreshHour + delta).coerceIn(0, 23)),
        )

    fun setRebateGiftNoCacheQuery(value: Boolean) =
        refreshRepository.saveRebateGiftRefreshPolicy(rebateGiftRefreshPolicy.value.copy(queryImmediatelyWhenNoCache = value))

    fun cycleVideoRingEntryMode() =
        refreshRepository.saveVideoRingRefreshPolicy(
            videoRingRefreshPolicy.value.copy(entryMode = nextPageEntryMode(videoRingRefreshPolicy.value.entryMode)),
        )

    fun changeVideoRingCache(deltaMinutes: Int) =
        refreshRepository.saveVideoRingRefreshPolicy(
            videoRingRefreshPolicy.value.copy(cacheValidityMinutes = (videoRingRefreshPolicy.value.cacheValidityMinutes + deltaMinutes).coerceIn(1, 24 * 60)),
        )

    private inline fun saveAppSettings(transform: AppSettings.() -> AppSettings) {
        appSettingsRepository.save(appSettings.value.transform())
    }

    private fun nextCachedMode(current: CachedBusinessEntryMode): CachedBusinessEntryMode {
        val values = CachedBusinessEntryMode.entries
        return values[(values.indexOf(current) + 1) % values.size]
    }

    private fun nextPageEntryMode(current: PageEntryRefreshMode): PageEntryRefreshMode {
        val values = PageEntryRefreshMode.entries
        return values[(values.indexOf(current) + 1) % values.size]
    }

    private fun nextIntegralMode(current: IntegralRefreshCycleMode): IntegralRefreshCycleMode {
        val values = IntegralRefreshCycleMode.entries
        return values[(values.indexOf(current) + 1) % values.size]
    }
}

@Composable
fun SettingsRootScreen(
    settingsViewModel: SettingsRootViewModel = viewModel(),
    onOpenCredentials: () -> Unit,
    onOpenRefreshLogic: () -> Unit,
) {
    val settings by settingsViewModel.appSettings.collectAsState()
    val appState by settingsViewModel.appState.collectAsState()
    val balanceState by settingsViewModel.balanceState.collectAsState()
    val accounts = appState.accounts.sortedBy { it.sortOrder }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            SettingsSection("显示") {
                SettingsSwitchRow("隐藏手机号中间四位", settings.hideMobileMiddleDigits, settingsViewModel::setHideMobileMiddleDigits)
                SettingsSwitchRow("宽带号码只显示前后4位", settings.hideBroadbandMiddleDigits, settingsViewModel::setHideBroadbandMiddleDigits)
                SettingsActionRow("流量单位", settings.displayUnit.title, settingsViewModel::cycleDisplayUnit)
                Text("账户卡片排序", style = MaterialTheme.typography.titleSmall)
                if (accounts.isEmpty()) Text("暂无手机账号", style = MaterialTheme.typography.bodySmall)
                accounts.forEachIndexed { index, account ->
                    AccountOrderRow(
                        title = account.displayName.ifBlank { displayMobileNumber(account.mobile, settings) },
                        canMoveUp = index > 0,
                        canMoveDown = index < accounts.lastIndex,
                        onMoveUp = { settingsViewModel.moveAccount(account.id, -1) },
                        onMoveDown = { settingsViewModel.moveAccount(account.id, 1) },
                    )
                }
                SettingsDeferredRow("号码归属纠正", "M11-C")
            }
        }
        item {
            SettingsSection("桌面组件") {
                SettingsDeferredRow("单号码组件信息编辑", "配置 M11-C / 组件 M12")
                SettingsDeferredRow("双号码组件信息编辑", "配置 M11-C / 组件 M12")
                SettingsDeferredRow("组件刷新编辑", "配置 M11-C / 组件 M12")
            }
        }
        item {
            SettingsSection("数据刷新") {
                SettingsActionRow("App刷新逻辑编辑", "统一 schema-3 authority", onOpenRefreshLogic)
                SettingsDeferredRow("每日用量基准", "M11-C")
            }
        }
        item {
            BalanceGroupingEditor(
                accounts = accounts,
                groups = balanceState.balanceAccountGroups,
                settings = settings,
                onAddGroup = settingsViewModel::addBalanceGroup,
                onDeleteGroup = settingsViewModel::deleteBalanceGroup,
                onToggleMember = settingsViewModel::toggleBalanceGroupMember,
            )
        }
        item {
            FinancialRefreshEditor(
                accounts = accounts.filter { it.isEnabled },
                groups = balanceState.balanceAccountGroups,
                homeAccountID = balanceState.homeBalanceAccountID,
                settings = settings,
                onSetHome = settingsViewModel::setHomeBalanceAccount,
                onSetGroupDefault = settingsViewModel::setDefaultFinancialAccount,
            )
        }
        item {
            SettingsSection("运营商号段") {
                SettingsDeferredRow("号段更新 / 已保存号段", "M11-C")
            }
        }
        item {
            SettingsSection("数据与安全") {
                SettingsActionRow("凭据信息新增 / 编辑", "本机 CredentialStore", onOpenCredentials)
                SettingsDeferredRow("电子受理单保存目录", "M11-C")
            }
        }
        item {
            SettingsSection("工具") {
                SettingsDeferredRow("快捷指令余量通知", "M11-C")
                SettingsDeferredRow("抓包工具", "入口 M11 / 主体 M14")
            }
        }
        item {
            SettingsSection("关于") {
                SettingsDeferredRow("App使用说明书", "M11-C")
                Text("Android 最低支持版本：Android 11 / API 30", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BalanceGroupingEditor(
    accounts: List<com.clxmhcs.chinaunicom.core.model.UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    settings: AppSettings,
    onAddGroup: () -> Unit,
    onDeleteGroup: (UUID) -> Unit,
    onToggleMember: (UUID, UUID) -> Unit,
) {
    SettingsSection("合账号码选择") {
        Text("同一号码只能属于一个合账组；至少 2 个成员时该组才参与统一余额/账单代表规则。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAddGroup) { Text("新增合账组") }
        if (groups.isEmpty()) Text("暂无合账组", style = MaterialTheme.typography.bodySmall)
        groups.forEach { group ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, fontWeight = FontWeight.SemiBold)
                    Text(if (group.memberAccountIDs.size >= 2) "有效 · ${group.memberAccountIDs.size} 个号码" else "未生效 · 至少选择 2 个号码", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onDeleteGroup(group.id) }) { Text("删除") }
            }
            accounts.forEach { account ->
                SettingsSwitchRow(
                    label = displayMobileNumber(account.mobile, settings),
                    checked = account.id in group.memberAccountIDs,
                    onCheckedChange = { onToggleMember(account.id, group.id) },
                )
            }
        }
    }
}

@Composable
private fun FinancialRefreshEditor(
    accounts: List<com.clxmhcs.chinaunicom.core.model.UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    homeAccountID: UUID?,
    settings: AppSettings,
    onSetHome: (UUID?) -> Unit,
    onSetGroupDefault: (UUID?, UUID) -> Unit,
) {
    SettingsSection("余额 / 账单 刷新号码") {
        Text("首页余额号码属于某个有效合账组时，它优先成为该组财务代表；组内保存的默认号码仍保留，首页号码离组后恢复生效。", style = MaterialTheme.typography.bodySmall)
        Text("首页余额显示 / 刷新号码", style = MaterialTheme.typography.titleSmall)
        accounts.forEach { account ->
            SettingsActionRow(
                label = displayMobileNumber(account.mobile, settings),
                detail = if (account.id == homeAccountID) "当前首页余额号码" else "设为首页余额号码",
                onClick = { onSetHome(account.id) },
            )
        }
        if (homeAccountID != null) {
            TextButton(onClick = { onSetHome(null) }) { Text("取消首页余额号码") }
        }
        groups.filter { it.memberAccountIDs.size >= 2 }.forEach { group ->
            Text("${group.name} · 默认财务号码", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onSetGroupDefault(null, group.id) }) { Text("自动选择") }
            group.memberAccountIDs.mapNotNull { id -> accounts.firstOrNull { it.id == id } }.forEach { account ->
                SettingsActionRow(
                    label = displayMobileNumber(account.mobile, settings),
                    detail = if (group.defaultAccountID == account.id) "已保存为组默认" else "设为组默认",
                    onClick = { onSetGroupDefault(account.id, group.id) },
                )
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
    val ordered by settingsViewModel.orderedBusinessRefreshPolicy.collectAsState()
    val myPackage by settingsViewModel.myPackageRefreshPolicy.collectAsState()
    val phoneBill by settingsViewModel.phoneBillRefreshPolicy.collectAsState()
    val integral by settingsViewModel.integralRefreshPolicy.collectAsState()
    val orders by settingsViewModel.orderRefreshPolicy.collectAsState()
    val rebateGift by settingsViewModel.rebateGiftRefreshPolicy.collectAsState()
    val videoRing by settingsViewModel.videoRingRefreshPolicy.collectAsState()

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
            SettingsSection("首页与基础数据 · 流量 / 语音") {
                SettingsSwitchRow("启用自动刷新", quota.automaticRefreshEnabled, settingsViewModel::setQuotaAutomaticRefresh)
                SettingsSwitchRow("冷启动时检查", quota.refreshOnColdLaunch, settingsViewModel::setQuotaColdLaunch)
                SettingsSwitchRow("回到前台时检查", quota.refreshOnForeground, settingsViewModel::setQuotaForeground)
                NumberEditor("最小刷新间隔", quota.minimumIntervalMinutes, "分钟", 5, settingsViewModel::changeQuotaInterval)
                NumberEditor("多号码刷新间隔", quota.accountGapSeconds, "秒", 1, settingsViewModel::changeQuotaAccountGap)
            }
        }
        item {
            SettingsSection("余额") {
                SettingsSwitchRow("启用自动刷新", balance.automaticRefreshEnabled, settingsViewModel::setBalanceAutomaticRefresh)
                SettingsSwitchRow("回到前台时检查", balance.checkOnForeground, settingsViewModel::setBalanceForeground)
                NumberEditor("刷新间隔", balance.intervalMinutes, "分钟", 15, settingsViewModel::changeBalanceInterval)
                NumberEditor("失败重试", balance.failureRetryMinutes, "分钟", 5, settingsViewModel::changeBalanceFailureRetry)
            }
        }
        item {
            SettingsSection("已订业务") {
                SettingsActionRow("进入页面策略", cachedModeTitle(ordered.entryMode), settingsViewModel::cycleOrderedBusinessEntryMode)
                NumberEditor("缓存有效期", ordered.cacheValidityHours, "小时", 1, settingsViewModel::changeOrderedBusinessCache)
                SettingsSwitchRow("无缓存时自动查询", ordered.noCacheAutoQuery, settingsViewModel::setOrderedBusinessNoCacheQuery)
                NumberEditor("刷新全部号码间隔", ordered.refreshAllAccountGapSeconds, "秒", 1, settingsViewModel::changeOrderedBusinessGap)
            }
        }
        item {
            SettingsSection("我的套餐") {
                SettingsActionRow("进入页面策略", pageModeTitle(myPackage.entryMode), settingsViewModel::cycleMyPackageEntryMode)
                NumberEditor("缓存有效期", myPackage.cacheValidityMinutes, "分钟", 5, settingsViewModel::changeMyPackageCache)
            }
        }
        item {
            SettingsSection("话费 / 账单") {
                NumberEditor("本月缓存", phoneBill.currentMonthCacheMinutes, "分钟", 5, settingsViewModel::changePhoneBillCurrentCache)
                NumberEditor("历史月份缓存", phoneBill.historicalCacheDays, "天", 1, settingsViewModel::changePhoneBillHistoricalCache)
                NumberEditor("每月复查日期", phoneBill.monthlyRecheckDay, "日", 1, settingsViewModel::changePhoneBillRecheckDay)
                NumberEditor("每月复查小时", phoneBill.monthlyRecheckHour, "时", 1, settingsViewModel::changePhoneBillRecheckHour)
            }
        }
        item {
            SettingsSection("积分") {
                SettingsSwitchRow("启用自动刷新", integral.automaticRefreshEnabled, settingsViewModel::setIntegralAutomatic)
                SettingsActionRow("刷新周期", integralModeTitle(integral.cycleMode), settingsViewModel::cycleIntegralMode)
                NumberEditor("每月刷新日期", integral.monthlyRefreshDay, "日", 1, settingsViewModel::changeIntegralMonthlyDay)
                NumberEditor("每月刷新小时", integral.monthlyRefreshHour, "时", 1, settingsViewModel::changeIntegralMonthlyHour)
                NumberEditor("固定周期", integral.fixedIntervalHours, "小时", 1, settingsViewModel::changeIntegralFixedHours)
                SettingsSwitchRow("进入积分页时检查", integral.checkOnEntry, settingsViewModel::setIntegralCheckOnEntry)
            }
        }
        item {
            SettingsSection("我的订单") {
                SettingsSwitchRow("进入页面自动刷新", orders.refreshOnEntry, settingsViewModel::setOrderRefreshOnEntry)
            }
        }
        item {
            SettingsSection("返费 / 赠费") {
                SettingsSwitchRow("启用自动刷新", rebateGift.automaticRefreshEnabled, settingsViewModel::setRebateGiftAutomatic)
                NumberEditor("每月刷新日期", rebateGift.monthlyRefreshDay, "日", 1, settingsViewModel::changeRebateGiftDay)
                NumberEditor("每月刷新小时", rebateGift.monthlyRefreshHour, "时", 1, settingsViewModel::changeRebateGiftHour)
                SettingsSwitchRow("无缓存时立即查询", rebateGift.queryImmediatelyWhenNoCache, settingsViewModel::setRebateGiftNoCacheQuery)
            }
        }
        item {
            SettingsSection("视频彩铃") {
                SettingsActionRow("进入页面策略", pageModeTitle(videoRing.entryMode), settingsViewModel::cycleVideoRingEntryMode)
                NumberEditor("缓存有效期", videoRing.cacheValidityMinutes, "分钟", 5, settingsViewModel::changeVideoRingCache)
            }
        }
        item {
            SettingsSection("统一存储说明") {
                Text("以上所有业务均读写现有 AppRefreshLogicPolicy schema 3 的同一 SharedPreferences 文档；没有创建第二套刷新设置 authority。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccountOrderRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("上移") }
        TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("下移") }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun NumberEditor(label: String, value: Int, unit: String, step: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text("$value $unit", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { onChange(-step) }) { Text("-$step") }
        TextButton(onClick = { onChange(step) }) { Text("+$step") }
    }
}

private fun cachedModeTitle(mode: CachedBusinessEntryMode): String = when (mode) {
    CachedBusinessEntryMode.CACHE_PREFERRED -> "优先使用缓存"
    CachedBusinessEntryMode.REFRESH_WHEN_EXPIRED -> "缓存过期时刷新"
    CachedBusinessEntryMode.EVERY_ENTRY -> "每次进入刷新"
    CachedBusinessEntryMode.MANUAL_ONLY -> "仅手动刷新"
}

private fun pageModeTitle(mode: PageEntryRefreshMode): String = when (mode) {
    PageEntryRefreshMode.EVERY_ENTRY -> "每次进入刷新"
    PageEntryRefreshMode.REFRESH_WHEN_EXPIRED -> "缓存过期时刷新"
    PageEntryRefreshMode.MANUAL_ONLY -> "仅手动刷新"
}

private fun integralModeTitle(mode: IntegralRefreshCycleMode): String = when (mode) {
    IntegralRefreshCycleMode.MONTHLY -> "每月"
    IntegralRefreshCycleMode.FIXED_INTERVAL -> "固定间隔"
    IntegralRefreshCycleMode.MANUAL_ONLY -> "仅手动"
}
