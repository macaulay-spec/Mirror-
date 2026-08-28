# JARVIS — Complete Capability Spec (Target State)

> What JARVIS must be able to do after the rebuild. Every item is mapped to what exists
> today (`OK` = works, `FIX` = written but broken/unwired, `NEW` = must be built) so we
> always know how far we are.
>
> Device target: Android 8.0+ (minSdk 26), sideloaded APK, English + Nigerian English,
> later pidgin. User: Macaulay. Wake word: "Hey JARVIS".

---

## 0. THE BIG GAP — it must behave like an assistant, not a command parser

Siri and Bixby feel intelligent for three reasons, none of which JARVIS does today:

1. **They ask when unsure.** "There are two Mumsi's — home or mobile?"
2. **They confirm before irreversible actions.** "Calling Mumsi. Say yes to confirm."
3. **They hold context.** "Call her back", "send it again", "make it louder" — pronouns
   resolve against the last entities.

Today `JarvisAIEngine.processCommand()` is strictly **one-shot**: input → (keyword match |
canned reply | one LLM call) → reply. There is no pending-clarification state, no
confirmation loop, and no entity memory. `AssistantOrchestrator.pendingConfirmation`
exists but **nothing ever writes to it**.

### 0.1 Dialog protocol (NEW — the core of the rebuild)

The model must be able to reply with four action types instead of just two:

```jsonc
{ "action": "reply",  "message": "..." }                       // just talk
{ "action": "tool_call", "tool": "...", "arguments": {...} }    // do it
{ "action": "ask",    "question": "Which Mumsi?",               // clarify (NEW)
  "options": ["Mumsi (home)", "Mumsi (mobile)"], "slot": "contact" }
{ "action": "confirm","tool": "call_contact",                   // confirm (NEW)
  "arguments": {...}, "prompt": "Call Mumsi on mobile now?",
  "risk": "LEVEL_2" }
```

Behaviour rules:

- **`ask`** parks the conversation. The next user utterance is resolved *against the open
  slot* — "the first one", "mobile", "never mind" — not treated as a new command.
- **`confirm`** renders the CONFIRM/EXECUTE card that already exists in `DualModeHost`
  (currently dead code because nothing sets `message.toolCall`). Voice path: JARVIS asks
  aloud and listens for yes/no.
- **Entity memory** keeps `{lastContact, lastApp, lastMessage, lastNumber, lastMedia,
  lastQuery}` so "call her again", "send that to dad too", "turn it up" resolve.
- **Repair, not failure**: if WhatsApp isn't installed → "WhatsApp isn't on this phone.
  Send an SMS instead, or open WhatsApp Business?" Never a bare "I couldn't do that".

### 0.2 Conversation qualities (NEW)

- Follow-up questions, corrections ("no, the other one"), partial commands that complete
  across turns
- Cancellation ("stop", "cancel", "never mind") at any point
- Acknowledges before long operations ("On it", "Calling now", "Searching…")
- Answers *about itself* honestly ("I can't read WhatsApp's message history — Android
  won't let any app do that")

---

## 1. PHONE & CALLS — `FIX` (almost entirely missing)

| # | Capability | Status |
|---|---|---|
| 1.1 | Call a contact by name/nickname: "call mumsi", "call my brother" | NEW |
| 1.2 | Call by relationship: "call my wife", "call dad" (needs a relationships map) | NEW |
| 1.3 | Call by number: "call 0803..." | NEW |
| 1.4 | Direct call (dials immediately) **or** open dialer first — user choice | NEW |
| 1.5 | Speakerphone on/off during a call | NEW |
| 1.6 | End a call | NEW (needs `ANSWER_PHONE_CALLS` + in-call service) |
| 1.7 | Answer / reject incoming call by voice | NEW |
| 1.8 | Disambiguate: two numbers for one contact → ask which one | NEW |
| 1.9 | Redial / "call her back" / "call the last person" | NEW |
| 1.10 | Read call log: "who called me?", "missed calls" | NEW |
| 1.11 | Read voicemail/visual voicemail | ❌ impossible (carrier locked) |

**Needs from Android:** `CALL_PHONE`, `READ_CONTACTS`, `READ_PHONE_STATE`,
`READ_CALL_LOG`, `ANSWER_PHONE_CALLS` (declared in manifest, never requested).
**Needs from you:** a nickname map — mumsi = which contact, plus any other names.

---

## 2. MESSAGING — `FIX` (notification reply works, the rest is dead code)

| # | Capability | Status |
|---|---|---|
| 2.1 | Send SMS to a contact/number ("text mumsi I'm on my way") | NEW (`DeviceToolkit.sendSms` written, never exposed) |
| 2.2 | Read SMS conversation history | NEW |
| 2.3 | Reply to WhatsApp/Telegram/Instagram via notification RemoteInput | OK (needs notification access ON) |
| 2.4 | Send a *new* WhatsApp message to a contact (not just reply) | NEW (deep link `wa.me/<number>` — opens chat, types, needs Accessibility to tap Send) |
| 2.5 | Send to Telegram / Instagram / Messenger / X (deep link + Accessibility) | NEW |
| 2.6 | Read unread messages aloud: "read my messages" | FIX (tool exists, blocked by alias bug + permission) |
| 2.7 | Reply by voice end-to-end: dictate → confirm → send | NEW |
| 2.8 | Group messages by sender, summarize ("5 messages, 2 from Mumsi") | NEW |
| 2.9 | Draft-and-confirm flow: JARVIS shows the draft, you say "send it" | NEW |
| 2.10 | Mark as read / dismiss notification | FIX |
| 2.11 | Read OTP codes aloud | OK (currently *redacted* — make it opt-in) |
| 2.12 | Schedule a message for later | NEW (WorkManager) |
| 2.13 | Read full WhatsApp chat history | ❌ impossible — Android sandbox |

---

## 3. APPS — `FIX` (the "open app" bug)

| # | Capability | Status |
|---|---|---|
| 3.1 | Open **any** installed app by name (WhatsApp, Chrome, Instagram, Bank app…) | FIX (arg-name bug — launches a random app today) |
| 3.2 | Open by nickname: "open the bank app", "open chrome" | NEW |
| 3.3 | Search **inside** an app: YouTube, Spotify, Maps, Play Store, Chrome | OK |
| 3.4 | Play a specific song/video: "play Burna Boy on YouTube" | FIX |
| 3.5 | Deep actions: "open WhatsApp chat with Mumsi" | NEW |
| 3.6 | Uninstall / open app info / force stop / clear cache | NEW |
| 3.7 | List installed apps matching a query | NEW |
| 3.8 | App usage stats: "how long have I been on Instagram today?" | OK |

---

## 4. DEVICE CONTROL — `FIX` (many half-written, unexposed)

| # | Capability | Status |
|---|---|---|
| 4.1 | Flashlight on/off | OK |
| 4.2 | Volume: up/down/mute/set %, per stream | FIX |
| 4.3 | Brightness: set %, auto mode | NEW (`DeviceToolkit.brightness` written, unexposed) |
| 4.4 | Wi-Fi on/off, list networks | NEW (unexposed) |
| 4.5 | Bluetooth on/off, pair, connect | NEW |
| 4.6 | Mobile data on/off | ❌ blocked since Android 5 (root only) |
| 4.7 | Airplane mode | ❌ blocked (root only) |
| 4.8 | Do Not Disturb on/off | NEW (unexposed) |
| 4.9 | Silent/vibrate/ring mode | NEW |
| 4.10 | Lock screen / turn display off | OK |
| 4.11 | Battery %, charging, temperature, saver mode | FIX (battery tool OK, saver NEW) |
| 4.12 | Storage free/total | OK |
| 4.13 | Connectivity status (Wi-Fi/cellular/Ethernet) | OK |
| 4.14 | Hotspot / tethering toggle | ❌ blocked |
| 4.15 | Screenshot | NEW (MediaProjection) |
| 4.16 | Screen recording | NEW |
| 4.17 | Reboot / power off | ❌ blocked (root/system only) |
| 4.18 | Vibrate / haptic feedback | OK |
| 4.19 | Media playback: play/pause/next/prev/stop/seek | PARTIAL (no seek, no "what's playing") |
| 4.20 | "What song is this?" (now-playing + Shazam-style) | NEW |
| 4.21 | Alarm / timer / stopwatch | NEW |
| 4.22 | Take a photo / record video | NEW (CameraX already a dependency) |
| 4.23 | Copy/paste clipboard read & write | NEW |

---

## 5. SCREEN CONTROL (Accessibility "Full mode") — `OK` when enabled

| # | Capability | Status |
|---|---|---|
| 5.1 | Read everything on screen (structured elements) | OK |
| 5.2 | Find text on screen | OK |
| 5.3 | Tap/click an element by text or description | OK |
| 5.4 | Type into a field | OK |
| 5.5 | Scroll up/down | OK |
| 5.6 | Tap coordinates, swipe gestures | OK |
| 5.7 | Back / Home / Recents / notification shade | OK |
| 5.8 | Multi-step in-app flows ("order my usual from X") | NEW (needs reliable step plans) |
| 5.9 | Screen *understanding* — screenshot + vision model describing layout | NEW |
| 5.10 | Work inside banking/secure apps | ❌ `FLAG_SECURE` blocks screenshots/Accessibility |

**Requires:** you enable JARVIS in *Settings → Accessibility* (OFF by default today).

---

## 6. NOTIFICATIONS — `FIX`

| # | Capability | Status |
|---|---|---|
| 6.1 | Read active notifications, filter by app | FIX (alias bug) |
| 6.2 | Reply inline via RemoteInput | OK |
| 6.3 | Dismiss one / clear all | OK |
| 6.4 | Persistent notification history in Room DB | OK (written, never surfaced) |
| 6.5 | Smart digest: "You have 12 notifications, 3 urgent" | NEW |
| 6.6 | Quiet hours / per-app muting | NEW |
| 6.7 | Announce notifications aloud while driving/charging | NEW |

**Requires:** you enable JARVIS in *Settings → Notification access*.

---

## 7. CONTACTS & PEOPLE — `NEW`

| # | Capability |
|---|---|
| 7.1 | Look up a contact (name, number, email) |
| 7.2 | Nickname/relationship map (mumsi = Mum, "my guy" = Tunde) |
| 7.3 | Disambiguate contacts with multiple numbers |
| 7.4 | Add / edit a contact |
| 7.5 | "Who is this number?" (reverse lookup of unknown caller) |

---

## 8. CALENDAR, REMINDERS, ALARMS — `FIX`

| # | Capability | Status |
|---|---|---|
| 8.1 | Create event (title + time + duration) | FIX (hardcoded "tomorrow 9am" — no time parsing) |
| 8.2 | Natural time parsing: "meeting with Tunde Thursday 3pm" | NEW |
| 8.3 | Read today's/upcoming events | NEW |
| 8.4 | Reschedule / delete event | NEW |
| 8.5 | Reminders & to-dos | NEW |
| 8.6 | Alarms: set, cancel, snooze, list | NEW |

---

## 9. LOCATION & NAVIGATION — `FIX`

| # | Capability | Status |
|---|---|---|
| 9.1 | "Where am I?" (coords + address) | OK |
| 9.2 | Share my location | NEW |
| 9.3 | Navigate somewhere ("take me to Lekki") | NEW (Maps intent) |
| 9.4 | "How long to get home?" (traffic-aware) | NEW |
| 9.5 | Save home/work; "I'm leaving now" | NEW |
| 9.6 | Find nearby places ("fuel station near me") | NEW |
| 9.7 | Continuous background location / geofences | NEW |

---

## 10. FILES, CAMERA, DOCUMENTS, OCR — `NEW`

| # | Capability | Status |
|---|---|---|
| 10.1 | Open the file picker and read .txt/.md/.json/.csv/.log | FIX (written, unreachable) |
| 10.2 | Read PDF / DOCX / XLSX | NEW |
| 10.3 | OCR a photo (ML Kit, on-device, no key) | NEW |
| 10.4 | Describe an image with a vision model ("what's in this screenshot?") | NEW |
| 10.5 | Search & open photos, share a photo | NEW |
| 10.6 | Scan a document / QR code | NEW |
| 10.7 | Summarize a long document | NEW (needs LLM) |

---

## 11. WEB & KNOWLEDGE — `NEW` (currently just opens a browser)

| # | Capability |
|---|---|
| 11.1 | Web search that returns a real **answer**, not a browser tab (search API) |
| 11.2 | Summarize a web page from a URL |
| 11.3 | Look up facts, prices, conversions, definitions, calculations |
| 11.4 | Real-time info: weather, news, FX rate (USD→NGN), fuel price |
| 11.5 | Translate between languages |
| 11.6 | Open a specific site: "open Instagram on Chrome" |

---

## 12. MEMORY & PERSONALIZATION — `FIX`

| # | Capability | Status |
|---|---|---|
| 12.1 | "Remember that …" / "what did I tell you?" | FIX |
| 12.2 | Auto-learn preferences (name, favourite apps, routines) | PARTIAL |
| 12.3 | Forget one memory / wipe everything | OK |
| 12.4 | Memory used to answer ("my usual order is …") | NEW (retrieval is keyword-only today) |
| 12.5 | Per-person notes ("Mumsi prefers calls over texts") | NEW |
| 12.6 | Never store secrets (already implemented — keep it) | OK |
| 12.7 | Export / inspect the memory vault in the UI | NEW |

---

## 13. BACKGROUND & PROACTIVE — `FIX` (the service exists but is never started)

| # | Capability | Status |
|---|---|---|
| 13.1 | Always-on wake word "Hey JARVIS" | FIX (service written, never started) |
| 13.2 | Survives screen-off and app-closed | NEW (needs battery exemption + OEM autostart) |
| 13.3 | Floating orb overlay you can invoke from any screen | FIX (never started) |
| 13.4 | Morning briefing (calendar, weather, unread count) | NEW |
| 13.5 | Proactive nudges ("Battery 15%", "Meeting in 20 min") | NEW |
| 13.6 | Routines: "I'm home" → Wi-Fi on, lights on, read messages | NEW |
| 13.7 | Headset/Bluetooth auto-launch | NEW |
| 13.8 | Charging / driving / sleeping modes | NEW |

---

## 14. VOICE & PERSONALITY — `FIX`

| # | Capability | Status |
|---|---|---|
| 14.1 | Wake word, offline-friendly | FIX |
| 14.2 | Natural HD voice (ElevenLabs) | FIX (silent failure → robot voice) |
| 14.3 | Pick a real JARVIS-style British voice | NEW (default is "Rachel", American) |
| 14.4 | Voice activity detection + barge-in (interrupt JARVIS mid-sentence) | NEW |
| 14.5 | Streaming TTS so replies start faster | NEW |
| 14.6 | Speaking rate/pitch controls, per-context tone | NEW |
| 14.7 | Sound effects / UI tones | OK |
| 14.8 | Offline STT fallback when there's no data | PARTIAL |
| 14.9 | Multiple languages / pidgin | NEW |

---

## 15. SAFETY & CONFIRMATION RULES — `NEW` (the risk tiers exist but never fire)

| Risk | Examples | Rule |
|---|---|---|
| L0 | read battery, time, screen | execute silently |
| L1 | open app, flashlight, volume | execute, show what happened |
| L2 | **call someone, send a message, post** | **always confirm** (voice or card) |
| L3 | delete data, edit calendar, change settings | **always confirm** |
| L4 | payments, account changes, wipes | confirm + never automate silently |

Plus: emergency stop (exists), audit log of every action the AI took, and "who did what"
transparency in Settings.

---

## 16. WHAT YOU MUST PROVIDE

### From you, personally
1. **One LLM API key** with **function/tool calling** — pick one:
   - **Google Gemini** — free tier, `https://aistudio.google.com/apikey` *(recommended)*
   - **OpenAI** — GPT-4o-mini, best tool-calling, needs billing
   - *(not the keys currently hardcoded — those are public and probably dead)*
2. **ElevenLabs API key** if you want the HD voice (optional; Android TTS otherwise).
3. **Your nickname map**: mumsi = <contact>, plus any other names/relationships
   (dad, "my guy", "the office").
4. **Preferences**: name to address you by, wake word, home/work address, favourite apps,
   morning-briefing time.
5. **A test phone** to run on (Android 8+) + one hour of permission-granting and testing.

### On the phone (one-time, I'll add buttons for all of these)
- Install the APK, allow unknown sources
- Grant runtime: microphone, phone, contacts, SMS, calendar, camera, notifications, location
- **Settings → Accessibility → enable JARVIS** (screen reading, tapping, typing)
- **Settings → Notification access → enable JARVIS** (read/reply to messages)
- **Battery → Unrestricted** for JARVIS, disable battery optimisation
- OEM extras (Tecno/Infinix/Xiaomi/Samsung): **Auto-start** ON, lock app in Recent Apps
- Optional: overlay (floating orb), usage access, all-files access

### Nice-to-have
- Home Assistant URL + token (lights/plug control)
- A search API key (Brave/Tavily/Serper) for real web answers
- Google Cloud STT/TTS keys (betterthan offline STT on noisy networks)

---

## 17. WHAT IT WILL NEVER DO (Android reality, not a skill gap)

- ❌ Read WhatsApp/Telegram **message history** — kernel sandbox; notifications & screen only
- ❌ Toggle mobile data / airplane mode / hotspot — blocked since Android 5 without root
- ❌ Reboot or power off the device — system-signature only
- ❌ See inside banking apps or any `FLAG_SECURE` screen
- ❌ Read another app's private database or files
- ❌ Truly silent always-on mic — Android requires the visible foreground notification
- ❌ Guaranteed background survival on aggressive OEMs (Infinix/Tecno/Xiaomi) without the
  user whitelisting it
- ❌ iOS/Siri-level OS integration (default assistant role) — needs the OEM

---

## 18. BUILD ORDER

| Phase | Scope | Result |
|---|---|---|
| **0** | Fix `open_app`, add call/contact/SMS tools, request real permissions, start the wake-word service, fix ElevenLabs reporting | "open X", "call mumsi", "text X", hands-free wake word, HD voice |
| **1** | Dialog protocol (`ask` / `confirm` / entity memory), native function calling, shrink the keyword router, repair-on-failure | it asks, confirms, and reasons like an assistant |
| **2** | Notifications, memory, calendar/time parsing, location, web answers | real daily usefulness |
| **3** | Background routines, proactivity, overlay orb, camera/OCR/files | it anticipates instead of waiting |
| **4** | Personality, streaming TTS, barge-in, multi-language, polish | it sounds and feels like JARVIS |
