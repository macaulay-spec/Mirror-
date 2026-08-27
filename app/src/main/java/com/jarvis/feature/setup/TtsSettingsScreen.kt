package com.jarvis.feature.setup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.theme.JarvisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var engineType by remember { mutableStateOf(ApiConfig.voiceEngineType) }
    var voiceId by remember { mutableStateOf(ApiConfig.selectedVoiceId) }
    var savedStatus by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack,
        topBar = {
            TopAppBar(
                title = { Text("TTS & Voice Profile", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisColors.CyanBright)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisColors.SurfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = JarvisColors.CyanBright)
                        Column {
                            Text("Text-to-Speech Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Choose between ElevenLabs HD and Native Android TTS", color = JarvisColors.TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                engineType = "elevenlabs"
                                savedStatus = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (engineType == "elevenlabs") JarvisColors.CyanPrimary else JarvisColors.VoidBlack,
                                contentColor = if (engineType == "elevenlabs") Color.Black else JarvisColors.TextPrimary
                            )
                        ) {
                            Text("ElevenLabs HD", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                engineType = "native"
                                savedStatus = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (engineType == "native") JarvisColors.CyanPrimary else JarvisColors.VoidBlack,
                                contentColor = if (engineType == "native") Color.Black else JarvisColors.TextPrimary
                            )
                        ) {
                            Text("Native Android", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (engineType == "elevenlabs") {
                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = {
                                voiceId = it
                                savedStatus = false
                            },
                            label = { Text("ElevenLabs Voice ID / Profile", color = JarvisColors.TextSecondary, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisColors.CyanBright,
                                unfocusedBorderColor = JarvisColors.BorderCyan,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Button(
                        onClick = {
                            ApiConfig.saveVoicePreferences(context, engineType, voiceId)
                            savedStatus = true
                            android.widget.Toast.makeText(context, "Voice preferences saved to DataStore!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanPrimary)
                    ) {
                        Text(if (savedStatus) "SAVED ✓" else "SAVE PREFERENCES", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = {
                            ApiConfig.saveVoicePreferences(context, engineType, voiceId)
                            try {
                                val speech = com.jarvis.app.voice.SpeechOutput(context)
                                speech.speak("Voice output test. JARVIS audio profile updated.")
                            } catch (_: Exception) {}
                            android.widget.Toast.makeText(context, "Testing voice output...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.CyanBright)
                    ) {
                        Text("🔊 PREVIEW VOICE OUTPUT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
