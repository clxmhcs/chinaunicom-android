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

/**
 * M9-D rough functional account selector matching the current iOS IntegralAccountSelectionView
 * boundary: enabled persisted 11-digit mobile accounts only. Independent broadband accounts are
 * intentionally excluded from Integral.
 */
@Composable
fun OtherIntegralAccountSelectionScreen(
    accounts: List<UnicomAccount>,
    onBack: () -> Unit,
    onOpenAccount: (UUID) -> Unit,
) {
    val mobileAccounts = accounts
        .filter { account -> account.isEnabled && account.mobile.count { it.isDigit() } == 11 }
        .sortedBy { it.sortOrder }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("积分", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择要查询的号码", style = MaterialTheme.typography.titleMedium)
                Text(
                    "积分按号码分别查询，请选择当前 App 已保存的手机号码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (mobileAccounts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("暂无手机号码", fontWeight = FontWeight.SemiBold)
                        Text(
                            "请先在设置中保存可用的联通手机号码凭据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(mobileAccounts, key = { it.id }) { account ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(account.id) },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(maskMobile(account.mobile), fontWeight = FontWeight.SemiBold)
                        Text(
                            "查询该号码的积分信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun maskMobile(raw: String): String {
    val mobile = raw.trim()
    return if (mobile.length >= 7) "${mobile.take(3)}****${mobile.takeLast(4)}" else mobile
}
