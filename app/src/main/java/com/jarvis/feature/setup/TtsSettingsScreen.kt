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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engineType = "elevenlabs"
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
                            Text("ElevenLabs Synthesis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Configure high-fidelity AI voice parameters.", color = JarvisColors.TextSecondary, fontSize = 12.sp)
                        }
                    }

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

                    val scope = rememberCoroutineScope()

                    OutlinedButton(
                        onClick = {
                            ApiConfig.saveVoicePreferences(context, engineType, voiceId)
                            android.widget.Toast.makeText(context, "Streaming ElevenLabs preview...", android.widget.Toast.LENGTH_SHORT).show()
                            scope.launch {
                                try {
                                    com.jarvis.app.voice.ElevenLabsVoicePlayer.speak(
                                        context, 
                                        "Voice output test. Eleven labs audio profile updated.", 
                                        voiceId
                                    )
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
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
