# JARVIS vs Siri vs Bixby — Capability Comparison

> Every row below is verified against the actual codebase, not marketing. If JARVIS can
> do it, the tool ID is listed. If it can't, the gap is called out honestly.

---

## 1. VOICE & WAKE WORD

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Always-on wake word | ✅ On-device neural net, milliwatt-level | ✅ Samsung NPU-optimized | ⚠️ Foreground service polling Android SpeechRecognizer — battery heavy, misses words |
| Wake word enrollment | ✅ Trains to YOUR voice | ✅ Trains to YOUR voice | ❌ Fixed phrase "Hey JARVIS", no personal enrollment |
| Voice Activity Detection | ✅ Stops recording when you stop talking | ✅ Same | ❌ Uses timeout-based silence detection |
| Echo cancellation | ✅ Cancels its own TTS output | ✅ Same | ❌ Can't hear you while it's speaking — no barge-in |
| Streaming TTS | ✅ Audio starts before sentence finishes | ✅ Same | ⚠️ ElevenLabs TTS waits for full audio file (2-4s first-word latency) |
| On-device STT | ✅ Neural Engine since iOS 15 | ✅ Samsung NPU | ⚠️ Uses Android's on-device SpeechRecognizer (quality varies by device) |
| Cloud STT fallback | ✅ Automatic | ✅ Automatic | ⚠️ Only if device supports it — no dedicated cloud STT |
| Voice recognition accuracy | ✅ Excellent | ✅ Very good | ⚠️ Depends on device OEM's SpeechRecognizer implementation |
| Multi-language | ✅ 20+ languages | ✅ 10+ languages | ⚠️ English only currently (ElevenLabs multilingual available but not wired) |

**JARVIS advantage:** Voice is powered by ElevenLabs HD voices (George — British, refined) which
sound more natural than Siri's synthetic voice. But Siri's pipeline (wake → VAD → echo cancel →
streaming TTS) makes it *feel* faster despite the inferior voice quality.

---

## 2. UNDERSTANDING (NLU)

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Deterministic intent routing | ✅ SiriKit domains (structured schemas) | ✅ Bixby Capsules (structured model) | ✅ `IntentRouter.kt` — flashlight, battery, time, calls, SMS |
| LLM-based understanding | ✅ Apple Intelligence (2024+) | ✅ Bixby AI | ✅ Gemini/xAI/Grok via `JarvisAIEngine` |
| Hybrid (deterministic + LLM) | ✅ Deterministic first, LLM for the rest | ✅ Same | ✅ Fast-path → LLM fallback via `AgentExecutor` |
| Contact disambiguation | ✅ "Which Mumsi? Home or mobile?" | ✅ Same | ✅ `PhoneTools.resolveTarget()` — multi-number contacts asked |
| Pronoun resolution | ✅ "Call her back" → uses last contact | ✅ Same | ✅ `DialogueManager` + `EntityMemory` stores lastContact |
| Nickname system | ✅ "Mumsi" → mapped to real contact | ✅ "My mom" → mapped | ✅ `PeopleGraph` + `ContextGraphDao` — nicknames, relationships |
| Time parsing | ✅ "Tomorrow 3pm" → parsed | ✅ Same | ✅ `TimeParser.kt` — natural language time understanding |
| Conversation context | ✅ Full session memory | ✅ Full session memory | ✅ `JarvisMemoryManager` — conversation history + stored memories |
| Multi-step reasoning | ✅ "Find John's last message and read it" | ✅ Same | ✅ `AgentExecutor` — up to 4 steps, model decides when to stop |

**JARVIS advantage:** The LLM-based understanding means it can handle *any* natural language,
not just predefined intents. "I'm having trouble hearing this" → raises volume, without that
phrase being hard-coded.

**Siri/Bixby advantage:** Deterministic routing is faster (sub-100ms) and never hallucinates.
JARVIS's fast-path covers only 5 intents; everything else waits for a network call.

---

## 3. DEVICE CONTROL

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Flashlight on/off | ✅ | ✅ | ✅ `device_flashlight` |
| Volume up/down/set/mute/max | ✅ | ✅ | ✅ `device_volume` |
| Brightness | ✅ | ✅ | ✅ `set_brightness` |
| Do Not Disturb | ✅ | ✅ | ✅ `set_dnd` |
| Ringer mode (silent/vibrate/ring) | ✅ | ✅ | ✅ `set_ringer_mode` |
| Wi-Fi on/off | ⚠️ Opens settings panel (API 29+) | ⚠️ Same | ⚠️ Opens settings panel (API 29+) |
| Bluetooth on/off | ⚠️ Opens settings panel (API 33+) | ⚠️ Same | ⚠️ Opens settings panel (API 33+) |
| Haptic/vibration | ✅ | ✅ | ✅ `device_vibrate` |
| Battery status | ✅ | ✅ | ✅ `device_battery` — level, charging state, temperature |
| Network status | ✅ | ✅ | ✅ `device_connectivity` |
| Storage status | ✅ | ✅ | ✅ `device_storage` |
| Screen brightness auto | ✅ | ✅ | ✅ `set_brightness` with `auto=true` |
| Power off/restart | ✅ | ❌ | ❌ |
| Airplane mode | ✅ | ❌ | ❌ |
| Dark mode toggle | ✅ | ✅ | ❌ |

**JARVIS advantage:** Battery includes temperature readout (not just percentage). Storage shows
free/total. Network checks Wi-Fi, cellular, and overall connectivity separately.

**Siri advantage:** Siri can power off/restart the device and toggle airplane mode — things
Android blocks apps from doing directly.

---

## 4. COMMUNICATION

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Phone calls | ✅ | ✅ | ✅ `call_contact` — with disambiguation for multiple numbers |
| Send SMS | ✅ | ✅ | ✅ `send_sms` / `send_message` — with contact resolution |
| Send WhatsApp | ❌ (no deep integration) | ❌ | ✅ `send_whatsapp` — via WhatsApp API deep link |
| Read notifications | ✅ | ✅ | ✅ `get_recent_notifications` — filter by app |
| Reply to notification | ✅ | ✅ | ✅ `reply_to_notification` — inline reply via NotificationListener |
| Dismiss notification | ✅ | ❌ | ✅ `dismiss_notification` |
| Send email | ✅ (Apple Mail / Gmail) | ✅ | ✅ `email_draft` — opens email composer with pre-filled recipient/subject/body |
| FaceTime / Video call | ✅ (Apple ecosystem) | ✅ (Samsung ecosystem) | ❌ |
| Contact lookup | ✅ | ✅ | ✅ `contact_lookup` — search by name/nickname |
| Call log | ✅ | ✅ | ✅ `call_log` — recent calls |
| OTP/code reading | ✅ | ❌ | ✅ `set_read_otp` — reads verification codes aloud from notifications |
| Sensitive content hiding | ❌ | ❌ | ✅ `set_hide_sensitive` — hides passwords/card details in notifications |

**JARVIS advantage:** WhatsApp sending (Siri can't do this), notification reply (both Siri and
Bixby are limited here), OTP auto-reading from notifications, sensitive content hiding.

**Siri advantage:** Email integration, FaceTime, iMessage. But these are ecosystem lock-in,
not intelligence.

---

## 5. APPS & WEB

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Open app | ✅ | ✅ | ✅ `app_launch` / `open_app` — with fuzzy matching and nickname resolution |
| App nicknames | ✅ ("My banking app" → mapped) | ✅ | ✅ `ContextGraphDao` — "the bank app" → real package |
| Web search | ✅ (Safari) | ✅ (Samsung Internet) | ✅ `web_search` / `web_extract` — DuckDuckGo instant answers + browser fallback |
| Read web page | ❌ | ❌ | ⚠️ `web_extract` opens URL but doesn't parse page content back |
| Recent apps | ❌ | ❌ | ✅ `get_recent_apps` — via UsageStatsManager |
| App usage stats | ❌ | ✅ (Digital Wellbeing) | ✅ `get_daily_usage` — per-app screen time |
| Deep link into app settings | ❌ | ❌ | ❌ |

**JARVIS advantage:** App nickname system (no other assistant lets you say "open the bank
app" and have it just work), usage stats integration, recent apps list.

---

## 6. CALENDAR & TIME

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Create calendar event | ✅ | ✅ | ✅ `calendar_create` — natural time ("tomorrow 3pm") |
| Set reminder | ✅ | ✅ | ✅ `set_reminder` — with alarm attached |
| Set alarm | ✅ | ✅ | ✅ `set_alarm` — via Android AlarmClock intent |
| Set timer | ✅ | ✅ | ✅ `set_timer` — via Android AlarmClock intent |
| Read calendar | ✅ | ✅ | ✅ `calendar_read` — reads today's or any date's events |
| Cancel alarm | ✅ | ✅ | ❌ |
| Modify/delete events | ✅ | ✅ | ❌ |
| Time awareness ("how long until...") | ✅ | ❌ | ⚠️ `navigate_to` gives travel time via Maps, but no standalone time-to-event |

**JARVIS advantage:** All time inputs parsed naturally ("in 2 hours", "tomorrow morning",
"every Thursday at 9am") via `TimeParser.kt`.

**Gap:** Can't read existing calendar entries or cancel/delete events. Siri can list today's
schedule and cancel specific events.

---

## 7. NAVIGATION & LOCATION

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Navigate to address | ✅ (Apple Maps) | ✅ (Samsung/Google Maps) | ✅ `navigate_to` — Google Maps intent |
| Find nearby places | ✅ | ✅ | ✅ `nearby_search` — opens Maps search |
| Share current location | ❌ | ❌ | ✅ `share_location` — opens share sheet with GPS coords |
| Get current location | ✅ | ✅ | ✅ `device_location` — GPS/network with address |
| "How long to get home?" | ✅ (Home/Work addresses) | ✅ (Home/Work) | ❌ **Gap** — no saved Home/Work addresses yet |
| Traffic conditions | ✅ | ❌ | ❌ |
| Direction to contact | ✅ | ❌ | ❌ |

**JARVIS advantage:** Share location is unique — no other assistant offers this as a voice
command.

**Gap:** No Home/Work address system yet. Siri uses saved addresses for "I'm going home"
and "How long to get to work?"

---

## 8. NOTIFICATIONS & MEMORY

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Read notifications | ✅ | ✅ | ✅ `get_recent_notifications` |
| Reply inline | ✅ | ✅ | ✅ `reply_to_notification` |
| Dismiss | ❌ | ❌ | ✅ `dismiss_notification` |
| OTP auto-read | ❌ | ❌ | ✅ OTP extraction + optional voice readout |
| Store facts | ❌ | ❌ | ✅ `memory_remember` — Room DB persistent storage |
| Recall facts | ❌ | ❌ | ✅ `memory_recall` — search stored memories |
| Learn relationships | ✅ (after onboarding) | ✅ (after onboarding) | ⚠️ `PeopleGraph` has the data model but onboarding doesn't prompt for it yet |
| Conversation history | ✅ | ✅ | ✅ Session-based via `JarvisMemoryManager` |
| Proactive suggestions | ✅ (Siri Suggestions) | ✅ (Bixby Routines) | ⚠️ `ProactiveScheduler` exists (morning briefing) but not wired to suggestions |

**JARVIS advantage:** Persistent memory system (Siri and Bixby have nothing like "remember
that my account number is X"). Notification dismissal is unique. OTP auto-reading is unique.

**Gap:** No proactive suggestions engine. Siri shows "call Mom" based on habits, weather
alerts on lock screen, etc. JARVIS has the infrastructure (`ProactiveScheduler`) but
it only does morning briefings right now.

---

## 9. SCREEN AWARENESS & CONTROL

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Read screen content | ✅ (VoiceOver integration) | ✅ (Bixby Vision) | ✅ `screen_read` — structured UI element tree |
| Find text on screen | ✅ | ✅ | ✅ `find_text` — search for specific text |
| Click element by text | ✅ | ✅ | ✅ `click_element` — with before/after screen verification |
| Type into fields | ✅ | ✅ | ✅ `type_text` — into editable fields |
| Scroll screen | ✅ | ✅ | ✅ `scroll` — forward/backward |
| Tap by coordinates | ❌ | ✅ | ✅ `tap` — arbitrary (x,y) tap |
| Swipe gesture | ❌ | ✅ | ✅ `swipe` — arbitrary start/end coordinates |
| Take screenshot | ✅ | ✅ | ✅ `screen_capture` — accessibility-based |
| Verify actions | ❌ | ❌ | ✅ `screenSignature()` — before/after fingerprinting |
| Read structured UI | ❌ | ❌ | ✅ `getStructuredScreenData()` — element tree with clickable/editable flags |

**JARVIS advantage:** The screen awareness system is the strongest part of the app. Before/after
screen verification means JARVIS knows if its click actually did something, instead of
trusting the OS return value. The structured UI data gives the LLM rich context about what's
on screen.

**Note:** Siri and Bixby don't offer coordinate-based tapping or arbitrary swipe gestures
through voice commands. JARVIS can literally drive any app on the phone.

---

## 10. PROACTIVE & AUTOMATION

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Morning briefing | ✅ (iOS 16+) | ✅ (Bixby Routines) | ✅ `set_briefing` — configurable time, reads notifications + weather (when weather tool added) |
| Contextual suggestions | ✅ (Siri Suggestions on lock screen) | ✅ (Bixby Home cards) | ❌ **Gap** — no contextual suggestions |
| Routines (multi-action) | ✅ (Shortcuts) | ✅ (Routines) | ❌ **Gap** — no multi-action routine support |
| Habits learning | ✅ (learns patterns over time) | ✅ | ❌ **Gap** — no habit learning |
| Location-based triggers | ✅ ("when I arrive home") | ✅ | ❌ |
| Time-based triggers | ✅ | ✅ | ⚠️ Only morning briefing (WorkManager-based) |
| Battery-based triggers | ❌ | ✅ | ❌ |
| Lock-screen widgets | ✅ | ✅ | ❌ |

**This is the biggest gap.** Siri and Bixby run *without being asked* — they suggest actions,
show relevant cards, and trigger routines. JARVIS only responds when spoken to (plus the
morning briefing).

---

## 11. IDENTITY & PERSONALITY

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Customizable name | ✅ ("Hey Siri" → custom) | ✅ ("Hi Bixby" → custom) | ⚠️ Says "JARVIS" but user name is configurable ("Macaulay") |
| Voice selection | ✅ Multiple voices per language | ✅ Multiple voices | ✅ 8 ElevenLabs voices (George, Rachel, Adam, Callum, Alice, Charlotte, Brian, Antoni) + Android TTS fallback |
| Personality modes | ❌ (fixed personality) | ❌ (fixed) | ✅ Three tones: `jarvis_protocol` (British), `conversational` (friendly), `executive` (bullet points) |
| Humor/personality | ❌ | ❌ | ✅ Can be configured to have personality — system prompt supports it |
| British voice | ✅ (Daniel, Karen) | ❌ | ✅ George (British ElevenLabs) — default |
| Honesty about limitations | ⚠️ Generic "I can't do that" | ⚠️ Same | ✅ Specific error messages: "I need the contacts permission", "This phone won't let me control DND until you grant notification access" |

**JARVIS advantage:** Three personality modes is unique. ElevenLabs HD voices sound
noticeably better than Siri's synthetic voice. The honesty about limitations ("I need
X permission") is more helpful than generic error messages.

---

## 12. SAFETY & PRIVACY

| Feature | Siri | Bixby | JARVIS |
|---|---|---|---|
| Risk-tier confirmations | ✅ (for payments, calls) | ✅ | ✅ 5 risk levels (L0-L4) with UI confirmation cards |
| One confirmation system | ✅ | ✅ | ✅ Model-issued AND typed-intent calls share the same system |
| API keys in app | ❌ (server-side) | ❌ (server-side) | ⚠️ Keys currently hardcoded in APK (private repo, but exposed if APK shared) |
| No data sent to servers | ❌ (Apple servers process) | ❌ (Samsung servers) | ✅ All AI calls go to user's chosen provider — no JARVIS middleman |
| Local-first | ✅ (on-device processing since iOS 15) | ✅ (Samsung NPU) | ✅ Fast-path runs entirely offline (flashlight, battery, time) |
| Screen data stays on-device | ✅ | ✅ | ✅ Screen text never leaves the phone — only the LLM sees it via function calling |
| Overlay permission control | ✅ | ✅ | ✅ `PermissionAndSetupHelper` — granular permission management |
| Emergency stop | ❌ | ❌ | ✅ `emergencyStop()` — one tap stops everything (voice, tools, state) |

**JARVIS advantage:** Emergency stop is unique. Risk-tier system covers ALL tools uniformly
(not just calls and messages). Screen data stays on-device.

---

## SUMMARY: WHERE JARVIS WINS, TIES, AND LOSES

### JARVIS wins (things Siri/Bixby can't do or do worse):
1. **Screen awareness + control** — can read, tap, type, swipe on ANY app
2. **Persistent memory** — "remember that my account number is X"
3. **WhatsApp sending** — via deep link
4. **OTP auto-reading** from notifications
5. **Sensitive content hiding** in notifications
6. **Location sharing** via voice command
7. **Notification dismissal** via voice
8. **Three personality modes** (British protocol, conversational, executive)
9. **ElevenLabs HD voices** (sounding more natural than Siri's synthetic voice)
10. **Open-source, no ecosystem lock-in** — works on any Android phone
11. **Emergency stop** — halt everything in one tap
12. **Screen verification** — knows if its actions actually worked
13. **App nicknames** — "the bank app" → maps to real package

### JARVIS ties (equal capability):
1. Phone calls with disambiguation
2. SMS sending
3. Calendar event creation
4. Alarms and timers
5. Flashlight, volume, brightness, DND, ringer
6. Web search
7. Navigation
8. Notification reading and reply
9. Battery/storage/connectivity status
10. Multi-step reasoning via LLM

### JARVIS loses (gaps to fill):
1. **No email sending** — Siri has "send email to Mom"
2. **No calendar reading** — "What's on my schedule today?"
3. **No proactive suggestions** — Siri/Bixby suggest without being asked
4. **No routines/multi-action** — "Good morning" → read notifications + weather + calendar
5. **No always-on wake word** — battery-heavy SpeechRecognizer loop vs milliwatt neural net
6. **No barge-in** — can't interrupt JARVIS while it's speaking
7. **No streaming TTS** — 2-4s first-word latency vs Siri's <500ms
8. **No voice enrollment** — doesn't learn YOUR voice
9. **No Home/Work addresses** — can't say "how long to get home?"
10. **No habit learning** — doesn't notice patterns
11. **No device power off/restart** — blocked by Android
12. **No dark mode toggle** — blocked by Android
13. **No FaceTime/video call** — ecosystem limitation

---

## WHAT TO BUILD NEXT (priority order)

| # | Gap | Impact | Difficulty | Status |
|---|---|---|---|---|
| 1 | **Calendar reading** ("What's my schedule?") | High | Low | ✅ Done — `calendar_read` tool |
| 2 | **Email draft** ("Email John about the meeting") | Medium | Low | ✅ Done — `email_draft` tool |
| 3 | **Weather** ("What's the weather?") | High | Low | ✅ Done — `weather` tool (Open-Meteo, no API key) |
| 4 | **Morning briefing upgrade** (weather + calendar + notifications) | High — makes JARVIS proactive | Medium — combine existing tools |
| 5 | **Home/Work addresses** | Medium — enables time-to-commute | Low — SharedPreferences + onboarding step |
| 6 | **Streaming TTS** (ElevenLabs WebSocket) | High — reduces first-word latency from 4s to <500ms | High — WebSocket implementation |
| 7 | **Vosk wake word** (offline, battery-efficient) | High — enables true hands-free | Medium — ~45MB model download |
| 8 | **Routines** ("Good morning" = briefing + weather + notifications) | Medium — Siri Shortcuts equivalent | Medium — needs a routine engine |
| 9 | **Contextual suggestions** | Medium — makes JARVIS proactive | High — needs habit learning |
| 10 | **Barge-in** (interrupt while speaking) | Medium — feels more alive | Medium — needs audio focus management |

---

*Last updated: September 2026. Verified against codebase — 25+ registered tools,
multi-step agent loop, ElevenLabs TTS, screen awareness with verification.*
