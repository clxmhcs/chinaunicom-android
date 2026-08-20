package com.clxmhcs.chinaunicom.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun UnicomBottomNavigationBar(selected: String = "流量") {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("流量", "语音", "综合业务", "其它业务", "设置").forEach { item ->
            Text(
                text = item,
                color = if (item == selected) Color.White else Color.Gray
            )
        }
    }
}
