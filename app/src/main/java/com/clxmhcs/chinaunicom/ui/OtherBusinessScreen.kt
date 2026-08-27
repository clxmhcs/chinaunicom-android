package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class OtherBusinessEntry(
    val title: String,
    val enabled: Boolean = false,
)

private val otherBusinessEntries = listOf(
    OtherBusinessEntry("已订业务", enabled = true),
    OtherBusinessEntry("视频彩铃会员", enabled = true),
    OtherBusinessEntry("电子受理单"),
    OtherBusinessEntry("我的订单", enabled = true),
    OtherBusinessEntry("我的套餐", enabled = true),
    OtherBusinessEntry("积分", enabled = true),
    OtherBusinessEntry("话费 / 账单", enabled = true),
    OtherBusinessEntry("返费 / 赠费", enabled = true),
    OtherBusinessEntry("资费专区", enabled = true),
    OtherBusinessEntry("附近营业厅"),
)

/** M9 functional shell. Remaining entries are enabled only when their later substages land. */
@Composable
fun OtherBusinessScreen(
    onOpenOrderedBusiness: () -> Unit,
    onOpenVideoRing: () -> Unit,
    onOpenMyOrder: () -> Unit,
    onOpenMyPackage: () -> Unit,
    onOpenIntegral: () -> Unit,
    onOpenPhoneBill: () -> Unit,
    onOpenRebateAndGift: () -> Unit,
    onOpenTariffZone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "其它业务",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = "当前先迁移业务功能；页面视觉将在后续逐页精修。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(otherBusinessEntries.chunked(2)) { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowEntries.forEach { entry ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = entry.enabled) {
                                    when (entry.title) {
                                        "已订业务" -> onOpenOrderedBusiness()
                                        "视频彩铃会员" -> onOpenVideoRing()
                                        "我的订单" -> onOpenMyOrder()
                                        "我的套餐" -> onOpenMyPackage()
                                        "积分" -> onOpenIntegral()
                                        "话费 / 账单" -> onOpenPhoneBill()
                                        "返费 / 赠费" -> onOpenRebateAndGift()
                                        "资费专区" -> onOpenTariffZone()
                                    }
                                },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (entry.enabled) "已接入" else "后续迁移",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (rowEntries.size == 1) {
                        Surface(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}
