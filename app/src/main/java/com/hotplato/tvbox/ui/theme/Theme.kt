package com.hotplato.tvbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvDarkColorScheme = darkColorScheme(
    primary = TvPrimary,
    onPrimary = Color.White,
    primaryContainer = TvPrimaryContainer,
    onPrimaryContainer = TvOnBackground,
    secondary = TvAccent,
    onSecondary = Color.White,
    background = TvBackground,
    onBackground = TvOnBackground,
    surface = TvSurface,
    onSurface = TvOnSurface,
    surfaceVariant = TvSurfaceVariant,
    onSurfaceVariant = TvMuted,
    border = TvFocusBorder,
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = TvTypography,
        content = content,
    )
}
