# JARVIS — Master Build Manifest

> The complete inventory of what gets built. "Call mumsi" is only the **proof case** for
> the engine — it is one row in this document, not the project.
>
> Phases: **P0** works at all · **P1** behaves like an assistant · **P2** daily usefulness
> · **P3** proactive · **P4** polish. Items marked `FIX` already have code; `NEW` is from
> scratch; `OK` works today.

---

## SECTION 1 — THE ENGINE (built first, used by everything else)

These four are **not** the "calling feature". They are the machine. Every capability in
Section 2 plugs into them.

| # | Subsystem | Phase | What it is |
|---|---|---|---|
| E1 | **Dialogue Manager** | P0 | slots, ask, disambiguate, confirm, entity memory, corrections, cancel, repair, follow-ups |
| E2 | **Personal Context Graph** | P0 | people + relationships + nicknames, places, app nicknames, habits, preferences (Room) |
| E3 | **Intent Router** | P0 | ~40 typed intents with slots, resolved locally and offline |
| E4 | **Fulfilment Layer** | P0/P1 | `ToolRegistry` exported as JSON schemas → native function calling → typed results with reasons |
| E5 | **System Assistant Integration** | P0/P1 | become the phone's **default digital assistant** (`RoleManager.ROLE_ASSISTANT`) + `VoiceInteractionService` so JARVIS is launched by the home gesture, draws over any app **without overlay permission**, and works from the lock screen — see `JARVIS_DEFAULT_ASSISTANT.md` |

**The invariant:** after E1–E4 exist, adding any new capability = **one `ToolDefinition`
+ one intent schema**. No architecture change. That's why E1–E4 come first and why
"call mumsi" is merely the first thing we test them with.

---

## SECTION 2 — THE FULL CAPABILITY INVENTORY

### 2.1 Calls — 9 tools
| Tool | Phase | Status |
|---|---|---|
| `call_contact` (name / nickname / relationship / number) | P0 | NEW |
| `redial_last` / "call her back" | P0 | NEW |
| `disambiguate_number` (home vs mobile) | P0 | NEW |
| `end_call` | P0 | NEW |
| `toggle_speakerphone` | P1 | NEW |
| `answer_call` / `reject_call` | P1 | NEW (reject needs InCallService — partial) |
| `read_call_log` / missed calls | P1 | NEW |
| `call_via_app` (WhatsApp / Telegram / Duo) | P2 | NEW |
| Contact → relationship tagging ("who is mumsi?") | P0 | NEW (part of E2) |

### 2.2 Messaging — 11 tools
| Tool | Phase | Status |
|---|---|---|
| `send_sms` (contact or number) | P0 | FIX (`DeviceToolkit.sendSms` written, unreachable) |
| `read_sms_conversation` | P1 | NEW |
| `reply_notification` (WhatsApp/Telegram/IG/Messenger/X) | P0 | FIX (alias bug) |
| `send_whatsapp_message` (new chat, not a reply) | P1 | NEW (deep link + ACC) |
| `send_telegram_message`, `send_instagram_message` | P2 | NEW |
| `read_unread_digest` (grouped by sender) | P1 | FIX |
| `mark_as_read`, `dismiss_notification`, `clear_all` | P1 | FIX |
| `schedule_message` | P3 | NEW |
| `read_otp` (opt-in; redacted by default) | P1 | FIX (currently redacted, never read) |
| `draft_and_confirm` flow | P0 | NEW |

### 2.3 Apps — 7 tools
| Tool | Phase | Status |
|---|---|---|
| `open_app` — **any app, by name or nickname** | P0 | **FIX (the arg-name bug)** |
| `app_search` (YouTube / Spotify / Maps / Play / Chrome) | P0 | OK |
| `play_specific_media` ("play Burna Boy on Spotify") | P1 | FIX |
| `deep_link_chat` ("open my chat with Tunde") | P1 | NEW |
| `list_apps`, `open_app_info`, `clear_cache` | P2 | NEW |
| `get_app_usage` ("how long on Instagram today") | P2 | OK |
| App nickname map ("the bank app" → Kuda) | P0 | NEW (part of E2) |

### 2.4 Device control — 18 tools
| Tool | Phase | Status |
|---|---|---|
| `device_flashlight`, `device_volume`, `device_vibrate` | P0 | OK/FIX |
| `set_brightness` | P0 | FIX (written, unexposed) |
| `toggle_wifi` | P0 | FIX (written, unexposed) |
| `toggle_bluetooth`, `connect_bluetooth_device` | P1 | NEW |
| `set_ringer_mode` (silent/vibrate/ring) | P1 | NEW |
| `toggle_dnd` | P0 | FIX (written, unexposed) |
| `device_lock` | P0 | OK |
| `battery_info`, `device_storage`, `device_connectivity` | P0 | OK |
| `toggle_battery_saver` | P2 | NEW |
| `device_media_control` + `get_now_playing` | P0/P1 | OK / NEW |
| `set_alarm`, `cancel_alarm`, `set_timer` | P2 | NEW |
| `take_photo`, `record_video` | P2 | NEW |
| `screenshot`, `screen_record` | P2 | NEW |
| `clipboard_read`, `clipboard_write` | P2 | NEW |
| ❌ mobile data / airplane / hotspot / reboot | — | *blocked by Android* |

### 2.5 Screen control (Accessibility) — 12 tools
`screen_read`, `find_text`, `click_element`, `type_text`, `scroll`, `tap`, `swipe`,
`press_back`, `press_home`, `open_recents`, `device_navigate_global` — **all OK today**,
plus NEW in P2: `screen_describe` (screenshot + vision model) and `multi_step_flow`
(reliable plans inside apps).
❌ banking / `FLAG_SECURE` screens — blocked by Android.

### 2.6 Notifications — 6 tools
`read_notifications` (FIX), `dismiss_notification` (OK), `notification_digest` (P1 NEW),
`mute_app` / `quiet_hours` (P2 NEW), `announce_while_driving` (P3 NEW),
persistent history (already in Room — surface it in P2).

### 2.7 Contacts & people — 7 tools
`contact_lookup` (FIX — `ContactsToolkit` written, dead code), `contact_create`,
`contact_update`, `reverse_number_lookup`, `set_relationship`, `set_nickname`,
`list_relationships`. **P0/P1.**

### 2.8 Calendar, reminders, alarms — 6 tools
`calendar_create` (FIX — currently hardcoded "tomorrow 9am"; needs real time parsing),
`calendar_read`, `calendar_update`, `calendar_delete`, `set_reminder` (incl. location-based),
`list_reminders`. **P0–P2.**

### 2.9 Location & navigation — 6 tools
`device_location` (OK), `navigate_to`, `travel_time` (traffic), `nearby_search`,
`share_location`, `geofence_add`. **P1–P2.**

### 2.10 Files, camera, documents — 7 tools
`file_read` (txt/md/json/csv/log — FIX, unreachable; PDF/DOCX/XLSX — NEW),
`ocr_image` (ML Kit, on-device, P2), `describe_image` (vision, P2), `scan_qr` (P2),
`find_photo` / `share_photo` (P2), `summarize_document` (P2).

### 2.11 Web & knowledge — 7 tools
`web_answer` (real answer, not a tab), `summarize_url`, `get_weather`, `get_fx_rate`
(USD→NGN), `get_news`, `translate`, `calculate`. **P2, all NEW.**

### 2.12 Media — 4 tools
`device_media_control` (OK), `get_now_playing` (P1), `media_seek` (P2),
`identify_song` (P3, optional).

### 2.13 Memory & personalization — 8 tools
`memory_remember` / `memory_recall` (FIX), `memory_forget`, `memory_list`,
`memory_export`, `set_preference`, `auto_learn_preferences`, `per_person_notes`. **P0–P2.**

### 2.14 Background & proactive — 10 items
`start_wake_word_service` (**FIX — never started**), `floating_orb` (**FIX — never
started**), `morning_briefing`, `context_nudges` (battery / calendar / traffic),
`routine_engine` (Good morning / Driving / Good night / Low battery),
`habit_suggestions`, `headset_autolaunch`, `charging_mode`, `driving_mode`.

### 2.18 ⭐ Default system assistant — 9 items — **see `JARVIS_DEFAULT_ASSISTANT.md`**
`RoleManager.ROLE_ASSISTANT` request flow (**P0, nothing uses it today**),
`ACTION_ASSIST` + `ACTION_VOICE_COMMAND` activity (**P0**), `VoiceInteractionService`
(**P1**), `VoiceInteractionSessionService` + session UI drawn by the system (**P1 —
removes the need for the overlay permission**), `AlwaysOnHotwordDetector` attempt with
Porcupine fallback (**P1**), `AssistContent` / screen context capture (**P1**),
Quick Settings tile (**P1**), lock-screen assist policy (**P1**), OEM fallback matrix
for Samsung/MIUI/HiOS (**P1**).

### 2.15 Voice & personality — 8 items
wake word (**FIX**), VAD end-pointing (NEW), echo cancellation + **barge-in** (NEW),
ElevenLabs HD (**FIX — silent failure**), real British JARVIS voice (NEW — current
default is American "Rachel"), streaming TTS (NEW), rate/pitch/style per context (NEW),
voice enrollment (NEW, optional).

### 2.16 Smart home — 2 items
`home_assistant_call` (lights, plugs, scenes) — needs your HA URL + token. **P3, optional.**

### 2.17 Safety & control — 6 items
risk-tier confirmations wired to the UI card + voice yes/no (**FIX — never fires**),
`audit_log` of every action, emergency stop (OK), access tiers (BASIC/STANDARD/FULL),
memory wipe (OK), cloud kill switch.

---

## SECTION 3 — TOTALS

| | Count |
|---|---|
| Tools in the registry today | 38 |
| Tools when complete | **≈ 120** |
| Subsystems (engine + domains) | 22 |
| Intent types in the catalog | ≈ 40 |
| Room entities | 8 (memories, conversation, notifications, people, places, apps, habits, audit) |
| Background services | 3 (wake word, notification listener, accessibility) |
| Screens | 7 (onboarding, home/voice stage, chat, settings, memory vault, routines, assist overlay) |
| API keys (optional except one) | 6 |
| Runtime permissions | 12 |
| Special Settings pages | 11 |

---

## SECTION 4 — PHASE CONTENT (what "done" means)

| Phase | Content | You can say |
|---|---|---|
| **P0** | E1–E5 engine · fix `open_app` · calls · SMS · contacts · device tools (brightness/DND/Wi-Fi) · real permissions · start the wake-word service · ElevenLabs diagnostics | "open WhatsApp", "call mumsi", "text Tunde", "brightness 30%", "Hey JARVIS" hands-free |
| **P1** | LLM native function calling · LLM demoted to generalist · confirmations · disambiguation · cross-app messaging · time parsing · location | it asks, confirms, corrects itself, plans multi-step |
| **P2** | notifications & digests · memory surfacing · calendar & reminders · files/OQR/OCR · web answers · weather & FX · alarms · screenshot | the daily-usefulness half |
| **P3** | morning briefing · nudges · routines · driving/charging modes · scheduled messages · smart home | it acts without being asked |
| **P4** | British JARVIS voice · streaming TTS · barge-in · personality · polish | it sounds and feels like JARVIS |

---

## SECTION 5 — WHY "CALL MUMSI" IS JUST THE TEST

```
E1 Dialogue Manager ──────────────┬── calls ── messaging ── calendar ── routines ─┐
E2 Context Graph    ──────────────┤                                                │
E3 Intent Router    ──────────────┼── device ── apps ── screen ── files ── web ────┤→ ≈120 tools
E4 Fulfilment Layer ──────────────┴── notifications ── location ── media ──────────┘
```

Every one of the ≈120 capabilities is the same shape: **an intent with slots → a
confirmation decision → a tool call → a spoken result.** Once that shape works for
"call mumsi", it works for "set an alarm for 6am", "how long to Lekki", "read my
messages", "turn the brightness down", "what's the dollar rate", and "remind me to
call mumsi when I get home" — because they are all the same code path with different
data.

**Build order is about proving the engine, not about prioritising calls.**
