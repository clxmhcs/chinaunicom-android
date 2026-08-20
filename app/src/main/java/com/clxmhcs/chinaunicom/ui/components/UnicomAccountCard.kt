package com.clxmhcs.chinaunicom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnicomAccountCard(
    number: String,
    location: String?,
    planName: String?,
    balance: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column(
            modifier = Modifier
                .background(Color.Transparent)
                .padding(18.dp)
        ) {
            Text(
                text = number,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (!location.isNullOrBlank()) {
                Text(
                    text = location,
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
            if (!planName.isNullOrBlank()) {
                Text(
                    text = planName,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!balance.isNullOrBlank()) {
                Text(
                    text = "余额 $balance",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
