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
 * M4-G2-C2
 * Flow page connected to BusinessOverview state.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel = viewModel()
) {
    val overview by flowViewModel.overview.collectAsState()
    val account = overview.accounts.firstOrNull()

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
                subtitle = account?.balance ?: "套餐流量",
                remaining = "剩余流量待接入"
            )
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}
