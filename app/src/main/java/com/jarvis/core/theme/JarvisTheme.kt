package com.jarvis.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object JarvisColors {
    val VoidBlack = Color(0xFF050811)
    val DarkSpace = Color(0xFF090E1A)
    val SurfaceDark = Color(0xFF0E1626)
    val SurfaceCard = Color(0xFF121D33)
    val SurfaceGlass = Color(0x1800E5FF)
    val SurfaceGlassElevated = Color(0x2800E5FF)

    // Holographic Accents
    val CyanPrimary = Color(0xFF00E5FF)
    val CyanBright = Color(0xFF5CEBFF)
    val CyanDim = Color(0xFF0096B4)
    val TealSecondary = Color(0xFF00F5D4)
    val AmberWarning = Color(0xFFFFB703)
    val CrimsonAlert = Color(0xFFFF3366)
    val PurpleSync = Color(0xFF9D4EDD)

    // Text & Borders
    val TextPrimary = Color(0xFFE2F7FF)
    val TextSecondary = Color(0xFF88A4BD)
    val TextMuted = Color(0xFF4D657C)
    val BorderCyan = Color(0x3300E5FF)
    val BorderCyanBright = Color(0x6600E5FF)
    val BorderGlass = Color(0x1FFFFFFF)

    // Gradients
    val GlassGradient = Brush.verticalGradient(
        listOf(
            Color(0x1A00E5FF),
            Color(0x0800E5FF)
        )
    )

    val CoreRadialGradient = Brush.radialGradient(
        listOf(
            Color(0x3300E5FF),
            Color(0x0F00E5FF),
            Color.Transparent
        )
    )

    val HeaderGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF00E5FF),
            Color(0xFF00F5D4)
        )
    )
}

@Immutable
data class JarvisExtendedColors(
    val cyanGlow: Color = JarvisColors.CyanBright,
    val glassBorder: Color = JarvisColors.BorderCyan,
    val glassBackground: Color = JarvisColors.SurfaceGlass,
    val amberWarning: Color = JarvisColors.AmberWarning,
    val emeraldSuccess: Color = JarvisColors.TealSecondary,
    val crimsonError: Color = JarvisColors.CrimsonAlert
)

val LocalJarvisColors = staticCompositionLocalOf { JarvisExtendedColors() }

private val JarvisDarkColorScheme: ColorScheme = darkColorScheme(
    primary = JarvisColors.CyanPrimary,
    onPrimary = JarvisColors.VoidBlack,
    primaryContainer = JarvisColors.SurfaceGlass,
    onPrimaryContainer = JarvisColors.CyanBright,
    secondary = JarvisColors.TealSecondary,
    onSecondary = JarvisColors.VoidBlack,
    tertiary = JarvisColors.AmberWarning,
    background = JarvisColors.VoidBlack,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.DarkSpace,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.SurfaceCard,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.BorderCyan,
    outlineVariant = JarvisColors.BorderGlass,
    error = JarvisColors.CrimsonAlert,
    onError = Color.White
)

val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 2.sp,
        color = JarvisColors.TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 1.5.sp,
        color = JarvisColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp,
        color = JarvisColors.CyanPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp,
        color = JarvisColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = JarvisColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = JarvisColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = JarvisColors.TextMuted
    )
)

@Composable
fun JarvisTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
