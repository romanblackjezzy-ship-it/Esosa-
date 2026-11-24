package com.example.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode
import com.example.data.model.ChatWallpaper

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
    val onSendFab: Color,
    val onlineDot: Color,
    val callDarkSurface: Color,
    val callDarkSurfaceAlt: Color,
    val callAcceptGreen: Color,
    val callEndRed: Color
)

@Immutable
data class EsosaGlassColors(
    val isDark: Boolean,
    val backdropScrim: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val specularBorder: Color,
    val innerSectionSurface: Color,
    val ambientGlowPrimary: Color,
    val ambientGlowSecondary: Color,
    val recommendedAccent: Color,
    val criticalAccent: Color,
    val successAccent: Color
)

private val EsosaLightGlassTokens = EsosaGlassColors(
    isDark = false,
    backdropScrim = EsosaGlassBackdropDay,
    surfaceTop = EsosaGlassSurfaceTopDay,
    surfaceBottom = EsosaGlassSurfaceBottomDay,
    specularBorder = EsosaGlassBorderDay,
    innerSectionSurface = EsosaGlassInnerCardDay,
    ambientGlowPrimary = EsosaGlassGlowPrimaryDay,
    ambientGlowSecondary = EsosaGlassGlowSecondaryDay,
    recommendedAccent = EsosaWarningAmber,
    criticalAccent = EsosaCallEndRed,
    successAccent = EsosaCallAcceptGreen
)

private val EsosaDarkGlassTokens = EsosaGlassColors(
    isDark = true,
    backdropScrim = EsosaGlassBackdropNight,
    surfaceTop = EsosaGlassSurfaceTopNight,
    surfaceBottom = EsosaGlassSurfaceBottomNight,
    specularBorder = EsosaGlassBorderNight,
    innerSectionSurface = EsosaGlassInnerCardNight,
    ambientGlowPrimary = EsosaGlassGlowPrimaryNight,
    ambientGlowSecondary = EsosaGlassGlowSecondaryNight,
    recommendedAccent = EsosaWarningAmberDark,
    criticalAccent = EsosaCallEndRed,
    successAccent = EsosaSecondaryLight
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
    sendFab = EsosaPrimaryBlue,
    onSendFab = Color.White,
    onlineDot = EsosaOnlineGreen,
    callDarkSurface = EsosaCallDarkSurface,
    callDarkSurfaceAlt = EsosaCallDarkSurfaceAlt,
    callAcceptGreen = EsosaCallAcceptGreen,
    callEndRed = EsosaCallEndRed
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
    sendFab = EsosaPrimaryLight,
    onSendFab = Color.White,
    onlineDot = EsosaOnlineGreen,
    callDarkSurface = EsosaCallDarkSurface,
    callDarkSurfaceAlt = EsosaCallDarkSurfaceAlt,
    callAcceptGreen = EsosaCallAcceptGreen,
    callEndRed = EsosaCallEndRed
)

fun wallpaperCanvasAndPattern(wallpaper: ChatWallpaper, isDark: Boolean): Pair<Color, Color> {
    return when (wallpaper) {
        ChatWallpaper.CLASSIC -> if (isDark) TelegramCanvasNight to TelegramPatternNight else TelegramCanvasDay to TelegramPatternDay
        ChatWallpaper.OCEANIC -> if (isDark) WallpaperOceanicNight to WallpaperOceanicPatternNight else WallpaperOceanicDay to WallpaperOceanicPatternDay
        ChatWallpaper.BOTANICAL -> if (isDark) WallpaperBotanicalNight to WallpaperBotanicalPatternNight else WallpaperBotanicalDay to WallpaperBotanicalPatternDay
        ChatWallpaper.MIDNIGHT -> if (isDark) WallpaperMidnightNight to WallpaperMidnightPatternNight else WallpaperMidnightDay to WallpaperMidnightPatternDay
    }
}

val LocalTelegramColors = staticCompositionLocalOf { TelegramLightTokens }
val LocalEsosaGlassColors = staticCompositionLocalOf { EsosaLightGlassTokens }

val MaterialTheme.telegramColors: TelegramColors
    @Composable
    @ReadOnlyComposable
    get() = LocalTelegramColors.current

val MaterialTheme.esosaGlassColors: EsosaGlassColors
    @Composable
    @ReadOnlyComposable
    get() = LocalEsosaGlassColors.current

private val EsosaDarkColorScheme = darkColorScheme(
    primary = EsosaPrimaryLight,
    onPrimary = Color(0xFF06253A),
    primaryContainer = TelegramOutgoingNight,
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = EsosaSecondaryMint,
    onSecondary = Color(0xFF002420),
    secondaryContainer = Color(0xFF153D3B),
    onSecondaryContainer = Color(0xFFBDF5EE),
    tertiary = EsosaPrimaryBlue,
    onTertiary = Color.White,
    tertiaryContainer = TelegramSurfaceHighestNight,
    onTertiaryContainer = Color(0xFFD8E8F8),
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
    primary = InstagramBlue,
    onPrimary = Color.White,
    primaryContainer = InstagramBlueSoft,
    onPrimaryContainer = Color(0xFF00325A),
    secondary = InstagramBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = InstagramChipGray,
    onSecondaryContainer = InstagramInk,
    tertiary = InstagramHeartRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBEE),
    onTertiaryContainer = Color(0xFF6B0B10),
    background = InstagramWhite,
    onBackground = InstagramBlack,
    surface = InstagramWhite,
    onSurface = InstagramInk,
    surfaceVariant = InstagramChipGray,
    onSurfaceVariant = InstagramMutedText,
    surfaceContainerLowest = InstagramWhite,
    surfaceContainerLow = InstagramOffWhite,
    surfaceContainer = InstagramChipGray,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFDADADA),
    outline = Color(0xFFC7C7CC),
    outlineVariant = InstagramHairline,
    error = InstagramHeartRed,
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
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EsosaDarkColorScheme else EsosaLightColorScheme
    val telegramTokens = if (darkTheme) TelegramDarkTokens else TelegramLightTokens
    val glassTokens = if (darkTheme) EsosaDarkGlassTokens else EsosaLightGlassTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalTelegramColors provides telegramTokens,
        LocalEsosaGlassColors provides glassTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EsosaTypography,
            shapes = EsosaShapes,
            content = content
        )
    }
}
