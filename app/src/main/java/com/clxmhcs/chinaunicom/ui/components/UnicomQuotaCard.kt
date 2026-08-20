package com.clxmhcs.chinaunicom.ui.components

import androidx.compose.foundation.layout.Column
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
 * Reusable iOS-style quota card.
 *
 * subtitle is separated so the same component can represent:
 * - package title
 * - quota category
 * - remaining value
 */
@Composable
fun UnicomQuotaCard(
    title: String,
    subtitle: String,
    remaining: String
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1E)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp
            )
            Text(
                text = subtitle,
                color = Color.LightGray,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = remaining,
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
