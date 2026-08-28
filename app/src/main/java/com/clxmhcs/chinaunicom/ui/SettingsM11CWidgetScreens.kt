package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationProfile
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationSlot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import com.clxmhcs.chinaunicom.core.model.WidgetQuotaResourceKind
import java.util.UUID

@Composable
fun SingleWidgetSettingsScreen(
    accounts: List<UnicomAccount>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.widgetState.collectAsState()
    val message by viewModel.operationMessage.collectAsState()
    val configuration = state.single
    val selected = configuration.selectedAccountID?.let { id -> accounts.firstOrNull { it.id == id } }
    val settings = LocalAppSettings.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("单号码组件信息编辑", onBack) }
        item {
            M11CCard {
                Text("这里只配置 M12 将读取的数据；桌面 Widget 主体仍在 Android-M12。", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = {
                    val next = nextAccount(configuration.selectedAccountID, accounts, allowAutomatic = true)
                    viewModel.saveSingleWidget(configuration.copy(selectedAccountID = next))
                }) {
                    Text("组件号码：${selected?.let { displayMobileNumber(it.mobile, settings) } ?: "自动使用首个卡片号码"}")
                }
                SettingSwitch("显示今日用量", configuration.showsTodayUsage) {
                    viewModel.saveSingleWidget(configuration.copy(showsTodayUsage = it))
                }
                SettingSwitch("显示余额", configuration.showsBalance) {
                    viewModel.saveSingleWidget(configuration.copy(showsBalance = it))
                }
            }
        }
        configuration.slots.forEachIndexed { index, slot ->
            item(key = "single-${slot.id}-$index") {
                M11CCard {
                    Text("位置 ${index + 1} · ${slot.displayTitle}", fontWeight = FontWeight.SemiBold)
                    SettingSwitch("显示", slot.isVisible) { visible ->
                        viewModel.saveSingleWidget(configuration.copy(slots = configuration.slots.replaceAt(index, slot.copy(isVisible = visible))))
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = slot.title,
                        onValueChange = { title ->
                            viewModel.saveSingleWidget(configuration.copy(slots = configuration.slots.replaceAt(index, slot.copy(title = title))))
                        },
                        label = { Text("标题") },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = {
                        val nextKind = if (slot.kind == WidgetQuotaResourceKind.FLOW) WidgetQuotaResourceKind.VOICE else WidgetQuotaResourceKind.FLOW
                        viewModel.saveSingleWidget(
                            configuration.copy(
                                slots = configuration.slots.replaceAt(index, slot.copy(kind = nextKind, packageIDs = emptyList())),
                            ),
                        )
                    }) { Text("类型：${slot.kind.title}") }
                    OutlinedButton(
                        enabled = selected != null,
                        onClick = {
                            val ids = when (slot.kind) {
                                WidgetQuotaResourceKind.FLOW -> selected?.visibleDetailPackages.orEmpty().map { it.id }
                                WidgetQuotaResourceKind.VOICE -> selected?.visibleVoicePackages.orEmpty().map { it.id }
                            }
                            viewModel.saveSingleWidget(
                                configuration.copy(slots = configuration.slots.replaceAt(index, slot.copy(packageIDs = ids))),
                            )
                        },
                    ) { Text(if (slot.packageIDs.isEmpty()) "绑定当前号码可见资源" else "已绑定 ${slot.packageIDs.size} 个资源") }
                }
            }
        }
    }
}

@Composable
fun DualWidgetSettingsScreen(
    accounts: List<UnicomAccount>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.widgetState.collectAsState()
    val configuration = state.dual
    val settings = LocalAppSettings.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("双号码组件信息编辑", onBack) }
        item {
            M11CCard {
                Text("左右号码不能相同；每侧固定保存 6 个位置。流量/语音可绑定分类或资源，积分位置读取 App 缓存。", style = MaterialTheme.typography.bodySmall)
                DualAccountSelector("左侧号码", WidgetDualSide.LEFT, configuration, accounts, settings, viewModel)
                DualAccountSelector("右侧号码", WidgetDualSide.RIGHT, configuration, accounts, settings, viewModel)
            }
        }
        listOf(WidgetDualSide.LEFT, WidgetDualSide.RIGHT).forEach { side ->
            val sideTitle = if (side == WidgetDualSide.LEFT) "左侧" else "右侧"
            configuration.slots(side).forEachIndexed { index, slot ->
                item(key = "${side.rawValue}-${slot.id}-$index") {
                    val accountID = configuration.accountID(side)
                    val account = accountID?.let { id -> accounts.firstOrNull { it.id == id } }
                    M11CCard {
                        Text("$sideTitle · 位置 ${index + 1} · ${slot.displayTitle}", fontWeight = FontWeight.SemiBold)
                        SettingSwitch("显示", slot.isVisible) { visible ->
                            saveDualSlot(viewModel, configuration, side, index, slot.copy(isVisible = visible))
                        }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = slot.title,
                            enabled = slot.kind != WidgetDualSlotKind.INTEGRAL,
                            onValueChange = { title -> saveDualSlot(viewModel, configuration, side, index, slot.copy(title = title)) },
                            label = { Text("标题") },
                            singleLine = true,
                        )
                        OutlinedButton(onClick = {
                            val values = WidgetDualSlotKind.entries
                            val next = values[(values.indexOf(slot.kind) + 1) % values.size]
                            saveDualSlot(
                                viewModel,
                                configuration,
                                side,
                                index,
                                slot.copy(
                                    kind = next,
                                    title = if (next == WidgetDualSlotKind.INTEGRAL) WidgetDualSlotKind.INTEGRAL.title else slot.title,
                                    flowSummaryGroupID = null,
                                    voiceSummaryGroupID = null,
                                    packageIDs = emptyList(),
                                ),
                            )
                        }) { Text("类型：${slot.kind.title}") }

                        if (slot.kind != WidgetDualSlotKind.INTEGRAL) {
                            OutlinedButton(enabled = account != null, onClick = {
                                val ids = when (slot.kind) {
                                    WidgetDualSlotKind.FLOW -> account?.visibleDetailPackages.orEmpty().map { it.id }
                                    WidgetDualSlotKind.VOICE -> account?.visibleVoicePackages.orEmpty().map { it.id }
                                    WidgetDualSlotKind.INTEGRAL -> emptyList()
                                }
                                saveDualSlot(
                                    viewModel,
                                    configuration,
                                    side,
                                    index,
                                    slot.copy(flowSummaryGroupID = null, voiceSummaryGroupID = null, packageIDs = ids),
                                )
                            }) { Text(if (slot.packageIDs.isEmpty()) "绑定当前号码可见资源" else "已绑定 ${slot.packageIDs.size} 个资源") }

                            OutlinedButton(enabled = account != null, onClick = {
                                when (slot.kind) {
                                    WidgetDualSlotKind.FLOW -> {
                                        val groups = account?.configuredSummaryGroups.orEmpty()
                                        val currentIndex = groups.indexOfFirst { it.id == slot.flowSummaryGroupID }
                                        val next = groups.getOrNull((currentIndex + 1).coerceAtLeast(0) % groups.size.coerceAtLeast(1))
                                        saveDualSlot(
                                            viewModel,
                                            configuration,
                                            side,
                                            index,
                                            slot.copy(flowSummaryGroupID = next?.id, voiceSummaryGroupID = null, packageIDs = emptyList()),
                                        )
                                    }
                                    WidgetDualSlotKind.VOICE -> {
                                        val groups = account?.voiceSummaryGroups.orEmpty().sortedBy { it.sortOrder }
                                        val currentIndex = groups.indexOfFirst { it.id == slot.voiceSummaryGroupID }
                                        val next = groups.getOrNull((currentIndex + 1).coerceAtLeast(0) % groups.size.coerceAtLeast(1))
                                        saveDualSlot(
                                            viewModel,
                                            configuration,
                                            side,
                                            index,
                                            slot.copy(flowSummaryGroupID = null, voiceSummaryGroupID = next?.id, packageIDs = emptyList()),
                                        )
                                    }
                                    WidgetDualSlotKind.INTEGRAL -> Unit
                                }
                            }) {
                                val binding = slot.flowSummaryGroupID ?: slot.voiceSummaryGroupID
                                Text(if (binding == null) "绑定一个统计分类" else "已绑定统计分类")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetRefreshSettingsScreen(
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val policy by viewModel.widgetRefreshPolicy.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("组件刷新编辑", onBack) }
        item {
            M11CCard {
                Text("此策略写入现有 AppRefreshLogic schema-3 文档；单/双号码配置读取同一刷新规则。M12 再接入真实 App Widget 调度。", style = MaterialTheme.typography.bodySmall)
                SettingSwitch("启用自动刷新", policy.automaticRefreshEnabled, viewModel::setWidgetAutomaticRefresh)
                ValueEditor("补偿窗口", "${policy.compensationMinutes} 分钟", { viewModel.changeWidgetCompensation(-1) }, { viewModel.changeWidgetCompensation(1) })
                ValueEditor("失败重试", "${policy.failureRetrySeconds} 秒", { viewModel.changeWidgetFailureRetry(-5) }, { viewModel.changeWidgetFailureRetry(5) })
            }
        }
        policy.scheduledMinutes.forEachIndexed { index, minute ->
            item(key = "time-$minute-$index") {
                M11CCard {
                    Text("刷新时间 ${index + 1}：${formatMinuteOfDay(minute)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.shiftWidgetRefreshTime(index, -30) }) { Text("-30分") }
                        TextButton(onClick = { viewModel.shiftWidgetRefreshTime(index, 30) }) { Text("+30分") }
                        TextButton(enabled = policy.scheduledMinutes.size > 1, onClick = { viewModel.removeWidgetRefreshTime(index) }) { Text("删除") }
                    }
                }
            }
        }
        item {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = viewModel::addWidgetRefreshTime) { Text("增加刷新时间") }
        }
    }
}

@Composable
fun ShortcutNotificationSettingsScreen(
    accounts: List<UnicomAccount>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val profiles by viewModel.shortcutProfiles.collectAsState()
    val settings = LocalAppSettings.current
    var selectedID by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val selected = selectedID?.let { id -> accounts.firstOrNull { it.id == id } }
    val profile = selectedID?.let { id -> profiles[id] ?: ShortcutNotificationProfile(accountID = id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { M11CPageHeader("快捷指令余量通知", onBack) }
        item {
            M11CCard {
                Text("通知设置按账号 UUID 独立保存；A/B/C/D 槽位不能重复绑定。Cookie、token_online 等凭据不会写入此配置。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = accounts.isNotEmpty(), onClick = {
                    selectedID = nextAccount(selectedID, accounts, allowAutomatic = false)
                }) {
                    Text("账号：${selected?.let { displayMobileNumber(it.mobile, settings) } ?: "暂无号码"}")
                }
            }
        }
        if (profile != null) {
            item {
                M11CCard {
                    OutlinedButton(onClick = {
                        val values = ShortcutNotificationSlot.entries
                        val next = values[(values.indexOf(profile.slot) + 1) % values.size]
                        viewModel.saveShortcutProfile(profile.copy(slot = next))
                    }) { Text("通知槽位：${profile.slot.title}") }
                    SettingSwitch("通知流量", profile.settings.notifyTraffic) {
                        viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(notifyTraffic = it)))
                    }
                    SettingSwitch("通知语音", profile.settings.notifyVoice) {
                        viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(notifyVoice = it)))
                    }
                    SettingSwitch("通知余额", profile.settings.notifyBalance) {
                        viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(notifyBalance = it)))
                    }
                    SettingSwitch("查询失败也通知", profile.settings.notifyOnFailure) {
                        viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(notifyOnFailure = it)))
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = profile.settings.titleTemplate,
                        onValueChange = { viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(titleTemplate = it))) },
                        label = { Text("通知标题模板") },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = profile.settings.subtitleTemplate,
                        onValueChange = { viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(subtitleTemplate = it))) },
                        label = { Text("通知副标题模板") },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = profile.settings.bodyTemplate,
                        onValueChange = { viewModel.saveShortcutProfile(profile.copy(settings = profile.settings.copy(bodyTemplate = it))) },
                        label = { Text("通知正文模板") },
                        minLines = 4,
                    )
                }
            }
        }
    }
}

@Composable
private fun DualAccountSelector(
    label: String,
    side: WidgetDualSide,
    configuration: WidgetDualDisplayConfiguration,
    accounts: List<UnicomAccount>,
    settings: com.clxmhcs.chinaunicom.core.model.AppSettings,
    viewModel: SettingsM11CViewModel,
) {
    val currentID = configuration.accountID(side)
    val account = currentID?.let { id -> accounts.firstOrNull { it.id == id } }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
        val excluded = configuration.accountID(if (side == WidgetDualSide.LEFT) WidgetDualSide.RIGHT else WidgetDualSide.LEFT)
        val eligible = accounts.filterNot { it.id == excluded }
        val next = nextAccount(currentID, eligible, allowAutomatic = true)
        viewModel.saveDualWidget(configuration.withAccount(side, next))
    }) {
        Text("$label：${account?.let { displayMobileNumber(it.mobile, settings) } ?: "未绑定"}")
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueEditor(label: String, value: String, decrement: () -> Unit, increment: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = decrement) { Text("-") }
        TextButton(onClick = increment) { Text("+") }
    }
}

private fun saveDualSlot(
    viewModel: SettingsM11CViewModel,
    configuration: WidgetDualDisplayConfiguration,
    side: WidgetDualSide,
    index: Int,
    slot: WidgetDualSlotConfiguration,
) {
    viewModel.saveDualWidget(configuration.withSlots(side, configuration.slots(side).replaceAt(index, slot)))
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

private fun nextAccount(currentID: UUID?, accounts: List<UnicomAccount>, allowAutomatic: Boolean): UUID? {
    if (accounts.isEmpty()) return null
    if (currentID == null) return accounts.first().id
    val currentIndex = accounts.indexOfFirst { it.id == currentID }
    if (currentIndex < 0) return accounts.first().id
    if (currentIndex == accounts.lastIndex) return if (allowAutomatic) null else accounts.first().id
    return accounts[currentIndex + 1].id
}

private fun formatMinuteOfDay(value: Int): String {
    val minute = Math.floorMod(value, 24 * 60)
    return "%02d:%02d".format(minute / 60, minute % 60)
}
