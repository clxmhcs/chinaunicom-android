package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID

/** M9-F iOS-equivalent selector: persisted enabled mobile-account authority only. */
@Composable
fun OtherRebateAndGiftAccountSelectionScreen(
    accounts: List<UnicomAccount>,
    onBack: () -> Unit,
    onOpenAccount: (UUID) -> Unit,
) {
    val targets = accounts
        .filter(UnicomAccount::isEnabled)
        .sortedWith(compareBy<UnicomAccount> { it.sortOrder }.thenBy { it.mobile })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("返费/赠费 查询", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择要查询的号码", style = MaterialTheme.typography.titleMedium)
                Text(
                    "返费/赠费按号码分别查询，请选择当前 App 已保存的手机号码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (targets.isEmpty()) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("暂无号码", fontWeight = FontWeight.SemiBold)
                        Text("请先在设置中保存可用的联通号码凭据。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(targets, key = { it.id }) { account ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(account.id) },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(maskRebateMobile(account.mobile), fontWeight = FontWeight.SemiBold)
                        Text(
                            "使用该号码凭据登录后进入返费/赠费查询页面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun maskRebateMobile(raw: String): String {
    val mobile = raw.trim()
    return if (mobile.length >= 7) "${mobile.take(3)}****${mobile.takeLast(4)}" else mobile
}
