package com.jarvis.core.theme

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

/**
 * JARVIS Design System — Color Tokens
 *
 * Base: deep graphite-blue, not true black.
 * Two accents: ice-blue (presence) + soft amber (warmth).
 * State colors: each state gets its own hue, not brightness steps of cyan.
 * Never pure #00FFFF or #FF0000.
 */
object JarvisColors {
    // ── Backgrounds ──────────────────────────────────────────────────────
    val VoidBlack = Color(0xFF0B0F17)       // root background
    val DarkSpace = Color(0xFF121826)       // cards, sheets
    val SurfaceCard = Color(0xFF1A2232)     // raised/hover surface
    val SurfaceDark = Color(0xFF121826)      // alias for legacy code
    val SurfaceGlass = Color(0x8C121826)    // glass panels (rgba 18,24,38,0.55)
    val SurfaceGlassElevated = Color(0xA3121826) // elevated glass

    // ── Accents ──────────────────────────────────────────────────────────
    val Presence = Color(0xFF6FD3FF)        // ice-blue — Jarvis's core identity
    val Warmth = Color(0xFFF5B87A)          // soft amber — highlights, thinking accent

    // ── State Colors ─────────────────────────────────────────────────────
    val StateIdle = Color(0x666FD3FF)       // dim presence (40% opacity)
    val StateListening = Color(0xE66FD3FF)  // bright presence (~90%)
    val StateThinking = Color(0xFFB79CFF)   // soft violet
    val StateExecuting = Color(0xFFF5B87A)  // warmth amber
    val StateSpeaking = Color(0xFF6FD3FF)   // full brightness presence
    val StateError = Color(0xFFFF8A80)      // warm coral, never pure red
    val StateSuccess = Color(0xFF7EE8B8)    // soft green, fades quickly

    // ── Legacy aliases (used by existing code, migrate gradually) ────────
    val CyanPrimary = Presence
    val CyanBright = Presence
    val CyanDim = Presence.copy(alpha = 0.5f)
    val TealSecondary = StateSuccess
    val AmberWarning = Warmth
    val CrimsonAlert = StateError
    val PurpleSync = StateThinking

    // ── Text ─────────────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFE8ECF1)     // slightly warm white
    val TextSecondary = Color(0xFF8A95A5)   // muted, readable
    val TextMuted = Color(0xFF4D5B6E)       // subtle, for captions

    // ── Borders ──────────────────────────────────────────────────────────
    val Hairline = Color(0x14FFFFFF)        // rgba(255,255,255,0.08) — never cyan
    val BorderGlass = Color(0x0FFFFFFF)     // rgba(255,255,255,0.06) — inner highlight

    // Legacy border aliases
    val BorderCyan = Hairline
    val BorderCyanBright = Hairline

    // ── Gradients ────────────────────────────────────────────────────────
    val GlassGradient = Brush.verticalGradient(
        listOf(
            SurfaceGlass,
            SurfaceGlass.copy(alpha = SurfaceGlass.alpha * 0.4f)
        )
    )

    val CoreRadialGradient = Brush.radialGradient(
        listOf(
            Presence.copy(alpha = 0.35f),
            Presence.copy(alpha = 0.10f),
            Color.Transparent
        )
    )

    val HeaderGradient = Brush.horizontalGradient(
        listOf(Presence, StateSuccess)
    )
}

@Immutable
data class JarvisExtendedColors(
    val presence: Color = JarvisColors.Presence,
    val warmth: Color = JarvisColors.Warmth,
    val glassBorder: Color = JarvisColors.Hairline,
    val glassBackground: Color = JarvisColors.SurfaceGlass,
    val amberWarning: Color = JarvisColors.Warmth,
    val emeraldSuccess: Color = JarvisColors.StateSuccess,
    val crimsonError: Color = JarvisColors.StateError
)

val LocalJarvisColors = staticCompositionLocalOf { JarvisExtendedColors() }

private val JarvisDarkColorScheme: ColorScheme = darkColorScheme(
    primary = JarvisColors.Presence,
    onPrimary = JarvisColors.VoidBlack,
    primaryContainer = JarvisColors.SurfaceGlass,
    onPrimaryContainer = JarvisColors.Presence,
    secondary = JarvisColors.Warmth,
    onSecondary = JarvisColors.VoidBlack,
    tertiary = JarvisColors.StateThinking,
    background = JarvisColors.VoidBlack,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.DarkSpace,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.SurfaceCard,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.Hairline,
    outlineVariant = JarvisColors.BorderGlass,
    error = JarvisColors.StateError,
    onError = Color.White
)

/**
 * JARVIS Typography — clean geometric-humanist sans.
 * Monospace reserved for technical readouts only.
 */
val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,   // Space Grotesk would be ideal; using system sans
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 2.sp,             // generous at display size only
        color = JarvisColors.TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.sp,
        color = JarvisColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = JarvisColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = JarvisColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = JarvisColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = JarvisColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = JarvisColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
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
