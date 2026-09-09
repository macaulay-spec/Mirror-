package com.jarvis.app.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.assistant.JarvisApiClient
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.app.assist.AssistantRoleManager
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.people.PeopleGraph
import com.jarvis.app.voice.VoiceDiagnostics
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.theme.JarvisTheme
import kotlinx.coroutines.launch

/** Settings -> "Diagnostics". */
class DiagnosticsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                Surface(color = JarvisColors.VoidBlack) {
                    DiagnosticsScreen(
                        onTestVoice = { text ->
                            com.jarvis.app.voice.GeminiVoicePlayer.speak(this@DiagnosticsActivity, text)
                        },
                        onSyncContacts = { PeopleGraph.syncFromContacts(this@DiagnosticsActivity) }
                    )
                }
            }
        }
    }
}

data class ProviderStatus(
    val provider: String,
    val model: String,
    val ok: Boolean,
    val message: String,
    val latencyMs: Long
)

@Composable
private fun DiagnosticsScreen(
    onTestVoice: suspend (String) -> Boolean,
    onSyncContacts: suspend () -> Int
) {
    val scope = rememberCoroutineScope()

    var providerResults by remember { mutableStateOf<List<ProviderStatus>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf(VoiceDiagnostics.summary) }
    var peopleCount by remember { mutableStateOf<Int?>(null) }
    var alwaysListening by remember { mutableStateOf(AssistantPrefs.alwaysListening) }
    var assistantStatus by remember { mutableStateOf("") }
    var toolCount by remember { mutableStateOf(ToolRegistry.getAllTools().size) }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        peopleCount = runCatching { PeopleGraph.allPeople(context).size }.getOrDefault(0)
        assistantStatus = AssistantRoleManager.statusText(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("DIAGNOSTICS", color = JarvisColors.Presence, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
        Text(
            "Gemini AI Multi-Model Brain & Voice status.",
            color = JarvisColors.TextSecondary, fontSize = 12.sp
        )

        Spacer(Modifier.height(8.dp))

        // AI CORE
        Section("AI DUAL-CORE (GEMINI + NVIDIA)") {
            Text(
                "Active provider: ${ApiConfig.getProviderLabel()}",
                color = JarvisColors.TextPrimary, fontSize = 12.sp
            )
            Text(
                "Gemini keys pool: ${ApiConfig.geminiKeys.size} key(s) configured",
                color = if (ApiConfig.geminiKeys.isEmpty()) JarvisColors.Warmth else JarvisColors.TextPrimary,
                fontSize = 12.sp
            )
            Text(
                "NVIDIA cluster: ${if (ApiConfig.NVIDIA_API_KEY.isNotBlank()) "Online (${ApiConfig.NVIDIA_SUPER_MODEL.substringAfter('/')})" else "Not configured"}",
                color = if (ApiConfig.NVIDIA_API_KEY.isNotBlank()) JarvisColors.StateSuccess else JarvisColors.Warmth,
                fontSize = 12.sp
            )
            Text(
                "Fallback chain: ${ApiConfig.PROVIDER_FALLBACK_CHAIN.size} tiers (Gemini Flash/Pro/Lite -> NVIDIA Nemotron Super/Llama/Mistral/Ultra)",
                color = JarvisColors.TextSecondary, fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    testing = true
                    scope.launch {
                        val client = JarvisApiClient()
                        val results = mutableListOf<ProviderStatus>()
                        // Test all Gemini and NVIDIA providers in the fallback chain
                        val providersToTest = ApiConfig.PROVIDER_FALLBACK_CHAIN
                        
                        for (p in providersToTest) {
                            val start = System.currentTimeMillis()
                            val res = client.chat(
                                systemPrompt = "You are JARVIS.",
                                history = emptyList(),
                                userMessage = "Operational ping. Reply in 3 words.",
                                provider = p,
                                model = ApiConfig.resolveModel(p)
                            )
                            val elapsed = System.currentTimeMillis() - start
                            if (res.isSuccess) {
                                results.add(ProviderStatus(
                                    p, ApiConfig.resolveModel(p), true, 
                                    "OK - ${res.getOrNull()?.message?.take(40) ?: "Success"}", elapsed
                                ))
                            } else {
                                results.add(ProviderStatus(
                                    p, ApiConfig.resolveModel(p), false, 
                                    res.exceptionOrNull()?.message ?: "Failed", elapsed
                                ))
                            }
                        }
                        providerResults = results
                        testing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Presence)
            ) { Text(if (testing) "TESTING PROVIDERS..." else "TEST ALL PROVIDERS (GEMINI + NVIDIA)", color = androidx.compose.ui.graphics.Color.Black) }

            for (status in providerResults.orEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        if (status.ok) "OK " else "FAIL",
                        color = if (status.ok) JarvisColors.StateSuccess else JarvisColors.StateError,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "  ${status.provider} (${status.model}) ${status.latencyMs}ms",
                        color = JarvisColors.TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "     ${status.message}",
                    color = JarvisColors.TextSecondary, fontSize = 11.sp
                )
            }
        }

        // VOICE
        Section("VOICE OUTPUT") {
            Text("Engine: ${ApiConfig.voiceEngineType}", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text("Voice: ${ApiConfig.selectedVoiceId}", color = JarvisColors.TextSecondary, fontSize = 12.sp)
            // Who owns the microphone right now. The wake-word engine and the
            // conversation engine each own a SpeechRecognizer and Android permits only
            // one to be live, so this is the first thing to check when hands-free
            // listening misbehaves.
            Text(
                com.jarvis.android.voice.MicArbiter.describe(),
                color = JarvisColors.TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(voiceStatus, color = JarvisColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    scope.launch {
                        onTestVoice("Diagnostics check. JARVIS voice is online.")
                        voiceStatus = VoiceDiagnostics.summary
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Presence)
            ) { Text("TEST VOICE", color = androidx.compose.ui.graphics.Color.Black) }
        }

        // PEOPLE
        Section("PEOPLE") {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Text(
                if (!granted) "Contacts permission: NOT granted - JARVIS cannot learn who anyone is."
                else "Contacts imported: ${peopleCount ?: 0}",
                color = if (!granted) JarvisColors.Warmth else JarvisColors.TextPrimary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                scope.launch { peopleCount = runCatching { onSyncContacts() }.getOrDefault(0) }
            }) { Text("RE-SYNC CONTACTS", color = JarvisColors.Presence, fontSize = 11.sp) }
        }

        // ALWAYS LISTENING
        Section("ALWAYS LISTENING") {
            Text(
                if (alwaysListening) "\"Hey JARVIS\" service is enabled."
                else "Always-on listening is off - use the mic button.",
                color = JarvisColors.TextPrimary, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                alwaysListening = !alwaysListening
                AssistantPrefs.setAlwaysListening(context, alwaysListening)
                val intent = android.content.Intent(
                    context, com.jarvis.app.voice.WakeWordForegroundService::class.java
                )
                if (alwaysListening) {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } else {
                    intent.action = "stop"
                    context.startService(intent)
                }
            }) {
                Text(
                    if (alwaysListening) "TURN OFF ALWAYS-ON" else "TURN ON ALWAYS-ON",
                    color = JarvisColors.Presence, fontSize = 11.sp
                )
            }
        }

        // DEFAULT ASSISTANT
        Section("DEFAULT ASSISTANT") {
            Text(assistantStatus, color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                // FIX: this used to call AssistantRoleManager.request() first, which
                // launched a role dialog that ROLE_ASSISTANT (requestable=false) makes
                // cancel itself instantly -- and returned true, so the Settings
                // fallback below it never ran. The button did nothing.
                val opened = AssistantRoleManager.makeDefault(context)
                assistantStatus = if (opened) {
                    "Settings opened — pick JARVIS as the assistant, then come back."
                } else {
                    "This device exposes no assistant settings page. Long-press home " +
                        "still opens whatever assistant is currently set."
                }
            }) { Text("OPEN ASSISTANT SETTINGS", color = JarvisColors.Presence, fontSize = 11.sp) }
            Text(
                "Once JARVIS holds the slot, long-press home or the assistant gesture " +
                    "opens it from any screen.",
                color = JarvisColors.TextSecondary, fontSize = 11.sp
            )
        }

        // TOOLS
        Section("TOOLS") {
            Text("$toolCount actions registered.", color = JarvisColors.TextPrimary, fontSize = 12.sp)
            Text(
                ToolRegistry.getAllTools().groupBy { it.category }.entries
                    .joinToString("  ") { "${it.key}:${it.value.size}" },
                color = JarvisColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = JarvisColors.Presence, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        HorizontalDivider(color = JarvisColors.Hairline)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
