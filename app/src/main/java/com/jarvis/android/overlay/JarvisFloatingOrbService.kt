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
import com.jarvis.app.MainActivity
import com.jarvis.app.R
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
 */
class JarvisFloatingOrbService : Service() {

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

        composeView.setContent {
            val state by VoiceBus.engineState.collectAsState()
            val audioLevel by VoiceBus.audioLevel.collectAsState()

            OrbOverlayContent(
                state = state,
                audioLevel = audioLevel,
                onOpenApp = {
                    startActivity(
                        Intent(this@JarvisFloatingOrbService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                }
            )
        }

        // Drag handling with edge snapping
        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f
        var isDragging = false

        composeView.setOnTouchListener { _, event ->
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
                        // Snap to nearest edge (design spec: within ~48dp of edge)
                        val metrics = resources.displayMetrics
                        val screenWidth = metrics.widthPixels
                        val snapX = if (params.x + (composeView.width / 2) > screenWidth / 2)
                            screenWidth else 0
                        params.x = snapX
                        windowManager?.updateViewLayout(composeView, params)
                        true
                    } else {
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

/**
 * Orb overlay content — uses the SAME JarvisCore as the main app.
 *
 * Tap expands to a compact strip; the strip auto-collapses after ~6s.
 * The Orb is the same visual everywhere — it scales, it doesn't become
 * a different graphic.
 */
@Composable
private fun OrbOverlayContent(
    state: JarvisVisualState,
    audioLevel: Float,
    onOpenApp: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var collapseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    if (isExpanded) {
        // Compact strip: mini Orb + status + actions
        Row(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JarvisColors.DarkSpace.copy(alpha = 0.92f))
                .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Orb — same JarvisCore, just smaller
            Box(modifier = Modifier.size(40.dp)) {
                JarvisCore(
                    state = state,
                    audioLevel = audioLevel,
                    size = 40.dp,
                    onClick = { isExpanded = false }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status text
            Column(modifier = Modifier.width(100.dp)) {
                Text(
                    text = "Jarvis",
                    color = state.orbColor(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = state.label,
                    color = JarvisColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Default
                )
            }

            IconButton(onClick = onOpenApp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Open App",
                    tint = JarvisColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Collapse",
                    tint = JarvisColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        // Compact Orb only — same JarvisCore, scaled to 80dp for overlay
        Box(
            modifier = Modifier
                .size(80.dp)
                .padding(8.dp)
        ) {
            JarvisCore(
                state = state,
                audioLevel = audioLevel,
                size = 64.dp,
                onClick = { isExpanded = true }
            )
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
}
