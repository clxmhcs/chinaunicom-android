package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.ui.components.UnicomBottomNavigationBar
import com.clxmhcs.chinaunicom.ui.components.UnicomHeader
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard

/**
 * M4-G2-B3
 *
 * Flow page assembled from reusable iOS-style components.
 * Business data binding remains isolated for the next stage.
 */
@Composable
fun FlowHomeScreen() {
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
                title = "中国联通号码",
                subtitle = "套餐流量",
                remaining = "剩余流量 -- GB"
            )
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}
