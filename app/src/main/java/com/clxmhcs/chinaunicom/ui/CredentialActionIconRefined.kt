package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class RefinedCredentialActionGlyph {
    LOGIN,
    KEY,
    TRANSFER,
}

@Composable
internal fun RefinedCredentialActionIcon(
    glyph: RefinedCredentialActionGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF3D6CF4),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (glyph) {
            RefinedCredentialActionGlyph.LOGIN -> {
                drawCircle(tint, radius = w * .105f, center = Offset(w * .37f, h * .30f))
                val torso = Path().apply {
                    moveTo(w * .18f, h * .67f)
                    cubicTo(w * .20f, h * .49f, w * .30f, h * .43f, w * .40f, h * .43f)
                    cubicTo(w * .50f, h * .43f, w * .56f, h * .51f, w * .57f, h * .68f)
                    close()
                }
                drawPath(torso, tint)
                drawCircle(tint, radius = w * .085f, center = Offset(w * .67f, h * .46f))
                drawCircle(Color.White, radius = w * .028f, center = Offset(w * .67f, h * .46f))
                drawLine(tint, Offset(w * .67f, h * .54f), Offset(w * .67f, h * .74f), 3.2.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .67f, h * .65f), Offset(w * .75f, h * .65f), 3.2.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .67f, h * .72f), Offset(w * .72f, h * .77f), 3.2.dp.toPx(), StrokeCap.Round)
            }
            RefinedCredentialActionGlyph.KEY -> {
                drawCircle(tint, radius = w * .125f, center = Offset(w * .40f, h * .28f))
                drawCircle(Color.White, radius = w * .045f, center = Offset(w * .40f, h * .28f))
                drawLine(tint, Offset(w * .40f, h * .39f), Offset(w * .40f, h * .78f), 6.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * .40f, h * .62f), Offset(w * .54f, h * .68f), 6.dp.toPx(), StrokeCap.Butt)
                drawLine(tint, Offset(w * .40f, h * .73f), Offset(w * .50f, h * .80f), 6.dp.toPx(), StrokeCap.Butt)
            }
            RefinedCredentialActionGlyph.TRANSFER -> {
                val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * .18f, h * .34f),
                    size = Size(w * .50f, h * .46f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * .30f, h * .43f),
                    size = Size(w * .50f, h * .44f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawLine(tint, Offset(w * .49f, h * .13f), Offset(w * .49f, h * .59f), 2.8.dp.toPx(), StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(w * .35f, h * .28f)
                    lineTo(w * .49f, h * .13f)
                    lineTo(w * .63f, h * .28f)
                }
                drawPath(arrow, tint, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
