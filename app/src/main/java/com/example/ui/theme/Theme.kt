package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsosaDarkColorScheme = darkColorScheme(
    primary = EsosaCoral,
    onPrimary = Color(0xFF1A0703),
    primaryContainer = EsosaCoralContainerDark,
    onPrimaryContainer = EsosaCoralSoft,
    secondary = EsosaIndigo,
    onSecondary = Color(0xFF0A1230),
    secondaryContainer = EsosaIndigoContainerDark,
    onSecondaryContainer = EsosaIndigoSoft,
    tertiary = EsosaMint,
    onTertiary = Color(0xFF022622),
    tertiaryContainer = EsosaMintDark,
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF2F3858),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF262E4A),
    error = ErrorCoral,
    onError = Color.White,
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFD5D7)
)

private val EsosaLightColorScheme = lightColorScheme(
    primary = EsosaCoralDeep,
    onPrimary = Color.White,
    primaryContainer = EsosaCoralSoft,
    onPrimaryContainer = Color(0xFF420D03),
    secondary = EsosaIndigoDeep,
    onSecondary = Color.White,
    secondaryContainer = EsosaIndigoSoft,
    onSecondaryContainer = Color(0xFF121E52),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF063833),
    background = AlabasterBg,
    onBackground = AlabasterTextPrimary,
    surface = AlabasterSurface,
    onSurface = AlabasterTextPrimary,
    surfaceVariant = AlabasterSurfaceHigh,
    onSurfaceVariant = AlabasterTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = AlabasterSurfaceHigh,
    surfaceContainerHigh = AlabasterSurfaceHighest,
    surfaceContainerHighest = Color(0xFFD8DFEF),
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E6F2),
    error = ErrorCoral,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF6B0B10)
)

val EsosaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EsosaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EsosaDarkColorScheme else EsosaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EsosaTypography,
        shapes = EsosaShapes,
        content = content
    )
}
