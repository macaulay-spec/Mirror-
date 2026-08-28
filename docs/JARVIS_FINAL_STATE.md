# JARVIS — Final State

**What it will do, exactly how it will run, and every single thing it needs access to.**
This is the post-rebuild description. Written so you can read it as a checklist and hold
me to it.

---

# PART A — WHAT IT WILL BE ABLE TO DO

Legend for the **Needs** column:
`MIC` = microphone only · `NOTIF` = notification access · `ACC` = accessibility ·
`PHONE` = phone/contacts/SMS permissions · `NET` = internet + AI key · `LOC` = location ·
`OFF` = works with no internet at all

## A1. Conversation & reasoning

| Capability | Needs | Offline? |
|---|---|---|
| Natural back-and-forth, follow-ups, corrections ("no, the other one", "make it 7pm") | MIC + NET | partial |
| Asks clarifying questions when something is ambiguous | MIC | ✅ |
| Confirms before anything risky (call, send, pay, delete) | MIC | ✅ |
| Remembers context across turns — "call her back", "send it to dad too" | MIC | ✅ |
| Admits what it can't do, and explains why | — | ✅ |
| Multi-step plans ("open Jumia, search for power bank, sort by price") | ACC + NET | ❌ |
| Explains what it just did, and why | — | ✅ |
| Cancels instantly on "stop / cancel / never mind" | MIC | ✅ |

## A2. Calls

| Capability | Needs |
|---|---|
| "Call mumsi" / "call my brother" / "call my wife" (relationship aware) | PHONE |
| Call by number, by nickname, by last caller ("call her back", "redial") | PHONE |
| Disambiguate: "Mumsi has two numbers — home or mobile?" | PHONE |
| Confirm before dialling, or dial straight if you've set "don't ask for calls" | PHONE |
| Speakerphone on/off during a call | PHONE |
| End the call | PHONE (+ InCallService for the last word) |
| Answer / reject an incoming call by voice ("answer", "decline") | PHONE (answer = TelecomManager; reject needs InCallService — partial) |
| "Who called me?" / missed calls / call log | PHONE |
| Call on WhatsApp / Telegram / Duo instead of the carrier | ACC + NET |

## A3. Messaging

| Capability | Needs |
|---|---|
| Send SMS to a contact or number, dictated by voice | PHONE |
| Read SMS conversations aloud | PHONE |
| Read unread messages from WhatsApp / Telegram / Instagram / Messenger / X | NOTIF |
| Reply to any of them by voice (notification RemoteInput) | NOTIF |
| Start a **new** WhatsApp/Telegram message to a contact (not just a reply) | ACC |
| Draft → read it back → "send it" → confirmed send | PHONE / NOTIF / ACC |
| Smart digest: "12 messages, 3 people — 2 from Mumsi, 1 from Tunde" | NOTIF |
| Group chat handling, sender names, timestamps | NOTIF |
| Read OTP / verification codes aloud (opt-in; redacted by default) | NOTIF |
| Schedule a message for later | PHONE + background |
| Announce messages while driving, then offer to reply hands-free | NOTIF + MIC |
| ❌ Read full WhatsApp history | *impossible — Android sandbox* |

## A4. Apps

| Capability | Needs |
|---|---|
| Open **any** installed app by name or nickname ("open the bank app") | MIC |
| Search inside YouTube / Spotify / Maps / Play Store / Chrome | MIC + NET |
| Play something specific ("play Burna Boy on Spotify") | MIC + NET |
| Deep actions ("open my chat with Tunde on WhatsApp") | ACC |
| List / find installed apps, open app info, clear cache | MIC |
| "How long have I been on Instagram today?" | usage access |

## A5. Device control

| Capability | Needs | Offline |
|---|---|---|
| Flashlight, volume (set/up/down/mute), brightness | MIC | ✅ |
| Wi-Fi on/off, Bluetooth on/off & connect | MIC (write-settings for some OEMs) | ✅ |
| Do Not Disturb, silent / vibrate / ring | NOTIF + write-settings | ✅ |
| Lock screen, turn display off | ACC | ✅ |
| Battery %, charging, temperature, battery saver | MIC | ✅ |
| Storage, connectivity, network type | MIC | ✅ |
| Media: play / pause / next / previous / stop / "what's playing" | MIC + NOTIF | ✅ |
| Screenshot, screen recording | MediaProjection consent | ✅ |
| Alarms, timers, stopwatch | MIC | ✅ |
| Take a photo / record video | camera | ✅ |
| Clipboard read & write | ACC | ✅ |
| ❌ Mobile data, airplane mode, hotspot, reboot | *blocked by Android since v5* | — |

## A6. Screen control (Accessibility)

| Capability | Needs |
|---|---|
| Read everything on screen / find text | ACC |
| Tap, type, scroll, swipe, back/home/recents, notification shade | ACC |
| Multi-step flows inside apps ("order my usual") | ACC |
| Screenshot + vision model: "what am I looking at?" | ACC + NET |
| ❌ Anything inside banking / `FLAG_SECURE` screens | *blocked by Android* |

## A7. Notifications

| Capability | Needs |
|---|---|
| Read / filter / dismiss / clear all, persistent history | NOTIF |
| Smart digest & priority ranking | NOTIF |
| Per-app muting, quiet hours | NOTIF |
| Announce while charging or driving | NOTIF + MIC |

## A8. Contacts & people

| Capability | Needs |
|---|---|
| Look up anyone (name, nickname, number, email) | PHONE |
| "Who is mumsi?" — teach it once, remembered forever | — |
| Relationship graph (mother, brother, wife, boss, "my guy") | — |
| Add / edit contacts | PHONE |
| "Who is this number?" (unknown caller lookup) | PHONE |

## A9. Calendar, reminders, alarms

| Capability | Needs |
|---|---|
| "Meeting with Tunde Thursday 3pm" — real time parsing | calendar |
| "Remind me to call mumsi when I get home" | calendar + LOC |
| Read today's agenda / reschedule / delete | calendar |
| Alarms: set, cancel, snooze, list | MIC |

## A10. Location & navigation

| Capability | Needs |
|---|---|
| "Where am I?" | LOC |
| "Take me to Lekki", "navigate home" | LOC |
| "How long to get home?" with traffic | LOC + NET |
| "Fuel station near me", nearby places | LOC + NET |
| Share my live location | LOC |
| Geofences / "when I leave work" triggers | LOC (background) |

## A11. Files, camera, documents

| Capability | Needs |
|---|---|
| Read .txt/.md/.json/.csv/.log, PDF, DOCX, XLSX | files |
| OCR a photo on-device (ML Kit) — read a letter, a receipt, a screenshot | camera |
| "What's in this image?" (vision model) | NET |
| Find & share photos, scan a QR code | files + camera |
| "Summarize this document" | NET |

## A12. Web & knowledge

| Capability | Needs |
|---|---|
| Real answers, not just a browser tab | NET + search key |
| Summarize a page from a URL | NET |
| Weather, news, USD→NGN, fuel price, conversions, calculations | NET |
| Translate | NET |
| Open a specific site | NET |

## A13. Memory & personalization

| Capability | Needs |
|---|---|
| "Remember that …" / "what did I tell you about …" | — |
| Auto-learns preferences, routines, favourite apps | usage access |
| Per-person notes ("Mumsi prefers calls") | — |
| Forget one thing / wipe everything | — |
| Inspect & export the memory vault in Settings | — |
| Never stores passwords, tokens, OTPs, card numbers | — |

## A14. Background & proactive

| Capability | Needs |
|---|---|
| "Hey JARVIS" with the screen off and the app closed | MIC + battery exemption |
| Floating orb you can summon over any app | overlay |
| Morning briefing (weather, calendar, unread, reminders) | NOTIF + NET |
| Nudges: "Battery 15%", "Meeting in 20 min — leave now" | LOC + NET |
| Routines: Good morning / Driving / Good night / Low battery | many |
| Habit suggestions: "You usually call Mumsi on Sunday" | usage access |
| Auto-launch on headset or Bluetooth connect | — |
| Optional: become the phone's **default assistant** (long-press home) | assistant role |

## A15. Voice & personality

| Capability | Needs |
|---|---|
| Wake word (on-device), VAD end-pointing | MIC |
| HD natural voice (ElevenLabs) with a real JARVIS-style British voice | NET + key |
| Offline TTS fallback when there's no data | — |
| **Barge-in** — interrupt it mid-sentence and it stops | MIC |
| Streaming speech so replies start in <1s | NET |
| Rate, pitch, style per context; UI tones | — |
| Optional voice enrollment so it only obeys you | MIC |

## A16. Safety & control

- Risk tiers with mandatory confirmation for calls, messages, payments, deletes
- An **audit log** in Settings: every action JARVIS took, when, and why
- Emergency stop (voice, orb, and notification)
- **Access tiers** — you choose how much power it has (see Part C1)
- Kill switch for cloud: everything above that is marked ✅ offline still works

---

# PART B — HOW IT WILL ACTUALLY OPERATE

## B1. The layers (what runs where)

```
┌──────────────────────────────────────────────────────────────┐
│ WAKE LAYER      on-device wake word, always on, ~1%/hr       │
├──────────────────────────────────────────────────────────────┤
│ AUDIO LAYER     VAD end-pointing · echo cancel · barge-in    │
├──────────────────────────────────────────────────────────────┤
│ SPEECH LAYER    on-device STT → cloud STT fallback           │
├──────────────────────────────────────────────────────────────┤
│ UNDERSTANDING   ┌───────────────┐   ┌──────────────────────┐ │
│                 │ INTENT ROUTER │   │ LLM (Gemini/OpenAI)  │ │
│                 │ ~40 typed     │   │ function calling —   │ │
│                 │ intents, slots│   │ conversation, plans, │ │
│                 │ instant,offline│  │ knowledge, summaries │ │
│                 └───────┬───────┘   └──────────┬───────────┘ │
│                         └──────────┬───────────┘             │
├──────────────────────────────────────────────────────────────┤
│ DIALOGUE        slots · asks · disambiguation · confirmations│
│ MANAGER         entity memory · corrections · cancel · repair│
├──────────────────────────────────────────────────────────────┤
│ CONTEXT GRAPH   people · relationships · nicknames · places  │
│ (on-device)     app nicknames · habits · preferences         │
├──────────────────────────────────────────────────────────────┤
│ FULFILMENT      ToolRegistry → real function calling → tools │
│                 result + reason → repair ladder              │
├──────────────────────────────────────────────────────────────┤
│ NLG + TTS       streaming ElevenLabs → on-device TTS fallback│
├──────────────────────────────────────────────────────────────┤
│ PROACTIVE       WorkManager · notification listener · usage  │
│                 → briefings, nudges, routines, suggestions   │
└──────────────────────────────────────────────────────────────┘
```

## B2. Boot sequence

1. `JarvisApp.onCreate` → load prefs, build the Room DB, register all tools
2. Start `WakeWordForegroundService` (persistent notification, mic cycling)
3. Bind `JarvisNotificationListener` + `JarvisAccessibilityService` **if the user enabled
   them** — otherwise JARVIS runs in reduced mode and says so
4. Schedule the proactive jobs (briefing, battery watch, calendar watch, routine engine)
5. Restore the entity memory and open dialogue state so a half-finished task survives
   an app restart

## B3. The turn cycle (every single interaction)

```
 1 wake / tap / type
 2 VAD → capture → STT
 3 resolve against an OPEN SLOT first?   ── yes → fill the slot, continue the task
 4 else → intent router (typed, local)      ── matched → slots
 5        else → LLM with function calling  ── plan / chat / knowledge
 6 slot check:   missing? → ASK       ambiguous? → DISAMBIGUATE
 7 risk check:   L2+? → CONFIRM (card + voice yes/no)
 8 execute → verify → speak (streaming) → store entities + audit log
 9 offer the natural next step ("Want me to text her you're on your way?")
```

## B4. Three worked examples (exactly what happens)

### Trace 1 — "Hey JARVIS, call mumsi" *(happy path)*

| Step | What happens |
|---|---|
| Wake | On-device model hears "Hey JARVIS" → chime, screen wakes, mic opens |
| STT | "call mumsi" |
| Route | Intent router → `CALL_PERSON{contact: mumsi}` (local, no cloud, instant) |
| Slots | Contact resolves via the context graph → **Amaka Okafor**, relationship *mother*, 2 numbers |
| Disambiguate | JARVIS: *"Mumsi has two numbers — home or mobile?"* → you: "mobile" |
| Risk | L2 → confirm: *"Calling Mumsi on mobile. Yes?"* → you: "yes" |
| Execute | `CALL_PHONE` → call starts, speaker state applied |
| Verify | Telephony callback confirms the call connected |
| Speak | *"Calling Mumsi."* (streaming, interruptible) |
| Memory | `lastContact = Mumsi (mobile)`; audit log entry |
| Offer | *"Want me to text her after the call?"* |

### Trace 2 — "Text Tunde I'm on my way" *(slot filling + repair)*

| Step | What happens |
|---|---|
| Route | `SEND_MESSAGE{contact: Tunde, body: "I'm on my way"}` |
| Slots | Contact: 2 people match "Tunde" → *"Tunde Ade or Tunde Bello?"* → "Ade" |
| Body | Already provided. Channel check: Tunde's `preferredChannel` = WhatsApp |
| Execute | Try notification reply → no live WhatsApp notification → **repair ladder** |
| Repair | Try Accessibility: open WhatsApp → find chat → type → (L2 confirm) → Send |
| Fallback | If Accessibility is off → *"I can send an SMS instead. Yes?"* → `SmsManager` sends |
| Speak | *"Sent to Tunde Ade: 'I'm on my way'."* |
| Memory | `lastContact`, `lastMessage` → enables "send it to dad too" |

### Trace 3 — "Read my messages" → "Tell him I'll be 10 minutes" *(context carry-over)*

| Step | What happens |
|---|---|
| Route | `READ_MESSAGES` → notification listener → 5 unread, 3 senders |
| Speak | *"Five messages. Two from Mumsi: 'Are you coming?', 'Call me'. One from Tunde…"* |
| Context | Entity memory stores the sender list with indices |
| Follow-up | "Tell him I'll be 10 minutes" → *"him"* resolves to **Tunde** (last male sender) |
| Confirm | Draft read back: *"To Tunde: 'I'll be 10 minutes.' Send it?"* |
| Execute | RemoteInput reply → verified |
| Speak | *"Sent."* → *"Mark the other four as read?"* |

### Trace 4 — proactive (no wake word at all)

- **07:00** — WorkManager fires the briefing job → JARVIS speaks: *"Good morning. 22
  degrees, light rain later. Two meetings: stand-up at 9, review at 2. Four unread
  messages, two from Mumsi. Your phone is at 38% — charge before you leave."*
- **08:35** — calendar watch + traffic: *"Meeting in 25 minutes. It's 31 minutes to
  Lekki right now. Leave in five."*
- **Driving** — Bluetooth connects → Driving routine: announce messages, offer to reply,
  auto-DND, "call home" one-tap.
- **21:00** — habit engine: *"You usually call Mumsi on Sunday evenings. Call her now?"*

## B5. Failure & repair ladder

Every tool result carries a **reason**, so JARVIS never just says "I couldn't":

```
permission missing   → "I need phone permission for that. Open settings?"  → deep link
service disabled     → "Turn on notification access and I can read those." → deep link
app not installed    → "WhatsApp isn't installed. SMS instead?"
ambiguous            → "Two Tundes — which one?"
action failed        → retry once with a different method → then explain + offer manual
offline              → everything marked OFFLINE above still works, cloud features say so
```

## B6. Performance targets

| Moment | Target |
|---|---|
| Wake word → chime | < 300 ms |
| End of speech → first word spoken | < 1.2 s (streaming TTS), < 0.6 s with cached reply |
| Simple device command (flashlight, volume, open app) | < 400 ms, fully offline |
| Complex LLM task | 2–5 s, with "On it…" acknowledgement at 300 ms |
| Barge-in | stops within 200 ms of you speaking |
| Background battery cost | < 2%/hour (Porcupine-class wake word) |

---

# PART C — WHAT IT WILL NEED ACCESS TO

## C1. Three access tiers — you choose the level

| | **BASIC** | **STANDARD** | **FULL** |
|---|---|---|---|
| Grants | microphone, notifications, AI key | + notification access, phone, contacts, SMS, calendar, location | + **accessibility**, overlay, usage, all-files |
| Conversations, knowledge, web | ✅ | ✅ | ✅ |
| Calls, SMS, contacts, calendar, navigation | ❌ | ✅ | ✅ |
| Open apps, device control, camera, files | ✅ | ✅ | ✅ |
| Read & reply WhatsApp/Telegram/Instagram | ❌ | ✅ read+reply | ✅ |
| Start new chats in any app, drive any UI | ❌ | ❌ | ✅ |
| Screenshot understanding, multi-step in-app tasks | ❌ | ❌ | ✅ |
| Privacy cost | low | medium | **high** (accessibility sees screens) |

Everything degrades gracefully: at BASIC, JARVIS says *"I can't read messages yet —
turn on notification access and I can."* It never silently pretends.

## C2. Android permissions — runtime (asked with a dialog)

| Permission | What it unlocks | If denied |
|---|---|---|
| `RECORD_AUDIO` | wake word, all voice input | JARVIS is text-only |
| `CALL_PHONE` | actually dial ("call mumsi") | can only open the dialer |
| `READ_CONTACTS` / `WRITE_CONTACTS` | resolve names, nicknames, add people | no calls/SMS by name |
| `SEND_SMS` / `READ_SMS` / `RECEIVE_SMS` | send & read texts | messaging only via notifications |
| `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_PHONE_NUMBERS` | "who called", caller id, redial | no call history |
| `ANSWER_PHONE_CALLS` | answer by voice | announce only |
| `READ_CALENDAR` / `WRITE_CALENDAR` | agenda, create/reschedule events | no calendar |
| `ACCESS_FINE_LOCATION` / `COARSE` | where am I, navigate, nearby | no location features |
| `ACCESS_BACKGROUND_LOCATION` | geofences ("when I leave work") | no place triggers |
| `CAMERA` | take photos, scan, OCR | no camera |
| `READ_MEDIA_*` / `MANAGE_EXTERNAL_STORAGE` | photos, files, documents | no file features |
| `POST_NOTIFICATIONS` | JARVIS's own notifications, orb, briefings | no proactive layer |
| `RECORD_AUDIO` foreground service | always-on listening | button-only |

## C3. Special access — granted only in Settings (JARVIS deep-links each one)

| Access | How | Unlocks | Risk |
|---|---|---|---|
| **Notification listener** | Settings → Notifications → Notification access | read + reply to WhatsApp/Telegram/IG/SMS, digests, OTP | sees all notification text (OTPs are redacted by default) |
| **Accessibility service** | Settings → Accessibility → JARVIS | read screen, tap, type, scroll, drive any app, lock screen | **highest** — sees and can act in every app |
| **Overlay** (`SYSTEM_ALERT_WINDOW`) | Settings → Display over other apps | floating orb over any screen | low |
| **Usage access** | Settings → Usage access | "how long on Instagram", habit learning, suggestions | medium |
| **Write settings** | Settings → Modify system settings | brightness, DND, ring modes | medium |
| **All-files access** | Settings → All files access | documents, PDFs, file search | medium |
| **Battery: Unrestricted** | Settings → Battery → App → Unrestricted | background survival | none (battery only) |
| **OEM autostart** | Manufacturer's security/auto-start manager | service survives reboot on Tecno/Infinix/Xiaomi/Oppo | none |
| **Default assistant role** | `RoleManager.ROLE_ASSISTANT` (Android 8+) | long-press home / "Hey Google" slot replaces with JARVIS | low |
| **Unknown sources** | Settings → Install unknown apps | sideload updates | medium |
| **MediaProjection** | per-session consent dialog | screenshot, screen recording | on-screen consent every time |

## C4. Hardware it uses

Mic (always-on) · speaker/earpiece · Bluetooth + headset · GPS/network location ·
camera + torch · vibration motor · proximity/accelerometer (raise-to-talk, driving
detection) · NPU/Neural Engine if present (on-device wake word + STT + OCR) ·
display (overlay orb) · network (Wi-Fi/cellular for cloud features)

## C5. Accounts & API keys

| Key | Required? | Unlocks | Notes |
|---|---|---|---|
| **Gemini** or **OpenAI** | **Yes, for the smart half** | conversation, planning, function calling, summaries, web answers | Gemini has a free tier — recommended. Keep it out of git (`local.properties`) |
| **ElevenLabs** | optional | HD natural voice, real JARVIS British voice | without it: Android's built-in TTS |
| **Search API** (Brave/Tavily/Serper) | optional | real answers instead of opening a browser | cheap/free tiers |
| **Google Cloud STT/TTS** | optional | better noisy-environment transcription | |
| **Home Assistant** URL + token | optional | lights, plugs, scenes | |
| **Porcupine / Picovoice** | optional | best-in-class on-device wake word | free tier available |

## C6. What YOU must tell it (the part that makes it smart)

| Data | Why |
|---|---|
| Your name / what to call you | "Good morning, Macaulay" |
| **Nickname map** — mumsi = Amaka Okafor (mother), plus dad, "my guy", boss… | the entire reason "call mumsi" can work |
| Relationships for each person | enables "call my wife", "text my brother" |
| Home & work addresses | "navigate home", "how long to get home", geofences |
| App nicknames — "the bank app" → Kuda, "my music" → Spotify | natural speech |
| Favourite apps & defaults | music service, maps, browser |
| Wake word preference + whether always-on is allowed | battery vs convenience |
| Morning briefing time | proactive layer |
| Routines you want (Good morning / Driving / Good night) | Bixby-Routines parity |
| Memory opt-outs (topics never to store) | privacy |

**It never stores:** passwords, tokens, API keys, card numbers, OTPs, or anything matching
the sensitive filter — that's already in `JarvisMemoryManager` and stays.

## C7. Privacy model

- **On-device by default**: wake word, STT, intent routing, dialogue state, context graph,
  memory, device control, notifications, OCR
- **Leaves the phone only if you enable it**: the text of a request sent to the LLM,
  text sent to ElevenLabs for speech, and (optionally) usage habits for suggestions
- Notification OTPs/passwords are **redacted before storage**; you opt in to hearing them
- Full **audit log** of every action JARVIS took on your behalf
- One-tap **memory wipe** and one-tap **cloud kill switch**
- Accessibility can be turned off at any time; JARVIS drops to reduced mode, no crash

---

# PART D — THE SHORT VERSION

**What it does:** everything a person can do on a phone through the legitimate doors —
call, message, open and drive apps, control the device, read and reply to notifications,
manage time and places, find things, and talk about it — **while asking, confirming,
remembering and interrupting like a person would.**

**How it runs:** an always-on on-device wake word → speech → a **typed intent router**
for known actions and an **LLM with function calling** for everything else → a **dialogue
manager** that fills slots, disambiguates, confirms and repairs → real tools → streaming
speech you can interrupt → plus a proactive layer that acts without being asked.

**What it needs:** your microphone always; **notification access + phone/contacts/SMS**
for the messaging half; **accessibility** for the "drive any app" half; one AI key for the
brain; an ElevenLabs key for the voice; battery exemption and OEM autostart to survive in
the background; and from you personally — **the names and relationships of the people you
actually talk to.**
