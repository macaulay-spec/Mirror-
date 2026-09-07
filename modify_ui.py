import re

with open('app/src/main/java/com/jarvis/feature/home/DualModeHost.kt', 'r') as f:
    content = f.read()

# 1. Remove StageMode switching
content = re.sub(r'var stageMode by remember \{ mutableStateOf\(StageMode.VOICE_STAGE\) \}', '', content)
content = re.sub(r'if \(messages.isNotEmpty\(\) && stageMode == StageMode.CONVERSATION\) \{', 'if (messages.isNotEmpty()) {', content)
content = re.sub(r'mode = stageMode,', 'mode = StageMode.CONVERSATION,', content)
content = re.sub(r'onSwitchMode = \{\s*stageMode = if \(stageMode == StageMode.VOICE_STAGE\)\s*StageMode.CONVERSATION else StageMode.VOICE_STAGE\s*\},', 'onSwitchMode = {},', content)

# 2. Replace AnimatedContent with Unified UI
unified_ui = """
                Spacer(modifier = Modifier.height(8.dp))
                
                // Holographic Unified Core
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // The Orb always stays at the top
                        JarvisCore(
                            state = visualState,
                            size = if (messages.isEmpty()) 260.dp else 120.dp,
                            audioLevel = 0f,
                            onClick = onToggleVoice,
                            modifier = Modifier.padding(top = if (messages.isEmpty()) 60.dp else 10.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        if (messages.isEmpty()) {
                            Text(
                                text = "$greeting, ${if (userName.isBlank()) "Sir" else userName}.",
                                color = JarvisColors.TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Mark 85 systems online. Ready for directive.",
                                color = JarvisColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        } else {
                            // Holographic Chat Stream below the Orb
                            ConversationView(
                                orchestrator = orchestrator,
                                visualState = visualState,
                                messages = messages,
                                listState = listState,
                                onOrbTap = onToggleVoice
                            )
                        }
                    }
                }

                ChatInputBar(
"""

# Replace the block from AnimatedContent to the ChatInputBar condition
content = re.sub(r'AnimatedContent\(.*?if \(stageMode == StageMode.CONVERSATION\) \{\s*ChatInputBar\(', unified_ui, content, flags=re.DOTALL)

with open('app/src/main/java/com/jarvis/feature/home/DualModeHost.kt', 'w') as f:
    f.write(content)
