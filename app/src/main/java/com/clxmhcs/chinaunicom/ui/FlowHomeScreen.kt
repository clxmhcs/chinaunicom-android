package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M4-G2-B
 *
 * Traffic home visual refinement.
 * Data remains separated from UI and will be connected through BusinessOverview later.
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "流量",
                    color = Color.White,
                    fontSize = 34.sp
                )
                Text(
                    text = "刷新",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C1C1E)
                )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "中国联通号码",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "套餐流量",
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "剩余流量 -- GB",
                        color = Color.White,
                        fontSize = 28.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "数据接口接入后显示实时余量",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("流量", "语音", "综合业务", "其它业务", "设置").forEachIndexed { index, item ->
                Text(
                    text = item,
                    color = if (index == 0) Color.White else Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
