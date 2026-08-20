package com.clxmhcs.chinaunicom.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ChinaUnicomShapes {
    val MigrationCardRadius = 18.dp
    val WidgetRadius = 20.dp
    val AccountCardRadius = 24.dp
    val EmptyCardRadius = 24.dp
    val EmptyIconRadius = 26.dp
    val VoiceCardRadius = 26.dp
}

val ChinaUnicomMaterialShapes = Shapes(
    small = RoundedCornerShape(ChinaUnicomShapes.MigrationCardRadius),
    medium = RoundedCornerShape(ChinaUnicomShapes.AccountCardRadius),
    large = RoundedCornerShape(ChinaUnicomShapes.VoiceCardRadius),
)
