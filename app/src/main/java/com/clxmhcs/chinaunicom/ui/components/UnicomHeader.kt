package com.clxmhcs.chinaunicom.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun UnicomHeader(title: String = "流量") {
    Text(
        text = title,
        color = Color.White,
        fontSize = 36.sp
    )
}
