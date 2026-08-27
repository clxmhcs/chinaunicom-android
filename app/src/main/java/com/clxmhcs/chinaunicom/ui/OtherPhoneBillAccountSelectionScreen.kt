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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID

/** M9-E rough functional selector. Visual parity remains deferred. */
@Composable
internal fun OtherPhoneBillAccountSelectionScreen(
    accounts: List<UnicomAccount>,
    representativeAccountID: (UUID) -> UUID?,
    onBack: () -> Unit,
    onOpenAccount: (UUID) -> Unit,
) {
    val accountByID = accounts.associateBy { it.id }
    val seenRepresentativeIDs = mutableSetOf<UUID>()
    val billingAccounts = accounts
        .asSequence()
        .filter { account -> account.isEnabled && account.mobile.count { it.isDigit() } == 11 }
        .sortedBy { it.sortOrder }
        .mapNotNull { source ->
            val representativeID = representativeAccountID(source.id) ?: source.id
            if (!seenRepresentativeIDs.add(representativeID)) return@mapNotNull null
            accountByID[representativeID]?.takeIf { representative ->
                representative.isEnabled && representative.mobile.count { it.isDigit() } == 11
            }
        }
        .toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "返回",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = "话费/账单 查询",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "话费/账单按号码分别查询；有效合账组只显示统一账务代表号码一次。请选择当前 App 已保存的手机号码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (billingAccounts.isEmpty()) {
            Text(
                text = "当前没有可查询话费/账单的手机号码。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(billingAccounts, key = { it.id }) { account ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(account.id) },
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(maskMobile(account.mobile), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "查询该号码的话费/账单",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun maskMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length == 11) "${digits.take(3)}****${digits.takeLast(4)}" else value
}
