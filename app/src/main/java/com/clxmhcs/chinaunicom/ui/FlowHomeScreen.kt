package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.ui.components.UnicomBottomNavigationBar
import com.clxmhcs.chinaunicom.ui.components.UnicomHeader
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard

/**
 * M4-G2-C3
 * Flow page consumes BusinessOverview quota data.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel = viewModel()
) {
    val overview by flowViewModel.overview.collectAsState()
    val account = overview.accounts.firstOrNull()
    val quota = account?.remainingData?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            UnicomHeader(title = "流量")

            UnicomQuotaCard(
                title = account?.maskedNumber ?: "中国联通号码",
                subtitle = quota?.title ?: "套餐流量",
                remaining = if (quota?.total != null && quota.used != null) {
                    "剩余 ${quota.total - quota.used}${quota.unit}"
                } else {
                    "剩余流量 --"
                },
                detail = if (quota != null && quota.used != null && quota.total != null) {
                    "已用 ${quota.used}${quota.unit} / 总量 ${quota.total}${quota.unit}"
                } else null
            )
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}
