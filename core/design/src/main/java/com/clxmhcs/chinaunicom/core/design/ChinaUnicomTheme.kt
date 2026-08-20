package com.clxmhcs.chinaunicom.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ChinaUnicomColors.BrandBlue,
    background = ChinaUnicomColors.LightBackground,
    surface = ChinaUnicomColors.LightSurface,
    onPrimary = ChinaUnicomColors.LightSurface,
    onBackground = ChinaUnicomColors.LightPrimaryText,
    onSurface = ChinaUnicomColors.LightPrimaryText,
    onSurfaceVariant = ChinaUnicomColors.LightSecondaryText,
)

private val DarkColorScheme = darkColorScheme(
    primary = ChinaUnicomColors.BrandBlue,
    background = ChinaUnicomColors.DarkBackground,
    surface = ChinaUnicomColors.DarkSurface,
    onPrimary = ChinaUnicomColors.LightSurface,
    onBackground = ChinaUnicomColors.DarkPrimaryText,
    onSurface = ChinaUnicomColors.DarkPrimaryText,
    onSurfaceVariant = ChinaUnicomColors.DarkSecondaryText,
)

@Composable
fun ChinaUnicomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ChinaUnicomTypography,
        shapes = ChinaUnicomMaterialShapes,
        content = content,
    )
}
