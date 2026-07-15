package com.hotplato.tvbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/** Keeps the Compose UI visually aligned with the enlarged legacy layouts. */
private const val TvUiScale = 1.2f

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density * TvUiScale,
            fontScale = density.fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = TvDarkColorScheme,
            typography = TvTypography,
            content = content,
        )
    }
}
