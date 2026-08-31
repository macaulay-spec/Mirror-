package com.jarvis.feature.voice

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import com.jarvis.app.voice.ElevenLabsTts

/**
 * Voice Selection Screen — choose and preview ElevenLabs voices.
 *
 * Design: calm, precise, alive.
 * - List of voices with preview button
 * - Selected voice highlighted
 * - Orb animates during preview
 * - Continue button at bottom
 */
@Composable
fun VoiceSelectionScreen(
    tts: ElevenLabsTts,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voices by tts.voices.collectAsState()
    val selectedVoiceId by tts.selectedVoiceId.collectAsState()
    val isSpeaking by tts.isSpeaking.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0F17),
                        Color(0xFF0E1420),
                        Color(0xFF0B0F17)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            Text(
                text = "Choose Voice",
                color = JarvisColors.TextPrimary,
                fontSize = 24.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select the voice you like.",
                color = JarvisColors.TextSecondary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Orb preview
            JarvisCore(
                state = if (isSpeaking) com.jarvis.core.model.JarvisVisualState.SPEAKING
                else com.jarvis.core.model.JarvisVisualState.IDLE,
                size = 100.dp,
                onClick = null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Voice list
            if (voices.isEmpty()) {
                // Show preset voices when API not available
                val presetVoices = ApiConfig.PRESET_VOICES
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetVoices) { preset ->
                        VoicePresetItem(
                            voiceId = preset.id,
                            name = preset.name,
                            description = "${preset.accent} • ${preset.gender} • ${preset.description}",
                            isSelected = preset.id == selectedVoiceId,
                            onPreview = { tts.previewVoice(ElevenLabsTts.Voice(preset.id, preset.name, "premade", preset.description, null, preset.gender, preset.accent)) },
                            onSelect = { tts.selectVoice(preset.id) }
                        )
                    }
                }
            } else {
                // Show API voices
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(voices) { voice ->
                        VoiceItem(
                            voice = voice,
                            isSelected = voice.voiceId == selectedVoiceId,
                            onPreview = { tts.previewVoice(voice) },
                            onSelect = { tts.selectVoice(voice.voiceId) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisColors.SurfaceGlass)
                    .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(14.dp))
                    .clickable {
                        val voice = voices.find { it.voiceId == selectedVoiceId }
                        if (voice != null) {
                            tts.previewVoice(voice)
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Preview",
                        tint = JarvisColors.Presence,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpeaking) "Stop" else "Preview Voice",
                        color = JarvisColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisColors.Presence)
                    .clickable { onContinue() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continue",
                    color = JarvisColors.VoidBlack,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Skip button
            Text(
                text = "Skip for now",
                color = JarvisColors.TextMuted,
                fontSize = 14.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier
                    .clickable { onSkip() }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun VoiceItem(
    voice: ElevenLabsTts.Voice,
    isSelected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .then(
                if (isSelected) Modifier.border(1.dp, JarvisColors.Presence, RoundedCornerShape(20.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) JarvisColors.Presence.copy(alpha = 0.2f)
                        else JarvisColors.SurfaceGlass
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isSelected) JarvisColors.Presence else JarvisColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Voice info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = voice.description ?: "${voice.category} • ${voice.gender ?: ""}",
                    color = JarvisColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default
                )
            }

            // Preview button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.SurfaceGlass)
                    .clickable { onPreview() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = JarvisColors.Presence,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VoicePresetItem(
    voiceId: String,
    name: String,
    description: String,
    isSelected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .then(
                if (isSelected) Modifier.border(1.dp, JarvisColors.Presence, RoundedCornerShape(20.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) JarvisColors.Presence.copy(alpha = 0.2f)
                        else JarvisColors.SurfaceGlass
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (isSelected) JarvisColors.Presence else JarvisColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Voice info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = JarvisColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default
                )
            }

            // Preview button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.SurfaceGlass)
                    .clickable { onPreview() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = JarvisColors.Presence,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
