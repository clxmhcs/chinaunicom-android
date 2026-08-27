package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.MyPackageMember
import com.clxmhcs.chinaunicom.core.model.MyPackageMemberGroup
import com.clxmhcs.chinaunicom.core.model.MyPackageResourceTab
import com.clxmhcs.chinaunicom.core.model.MyPackageSnapshot
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.mypackage.MyPackageRefreshState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M9-B4 functional My Package destination.
 * Presentation remains intentionally rough while mobile and independent broadband targets share the B1 store.
 */
@Composable
fun MyPackageScreen(
    accounts: List<UnicomAccount>,
    viewModel: MyPackageViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val enabledAccounts = remember(accounts) {
        accounts.filter { it.isEnabled }.sortedBy { it.sortOrder }
    }
    var selectedAccountID by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(MyPackageResourceTab.MOBILE.name) }
    var showFullNumberNotice by rememberSaveable { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val selectedAccount = enabledAccounts.firstOrNull { it.id.toString() == selectedAccountID }

    LaunchedEffect(selectedAccount?.id) {
        selectedAccount?.let(viewModel::load)
    }

    if (showFullNumberNotice) {
        AlertDialog(
            onDismissRequest = { showFullNumberNotice = false },
            title = { Text("完整号码") },
            text = { Text("当前展示联通接口直接返回的脱敏成员号码。完整号码需要短信验证，本页面暂不发起验证。") },
            confirmButton = {
                TextButton(onClick = { showFullNumberNotice = false }) { Text("知道了") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MyPackageHeader(
            selectedAccount = selectedAccount,
            onBack = {
                if (selectedAccount == null) {
                    onBack()
                } else {
                    selectedAccountID = null
                    selectedTabName = MyPackageResourceTab.MOBILE.name
                    expandedGroups.clear()
                    viewModel.clear()
                }
            },
            onClose = onBack,
            onRefresh = selectedAccount?.let { account -> { viewModel.refresh(account) } },
        )

        when {
            enabledAccounts.isEmpty() -> EmptyMyPackageAccounts()
            selectedAccount == null -> MyPackageAccountSelection(
                accounts = enabledAccounts,
                onSelect = { account ->
                    selectedTabName = if (account.isBroadbandTarget()) {
                        MyPackageResourceTab.BROADBAND.name
                    } else {
                        MyPackageResourceTab.MOBILE.name
                    }
                    selectedAccountID = account.id.toString()
                },
            )
            else -> MyPackageContent(
                account = selectedAccount,
                state = state,
                selectedTab = runCatching { MyPackageResourceTab.valueOf(selectedTabName) }
                    .getOrDefault(MyPackageResourceTab.MOBILE),
                onSelectTab = { selectedTabName = it.name },
                expandedGroups = expandedGroups,
                onShowFullNumberNotice = { showFullNumberNotice = true },
                onRetry = { viewModel.refresh(selectedAccount) },
            )
        }
    }
}

@Composable
private fun MyPackageHeader(
    selectedAccount: UnicomAccount?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Column {
                    Text("我的套餐", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    selectedAccount?.let {
                        Text(maskBusinessNumber(it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    if (onRefresh != null) TextButton(onClick = onRefresh) { Text("刷新") }
                    TextButton(onClick = onClose) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun EmptyMyPackageAccounts() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂无号码", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("请先在设置中保存联通手机或独立宽带账号。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MyPackageAccountSelection(
    accounts: List<UnicomAccount>,
    onSelect: (UnicomAccount) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("选择要查询的号码", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "我的套餐按号码分别查询。已保存的联通手机账号和独立宽带账号都会出现在这里；宽带账号不会加入首页流量、语音或余额卡片。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(accounts, key = { it.id }) { account ->
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clickable { onSelect(account) },
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(maskBusinessNumber(account), style = MaterialTheme.typography.titleMedium)
                    if (account.displayName.isNotBlank()) {
                        Text(account.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (account.packageName.isNotBlank()) {
                        Text(account.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun MyPackageContent(
    account: UnicomAccount,
    state: com.clxmhcs.chinaunicom.data.mypackage.MyPackageStoreState,
    selectedTab: MyPackageResourceTab,
    onSelectTab: (MyPackageResourceTab) -> Unit,
    expandedGroups: MutableMap<String, Boolean>,
    onShowFullNumberNotice: () -> Unit,
    onRetry: () -> Unit,
) {
    val snapshot = state.snapshot.takeIf { state.activeAccountID == account.id }
    val refreshState = state.refreshState

    if (snapshot == null && refreshState == MyPackageRefreshState.Loading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在查询我的套餐…")
        }
        return
    }

    if (snapshot == null && refreshState is MyPackageRefreshState.Failed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("套餐查询失败", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(refreshState.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("重新查询") }
        }
        return
    }

    if (snapshot == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("暂无套餐数据")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重新查询") }
        }
        return
    }

    MyPackageSnapshotList(
        snapshot = snapshot,
        fetchedAtText = state.snapshotFetchedAt?.let {
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(it)
        },
        selectedTab = selectedTab,
        onSelectTab = onSelectTab,
        refreshState = refreshState,
        expandedGroups = expandedGroups,
        onShowFullNumberNotice = onShowFullNumberNotice,
    )
}

@Composable
private fun MyPackageSnapshotList(
    snapshot: MyPackageSnapshot,
    fetchedAtText: String?,
    selectedTab: MyPackageResourceTab,
    onSelectTab: (MyPackageResourceTab) -> Unit,
    refreshState: MyPackageRefreshState,
    expandedGroups: MutableMap<String, Boolean>,
    onShowFullNumberNotice: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("套餐") {
                Text(snapshot.productName.ifBlank { "--" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("套餐价格：${snapshot.displayedMonthFee}元/月")
                Text("生效时间：${snapshot.productStartDate.ifBlank { "--" }}")
                fetchedAtText?.let { Text("刷新：$it", style = MaterialTheme.typography.bodySmall) }
                if (snapshot.promotionText.isNotBlank()) Text(snapshot.promotionText)
                snapshot.promotionURL?.let { Text("活动链接：$it", style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            SectionCard("套餐资源") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MyPackageResourceTab.entries.forEach { tab ->
                        if (selectedTab == tab) {
                            Button(onClick = { onSelectTab(tab) }) { Text(tab.title) }
                        } else {
                            OutlinedButton(onClick = { onSelectTab(tab) }) { Text(tab.title) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (selectedTab) {
                    MyPackageResourceTab.MOBILE -> {
                        Text("套外计费规则", fontWeight = FontWeight.SemiBold)
                        if (snapshot.mobileRules.isEmpty()) {
                            Text("当前套餐接口没有返回可单独展示的套外计费字段。")
                        } else snapshot.mobileRules.forEach { rule ->
                            KeyValueLine(rule.title, rule.value)
                        }
                    }
                    MyPackageResourceTab.BROADBAND -> {
                        if (snapshot.broadbandResources.isEmpty()) {
                            Text("当前套餐未返回关联宽带资源。")
                        } else snapshot.broadbandResources.forEachIndexed { index, resource ->
                            if (index > 0) HorizontalDivider()
                            KeyValueLine("宽带号码", resource.mobile)
                            KeyValueLine("套餐速率", resource.packageSpeed)
                            KeyValueLine("实际速率", resource.actualSpeed)
                            KeyValueLine("生效时间", resource.startDate)
                            KeyValueLine("到期时间", resource.endDate)
                        }
                        if (snapshot.broadbandTips.isNotBlank()) Text(snapshot.broadbandTips)
                    }
                }
            }
        }

        item {
            SectionCard("套餐说明") {
                Text(snapshot.packageDescription.ifBlank { "当前接口没有返回套餐说明。" })
                Spacer(Modifier.height(8.dp))
                Text("业务规则", fontWeight = FontWeight.SemiBold)
                Text(snapshot.businessRules.ifBlank { snapshot.packageDescription.ifBlank { "当前接口没有返回套餐说明。" } })
                if (snapshot.monthFeeDescription.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("月费说明", fontWeight = FontWeight.SemiBold)
                    Text(snapshot.monthFeeDescription)
                }
            }
        }

        item {
            SectionCard("我的合约") {
                if (snapshot.activities.isEmpty()) {
                    Text("当前接口没有返回在网合约。")
                } else snapshot.activities.forEachIndexed { index, activity ->
                    if (index > 0) HorizontalDivider()
                    KeyValueLine("合约名称", activity.name)
                    KeyValueLine("合约生效时间", activity.startDate)
                    KeyValueLine("合约到期时间", activity.endDate)
                    KeyValueLine("合约剩余天数", activity.remainingDays.ifBlank { "--" }.let { if (it == "--") it else "${it}天" })
                }
                Spacer(Modifier.height(8.dp))
                Text("合约规则", fontWeight = FontWeight.SemiBold)
                Text(snapshot.contractTips.ifBlank { "当前接口没有返回合约规则说明。" })
                if (snapshot.cannotCancelPrompt.isNotBlank()) Text(snapshot.cannotCancelPrompt)
            }
        }

        item {
            SectionCard("我的成员") {
                TextButton(onClick = onShowFullNumberNotice) { Text("查看完整号码") }
                if (snapshot.memberGroups.isEmpty()) {
                    Text("当前套餐没有返回成员信息。")
                } else snapshot.memberGroups.forEach { group ->
                    MemberGroup(group, expandedGroups)
                }
            }
        }

        if (snapshot.isPrettyNumber) {
            item {
                SectionCard("我的靓号") {
                    Text("当前号码已被联通系统标记为靓号。")
                }
            }
        }

        if (refreshState is MyPackageRefreshState.Warning) {
            item { Text(refreshState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else if (refreshState is MyPackageRefreshState.Failed) {
            item { Text(refreshState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MemberGroup(group: MyPackageMemberGroup, expandedGroups: MutableMap<String, Boolean>) {
    val expanded = expandedGroups[group.id] == true
    val members = if (group.groupType == "05" || expanded || group.members.size <= 2) {
        group.members
    } else {
        group.members.take(2)
    }
    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (group.groupType != "05" && group.primaryMembers.isNotEmpty()) {
        Text("主成员", fontWeight = FontWeight.SemiBold)
        group.primaryMembers.forEach { MemberLine(it, false) }
    }
    if (members.isNotEmpty()) {
        if (group.groupType != "05") Text("成员", fontWeight = FontWeight.SemiBold)
        members.forEach { MemberLine(it, group.groupType == "05") }
    }
    if (group.groupType != "05" && group.members.size > 2) {
        TextButton(onClick = { expandedGroups[group.id] = !expanded }) {
            Text(if (expanded) "收起" else "查看更多")
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MemberLine(member: MyPackageMember, usesRoleLabel: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (usesRoleLabel) member.role else member.serviceType, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(member.maskedNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun KeyValueLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "--" })
    }
}

private fun UnicomAccount.isBroadbandTarget(): Boolean = packageName == "宽带账号"

private fun maskBusinessNumber(account: UnicomAccount): String {
    val value = account.mobile.trim()
    if (account.isBroadbandTarget()) {
        return if (value.length > 8) value.take(4) + "****" + value.takeLast(4) else value
    }
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 7) "${digits.take(3)}****${digits.takeLast(4)}" else digits.ifBlank { value }
}
