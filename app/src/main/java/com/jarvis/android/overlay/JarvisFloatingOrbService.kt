




package com.jarvis.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.app.JarvisApp
import com.jarvis.app.MainActivity
import com.rork.jarvisaiassistant.R
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.JarvisCore

/**
 * JARVIS Floating Orb — overlay service.
 *
 * Design spec compliance:
 * - Uses the SAME JarvisCore composable as the main app (one Orb identity)
 * - Draggable anywhere, snaps to edge when released near it
 * - Tap expands to compact strip (Orb shrinks to 32dp, shows last reply + mic + close)
 * - Auto-collapses after ~6s of no interaction
 * - Never intercepts touches outside its own bounds
 * - **CONNECTED TO ORCHESTRATOR** — mic button triggers voice input
 */
class JarvisFloatingOrbService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var overlayLifecycleOwner: ServiceLifecycleOwner? = null
    private lateinit var params: WindowManager.LayoutParams

    /** Direct reference to the orchestrator — lives on the Application, not the Activity. */
    private val orchestrator get() = (application as? JarvisApp)?.orchestrator
    private val voiceEngine get() = (application as? JarvisApp)?.voiceEngine

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
        overlayLifecycleOwner = lifecycleOwner
        lifecycleOwner.start()
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(null)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        composeView.setContent {
            val state by VoiceBus.engineState.collectAsState()
            val audioLevel by VoiceBus.audioLevel.collectAsState()
            val orchestratorMessages = orchestrator?.messages?.collectAsState()
            val lastReply = orchestratorMessages?.value
                ?.lastOrNull { it.role == com.jarvis.core.model.MessageRole.JARVIS }?.text

            OrbOverlayContent(
                state = state,
                audioLevel = audioLevel,
                lastReply = lastReply,
                onOpenApp = {
                    startActivity(
                        Intent(this@JarvisFloatingOrbService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                },
                onToggleMic = {
                    // Toggle voice input — connected to the real orchestrator
                    val engine = voiceEngine ?: return@OrbOverlayContent
                    if (engine.engineState.value == JarvisVisualState.LISTENING) {
                        engine.stopListening()
                    } else {
                        engine.startListening()
                    }
                },
                isListening = state == JarvisVisualState.LISTENING
            )
        }

        // Drag handling with edge snapping
        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f
        var isDragging = false

        composeView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchStartX = event.rawX; touchStartY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(composeView, params)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Snap to nearest edge. params.x is the view's LEFT edge,
                        // so the right-edge snap must subtract the view width —
                        // otherwise the orb parks fully off-screen and is lost.
                        val metrics = resources.displayMetrics
                        val viewWidth = if (composeView.width > 0) composeView.width else 200
                        val snapX = if (params.x + (viewWidth / 2) > metrics.widthPixels / 2)
                            metrics.widthPixels - viewWidth else 0
                        params.x = snapX
                        params.y = params.y.coerceIn(0, metrics.heightPixels - viewWidth)
                        windowManager?.updateViewLayout(composeView, params)
                        true
                    } else {
                        view.performClick()
                        false
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
        // Tear the composition down properly: without DESTROYED + dispose, the
        // recomposer and JarvisCore's infinite animations keep running against
        // a detached window after the orb is dismissed.
        overlayLifecycleOwner?.markDestroyed()
        (floatingView as? ComposeView)?.let { try { it.disposeComposition() } catch (_: Exception) {} }
        floatingView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        floatingView = null
        overlayLifecycleOwner = null
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







/**
 * Orb overlay content — uses the SAME JarvisCore as the main app.
 *
 * Rewritten:
 *  - No longer forces navigation or a rigid command strip.
 *  - Shows the Orb on the left.
 *  - When JARVIS is active (listening/thinking/speaking), streams the reply
 *    in a floating glass text bubble right beside the Orb.
 */
@Composable
private fun OrbOverlayContent(
    state: JarvisVisualState,
    audioLevel: Float,
    lastReply: String?,
    onOpenApp: () -> Unit,
    onToggleMic: () -> Unit,
    isListening: Boolean
) {
    val isActive = state == JarvisVisualState.LISTENING || state == JarvisVisualState.THINKING || state == JarvisVisualState.SPEAKING
    
    Row(verticalAlignment = Alignment.Top) {
        // Core Orb
        Box(
            modifier = Modifier
                .size(80.dp)
                .padding(8.dp)
        ) {
            JarvisCore(
                state = state,
                audioLevel = audioLevel,
                size = 64.dp,
                onClick = onToggleMic // Toggles mic directly without opening the app!
            )
        }

        // Floating Streaming Text Bubble
        AnimatedVisibility(
            visible = isActive && !lastReply.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .widthIn(max = 280.dp)
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .background(JarvisColors.SurfaceGlassElevated.copy(alpha = 0.95f))
                    .border(0.5.dp, JarvisColors.Presence.copy(alpha = 0.5f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = lastReply ?: "",
                    color = JarvisColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}

/**
 * ServiceLifecycleOwner — required for ComposeView inside a Service.
 */
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

    fun markDestroyed() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
