package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorErrorSoft = androidx.compose.ui.graphics.Color(0xFFFCA5A5)

private val AutomatonDarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldMintContainer,
    onPrimaryContainer = EmeraldMintSoft,
    secondary = AmberGold,
    onSecondary = ObsidianBg,
    secondaryContainer = AmberGoldContainer,
    onSecondaryContainer = AmberGoldSoft,
    tertiary = ElectricCyan,
    onTertiary = ObsidianBg,
    tertiaryContainer = ElectricCyanContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceElevated,
    surfaceContainerHigh = MidnightSurfaceHighest,
    outline = BorderSlate,
    outlineVariant = BorderSlate.copy(alpha = 0.5f),
    error = CoralDanger,
    onError = TextPrimary,
    errorContainer = CoralDangerContainer,
    onErrorContainer = ColorErrorSoft
)

private val AutomatonShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AutomatonDeskTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AutomatonDarkColorScheme,
        typography = AutomatonTypography,
        shapes = AutomatonShapes,
        content = content
    )
}
