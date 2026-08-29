package com.jarvis.android.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.JarvisVisualState
import kotlin.math.cos
import kotlin.math.sin

/**
 * JarvisFloatingOrbService — the always-visible JARVIS orb overlay.
 *
 * Fully connected to VoiceBus.engineState so it reflects every assistant state:
 *   IDLE        → pulsing blue ring
 *   LISTENING   → animated cyan wave rings (reacts to audio level)
 *   THINKING    → rotating arc segments (processing animation)
 *   EXECUTING   → amber pulse (action in progress)
 *   SPEAKING    → green radiating rings
 *   ERROR       → red flash
 *   SUCCESS     → brief white flash then IDLE
 *
 * Tap: opens MainActivity and starts listening.
 * Drag: repositions the orb anywhere on screen.
 */
class JarvisFloatingOrbService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            110.dp.value.toInt().dpToPx(),
            110.dp.value.toInt().dpToPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        val composeView = ComposeView(this)

        // Required for Compose lifecycle in a Service
        val lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.start()
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(null)
        composeView.setViewTreeSavedStateRegistryOwner(null)

        composeView.setContent {
            val state by VoiceBus.engineState.collectAsState()
            val audioLevel by VoiceBus.audioLevel.collectAsState()

            JarvisOrbContent(
                state = state,
                audioLevel = audioLevel,
                onTap = {
                    val intent = Intent(this@JarvisFloatingOrbService, com.jarvis.app.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("WAKE_WORD_ACTIVATED", true)
                    }
                    startActivity(intent)
                }
            )
        }

        // Touch-drag to reposition
        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchStartX = event.rawX; touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (dx * dx + dy * dy > 16) {  // minimum drag threshold
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(composeView, params)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = composeView
        try { windowManager?.addView(floatingView, params) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
        isRunning = false
        super.onDestroy()
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    companion object {
        var isRunning: Boolean = false
            private set
    }
}

/**
 * Pure Compose orb — drawn entirely on Canvas so there are zero dependencies on the
 * UI module's component tree. Works in a Service context without a full Activity.
 */
@androidx.compose.runtime.Composable
private fun JarvisOrbContent(
    state: JarvisVisualState,
    audioLevel: Float,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Rotating angle for THINKING arc
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "rotation"
    )

    // Pulse scale for IDLE / LISTENING / SPEAKING
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Core brightness driven by audio level when LISTENING
    val coreBrightness by animateFloatAsState(
        targetValue = if (state == JarvisVisualState.LISTENING) 0.5f + audioLevel * 0.5f else 0.35f,
        animationSpec = tween(80),
        label = "brightness"
    )

    val (coreColor, ringColor, glowColor) = orbColors(state)

    Box(
        modifier = Modifier
            .size(110.dp)
            .background(Color.Transparent)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(110.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = size.width * 0.22f
            val ringRadius = size.width * 0.35f * pulseScale

            // Outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glowColor.copy(alpha = 0.18f), Color.Transparent),
                    center = center, radius = size.width * 0.52f
                ),
                radius = size.width * 0.52f,
                center = center
            )

            // Animated ring
            drawCircle(
                color = ringColor.copy(alpha = if (state == JarvisVisualState.IDLE) 0.4f else 0.65f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.6f)
            )

            // Second pulse ring for LISTENING / SPEAKING
            if (state == JarvisVisualState.LISTENING || state == JarvisVisualState.SPEAKING) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.3f),
                    radius = ringRadius * 1.3f + audioLevel * 14f,
                    center = center,
                    style = Stroke(width = 0.8f)
                )
            }

            // THINKING: rotating arc segments
            if (state == JarvisVisualState.THINKING || state == JarvisVisualState.EXECUTING) {
                drawThinkingArcs(center, ringRadius * 1.15f, rotation, ringColor)
            }

            // Core orb sphere
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        coreColor.copy(alpha = coreBrightness + 0.4f),
                        coreColor.copy(alpha = coreBrightness * 0.5f),
                        coreColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = coreRadius * 0.35f,
                center = Offset(center.x - coreRadius * 0.22f, center.y - coreRadius * 0.22f)
            )
        }
    }
}

private fun DrawScope.drawThinkingArcs(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color
) {
    // Four rotating arc segments separated by gaps
    val segmentAngle = 72f
    val gapAngle = 18f
    for (i in 0..4) {
        val startAngle = rotation + i * (segmentAngle + gapAngle)
        drawArc(
            color = color.copy(alpha = 0.7f - i * 0.12f),
            startAngle = startAngle,
            sweepAngle = segmentAngle,
            useCenter = false,
            style = Stroke(width = 2f),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )
    }
}

private data class OrbColorSet(val core: Color, val ring: Color, val glow: Color)

private fun orbColors(state: JarvisVisualState): OrbColorSet = when (state) {
    JarvisVisualState.IDLE      -> OrbColorSet(Color(0xFF00B8D4), Color(0xFF00B8D4), Color(0xFF00E5FF))
    JarvisVisualState.LISTENING -> OrbColorSet(Color(0xFF00E5FF), Color(0xFF00E5FF), Color(0xFF00FFFF))
    JarvisVisualState.THINKING  -> OrbColorSet(Color(0xFF7C4DFF), Color(0xFF9C27B0), Color(0xFFAA00FF))
    JarvisVisualState.EXECUTING -> OrbColorSet(Color(0xFFFFAB00), Color(0xFFFFD740), Color(0xFFFFD740))
    JarvisVisualState.SPEAKING  -> OrbColorSet(Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF1DE9B6))
    JarvisVisualState.SUCCESS   -> OrbColorSet(Color(0xFFFFFFFF), Color(0xFFE0F7FA), Color(0xFFFFFFFF))
    JarvisVisualState.ERROR     -> OrbColorSet(Color(0xFFFF1744), Color(0xFFFF5252), Color(0xFFFF1744))
}

/** Minimal LifecycleOwner that a ComposeView inside a Service can bind to. */
private class ServiceLifecycleOwner :
    androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
    }

    fun stop() {
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
    }
}
