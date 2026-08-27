package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.GiftRecord
import com.clxmhcs.chinaunicom.core.model.RebateContract
import com.clxmhcs.chinaunicom.core.model.RebateQueryScope
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RebateAndGiftTab(val title: String) {
    CONTRACT("合约返赠"),
    GIFT("赠款记录"),
}

/** M9-F rough functional screen. Final visual parity is intentionally deferred. */
@Composable
fun RebateAndGiftScreen(
    account: UnicomAccount,
    viewModel: RebateAndGiftViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var tab by remember(account.id) { mutableStateOf(RebateAndGiftTab.CONTRACT) }
    var scope by remember(account.id) { mutableStateOf(RebateQueryScope.ACCOUNT) }

    LaunchedEffect(account.id, scope) { viewModel.load(account, scope) }
    LaunchedEffect(account.id, tab) {
        if (tab == RebateAndGiftTab.GIFT) viewModel.loadGift(account)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text("返回") }
                    TextButton(
                        enabled = !state.loading,
                        onClick = { viewModel.refresh(account, scope) },
                    ) { Text("刷新") }
                }
                Text("返费与赠款", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(maskRebateDetailMobile(account.mobile), style = MaterialTheme.typography.titleMedium)
                Text(
                    "查询时间：${formatRebateTime(if (tab == RebateAndGiftTab.CONTRACT) state.queryTime else state.giftQueryTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.lastManualRefreshAt?.let {
                    Text("上次手动刷新：${formatRebateTime(it)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RebateChoiceButton("合约返赠", tab == RebateAndGiftTab.CONTRACT, Modifier.weight(1f)) { tab = RebateAndGiftTab.CONTRACT }
                RebateChoiceButton("赠款记录", tab == RebateAndGiftTab.GIFT, Modifier.weight(1f)) { tab = RebateAndGiftTab.GIFT }
            }
        }

        if (tab == RebateAndGiftTab.CONTRACT) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RebateChoiceButton("账户", scope == RebateQueryScope.ACCOUNT, Modifier.weight(1f)) { scope = RebateQueryScope.ACCOUNT }
                    RebateChoiceButton("用户", scope == RebateQueryScope.USER, Modifier.weight(1f)) { scope = RebateQueryScope.USER }
                }
            }
        }

        if (state.loading && !state.hasVisibleContent) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(if (tab == RebateAndGiftTab.CONTRACT) "正在查询合约返赠" else "正在查询赠款记录")
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("查询提示", fontWeight = FontWeight.SemiBold)
                        Text(message, style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !state.loading, onClick = { viewModel.refresh(account, scope) }) { Text("重新查询") }
                    }
                }
            }
        }

        if (tab == RebateAndGiftTab.CONTRACT) {
            if (!state.loading && state.contracts.isEmpty() && state.errorMessage == null) {
                item { RebateEmptyCard("暂无合约返赠信息", "当前号码未查询到合约返赠记录。") }
            } else {
                items(state.contracts, key = { it.id }) { RebateContractCardRough(it) }
            }
            item { RebateTipsCard("如您对返赠金额存疑，可到当地联通营业厅或拨打10010热线进行咨询。") }
        } else {
            if (!state.loading && state.gifts.isEmpty() && state.errorMessage == null) {
                item { RebateEmptyCard("尊敬的客户您好，您暂无赠款信息", "") }
            } else {
                items(state.gifts, key = { it.id }) { GiftRecordCardRough(it) }
            }
            item { RebateTipsCard("赠款记录以联通接口实际返回为准。") }
        }
    }
}

@Composable
private fun RebateChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 3.dp else 1.dp,
        onClick = onClick,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RebateContractCardRough(item: RebateContract) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.activityName, fontWeight = FontWeight.SemiBold)
            Text("已返金额（元）：${item.returnedAmount}")
            Text("总金额：${item.totalAmount}")
            Text("冻结金额：${item.frozenAmount}")
            if (item.mobile.isNotBlank()) Text("合约号码：${maskRebateDetailMobile(item.mobile)}")
            if (item.periodText.isNotBlank()) Text("活动时间：${item.periodText}")
            if (item.detail.isNotEmpty()) {
                Text("返赠明细", fontWeight = FontWeight.SemiBold)
                item.detail.forEach { detail ->
                    Text("${displayRebateDate(detail.date)}  赠款 ${detail.giftMoney} 元  返费 ${detail.freeMoney} 元", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GiftRecordCardRough(item: GiftRecord) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold)
            Text("赠款金额（元）：${item.amount}")
            if (item.mobile.isNotBlank()) Text("号码：${maskRebateDetailMobile(item.mobile)}")
            if (item.date.isNotBlank()) Text("时间：${item.date}")
            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RebateEmptyCard(title: String, message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RebateTipsCard(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("温馨提示", fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun maskRebateDetailMobile(raw: String): String {
    val mobile = raw.trim()
    return if (mobile.length >= 7) "${mobile.take(3)}****${mobile.takeLast(4)}" else mobile
}

private fun displayRebateDate(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 8) "${digits.take(4)}年${digits.drop(4).take(2)}月${digits.drop(6).take(2)}日" else value
}

private fun formatRebateTime(value: Instant?): String {
    if (value == null) return "尚未查询"
    return REBATE_TIME_FORMATTER.format(value.atZone(ZoneId.of("Asia/Shanghai")))
}

private val REBATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
