# JARVIS Build Status — Current State

**Last updated by Achilles: 2026-08-29**

---

## What is fully implemented and committed

| Area | Status | Detail |
|------|--------|--------|
| xAI Grok as primary AI provider | ✅ DONE | `BuildConfig.XAI_API_KEY`, `JarvisApiClient`, `ApiConfig` |
| ReplySanitizer | ✅ DONE | Strips JSON artefacts, stack traces, system leakage |
| JarvisAIEngine | ✅ FIXED | Removed 200-line deterministic router. LLM-first with 4 fast-path exceptions |
| System prompt | ✅ FIXED | No longer instructs AI to reply with JSON blobs — trusts native function calling |
| AssistantOrchestrator | ✅ WIRED | DialogueManager → LLM fallback pipeline. VoiceBus state driven from here |
| ElevenLabsVoicePlayer | ✅ REBUILT | Barge-in support, voice cascade, turbo model, proper VoiceBus state |
| JarvisFloatingOrbService | ✅ REBUILT | Connected to VoiceBus, 7 visual states, Canvas-drawn, lifecycle-safe, draggable |
| JarvisApp | ✅ UPDATED | Auto-starts Orb on launch when overlay permission is granted |
| ApiConfig | ✅ UPDATED | xAI auto-detection (`AQ.`), clean priority chain, all providers |
| VoiceBus | ✅ CONNECTED | Single source of truth for engine state, audio level, and orb |
| Notification channels | ✅ DONE | Previously missing — now created in JarvisApp |
| PeopleGraph auto-import | ✅ DONE | Syncs contacts on first launch |
| Proactive briefings | ✅ DONE | Re-armed after reboot/update |

---

## What still needs your local action

| Item | Action |
|------|--------|
| **Add key to local.properties** | `cp local.properties.example local.properties` then add your xAI key |
| **Build** | `./gradlew assembleDebug` in Android Studio |
| **Run Diagnostics** | Settings → DIAGNOSTICS → TEST ALL PROVIDERS |
| **Grant permissions** | Mic, notifications, accessibility, overlay, contacts |
| **Set as default assistant** | Settings → DIAGNOSTICS → SET JARVIS AS DEFAULT |

---

## Known Android limits (not bugs)

- Cannot read WhatsApp message history (Android sandbox)
- Cannot toggle mobile data / airplane mode / hotspot (blocked since Android 5)
- Cannot see inside FLAG_SECURE screens (banking apps)
- Wi-Fi toggle opens system panel on Android 10+; Bluetooth on Android 13+

---

## Next planned improvements (P2)

- Vosk offline wake word (replace SystemSpeechRecognizer — see docs/VOSK_SETUP.md)
- Real web search (Brave/Tavily) as a proper ToolRegistry entry
- Streaming TTS (first word in <0.5s)
- Context window management (prune conversation history after N turns)
- Onboarding rewrite with guided assistant-role setup
