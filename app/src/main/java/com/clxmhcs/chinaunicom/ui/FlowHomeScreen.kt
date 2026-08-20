package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.ui.components.UnicomAccountCard
import com.clxmhcs.chinaunicom.ui.components.UnicomBottomNavigationBar
import com.clxmhcs.chinaunicom.ui.components.UnicomHeader
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard

/**
 * M4-G2-C7
 * FlowHomeScreen consumes FlowUiState.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel = viewModel()
) {
    val uiState by flowViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            UnicomHeader(title = "流量")

            when (val state = uiState) {
                FlowUiState.Loading -> {
                    Text(text = "加载中...")
                }

                is FlowUiState.Error -> {
                    Text(text = state.message)
                }

                is FlowUiState.Content -> {
                    val account = state.overview.accounts.firstOrNull()
                    val quotas = account?.remainingData.orEmpty()

                    if (account != null) {
                        UnicomAccountCard(
                            number = account.maskedNumber,
                            location = account.location,
                            planName = account.planName,
                            balance = account.balance
                        )
                    }

                    quotas.forEach { quota ->
                        val total = quota.total ?: 0L
                        val used = quota.used ?: 0L
                        val safeUnit = quota.unit ?: "MB"
                        val progress = if (total > 0L) {
                            (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        UnicomQuotaCard(
                            title = quota.title,
                            subtitle = account?.maskedNumber ?: "中国联通号码",
                            remaining = "剩余 ${formatQuota(total - used, safeUnit)}",
                            detail = "已用 ${formatQuota(used, safeUnit)} / 总量 ${formatQuota(total, safeUnit)}",
                            progress = progress
                        )
                    }
                }
            }
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}

private fun formatQuota(value: Long, unit: String): String {
    return if (unit.equals("MB", ignoreCase = true) && value >= 1024) {
        val gb = value.toDouble() / 1024.0
        "%.2fGB".format(gb)
    } else {
        "${value}${unit}"
    }
}
