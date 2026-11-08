package com.applens.inspector.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AppMint = Color(0xFF5DEBB8)
val AppMintDeep = Color(0xFF0C3A2C)
val AppBlue = Color(0xFF8CB7FF)
val AppBlueDeep = Color(0xFF10294A)
val AppAmber = Color(0xFFFFC46B)
val AppAmberDeep = Color(0xFF3D2A0B)
val AppBackground = Color(0xFF070A0F)
val AppSurface = Color(0xFF0E141C)
val AppSurfaceVariant = Color(0xFF151D27)
val AppSurfaceHigh = Color(0xFF1A2431)
val AppText = Color(0xFFE9F1F4)
val AppMuted = Color(0xFF94A6B7)
val AppOutline = Color(0xFF263545)
val AppError = Color(0xFFFF8D8D)

private val AppColorScheme = darkColorScheme(
    primary = AppMint,
    onPrimary = Color(0xFF002117),
    primaryContainer = AppMintDeep,
    onPrimaryContainer = Color(0xFFB9FFE2),
    inversePrimary = Color(0xFF006C4C),
    secondary = AppBlue,
    onSecondary = Color(0xFF001B3D),
    secondaryContainer = AppBlueDeep,
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = AppAmber,
    onTertiary = Color(0xFF2A1800),
    tertiaryContainer = AppAmberDeep,
    onTertiaryContainer = Color(0xFFFFE1B2),
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppMuted,
    surfaceTint = AppMint,
    inverseSurface = Color(0xFFE9F1F4),
    inverseOnSurface = Color(0xFF1A2431),
    error = AppError,
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF4A1111),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = AppOutline,
    outlineVariant = Color(0xFF1B2733),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF28323E),
    surfaceDim = Color(0xFF070A0F),
    surfaceContainerLowest = Color(0xFF05080C),
    surfaceContainerLow = Color(0xFF0B1118),
    surfaceContainer = Color(0xFF0F1720),
    surfaceContainerHigh = AppSurfaceHigh,
    surfaceContainerHighest = Color(0xFF222E3C),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
