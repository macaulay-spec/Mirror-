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
 * JARVIS Design System — Color Tokens (V4 mockup alignment)
 *
 * Base: deep teal-navy (#0A1520 family) from designs/full_app, not true black.
 * Accent: saturated cyan #00D4FF (V4) with #00E5FF highlight.
 * State colors: each state keeps its OWN hue (violet/amber/coral/green),
 * only the presence family follows the V4 cyan.
 * Never pure #00FFFF or #FF0000.
 */
object JarvisColors {
    // ── Backgrounds (V4 / Mark 85 Obsidian & Nano-Carbon) ───────────────
    val VoidBlack = Color(0xFF070B11)       // nano-carbon root background
    val DarkSpace = Color(0xFF0D1622)       // cards, sheets
    val SurfaceCard = Color(0xFF132234)     // raised/hover surface
    val SurfaceDark = Color(0xFF0D1622)     // alias for legacy code
    val SurfaceGlass = Color(0x990C1624)    // Apple glass panels (40-60% opacity with blur)
    val SurfaceGlassCyan = Color(0x66081E32) // JARVIS message glass tint (faint arc cyan)
    val SurfaceGlassElevated = Color(0xB8132338) // elevated glass dock/cards

    // ── Accents (Mark 85 Stark Nanotech + Apple Luminescence) ────────────
    val Presence = Color(0xFF00D4FF)        // Arc reactor cyan — Jarvis core identity
    val PresenceBright = Color(0xFF00F0FF)  // Pure Mark 85 Arc highlight
    val PresenceCore = Color(0xFFE2F9FF)    // Plasma core white-cyan
    val PresenceDeep = Color(0xFF008BB8)    // Deep reactor blue
    val ElectricBlue = Color(0xFF1D63FF)    // Stark repulsor electric blue
    val StarkGold = Color(0xFFFFC043)       // Mark 85 Nanotech Gold accent
    val StarkCrimson = Color(0xFFFF3B47)    // Mark 85 Armor Hot-Rod crimson accent
    val Warmth = Color(0xFFFFB259)          // Soft amber — thinking / warning highlight

    // ── State Colors (V4 presence family + functional state hues) ────────
    val StateIdle = Color(0xAA00D4FF)       // visible presence (~67% alpha)
    val StateListening = Color(0xE600D4FF)  // bright presence (~90%)
    val StateThinking = Color(0xFFB79CFF)   // soft violet
    val StateExecuting = Color(0xFFF5B87A)  // warmth amber
    val StateSpeaking = Color(0xFF00D4FF)   // full brightness presence
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

    // ── Text (V4: #99BBCC muted teal family) ─────────────────────────────
    val TextPrimary = Color(0xFFE6EEF5)     // slightly cool white
    val TextSecondary = Color(0xFF99BBCC)   // V4 muted teal
    val TextMuted = Color(0xFF5A7386)       // subtle, for captions

    // ── Borders (V4: #336699 steel-blue family) ──────────────────────────
    val Hairline = Color(0x1E99BBCC)        // subtle steel border
    val BorderGlass = Color(0x145A9BD6)     // inner highlight, faint cyan-blue
    val BorderSteel = Color(0x55336699)     // V4 card border (#336699 @ 33%)
    val GridLine = Color(0x0A00D4FF)        // cyan at 4% — barely-there grid

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
