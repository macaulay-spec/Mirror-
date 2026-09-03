package com.jarvis.feature.control

import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.HudBackground
import kotlinx.coroutines.launch

/**
 * Device Control Deck — v3 carbon copy of mockup 04.
 *
 * A 2-column grid of glass action cards driving the REAL device:
 * flashlight, Wi-Fi, Bluetooth and Do-Not-Disturb toggles (via the same
 * ToolRegistry tools JARVIS uses), brightness + volume sliders, and a
 * battery ring-gauge card.
 */
@Composable
fun DeviceControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val batteryManager = remember { context.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager }
    val wifiManager = remember {
        try {
            @Suppress("DEPRECATION") context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        } catch (_: Exception) { null }
    }
    val bluetoothAdapter = remember {
        (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    val notificationManager = remember {
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
    }

    var battery by remember { mutableIntStateOf(-1) }
    var torchOn by remember { mutableStateOf(false) }
    var wifiOn by remember { mutableStateOf(false) }
    var btOn by remember { mutableStateOf(false) }
    var dndOn by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(0) }
    var maxVolume by remember { mutableIntStateOf(1) }
    var volumeDraft by remember { mutableStateOf(0f) }
    var brightness by remember { mutableIntStateOf(128) }
    var brightnessDraft by remember { mutableStateOf(0.5f) }

    fun refreshStates() {
        battery = try { batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 } catch (_: Exception) { -1 }
        wifiOn = try { wifiManager?.isWifiEnabled == true } catch (_: Exception) { false }
        btOn = try { bluetoothAdapter?.isEnabled == true } catch (_: Exception) { false }
        dndOn = try {
            (notificationManager?.currentInterruptionFilter ?: android.app.NotificationManager.INTERRUPTION_FILTER_ALL) !=
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) { false }
        volume = try { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 } catch (_: Exception) { 0 }
        maxVolume = (try { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1 } catch (_: Exception) { 1 }).coerceAtLeast(1)
        volumeDraft = volume.toFloat() / maxVolume.toFloat()
        brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) { 128 }.coerceIn(1, 255)
        brightnessDraft = (brightness - 1).toFloat() / 254f
    }

    LaunchedEffect(Unit) { refreshStates() }

    fun runTool(id: String, args: Map<String, Any?>, then: () -> Unit = {}) {
        scope.launch {
            try { ToolRegistry.execute(context, ToolExecutionRequest(id, id, args, RiskLevel.LEVEL_0)) } catch (_: Exception) {}
            refreshStates()
            then()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
    ) {
        HudBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device Control",
                    color = JarvisColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Flashlight + Battery (ring gauge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleCard(
                        icon = Icons.Default.FlashlightOn,
                        title = "Flashlight",
                        active = torchOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        torchOn = !torchOn
                        runTool("device_flashlight", mapOf("enabled" to torchOn))
                    }
                    BatteryCard(percent = battery, modifier = Modifier.weight(1f))
                }

                // Row 2: Wi-Fi + Bluetooth
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleCard(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi",
                        active = wifiOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        runTool("toggle_wifi", mapOf("on" to !wifiOn))
                    }
                    ToggleCard(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth",
                        active = btOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        runTool("toggle_bluetooth", mapOf("on" to !btOn))
                    }
                }

                // Row 3: Do Not Disturb (wide)
                ToggleCard(
                    icon = Icons.Default.Notifications,
                    title = "Do Not Disturb",
                    active = dndOn,
                    wide = true
                ) {
                    runTool("set_dnd", mapOf("on" to !dndOn))
                }

                // Brightness slider card
                SliderCard(
                    icon = Icons.Default.BrightnessMedium,
                    title = "Brightness",
                    valueText = "${(brightnessDraft * 100).toInt()}%",
                    fraction = brightnessDraft,
                    onChange = { brightnessDraft = it },
                    onCommit = {
                        // Map 0..1 → 10..255 and run the same tool JARVIS uses
                        runTool("set_brightness", mapOf("percent" to ((brightnessDraft * 100).toInt()), "auto" to false))
                    }
                )

                // Volume slider card
                SliderCard(
                    icon = Icons.Default.VolumeUp,
                    title = "Volume",
                    valueText = "${(volumeDraft * 100).toInt()}%",
                    fraction = volumeDraft,
                    onChange = { volumeDraft = it },
                    onCommit = {
                        val target = (volumeDraft * maxVolume).toInt().coerceIn(0, maxVolume)
                        try { audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) } catch (_: Exception) {}
                        refreshStates()
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

// ── Cards ──────────────────────────────────────────────────────────────────

@Composable
private fun ToggleCard(
    icon: ImageVector,
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (active) JarvisColors.Presence.copy(alpha = 0.10f)
        else JarvisColors.SurfaceGlassElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (wide) 12.dp else 16.dp),
            horizontalAlignment = if (wide) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (wide) Arrangement.spacedBy(10.dp) else Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (active) JarvisColors.Presence else JarvisColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                if (wide) Spacer(modifier = Modifier.width(0.dp))
                Text(
                    text = title,
                    color = JarvisColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(if (wide) 4.dp else 10.dp))
            // State chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) JarvisColors.Presence.copy(alpha = 0.18f) else JarvisColors.SurfaceGlass)
                    .border(
                        0.6.dp,
                        if (active) JarvisColors.Presence.copy(alpha = 0.55f) else JarvisColors.Hairline,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (active) "ON" else "OFF",
                    color = if (active) JarvisColors.Presence else JarvisColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(percent: Int, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
                    val inset = 6.dp.toPx()
                    val sizePx = size.width - inset * 2
                    drawArc(
                        color = JarvisColors.Hairline,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                        style = stroke
                    )
                    if (percent >= 0) {
                        drawArc(
                            color = JarvisColors.Presence,
                            startAngle = -90f,
                            sweepAngle = 360f * (percent / 100f),
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                            style = stroke
                        )
                    }
                }
                Text(
                    text = if (percent >= 0) "$percent%" else "—",
                    color = JarvisColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = JarvisColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Battery",
                    color = JarvisColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SliderCard(
    icon: ImageVector,
    title: String,
    valueText: String,
    fraction: Float,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = JarvisColors.Presence,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = JarvisColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = valueText,
                    color = JarvisColors.Presence,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = onChange,
                onValueChangeFinished = onCommit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
