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
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.ui.components.UnicomAccountCard
import com.clxmhcs.chinaunicom.ui.components.UnicomBottomNavigationBar
import com.clxmhcs.chinaunicom.ui.components.UnicomHeader
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard
import java.util.Locale

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
                    val quotas = account?.packages.orEmpty()

                    if (account != null) {
                        UnicomAccountCard(
                            number = account.mobile,
                            location = null,
                            planName = account.packageName.takeIf { it.isNotBlank() },
                            balance = account.balanceYuan?.let { "${it}元" },
                        )
                    }

                    quotas.forEach { quota ->
                        val resolvedQuotaType = account?.quotaType(quota) ?: quota.detectedQuotaType
                        val progress = quota.detailDisplayFraction(resolvedQuotaType)?.toFloat() ?: 0f
                        val remainingText = if (resolvedQuotaType == QuotaType.UNLIMITED) {
                            "不限量"
                        } else {
                            "剩余 ${formatQuota(quota.remainingMB, "MB")}"
                        }

                        UnicomQuotaCard(
                            title = quota.originalName,
                            subtitle = account?.mobile ?: "中国联通号码",
                            remaining = remainingText,
                            detail = "已用 ${formatQuota(quota.usedMB, "MB")} / 总量 ${formatQuota(quota.totalMB, "MB")}",
                            progress = progress,
                        )
                    }
                }
            }
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}

private fun formatQuota(value: Double?, unit: String): String {
    if (value == null) return "--"
    return if (unit.equals("MB", ignoreCase = true) && value >= 1024.0) {
        val gb = value / 1024.0
        String.format(Locale.US, "%.2fGB", gb)
    } else if (value % 1.0 == 0.0) {
        "${value.toLong()}$unit"
    } else {
        "$value$unit"
    }
}
