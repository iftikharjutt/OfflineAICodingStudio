package com.offlineai.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorBlack = Color(0xFF081014)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = ColorBlack,
    primaryContainer = Color(0xFF16444D),
    onPrimaryContainer = OnSurfaceLight,
    secondary = SecondaryGreen,
    onSecondary = ColorBlack,
    secondaryContainer = Color(0xFF193B23),
    onSecondaryContainer = OnSurfaceLight,
    tertiary = AccentOrange,
    onTertiary = ColorBlack,
    tertiaryContainer = Color(0xFF4A3215),
    onTertiaryContainer = OnSurfaceLight,
    background = DarkBackground,
    onBackground = OnSurfaceLight,
    surface = DarkSurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    outline = DividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F4F7),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2DA),
    onSecondaryContainer = Color(0xFF0A3614),
    tertiary = LightAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C5),
    onTertiaryContainer = Color(0xFF321900),
    background = LightBackground,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightMuted
)

private val StudioTypography = Typography().copy(
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = Typography().labelMedium.copy(fontWeight = FontWeight.Medium)
)

private val StudioShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

@Composable
fun OfflineAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
