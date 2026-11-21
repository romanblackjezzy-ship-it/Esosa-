package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TelegramColors(
    val isDark: Boolean,
    val topBarContainer: Color,
    val onTopBar: Color,
    val onTopBarMuted: Color,
    val chatCanvas: Color,
    val patternTint: Color,
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val outgoingMeta: Color,
    val checkTint: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color,
    val incomingMeta: Color,
    val datePillContainer: Color,
    val onDatePill: Color,
    val unreadBadge: Color,
    val onUnreadBadge: Color,
    val pinnedTint: Color,
    val divider: Color,
    val composerBar: Color,
    val composerInput: Color,
    val sendFab: Color,
    val onSendFab: Color
)

private val TelegramLightTokens = TelegramColors(
    isDark = false,
    topBarContainer = TelegramHeaderDay,
    onTopBar = TelegramOnHeaderDay,
    onTopBarMuted = Color(0xFFD7E6F3),
    chatCanvas = TelegramCanvasDay,
    patternTint = TelegramPatternDay,
    outgoingBubble = TelegramOutgoingDay,
    onOutgoingBubble = TelegramOnOutgoingDay,
    outgoingMeta = TelegramOutgoingMetaDay,
    checkTint = TelegramCheckTintDay,
    incomingBubble = TelegramIncomingDay,
    onIncomingBubble = TelegramOnIncomingDay,
    incomingMeta = TelegramTextSecondaryDay,
    datePillContainer = TelegramDatePillDay,
    onDatePill = Color.White,
    unreadBadge = TelegramUnreadBadgeDay,
    onUnreadBadge = Color.White,
    pinnedTint = TelegramPinnedTintDay,
    divider = TelegramDividerDay,
    composerBar = Color.White,
    composerInput = TelegramSurfaceSecondaryDay,
    sendFab = TelegramAzure,
    onSendFab = Color.White
)

private val TelegramDarkTokens = TelegramColors(
    isDark = true,
    topBarContainer = TelegramHeaderNight,
    onTopBar = TelegramOnHeaderNight,
    onTopBarMuted = Color(0xFFA2B5C7),
    chatCanvas = TelegramCanvasNight,
    patternTint = TelegramPatternNight,
    outgoingBubble = TelegramOutgoingNight,
    onOutgoingBubble = TelegramOnOutgoingNight,
    outgoingMeta = TelegramOutgoingMetaNight,
    checkTint = TelegramCheckTintNight,
    incomingBubble = TelegramIncomingNight,
    onIncomingBubble = TelegramOnIncomingNight,
    incomingMeta = TelegramTextSecondaryNight,
    datePillContainer = TelegramDatePillNight,
    onDatePill = Color.White,
    unreadBadge = TelegramUnreadBadgeNight,
    onUnreadBadge = Color.White,
    pinnedTint = TelegramPinnedTintNight,
    divider = TelegramDividerNight,
    composerBar = TelegramSurfaceNight,
    composerInput = TelegramSurfaceSecondaryNight,
    sendFab = TelegramAzureNight(),
    onSendFab = Color.White
)

private fun TelegramAzureNight(): Color = TelegramAzureLight

val LocalTelegramColors = staticCompositionLocalOf { TelegramLightTokens }

val MaterialTheme.telegramColors: TelegramColors
    @Composable
    @ReadOnlyComposable
    get() = LocalTelegramColors.current

private val EsosaDarkColorScheme = darkColorScheme(
    primary = TelegramAzureLight,
    onPrimary = Color(0xFF06253A),
    primaryContainer = TelegramOutgoingNight,
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = TelegramAzure,
    onSecondary = Color.White,
    secondaryContainer = TelegramSurfaceHighestNight,
    onSecondaryContainer = Color(0xFFD8E8F8),
    tertiary = TelegramTealAccent,
    onTertiary = Color(0xFF002420),
    tertiaryContainer = Color(0xFF153D3B),
    onTertiaryContainer = Color(0xFFBDF5EE),
    background = TelegramCanvasNight,
    onBackground = TelegramTextPrimaryNight,
    surface = TelegramSurfaceNight,
    onSurface = TelegramTextPrimaryNight,
    surfaceVariant = TelegramSurfaceSecondaryNight,
    onSurfaceVariant = TelegramTextSecondaryNight,
    surfaceContainerLowest = TelegramCanvasNight,
    surfaceContainerLow = TelegramSurfaceNight,
    surfaceContainer = TelegramSurfaceSecondaryNight,
    surfaceContainerHigh = TelegramSurfaceHighestNight,
    surfaceContainerHighest = Color(0xFF2F4459),
    outline = Color(0xFF32465B),
    outlineVariant = TelegramDividerNight,
    error = TelegramError,
    onError = Color.White,
    errorContainer = TelegramErrorContainerDark,
    onErrorContainer = Color(0xFFFFD8D6)
)

private val EsosaLightColorScheme = lightColorScheme(
    primary = TelegramAzure,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFC),
    onPrimaryContainer = Color(0xFF06324B),
    secondary = TelegramHeaderDay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEF7),
    onSecondaryContainer = Color(0xFF1D3952),
    tertiary = TelegramTealAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F5F2),
    onTertiaryContainer = Color(0xFF053833),
    background = TelegramSurfaceDay,
    onBackground = TelegramTextPrimaryDay,
    surface = TelegramSurfaceDay,
    onSurface = TelegramTextPrimaryDay,
    surfaceVariant = TelegramSurfaceSecondaryDay,
    onSurfaceVariant = TelegramTextSecondaryDay,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = TelegramSurfaceSecondaryDay,
    surfaceContainer = TelegramSurfaceSecondaryDay,
    surfaceContainerHigh = TelegramSurfaceHighestDay,
    surfaceContainerHighest = Color(0xFFD9E3ED),
    outline = Color(0xFFC5D0DC),
    outlineVariant = TelegramDividerDay,
    error = TelegramError,
    onError = Color.White,
    errorContainer = TelegramErrorContainerLight,
    onErrorContainer = Color(0xFF6B0B10)
)

val EsosaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun EsosaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EsosaDarkColorScheme else EsosaLightColorScheme
    val telegramTokens = if (darkTheme) TelegramDarkTokens else TelegramLightTokens

    CompositionLocalProvider(LocalTelegramColors provides telegramTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EsosaTypography,
            shapes = EsosaShapes,
            content = content
        )
    }
}
