package com.clxmhcs.chinaunicom.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M4-G2-D1
 * First visual refinement stage for iOS style quota card.
 */
@Composable
fun UnicomQuotaCard(
    title: String,
    subtitle: String,
    remaining: String,
    detail: String? = null,
    progress: Float = 0f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1E)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp
            )

            Text(
                text = subtitle,
                color = Color.LightGray,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = remaining,
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 18.dp)
            )

            detail?.let {
                Text(
                    text = it,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                )
            }
        }
    }
}
