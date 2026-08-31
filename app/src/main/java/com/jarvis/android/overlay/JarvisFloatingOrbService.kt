package com.jarvis.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.app.NotificationCompat
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.JarvisVisualState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * JARVIS Floating Orb — completely redesigned for maximum visual impact.
 *
 * Visual system:
 *   · Volumetric chromatic core with triple-layer radial glow
 *   · Plasma ring — slow rotating, colour-bleeding dashed ring
 *   · Chromatic aberration shimmer — RGB offsets around core edge
 *   · Particle field — 48 particles in golden-ratio spiral, audio-reactive spread
 *   · Audio waveform arc — real microphone RMS drives ripple height
 *   · HUD tick ring — 36 ticks, every 6th highlighted
 *   · State-specific overlays:
 *       THINKING / WAKING  → 5-segment rotating arc spinner
 *       LISTENING          → live waveform ripple bands
 *       SPEAKING           → green harmonic rings
 *       EXECUTING          → amber radial scanner sweep
 *       ERROR              → red pulsing cross-hatch
 *       SUCCESS            → white starburst flash
 *       OFFLINE            → desaturated + scanlines
 */
class JarvisFloatingOrbService : Service() {

    // CHANGED (mirror fix pass, item 10): this used to be a plain background
    // Service -- no startForeground(), no foregroundServiceType in the
    // manifest -- so Android was free to kill it minutes after the user left
    // the app, taking the Orb with it. It now runs as a foreground service
    // (specialUse type, low-priority ongoing notification, START_STICKY),
    // mirroring how WakeWordForegroundService already does it.

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 180
        }

        val composeView = ComposeView(this)
        val lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.start()
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(null)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val isExpandedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

        composeView.setContent {
            val state by VoiceBus.engineState.collectAsState()
            val audioLevel by VoiceBus.audioLevel.collectAsState()
            val isExpanded by isExpandedFlow.collectAsState()
            
            // Expanded Strip or Compact Orb
            if (isExpanded) {
                ExpandedOrbStrip(
                    state = state,
                    audioLevel = audioLevel,
                    onCollapse = { isExpandedFlow.value = false },
                    onOpenApp = {
                        startActivity(
                            Intent(this@JarvisFloatingOrbService, com.jarvis.app.MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        )
                    }
                )
            } else {
                JarvisOrbContent(state = state, audioLevel = audioLevel, onTap = {
                    isExpandedFlow.value = true
                })
            }
        }

        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f
        var isDragging = false

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchStartX = event.rawX; touchStartY = event.rawY
                    isDragging = false
                    false // Let Compose handle taps if not dragged
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (dx * dx + dy * dy > 100) { // Drag threshold
                        isDragging = true
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(composeView, params)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Snap to nearest edge
                        val metrics = resources.displayMetrics
                        val screenWidth = metrics.widthPixels
                        val snapX = if (params.x + (composeView.width / 2) > screenWidth / 2) screenWidth else 0
                        
                        // Simple snap animation using a thread or coroutine (here just immediate)
                        params.x = snapX
                        windowManager?.updateViewLayout(composeView, params)
                        true // Consume so tap isn't fired
                    } else {
                        false // Pass tap to compose
                    }
                }
                else -> false
            }
        }

        floatingView = composeView
        try { windowManager?.addView(floatingView, params) } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Restart if the system kills us anyway -- the Orb is JARVIS's
        // visible presence and should come back on its own.
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Orb",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating JARVIS orb available over other apps"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, JarvisFloatingOrbService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS orb is active")
            .setContentText("Tap to open JARVIS")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .addAction(0, "Hide orb", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        floatingView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        floatingView = null
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_orb"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.jarvis.android.overlay.ACTION_STOP_ORB"
        var isRunning: Boolean = false
            private set
    }
}

@Composable
private fun ExpandedOrbStrip(
    state: JarvisVisualState,
    audioLevel: Float,
    onCollapse: () -> Unit,
    onOpenApp: () -> Unit
) {
    val palette = orbPalette(state)
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xD90E1626))
            .border(1.dp, palette.coreOuter.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini Orb
        Box(modifier = Modifier.size(48.dp)) {
            JarvisOrbContent(state = state, audioLevel = audioLevel, onTap = onCollapse)
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Status text
        Column(modifier = Modifier.width(120.dp)) {
            Text(
                text = "JARVIS",
                color = palette.coreOuter,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.name,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        // Actions
        IconButton(onClick = onOpenApp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.OpenInFull, contentDescription = "Open App", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close Strip", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Orb Colour Palette
// ─────────────────────────────────────────────────────────────────────────────

private data class OrbPalette(
    val coreInner: Color,
    val coreOuter: Color,
    val ringA: Color,
    val ringB: Color,
    val glow: Color,
    val particle: Color,
    val chromR: Color,
    val chromB: Color
)

private fun orbPalette(state: JarvisVisualState): OrbPalette = when (state) {
    JarvisVisualState.IDLE      -> OrbPalette(Color(0xFFE0FEFF), Color(0xFF00B8D4), Color(0xFF00E5FF), Color(0xFF0096B4), Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0x5500AAFF), Color(0x5500FFEE))
    JarvisVisualState.WAKING    -> OrbPalette(Color(0xFFEAFDFF), Color(0xFF29DEFF), Color(0xFF5CEBFF), Color(0xFF00BCD4), Color(0xFF5CEBFF), Color(0xFFB2EBF2), Color(0x4400CCFF), Color(0x4400FFFF))
    JarvisVisualState.LISTENING -> OrbPalette(Color(0xFFFFFFFF), Color(0xFF00F5D4), Color(0xFF1DE9B6), Color(0xFF00BFA5), Color(0xFF00FFF0), Color(0xFFA7FFEB), Color(0x5500FFD0), Color(0x5500CCFF))
    JarvisVisualState.THINKING  -> OrbPalette(Color(0xFFF3E5FF), Color(0xFF9C27B0), Color(0xFF7C4DFF), Color(0xFFAA00FF), Color(0xFFCE93D8), Color(0xFFEA80FC), Color(0x55FF00FF), Color(0x55AA00FF))
    JarvisVisualState.EXECUTING -> OrbPalette(Color(0xFFFFF8E1), Color(0xFFFF8F00), Color(0xFFFFD740), Color(0xFFFFA000), Color(0xFFFFD54F), Color(0xFFFFE082), Color(0x55FFCC00), Color(0x55FF6600))
    JarvisVisualState.SPEAKING  -> OrbPalette(Color(0xFFE8FFF3), Color(0xFF00C853), Color(0xFF69F0AE), Color(0xFF00BFA5), Color(0xFF1DE9B6), Color(0xFFB9F6CA), Color(0x5500FF88), Color(0x5500FFCC))
    JarvisVisualState.SUCCESS   -> OrbPalette(Color(0xFFFFFFFF), Color(0xFFE0F7FA), Color(0xFFFFFFFF), Color(0xFF80DEEA), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0x88FFFFFF), Color(0x88CCFFFF))
    JarvisVisualState.ERROR     -> OrbPalette(Color(0xFFFFE5E8), Color(0xFFD32F2F), Color(0xFFFF1744), Color(0xFFB71C1C), Color(0xFFFF5252), Color(0xFFFF8A80), Color(0x55FF0044), Color(0x55AA0000))
    JarvisVisualState.OFFLINE   -> OrbPalette(Color(0xFF8A9BB0), Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF37474F), Color(0xFF607D8B), Color(0xFF78909C), Color(0x33607D8B), Color(0x33455A64))
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable Orb
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JarvisOrbContent(
    state: JarvisVisualState,
    audioLevel: Float,
    onTap: () -> Unit
) {
    val palette = orbPalette(state)
    val inf = rememberInfiniteTransition(label = "orb_inf")

    // Slow plasma ring rotation (9 s)
    val plasmaAngle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "plasma")
    // Fast HUD tick ring counter-rotation (4 s)
    val hudAngle by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "hud")
    // Medium arc spinner (2.2 s) for THINKING / WAKING / EXECUTING
    val spinnerAngle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "spinner")
    // Pulse for idle / speaking / success
    val pulseA by inf.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulseA")
    val pulseB by inf.animateFloat(0.80f, 1.20f, infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulseB")
    // Shimmer phase for chromatic aberration
    val shimmerPhase by inf.animateFloat(0f, (2f * PI.toFloat()), infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "shimmer")
    // Particle drift
    val particleDrift by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "pdrift")
    // Scanner sweep for EXECUTING
    val scannerAngle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "scanner")
    // Error pulse
    val errorPulse by inf.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse), label = "errpulse")

    // Reactive audio brightness (spring for snappy response)
    val brightness by animateFloatAsState(
        targetValue = if (state == JarvisVisualState.LISTENING || state == JarvisVisualState.SPEAKING)
            0.5f + audioLevel * 0.5f else 0.35f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "brightness"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val r = size.minDimension / 2f

            // ── 1. Deep void ambient halo ──────────────────────────────────
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = 0.15f), palette.glow.copy(alpha = 0.05f), Color.Transparent),
                    center = center, radius = r * 0.95f
                ), radius = r * 0.95f, center = center
            )

            // ── 2. HUD tick ring ───────────────────────────────────────────
            drawHudRing(center, r * 0.88f, hudAngle, palette.ringA, state)

            // ── 3. Plasma dashed ring ──────────────────────────────────────
            drawPlasmaRing(center, r * 0.72f, plasmaAngle, palette, pulseA)

            // ── 4. State-specific overlay ──────────────────────────────────
            when (state) {
                JarvisVisualState.THINKING, JarvisVisualState.WAKING ->
                    drawArcSpinner(center, r * 0.60f, spinnerAngle, palette.ringA, 5, 60f, 14f)
                JarvisVisualState.EXECUTING ->
                    drawScannerSweep(center, r * 0.62f, scannerAngle, palette.coreOuter)
                JarvisVisualState.LISTENING ->
                    drawWaveformRipple(center, r * 0.58f, audioLevel, palette, shimmerPhase)
                JarvisVisualState.SPEAKING ->
                    drawHarmonicRings(center, r * 0.58f, pulseA, pulseB, audioLevel, palette)
                JarvisVisualState.ERROR ->
                    drawErrorOverlay(center, r * 0.60f, errorPulse, palette)
                JarvisVisualState.SUCCESS ->
                    drawStarburst(center, r * 0.66f, palette)
                JarvisVisualState.OFFLINE ->
                    drawScanlines(center, r * 0.60f, palette)
                else -> {}
            }

            // ── 5. Particle cloud ─────────────────────────────────────────
            drawParticleField(center, r * 0.78f, particleDrift, audioLevel, palette, state)

            // ── 6. Volumetric core sphere ──────────────────────────────────
            drawVolumetricCore(center, r * 0.30f, palette, brightness, shimmerPhase)

            // ── 7. Chromatic aberration shimmer ────────────────────────────
            drawChromaticShimmer(center, r * 0.31f, shimmerPhase, palette)

            // ── 8. Specular white lens highlight ──────────────────────────
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = r * 0.10f,
                center = Offset(cx - r * 0.11f, cy - r * 0.11f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = r * 0.05f,
                center = Offset(cx + r * 0.10f, cy + r * 0.10f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing sub-functions
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawHudRing(
    center: Offset, radius: Float, angle: Float, accent: Color, state: JarvisVisualState
) {
    rotate(angle, pivot = center) {
        val tickCount = 36
        for (i in 0 until tickCount) {
            val a = i * (360f / tickCount) * (PI.toFloat() / 180f)
            val major = i % 6 == 0
            val tickLen = if (major) 10f else 5f
            val ox = center.x + cos(a) * radius
            val oy = center.y + sin(a) * radius
            val ix = center.x + cos(a) * (radius - tickLen)
            val iy = center.y + sin(a) * (radius - tickLen)
            drawLine(
                color = if (major) accent.copy(alpha = 0.9f) else accent.copy(alpha = 0.30f),
                start = Offset(ix, iy),
                end = Offset(ox, oy),
                strokeWidth = if (major) 2.4f else 1.0f,
                cap = StrokeCap.Round
            )
        }
        // Outer ring circle
        drawCircle(
            color = accent.copy(alpha = 0.18f),
            radius = radius,
            center = center,
            style = Stroke(width = 0.8f)
        )
    }
}

private fun DrawScope.drawPlasmaRing(
    center: Offset, radius: Float, angle: Float, palette: OrbPalette, pulse: Float
) {
    val dynRadius = radius * (0.95f + pulse * 0.05f)
    rotate(angle, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    palette.ringA.copy(alpha = 0.85f),
                    palette.ringB.copy(alpha = 0.4f),
                    Color.Transparent,
                    palette.ringA.copy(alpha = 0.65f),
                    palette.ringB.copy(alpha = 0.85f)
                ),
                center = center
            ),
            radius = dynRadius,
            center = center,
            style = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(55f, 22f, 12f, 22f), 0f)
            )
        )
    }
}

private fun DrawScope.drawVolumetricCore(
    center: Offset, coreR: Float, palette: OrbPalette, brightness: Float, phase: Float
) {
    val dynamicR = coreR * (1f + brightness * 0.18f)

    // Outer volumetric glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                palette.coreInner.copy(alpha = 0.30f),
                palette.coreOuter.copy(alpha = 0.22f),
                palette.glow.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = center, radius = dynamicR * 2.4f
        ),
        radius = dynamicR * 2.4f, center = center
    )

    // Middle glow layer
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                palette.coreInner.copy(alpha = 0.70f),
                palette.coreOuter.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = center, radius = dynamicR * 1.5f
        ),
        radius = dynamicR * 1.5f, center = center
    )

    // Solid core
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                palette.coreInner.copy(alpha = 0.96f),
                palette.coreOuter.copy(alpha = 0.85f)
            ),
            center = center, radius = dynamicR
        ),
        radius = dynamicR, center = center
    )

    // Inner nucleus
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = dynamicR * 0.42f,
        center = center
    )

    // Dark void pupil
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF030812), Color(0xFF07111F), Color.Transparent),
            center = center, radius = dynamicR * 0.24f
        ),
        radius = dynamicR * 0.24f, center = center
    )

    // Iris ring
    drawCircle(
        color = palette.ringA.copy(alpha = 0.7f),
        radius = dynamicR * 0.22f,
        center = center,
        style = Stroke(width = 1.2f)
    )
}

private fun DrawScope.drawChromaticShimmer(
    center: Offset, radius: Float, phase: Float, palette: OrbPalette
) {
    // Red channel offset
    val rOffset = Offset(cos(phase) * 4f, sin(phase) * 3f)
    drawCircle(
        color = palette.chromR.copy(alpha = 0.25f),
        radius = radius, center = center + rOffset,
        style = Stroke(width = 1.5f)
    )
    // Blue channel offset (opposite)
    val bOffset = Offset(-cos(phase) * 4f, -sin(phase) * 3f)
    drawCircle(
        color = palette.chromB.copy(alpha = 0.25f),
        radius = radius, center = center + bOffset,
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawArcSpinner(
    center: Offset, radius: Float, rotation: Float,
    color: Color, segments: Int, sweep: Float, gap: Float
) {
    val step = (sweep + gap)
    for (i in 0 until segments) {
        val startAngle = rotation + i * step
        val alpha = 0.9f - i * (0.7f / segments)
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )
    }
}

private fun DrawScope.drawScannerSweep(
    center: Offset, radius: Float, angle: Float, color: Color
) {
    rotate(angle, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.7f), color.copy(alpha = 0.0f)),
                center = center
            ),
            startAngle = -10f, sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )
    }
    // Leading edge line
    rotate(angle, pivot = center) {
        drawLine(
            color = color.copy(alpha = 0.9f),
            start = center,
            end = Offset(center.x + radius, center.y),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawWaveformRipple(
    center: Offset, radius: Float, audioLevel: Float, palette: OrbPalette, phase: Float
) {
    val rings = 4
    for (i in 0 until rings) {
        val t = i.toFloat() / rings
        val rippleR = radius * (0.7f + t * 0.55f) + audioLevel * radius * 0.30f * (1f - t)
        val waveOffset = sin(phase + t * PI.toFloat() * 2f) * 5f * audioLevel
        drawCircle(
            color = palette.ringA.copy(alpha = (0.5f - t * 0.1f) * (0.4f + audioLevel * 0.6f)),
            radius = rippleR + waveOffset,
            center = center,
            style = Stroke(width = 1.5f - t * 0.3f)
        )
    }
}

private fun DrawScope.drawHarmonicRings(
    center: Offset, baseR: Float, pulseA: Float, pulseB: Float, audio: Float, palette: OrbPalette
) {
    drawCircle(
        color = palette.ringA.copy(alpha = 0.55f),
        radius = baseR * pulseA + audio * 12f,
        center = center,
        style = Stroke(width = 2f)
    )
    drawCircle(
        color = palette.ringB.copy(alpha = 0.30f),
        radius = baseR * pulseB * 1.15f + audio * 20f,
        center = center,
        style = Stroke(width = 1.2f)
    )
    drawCircle(
        color = palette.glow.copy(alpha = 0.15f),
        radius = baseR * pulseA * 1.35f + audio * 28f,
        center = center,
        style = Stroke(width = 0.8f)
    )
}

private fun DrawScope.drawParticleField(
    center: Offset, outerR: Float, drift: Float, audio: Float, palette: OrbPalette, state: JarvisVisualState
) {
    val count = when (state) {
        JarvisVisualState.THINKING, JarvisVisualState.LISTENING -> 48
        JarvisVisualState.SPEAKING, JarvisVisualState.EXECUTING -> 36
        JarvisVisualState.OFFLINE, JarvisVisualState.ERROR -> 16
        else -> 26
    }

    for (i in 0 until count) {
        val phi = (i * 137.508f + drift) * (PI.toFloat() / 180f)
        val spread = outerR * (0.45f + (i % 8) * 0.072f) + audio * 18f
        val px = center.x + cos(phi) * spread
        val py = center.y + sin(phi) * spread
        val pSize = 1.0f + (i % 5) * 0.55f

        val alpha = when {
            state == JarvisVisualState.OFFLINE -> 0.25f
            i % 4 == 0 -> 0.90f
            i % 4 == 1 -> 0.60f
            else -> 0.35f
        }

        val c = if (i % 3 == 0) Color.White.copy(alpha = alpha) else palette.particle.copy(alpha = alpha)
        drawCircle(color = c, radius = pSize, center = Offset(px, py))
    }
}

private fun DrawScope.drawErrorOverlay(
    center: Offset, radius: Float, pulse: Float, palette: OrbPalette
) {
    // Pulsing outer danger ring
    drawCircle(
        color = palette.coreOuter.copy(alpha = pulse * 0.6f),
        radius = radius * pulse,
        center = center,
        style = Stroke(width = 2.5f)
    )
    // Cross-hatch lines
    for (angle in listOf(45f, -45f)) {
        rotate(angle, pivot = center) {
            drawLine(
                color = palette.ringA.copy(alpha = pulse * 0.8f),
                start = Offset(center.x - radius * 0.7f, center.y),
                end = Offset(center.x + radius * 0.7f, center.y),
                strokeWidth = 2f, cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawStarburst(center: Offset, radius: Float, palette: OrbPalette) {
    for (i in 0 until 12) {
        val angle = (i * 30f) * (PI.toFloat() / 180f)
        val spokeR = radius * (0.5f + (i % 3) * 0.18f)
        drawLine(
            color = if (i % 2 == 0) Color.White.copy(alpha = 0.85f) else palette.glow.copy(alpha = 0.55f),
            start = center,
            end = Offset(center.x + cos(angle) * spokeR, center.y + sin(angle) * spokeR),
            strokeWidth = if (i % 3 == 0) 2.5f else 1.2f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawScanlines(center: Offset, radius: Float, palette: OrbPalette) {
    val lineSpacing = 8f
    var y = center.y - radius
    while (y <= center.y + radius) {
        val halfW = sqrt((radius * radius) - ((y - center.y) * (y - center.y)).coerceAtLeast(0f))
        drawLine(
            color = palette.ringA.copy(alpha = 0.10f),
            start = Offset(center.x - halfW, y),
            end = Offset(center.x + halfW, y),
            strokeWidth = 0.8f
        )
        y += lineSpacing
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ServiceLifecycleOwner — required for ComposeView inside a Service
// ─────────────────────────────────────────────────────────────────────────────

private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }
}
