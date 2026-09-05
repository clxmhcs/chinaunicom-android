package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class SettingsIosGlyph {
    SORT,
    CORRECTION,
    WIDGET_SINGLE,
    WIDGET_DUAL,
    REFRESH,
    CLOCK,
    GROUP,
    FINANCIAL,
    SEGMENTS,
    LIST,
    KEY,
    SERVER,
    BELL,
    BOOK,
    HEART,
}

@Composable
internal fun SettingsIosGlyphView(
    glyph: SettingsIosGlyph,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3478F6),
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.075f).coerceAtLeast(1.8f)
        val outline = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun arrowHead(tip: Offset, direction: Offset, length: Float = w * 0.16f) {
            val mag = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y).coerceAtLeast(0.001f)
            val ux = direction.x / mag
            val uy = direction.y / mag
            val px = -uy
            val py = ux
            val back = Offset(tip.x - ux * length, tip.y - uy * length)
            drawLine(color, tip, Offset(back.x + px * length * 0.62f, back.y + py * length * 0.62f), stroke, StrokeCap.Round)
            drawLine(color, tip, Offset(back.x - px * length * 0.62f, back.y - py * length * 0.62f), stroke, StrokeCap.Round)
        }

        when (glyph) {
            SettingsIosGlyph.SORT -> {
                val x1 = w * 0.34f
                val x2 = w * 0.66f
                drawLine(color, Offset(x1, h * 0.20f), Offset(x1, h * 0.80f), stroke, StrokeCap.Round)
                arrowHead(Offset(x1, h * 0.18f), Offset(0f, -1f))
                drawLine(color, Offset(x2, h * 0.20f), Offset(x2, h * 0.80f), stroke, StrokeCap.Round)
                arrowHead(Offset(x2, h * 0.82f), Offset(0f, 1f))
            }

            SettingsIosGlyph.CORRECTION -> {
                drawCircle(color, radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f), style = outline)
                drawLine(color, Offset(w * 0.24f, h * 0.42f), Offset(w * 0.68f, h * 0.42f), stroke, StrokeCap.Round)
                arrowHead(Offset(w * 0.70f, h * 0.42f), Offset(1f, 0f), w * 0.12f)
                drawLine(color, Offset(w * 0.76f, h * 0.61f), Offset(w * 0.32f, h * 0.61f), stroke, StrokeCap.Round)
                arrowHead(Offset(w * 0.30f, h * 0.61f), Offset(-1f, 0f), w * 0.12f)
            }

            SettingsIosGlyph.WIDGET_SINGLE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.76f, h * 0.60f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = outline,
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.20f, h * 0.28f),
                    size = Size(w * 0.60f, h * 0.44f),
                    cornerRadius = CornerRadius(w * 0.035f),
                )
            }

            SettingsIosGlyph.WIDGET_DUAL -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.76f, h * 0.60f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = outline,
                )
                drawLine(color, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.78f), stroke * 0.85f, StrokeCap.Round)
            }

            SettingsIosGlyph.REFRESH, SettingsIosGlyph.SEGMENTS -> {
                val arcStroke = Stroke(width = stroke, cap = StrokeCap.Round)
                drawArc(
                    color,
                    startAngle = 28f,
                    sweepAngle = 282f,
                    useCenter = false,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.70f, h * 0.70f),
                    style = arcStroke,
                )
                arrowHead(Offset(w * 0.80f, h * 0.31f), Offset(0.72f, 0.70f), w * 0.13f)
                if (glyph == SettingsIosGlyph.SEGMENTS) {
                    drawLine(color, Offset(w * 0.28f, h * 0.50f), Offset(w * 0.72f, h * 0.50f), stroke * 0.72f, StrokeCap.Round)
                }
            }

            SettingsIosGlyph.CLOCK -> {
                drawCircle(color, radius = w * 0.37f, center = Offset(w * 0.5f, h * 0.5f), style = outline)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.28f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.32f, h * 0.50f), stroke, StrokeCap.Round)
            }

            SettingsIosGlyph.GROUP -> {
                drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.39f, h * 0.38f), style = outline)
                drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.64f, h * 0.40f), style = outline)
                drawArc(color, 205f, 130f, false, Offset(w * 0.20f, h * 0.44f), Size(w * 0.38f, h * 0.35f), style = outline)
                drawArc(color, 205f, 130f, false, Offset(w * 0.44f, h * 0.46f), Size(w * 0.36f, h * 0.33f), style = outline)
                drawCircle(color, radius = w * 0.41f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = stroke * 0.78f))
            }

            SettingsIosGlyph.FINANCIAL -> {
                drawCircle(color, radius = w * 0.39f, center = Offset(w * 0.48f, h * 0.5f), style = outline)
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.42f, h * 0.36f), style = outline)
                drawArc(color, 205f, 130f, false, Offset(w * 0.24f, h * 0.43f), Size(w * 0.36f, h * 0.31f), style = outline)
                drawLine(color, Offset(w * 0.60f, h * 0.64f), Offset(w * 0.69f, h * 0.73f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.69f, h * 0.73f), Offset(w * 0.84f, h * 0.55f), stroke, StrokeCap.Round)
            }

            SettingsIosGlyph.LIST -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, h * 0.19f),
                    size = Size(w * 0.76f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = outline,
                )
                listOf(0.34f, 0.50f, 0.66f).forEach { y ->
                    drawCircle(color, radius = w * 0.025f, center = Offset(w * 0.28f, h * y))
                    drawLine(color, Offset(w * 0.37f, h * y), Offset(w * 0.73f, h * y), stroke * 0.72f, StrokeCap.Round)
                }
            }

            SettingsIosGlyph.KEY -> {
                drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.36f, h * 0.34f), style = Stroke(width = stroke * 1.15f))
                drawLine(color, Offset(w * 0.44f, h * 0.45f), Offset(w * 0.72f, h * 0.73f), stroke * 1.45f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.61f, h * 0.61f), Offset(w * 0.72f, h * 0.50f), stroke * 1.15f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.69f, h * 0.69f), Offset(w * 0.79f, h * 0.59f), stroke * 1.15f, StrokeCap.Round)
            }

            SettingsIosGlyph.SERVER -> {
                listOf(0.22f, 0.43f, 0.64f).forEach { y ->
                    drawRoundRect(
                        color,
                        topLeft = Offset(w * 0.12f, h * y),
                        size = Size(w * 0.76f, h * 0.15f),
                        cornerRadius = CornerRadius(w * 0.035f),
                        style = Stroke(width = stroke * 0.90f),
                    )
                    drawCircle(color, radius = w * 0.025f, center = Offset(w * 0.75f, h * (y + 0.075f)))
                }
            }

            SettingsIosGlyph.BELL -> {
                val p = Path().apply {
                    moveTo(w * 0.27f, h * 0.62f)
                    cubicTo(w * 0.30f, h * 0.50f, w * 0.28f, h * 0.34f, w * 0.50f, h * 0.28f)
                    cubicTo(w * 0.72f, h * 0.34f, w * 0.70f, h * 0.50f, w * 0.73f, h * 0.62f)
                    lineTo(w * 0.78f, h * 0.69f)
                    lineTo(w * 0.22f, h * 0.69f)
                    close()
                }
                drawPath(p, color, style = outline)
                drawArc(color, 10f, 160f, false, Offset(w * 0.42f, h * 0.64f), Size(w * 0.16f, h * 0.15f), style = outline)
                drawCircle(color, radius = w * 0.075f, center = Offset(w * 0.78f, h * 0.24f))
            }

            SettingsIosGlyph.BOOK -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.20f, h * 0.14f),
                    size = Size(w * 0.57f, h * 0.70f),
                    cornerRadius = CornerRadius(w * 0.05f),
                    style = outline,
                )
                drawLine(color, Offset(w * 0.30f, h * 0.16f), Offset(w * 0.30f, h * 0.82f), stroke * 0.85f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.30f, h * 0.72f), Offset(w * 0.70f, h * 0.72f), stroke * 0.65f, StrokeCap.Round)
            }

            SettingsIosGlyph.HEART -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.79f)
                    cubicTo(w * 0.18f, h * 0.59f, w * 0.14f, h * 0.36f, w * 0.29f, h * 0.25f)
                    cubicTo(w * 0.40f, h * 0.17f, w * 0.49f, h * 0.25f, w * 0.50f, h * 0.31f)
                    cubicTo(w * 0.51f, h * 0.25f, w * 0.60f, h * 0.17f, w * 0.71f, h * 0.25f)
                    cubicTo(w * 0.86f, h * 0.36f, w * 0.82f, h * 0.59f, w * 0.50f, h * 0.79f)
                    close()
                }
                drawPath(p, color)
            }
        }
    }
}
