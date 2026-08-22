package com.offlineai.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val OfflineDarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = OnPrimaryDark,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = OnSurfaceLight,
    secondary = SecondaryGreen,
    onSecondary = OnPrimaryDark,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = OnSurfaceLight,
    tertiary = AccentOrange,
    onTertiary = OnPrimaryDark,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = OnSurfaceLight,
    background = DarkBackground,
    onBackground = OnSurfaceLight,
    surface = DarkSurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    error = DangerRed,
    onError = OnPrimaryDark,
    errorContainer = Color(0xFF3D1214),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = BorderColor,
    outlineVariant = DarkSurfaceVariant,
    inverseSurface = OnSurfaceLight,
    inverseOnSurface = DarkBackground,
    inversePrimary = Color(0xFF1F6FEB),
    scrim = Color(0xCC0D1117),
    surfaceBright = DarkSurfaceVariant,
    surfaceDim = CodeBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF2D333B),
    surfaceContainerLow = DarkBackground,
    surfaceContainerLowest = CodeBackground,
)

private val OfflineTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = OnSurfaceLight,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = OnSurfaceLight,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = OnSurfaceLight,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = OnSurfaceLight,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = OnSurfaceLight,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = OnSurfaceLight,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = OnSurfaceLight,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = OnSurfaceMuted,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = OnSurfaceLight,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = OnSurfaceMuted,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        color = OnSurfaceMuted,
    ),
)

@Composable
fun OfflineAITheme(
    // Always dark — matches Offline Studio web (GitHub-dark IDE)
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    val _forceDark = darkTheme || isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = OfflineDarkColorScheme,
        typography = OfflineTypography,
        content = content,
    )
}
