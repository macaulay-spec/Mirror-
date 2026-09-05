package com.jarvis.feature.voice

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.voice.ElevenLabsVoicePlayer
import kotlinx.coroutines.launch

// Arc-reactor palette, matching the JARVIS identity used across the app.
private val JarvisCyan = Color(0xFF3FD9E8)
private val JarvisGold = Color(0xFFD9A83F)
private val JarvisBackground = Color(0xFF05080D)
private val JarvisSurface = Color(0xFF0C121B)
private val JarvisCard = Color(0xFF121B27)
private val JarvisTextPrimary = Color(0xFFE8EEF5)
private val JarvisTextSecondary = Color(0xFF7C8A9C)

/**
 * Voice Room — hear each cloud voice before choosing.
 *
 * Every preset speaks through the same managed gateway chain JARVIS uses for
 * replies (xAI grok-tts / OpenAI tts-1), so what you preview is exactly what
 * you get. Selection persists immediately; previews play one at a time and a
 * new preview barges in over the old one.
 */
@Composable
fun VoiceRoomScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var selectedVoice by remember { mutableStateOf(ApiConfig.selectedVoiceId) }
    var previewingVoice by remember { mutableStateOf<String?>(null) }

    // Leaving the screen kills the preview audio — both the cloud player and
    // the device-voice fallback — instead of talking on over the next screen.
    DisposableEffect(Unit) {
        onDispose {
            ElevenLabsVoicePlayer.stop()
            previewTts?.shutdown()
            previewTts = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = JarvisBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisTextPrimary
                    )
                }
                Column {
                    Text("Voice Room", color = JarvisTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "JARVIS speaks with the cloud voice you pick",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ApiConfig.PRESET_VOICES.forEach { preset ->
                    val isSelected = preset.id == selectedVoice
                    val isPreviewing = preset.id == previewingVoice

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) JarvisCard else JarvisSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                selectedVoice = preset.id
                                ApiConfig.saveVoicePreferences(context, "cloud", preset.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice orb dot — cyan when selected, gold while previewing
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(
                                        when {
                                            isPreviewing -> JarvisGold
                                            isSelected -> JarvisCyan
                                            else -> JarvisTextSecondary.copy(alpha = 0.35f)
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        preset.name,
                                        color = JarvisTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = JarvisCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    "${preset.accent} · ${preset.gender} · ${preset.description}",
                                    color = JarvisTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    previewingVoice = preset.id
                                    scope.launch {
                                        val played = ElevenLabsVoicePlayer.speak(
                                            context,
                                            "Good evening. This is ${preset.name}, at your service.",
                                            preset.id
                                        )
                                        if (!played) previewWithDeviceVoice(context, preset.name)
                                        previewingVoice = null
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Preview ${preset.name}",
                                    tint = if (isPreviewing) JarvisGold else JarvisCyan
                                )
                            }
                        }
                    }
                }

                Text(
                    "Previews use the exact cloud chain JARVIS uses for replies. If the cloud is unreachable, the device voice reads the sample instead.",
                    color = JarvisTextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            Text(
                "Your choice is saved automatically.",
                color = JarvisCyan.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Reusable device-voice instance — a fresh TextToSpeech per preview leaked engine connections. */
private var previewTts: android.speech.tts.TextToSpeech? = null

/** Device-voice fallback for previews when the cloud chain is unreachable. */
private fun previewWithDeviceVoice(context: Context, voiceName: String) {
    try {
        val tts = previewTts
            ?: android.speech.tts.TextToSpeech(context.applicationContext) { }.also { previewTts = it }
        tts.speak(
            "Good evening. This is $voiceName, at your service.",
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            null,
            "voice_room_preview"
        )
    } catch (_: Exception) {
    }
}
