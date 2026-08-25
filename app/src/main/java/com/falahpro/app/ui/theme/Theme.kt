package com.falahpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FalahLightColorScheme = lightColorScheme(
    primary = FalahColors.OldMoneyGreen,
    onPrimary = FalahColors.OnPrimary,
    primaryContainer = FalahColors.SoftBrass,
    onPrimaryContainer = FalahColors.Forest,
    secondary = FalahColors.Forest,
    onSecondary = FalahColors.Ivory,
    secondaryContainer = FalahColors.WarmSand,
    onSecondaryContainer = FalahColors.InkBrown,
    tertiary = FalahColors.Brass,
    onTertiary = FalahColors.InkBrown,
    background = FalahColors.Ivory,
    onBackground = FalahColors.InkBrown,
    surface = FalahColors.Surface,
    onSurface = FalahColors.InkBrown,
    surfaceVariant = FalahColors.ButterCream,
    onSurfaceVariant = FalahColors.WarmBrown,
    outline = FalahColors.Outline,
    error = FalahColors.Danger,
    onError = FalahColors.Ivory
)

/**
 * Canonical Falah Pro theme. Light editorial identity — not system-dark maroon.
 */
@Composable
fun FalahProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FalahLightColorScheme,
        typography = FalahTypography,
        shapes = FalahMaterialShapes,
        content = content
    )
}
