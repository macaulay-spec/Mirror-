# How Siri & Bixby Actually Work — And How We Make JARVIS Like Them

---

# PART 1 — What Siri and Bixby do FIRST

## 1A. What happens in the first 300 milliseconds (the audio pipeline)

Before any "AI" is involved, both assistants run a fixed 7-stage pipeline. This is the
part JARVIS has almost none of.

```
 ① WAKE WORD        Always-on tiny neural net on a low-power core listening for
   "Hey Siri"/"Hi Bixby". Two-stage: a tiny model gates a bigger model, so the
   main CPU stays asleep. Runs for days on milliwatts.
        ↓
 ② VAD              Voice Activity Detection — when did speech start, when did it
   END? End-pointing decides when to stop recording and send.
        ↓
 ③ AUDIO FRONT-END  Echo cancellation (kill the music it's playing), beamforming,
   noise suppression. This is why Siri hears you over TV noise.
        ↓
 ④ ASR              Speech → text. On-device first (Siri: Neural Engine since iOS 15;
   Bixby: on-device on Samsung NPU), cloud fallback when needed.
        ↓
 ⑤ NLU              Text → INTENT + SLOTS. "call mumsi on mobile"
                    → intent=CALL_PERSON, slots={contact: mumsi, number_type: mobile}
        ↓
 ⑥ DIALOGUE         State tracker + policy: do I have every slot? Is there more than
   MANAGER          one "mumsi"? Is this action risky? → ask / confirm / execute
        ↓
 ⑦ FULFILLMENT      Call the API (SiriKit domain / Bixby Capsule / App Intent),
   + NLG + TTS      get a result, generate a natural reply, speak it — streaming
                    so audio starts before the sentence is finished.
```

**JARVIS today:** ④ only (Android SpeechRecognizer), a crude ⑤ (regex keyword list),
**no ⑥ at all**, and ⑦ that fires once with no follow-up. Stages ①, ②, ③ and
streaming ⑦ are missing. That's the whole "it feels dumb" feeling, in one table.

---

## 1B. What they make YOU do first (first-run setup)

This is the part everyone forgets, and it's half the magic. Siri and Bixby are dumb
until they finish onboarding — but onboarding is what makes them smart.

### Siri's first run
1. **Language + region + Siri voice** (pick accent, gender, speed)
2. **"Hey Siri" enrollment** — you read ~5 phrases so the wake word is tuned to your voice
3. **Allow Siri when locked** — the permission that makes hands-free useful
4. **Siri Suggestions opt-in** — permission to learn from app usage, location, habits
5. **Share with Apple / Improve Siri** — telemetry choice
6. **Personal context setup** — triggered *lazily*, at the moment it's needed:
   - The first time you say "call mumsi", Siri asks **"Who is mumsi?"** and you pick a
     contact. It then asks **"What's mumsi to you?"** → *mother / father / sister /
     friend / assistant…* and **saves the relationship permanently**.
   - Same for "my boss", "my guy", "home", "work".
7. **Default apps / services** — music service, payments, navigation
8. **Shortcuts / automations** — later, discoverable in the Shortcuts app

### Bixby's first run
1. Samsung account + terms
2. **"Hi Bixby" wake-up enrollment** — read phrases to train the wake word
3. **Voice wake-up consent** + Bixby Voice settings
4. **Bixby Home** — pick the cards you want (weather, calendar, news, health)
5. **Bixby Routines** — pre-built suggestions ("Good morning": read notifications,
   turn on Wi-Fi, tell me the weather) — you accept or edit
6. **Bixby Vision / Bixby Touch** — camera and hardware-button integrations
7. **Capsule permissions** — which apps Bixby is allowed to act inside

### The pattern — and why it matters for you

> **Neither assistant is smart because of its model. Both are smart because of the
> ~10 things they made you tell them on day one.** Relationships. Nicknames. Home.
> Work. Favourites. Permissions. Routines.

"Call mumsi" doesn't work on a phone that was never told who mumsi is. That's the single
most important lesson for JARVIS — and it's exactly the piece you asked for.

---

# PART 2 — The architecture lesson: Siri and Bixby are NOT LLM-only

This is the mistake in the current code, and it's worth stating plainly.

**What JARVIS does now:** hand a text blob to an LLM with a text list of tools, and ask
it to invent JSON. One shot. No state.

**What Siri and Bixby actually do:** a **hybrid**.

| Layer | Siri | Bixby | JARVIS must be |
|---|---|---|---|
| Understanding of **known actions** | **SiriKit domains** — fixed schemas (Messaging, Calls, Payments, Maps, Media, Photos, Weather) with declared slots | **Bixby Capsules** — apps declare concepts, actions and views in a structured model | **Structured intent catalog** — `CALL_PERSON`, `SEND_MESSAGE`, `OPEN_APP`, `SET_VOLUME`… each with typed slots |
| Dialogue | Stateful slot-filling, disambiguation lists, corrections | Explicitly conversational & relational — "no, the other one" works | **Dialogue manager** with slots, asks, confirms, repairs |
| Fulfillment | App Intents / Shortcuts — real APIs, real results | Capsule runtime | **ToolRegistry with real function calling** (schemas, not prose) |
| General knowledge / chat | Apple Intelligence LLM (added *on top*, 2024) | LLM features added later | **LLM as the generalist** — conversation, planning, summarising, web |
| Proactive | Siri Suggestions, widgets, automations | Bixby Routines, Bixby Home | **Proactive engine** — briefings, nudges, routines |

**Rule we should adopt:** *Deterministic structured intents for anything that must not
fail (calls, messages, payments). LLM for everything else (conversation, planning,
knowledge, multi-step device tasks).* Siri didn't become an LLM until 18 years in —
and it kept the structured layer underneath.

---

# PART 3 — The 7 pieces JARVIS needs to feel like Siri/Bixby

## Piece 1 — Audio front-end (① ② ③)
- **Wake word**: today it's Android `SpeechRecognizer` restarted in a loop (battery
  heavy, misses words, needs network for accuracy). Options, behind one
  `WakeWordEngine` interface so they are swappable:
  - **Vosk (default)** — fully offline, open source, **no account, no email, no key**.
    One-time ~45 MB model download at setup. Runs a tiny grammar
    `["hey jarvis", "jarvis", …]` continuously against `vosk-model-small-en-us`.
    ~2–4 % battery/hour, zero network, zero telemetry.
  - **System SpeechRecognizer** — zero download, zero setup, works on the very first run
    before any model is downloaded. Kept as the fallback.
  - **Picovoice Porcupine (optional)** — best accuracy, ~1 % battery/hour, model measured
    in kilobytes — but needs a free Picovoice account. Only if you want one.
- **VAD + end-pointing** — stop listening the moment you finish speaking instead of
  waiting for a timeout (currently `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`
  only)
- **Echo cancellation** — so JARVIS can hear you *while it is speaking* → **barge-in**:
  you interrupt it and it stops immediately. This alone makes it feel alive.

## Piece 2 — Intent catalog + slot filling (⑤) — NEW
A real NLU layer, local and instant, before the LLM ever runs:

```kotlin
sealed interface Intent {
  data class CallPerson(val contact: String?, val numberType: String?,
                        val confirm: Boolean) : Intent
  data class SendMessage(val contact: String?, val body: String?) : Intent
  data class OpenApp(val app: String?, val nickname: String?) : Intent
  data class SetVolume(val level: Int?, val direction: String?) : Intent
  data class Navigate(val destination: String?, val saveAs: String?) : Intent
  data class Unknown(val raw: String) : Intent   // → hand to the LLM
}
```

Each intent declares **required slots**, **optional slots**, and a **risk tier**.
Parsing uses: keyword/grammar rules + gazetteers (contact names, installed-app names,
nicknames) + a small local time parser for calendar/alarms.

## Piece 3 — Dialogue manager (⑥) — THE MISSING PIECE
This is the thing that turns a command runner into an assistant.

```kotlin
class DialogueManager {
    var openSlot: Slot? = null            // "which mumsi?"
    var pendingConfirm: PendingAction?=null // "call mumsi now?"
    val entities = EntityMemory()          // lastContact, lastApp, lastNumber…

    fun handle(utterance: String, context: Context): DialogueResult
}
```

Behaviours it must implement:

| Behaviour | Example |
|---|---|
| **Ask for a missing slot** | "Text Tunde" → *"What should I say to Tunde?"* |
| **Disambiguate** | Two numbers for Mumsi → *"Home or mobile?"* |
| **Confirm risky actions** (L2+) | *"Calling Mumsi on mobile. Yes or no?"* |
| **Resolve pronouns** | "call her back" → uses `lastContact` |
| **Accept corrections** | "no, the other one" / "make it 7pm instead" |
| **Cancel** | "stop", "never mind", "cancel that" |
| **Repair on failure** | WhatsApp missing → *"Not installed. SMS instead?"* |
| **Follow-ups** | after calling → *"Want me to text her you're on your way?"* |
| **Chained intents** | "open WhatsApp and tell Tunde I'm coming" |

Reply protocol (four actions, not two):

```jsonc
{ "action": "reply",   "message": "..." }
{ "action": "tool_call", "tool": "...", "arguments": {...} }
{ "action": "ask",     "slot": "contact", "question": "Which Mumsi?",
  "options": ["Home", "Mobile"] }
{ "action": "confirm", "tool": "call_contact", "arguments": {...},
  "prompt": "Call Mumsi on mobile?", "risk": "LEVEL_2" }
```

## Piece 4 — Personal context graph (the "who is mumsi" database) — NEW
An on-device knowledge graph, Room-backed, that the intent layer reads first:

```
People    { name, nicknames[], relationship, phoneNumbers[], preferredChannel,
            notes ("prefers calls over texts") }
Places    { label: home|work|gym, address, lat/lng }
Apps      { label, package, nicknames[] ("the bank app" → Kuda) }
Habits    { "calls mumsi Sunday evenings", "orders lunch ~1pm" }
Prefs     { addressName, wakeWord, voiceId, briefingTime, defaultMusicApp }
```

**Seeded at onboarding, grown lazily** exactly like Siri: the first time a name is
unknown, JARVIS asks "Who is mumsi?" once, then never again.

## Piece 5 — Fulfillment with real function calling (⑦)
- Export the `ToolRegistry` as **JSON schemas** and pass them to Gemini/OpenAI via
  **native function calling** — no more prose-JSON guessing
- Every tool returns a typed result; failures return a **reason** so the dialogue
  manager can repair rather than give up
- Action audit log visible in Settings ("what did JARVIS do on my behalf?")

## Piece 6 — Proactive layer (Siri Suggestions / Bixby Routines)
Runs without you speaking, driven by `WorkManager` + the notification listener +
usage stats + a time/place trigger engine:

- **Morning briefing** (configurable time): weather, today's calendar, unread summary
- **Context nudges**: "Battery 15%", "Meeting in 20 minutes — leave now, 25 min traffic"
- **Routines**: `IF {time=07:00 OR charging} THEN {read notifications, weather, calendar}`
- **Habit suggestions**: "You usually call Mumsi on Sunday — call her now?"
- **Lock-screen / notification cards** so JARVIS is useful without the app open

## Piece 7 — Voice & personality
- Real JARVIS-style British voice (current default is "Rachel", American)
- **Streaming TTS** so the first word lands in ~400ms instead of ~2.5s
- Barge-in (from Piece 1)
- Speaking style per context: terse confirmations ("Done."), warmer long answers
- Optional voice enrollment so it recognises *your* voice and ignores others

---

# PART 4 — The first-run experience we should copy (step by step)

JARVIS onboarding currently: 5 screens (welcome → identity → voice → device control →
notifications). Replace with Siri/Bixby-style guided setup:

1. **"What should I call you?"** → name, stored in `ApiConfig.userName`
2. **Voice setup** → pick HD voice (ElevenLabs) or offline voice; test sentence
3. **Wake word enrollment** → say "Hey JARVIS" 3× ; choose always-on or button-only
4. **Permissions, one screen at a time, each explaining WHY**
   (mic → phone → contacts → SMS → notifications → accessibility → battery → overlay)
5. **"Who matters to you?"** → import contacts, then tag 3–5 relationships:
   *"Who is Mumsi?" → pick contact → "What is she to you?" → mother*
6. **Home & work** → set both addresses (enables "I'm leaving", "how long to get home")
7. **App nicknames** → "the bank app", "my music" → map to real packages
8. **Routines starter pack** → accept/edit: Good morning, Driving, Good night, Low battery
9. **Proactive opt-in** → allow briefings, suggestions, and learning from usage
10. **Finish → "Try me"** → suggested first commands: *"Call Mumsi"*, *"What's my day
    look like?"*, *"Read my messages"*

---

# PART 5 — What we build first (dependency order)

| # | Build | Why first | Unlocks |
|---|---|---|---|
| **1** | **Dialogue manager** (slots, ask, confirm, entity memory, cancel, repair) | Everything else plugs into it. Currently the single missing layer. | "call mumsi" that asks, confirms, and remembers |
| **2** | **Personal context graph** + the "Who is mumsi?" onboarding step | Without it, `contact: mumsi` resolves to nothing | all people-based commands |
| **3** | **Intent catalog for the top ~12 actions** (call, text, open, volume, brightness, wifi, bluetooth, DND, flashlight, media, navigate, remind) + fix the `open_app` bug | Deterministic = reliable; Siri does this, not the LLM | 80% of daily commands work offline and instantly |
| **4** | **Permissions & real device tools** (CALL_PHONE, READ_CONTACTS, SEND_SMS, brightness, DND, Wi-Fi) | The intents need actual implementations | calls, SMS, settings actually execute |
| **5** | **Risk-tier confirmations** wired to the UI card + voice yes/no | Required before the app can send or call on your behalf | trust |
| **6** | **LLM upgrade: native function calling** + demote the LLM to "generalist" | Complex/novel commands stop failing | multi-step plans, summarising, web |
| **7** | **Wake word + VAD + barge-in + streaming TTS** | The "feels alive" layer | hands-free that doesn't feel like a toy |
| **8** | **Proactive engine** (briefings, nudges, routines) | Siri Suggestions / Bixby Routines parity | JARVIS without being asked |
| **9** | **Personality, memory surfacing, polish** | Delight | it sounds like JARVIS |

---

# PART 6 — The one-paragraph answer

> Siri and Bixby feel intelligent because, before the model ever runs, they (1) catch
> your wake word on low-power hardware, (2) turn your sentence into a **typed intent
> with slots**, (3) run a **stateful dialogue manager** that asks, disambiguates,
> confirms and repairs, (4) execute through **structured app APIs**, and (5) speak back
> with **streaming audio you can interrupt**. And on day one they made you tell them
> who your people are. JARVIS has steps 2-and-a-half and none of the rest. We build the
> dialogue manager and the personal context graph first — those two are what turn
> "open Chrome" into "call mumsi and tell her I'm on my way."
