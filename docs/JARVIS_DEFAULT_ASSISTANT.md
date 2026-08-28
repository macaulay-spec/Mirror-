# JARVIS as the Phone's Default AI Assistant

**Requirement:** you must be able to set JARVIS as the default digital assistant on the
phone — the way Siri owns the iPhone's side button and Bixby owns Samsung's.

This is now **E5** in the build manifest, and it is not a nice-to-have: without it JARVIS
is an app you have to open. With it, JARVIS is *the* assistant on the device.

---

## 1. What "default assistant" actually buys you

| Without it (today) | With it |
|---|---|
| You must find and open the JARVIS app | **Long-press home / corner-swipe gesture** launches JARVIS instantly, from any app |
| Wake word only works while our own service is alive | System routes the assist gesture to JARVIS; hotword can run at the DSP level |
| Overlay orb needs `SYSTEM_ALERT_WINDOW` permission | The system draws JARVIS's UI over any app — **no overlay permission needed** |
| No lock-screen presence | JARVIS can answer on the lock screen (with a privacy policy you control) |
| Bluetooth headset button goes to Google/Bixby | Headset long-press can route to JARVIS |
| Another app's screen is invisible to JARVIS | Assist gives us the **on-screen context** (`AssistContent`) — "summarize this page", "what am I looking at?" |
| It feels like an app | It feels like the phone's assistant |

---

## 2. Three levels of integration

### L1 — Assistant role + assist intent *(P0 — the must-have)*

**Timeline: P0. Works on essentially every Android 8+ phone.**

Two manifest lines and one system dialog:

```xml
<activity android:name=".assist.AssistActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:excludeFromRecents="true"
    android:theme="@style/Theme.Jarvis.Translucent">
    <intent-filter>
        <action android:name="android.intent.action.ASSIST"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VOICE_COMMAND"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
</activity>
```

```kotlin
object AssistantRoleManager {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)
            .isRoleHeld(RoleManager.ROLE_ASSISTANT)          // API 26+ ✔ our minSdk is 26

    fun request(activity: Activity, requestCode: Int) {
        val rm = activity.getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            openFallbackSettings(activity); return
        }
        activity.startActivityForResult(
            rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), requestCode)
    }

    fun openFallbackSettings(context: Context) {              // OEM differences
        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
```

**Result:** Settings → *Apps → Default apps → Digital assistant app* → JARVIS. After
that, the home gesture and the headset button summon JARVIS, listening, over whatever
you were doing.

### L2 — `VoiceInteractionService` *(P1 — the Siri-parity layer)*

**Timeline: P1.** This is the real assistant API (`android.service.voice`, API 21+) and
the thing that makes JARVIS behave like Google Assistant rather than like a normal app.

```xml
<!-- The session service: draws JARVIS's UI over ANY app, drawn by the system -->
<service android:name=".assist.JarvisInteractionSessionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionSessionService"/>
    </intent-filter>
</service>

<!-- The main voice-interaction service: lifecycle + hotword -->
<service android:name=".assist.JarvisVoiceInteractionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <meta-data android:name="android.voice_interaction"
        android:value="com.jarvis.app.assist.JarvisInteractionSessionService"/>
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService"/>
    </intent-filter>
</service>
```

```kotlin
class JarvisVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        // start/enroll the hotword detector, warm the STT engine, load the context graph
    }
}

class JarvisInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?) = JarvisSession(this)   // Compose UI,
}                                                                    // drawn by the system
```

**What L2 unlocks**
- JARVIS's UI (orb + transcript + confirmations) appears **over any app**, drawn by the
  system → **we no longer need `SYSTEM_ALERT_WINDOW`** on devices that support it
- Works on the **lock screen**
- Receives `AssistContent` and the visible-activity context, so "what's on my screen?"
  and "summarize this article" become possible
- `AlwaysOnHotwordDetector` — attempt DSP-level "Hey JARVIS"

⚠️ **Honest caveat on DSP hotword:** on most shipping phones, enrolling a *custom*
keyphrase ("Hey JARVIS") requires system-private APIs (`KeyphraseModelManager` is
hidden). Only "Ok Google"-class preinstalled models are guaranteed. So the plan is:
**try `AlwaysOnHotwordDetector`; if the device refuses, fall back to our own Porcupine /
openWakeWord engine running in the foreground mic service.** Either way you get
hands-free — the fallback just costs a little more battery.

### L3 — OEM reality & fallbacks *(P1)*

| OEM / skin | Status | Path if blocked |
|---|---|---|
| **Stock / Pixel / Android One / Nokia / Motorola** | clean | role request works, L2 fully supported |
| **Samsung (One UI)** | *Digital assistant app* setting exists; Bixby may be hard-wired to the side key on older models | role request → if the gesture is stolen, use the Quick Settings tile + floating orb |
| **Tecno / Infinix / Itel (HiOS)** | usually allows default assistant | role request → autostart ON, battery Unrestricted |
| **Xiaomi (MIUI/HyperOS)** | often restricts | `ACTION_VOICE_INPUT_SETTINGS` → if refused, tile + orb |
| **Huawei / others without GMS** | role usually works | SpeechRecognizer may be absent → cloud STT key needed |

**Fallback ladder so JARVIS is always reachable even on hostile devices:**
1. Home gesture / headset (L1+L2)
2. Quick Settings tile — `TileService` (API 24+): one tap to start listening
3. Floating orb (overlay permission)
4. Persistent notification with a "Tap to talk" action
5. App icon / launcher shortcut

---

## 3. Onboarding & Settings changes this forces

**Onboarding — insert as step 4, right after permissions:**

> **"Make JARVIS your default assistant"**
> *"So you can summon me from any screen with the home gesture, without opening the app."*
> → [Set as default] → system dialog → verify `isRoleHeld()` → continue
> → if refused: "You can do this later in Settings." (never block onboarding)

**Settings — new "System Assistant" section:**

| Row | Shows | Action |
|---|---|---|
| Digital assistant app | DEFAULT / NOT SET | request role or open the settings page |
| Home gesture | supported / stolen by OEM | link to OEM guidance |
| Wake word | enrolled / not enrolled | 3-phrase enrollment |
| Lock-screen assist | on / off | reads messages only when unlocked by default |
| Quick Settings tile | added / not added | instructions to add the tile |
| Floating orb | on / off | overlay permission |

---

## 4. Privacy rules that come with being the default assistant

- **Lock-screen policy (default OFF):** on a locked screen JARVIS will tell time, set
  alarms, control the device — but will **not** read messages, notifications or memory
  aloud until you unlock. One toggle to relax it.
- Assist context (`AssistContent`) is read **only** when you invoke JARVIS, never
  continuously, and never stored.
- As the assistant, JARVIS can be launched on top of any app — so the **audit log** and
  the **risk-tier confirmations** (E1/P1) are mandatory, not optional.
- Emergency stop stays available from the session UI, the tile and the notification.

---

## 5. Battery cost of "always available"

| Approach | Typical cost |
|---|---|
| Today: Android `SpeechRecognizer` restarted in a loop | **2–4 % / hour** |
| Porcupine / openWakeWord in a foreground mic service | **~1 % / hour** |
| `AlwaysOnHotwordDetector` on the DSP (where supported) | **~0.3 % / hour** |
| Quick Settings tile + manual invocation | ~0 % (nothing running) |

That's why L2's DSP path is worth attempting even though we keep the fallbacks.

---

## 6. Build checklist

| # | Item | Phase |
|---|---|---|
| 1 | `AssistActivity` + `ACTION_ASSIST` / `ACTION_VOICE_COMMAND` filters | P0 |
| 2 | `AssistantRoleManager` (isHeld / request / OEM settings fallback) | P0 |
| 3 | Onboarding step "Make JARVIS your default assistant" | P0 |
| 4 | Settings → System Assistant section with live status | P0 |
| 5 | Deep-link JARVIS's dialogue manager from the assist intent (open already-listening) | P0 |
| 6 | `VoiceInteractionService` + `VoiceInteractionSessionService` + Compose session UI | P1 |
| 7 | `AlwaysOnHotwordDetector` attempt → Porcupine fallback | P1 |
| 8 | `AssistContent` capture → "what's on my screen?" / "summarize this" | P1 |
| 9 | Quick Settings `TileService` | P1 |
| 10 | Lock-screen policy + audit log integration | P1 |
| 11 | OEM fallback matrix + in-app guidance per device | P1 |

---

## 7. The honest bottom line

**Yes — JARVIS can become the phone's default assistant.** The role API is public, our
minSdk (26) is exactly the version it was introduced in, and the manifest changes are
small.

What varies by device is **how much** of the deep integration the OEM lets through:
the home gesture and the "digital assistant app" slot are reliable; DSP-level "Hey
JARVIS" is device-dependent and may need our own wake-word engine; Samsung and Xiaomi
may need manual Settings work. So we build the fallback ladder (tile → orb →
notification → app icon) from day one, and JARVIS is always one tap away regardless.
