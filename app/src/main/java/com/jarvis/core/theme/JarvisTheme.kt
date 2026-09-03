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
 * JARVIS Design System v3 — "Command Deck"
 *
 * Carbon-copy tokens measured directly from the approved mockups
 * (designs/full_app/00-09): true black #000000 base, glass surfaces with a
 * faint blue tint, hairline white borders (rgba 255,255,255,0.08), vivid
 * cyan #19C5FF presence + electric blue #1952FF accents, faint technical
 * grid, soft neon glow.
 */
object JarvisColors {
    // ── Backgrounds (true black, per mockup) ─────────────────────────────
    val VoidBlack = Color(0xFF000000)       // root background — measured #000000
    val DarkSpace = Color(0xFF070B12)       // cards, sheets (blue-tinted near-black)
    val SurfaceCard = Color(0xFF0C111B)     // raised/hover surface
    val SurfaceDark = Color(0xFF070B12)     // alias for legacy code
    val SurfaceGlass = Color(0x73060B14)    // glass panels rgba(6,11,20,0.45)
    val SurfaceGlassElevated = Color(0x99060B14) // elevated glass rgba(6,11,20,0.60)
    val SurfaceGlassCyan = Color(0x59061828)     // JARVIS bubble tint (faint cyan)

    // ── Accents (measured from mockups) ──────────────────────────────────
    val Presence = Color(0xFF19C5FF)        // vivid cyan — JARVIS's core identity
    val ElectricBlue = Color(0xFF1952FF)    // electric blue — executing/active accents
    val Warmth = Color(0xFFF5B87A)          // soft amber — reserved for risk/confirm

    // ── State Colors ─────────────────────────────────────────────────────
    val StateIdle = Color(0xAA19C5FF)       // visible presence (~67% alpha)
    val StateListening = Color(0xFF19C5FF)  // bright vivid cyan
    val StateThinking = Color(0xFF4472FF)   // cyan → electric blue blend
    val StateExecuting = Color(0xFF1952FF)  // electric blue (mockup: "EXECUTING")
    val StateSpeaking = Color(0xFF19C5FF)   // full brightness presence
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
    val TextPrimary = Color(0xFFEAF2FF)     // cool white (per mockup)
    val TextSecondary = Color(0xFF8FA0B8)   // muted, readable
    val TextMuted = Color(0xFF51607A)       // subtle, for captions

    // ── Borders ──────────────────────────────────────────────────────────
    val Hairline = Color(0x14FFFFFF)        // rgba(255,255,255,0.08) — never cyan
    val BorderGlass = Color(0x0FFFFFFF)     // rgba(255,255,255,0.06) — inner highlight
    val BorderCyan = Hairline                               // legacy alias
    val BorderCyanBright = Color(0x3D19C5FF)                // cyan glow border (selected states)

    // ── HUD grid (faint technical grid from mockups) ─────────────────────
    val GridLine = Color(0x0A19C5FF)        // cyan at 4% — barely-there grid

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
        listOf(Presence, ElectricBlue)
    )

    /** Soft neon halo used behind glowing buttons and the orb base. */
    val GlowRadial = Brush.radialGradient(
        listOf(
            Presence.copy(alpha = 0.45f),
            Presence.copy(alpha = 0.16f),
            Color.Transparent
        )
    )

    /** Electric-blue variant of the halo. */
    val GlowRadialBlue = Brush.radialGradient(
        listOf(
            ElectricBlue.copy(alpha = 0.40f),
            ElectricBlue.copy(alpha = 0.14f),
            Color.Transparent
        )
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
    secondary = JarvisColors.ElectricBlue,
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
 * JARVIS Typography — thin, wide-letter-spaced sans for the HUD wordmark;
 * clean geometric-humanist sans everywhere else.
 */
val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 6.sp,             // wordmark-grade spacing at display size
        color = JarvisColors.TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 3.sp,
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
        letterSpacing = 1.sp,
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
