# Accessibility Service — Systematic Diagnosis (Audit Item 6)

This document records a systematic read-through of the accessibility and
overlay subsystems against the JARVIS vision
(`HOW_JARVIS_IS_SUPPOSED_TO_WORK.md`). It is a diagnosis, not a spec: it
states what exists, what is correct, and what an owner should watch.

## 1. Service instance lifecycle — CORRECT

`JarvisAccessibilityService` (package `com.jarvis.android.accessibility`)
manages a single shared instance via a companion property:

- `onServiceConnected()` sets `instance = this` and flips `_isEnabled` to
  `true`, then configures `AccessibilityServiceInfo` with
  `TYPES_ALL_MASK`, `FEEDBACK_GENERIC`, `FLAG_REPORT_VIEW_IDS`,
  `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, and
  `FLAG_REQUEST_ACCESSIBILITY_BUTTON`.
- `onDestroy()` clears the instance with an identity guard
  (`if (instance === this) instance = null`) and flips `_isEnabled` to
  `false`. The identity guard correctly prevents a stale re-bind from
  clobbering a newer instance.
- `isServiceRunning()` returns `instance != null`.
- `isEnabled: StateFlow<Boolean>` is the observable version used by the UI.

`currentPackageName` is updated in `onAccessibilityEvent` from
`event.packageName`, which is what `MessagingAutomation.waitForPackage()`
polls to confirm an app actually came to the foreground. This is sound.

**One note:** there is no `onUnbind` override. Android unbinds/re-binds
accessibility services on settings toggles and across some system events;
the `onDestroy` path covers the teardown, and `onServiceConnected` covers
re-attach, so the lifecycle is complete. No fix required.

## 2. Overlay → orchestrator path — CORRECT (the "already fixed" critical connection)

The floating Orb (`JarvisFloatingOrbService`, package
`com.jarvis.android.overlay`) reaches the orchestrator through the
Application class, not through a singleton or a re-creation:

1. The orb obtains the engine via
   `(application as? JarvisApp)?.voiceEngine` — the **same** lazy
   `JarvisVoiceEngine` instance the orchestrator holds.
2. `onToggleMic` calls `engine.startListening()` / `engine.stopListening()`
   on that shared instance.
3. In `JarvisApp.onCreate`, `VoiceOrchestratorBridge.create(...)` wires
   `voiceEngine.onSpeechResult` → `orchestrator.submitUserInput()`. This
   is the bridge that was previously missing and is now in place.
4. Orb visual state (`JarvisVisualState`) and audio level are observed via
   `VoiceBus` flows, decoupling the overlay's rendering from the engine.

So a tap on the Orb → listen → recognize → `submitUserInput` →
`processUserCommand` → DialogueManager/AgentExecutor → tools. The path is
end-to-end and uses one engine instance. Confirmed working on HEAD.

## 3. Duplicate-id crash check — CORRECT and protective

`ToolRegistry.register()` calls `require(!tools.containsKey(tool.id))`
with a descriptive message and hard-fails on a duplicate id. This was the
root-cause fix for the old silent overwrites (battery, volume, flashlight,
call_contact, calendar, alarm, timer, navigate_to all had duplicate
registrations that silently replaced each other).

During this audit I found and removed one remaining duplicate:
`communication_send` (#5) duplicated `send_message` (#18). It is now
removed, and `send_sms` (PhoneTools) was rewired onto the unified
`MessagingAutomation` path. There are no remaining duplicate tool ids;
the `require()` guard will keep it that way going forward.

## 4. Active provider path — CORRECT but configuration-dependent

`ApiConfig.activeProvider` resolves in priority order:

1. The user's custom key/provider (entered in Settings, auto-detected from
   the key prefix, persisted in SharedPreferences).
2. The Rork AI gateway (Claude) as the zero-setup default brain.

`activeApiKey` mirrors this: custom key first, then `rorkApiKey`
(`BuildConfig.RORK_TOOLKIT_KEY`).

**After the Item 3 security fix**, `rorkApiKey` returns the BuildConfig
value only (default `""`). This means:

- With no Rork key in `local.properties` / CI secrets, `hasAI` is `false`
  and the app has **no default brain** until the user enters a custom key
  in Settings.
- This is the correct fail-closed trade-off: the app will not silently use
  a leaked credential. The owner MUST set the Rork key
  (`EXPO_PUBLIC_RORK_TOOLKIT_SECRET_KEY`) in `local.properties` / CI for
  the out-of-the-box Claude-via-Rork path to work.

`BackendConfig.isBackendReady` is the equivalent guard for the Convex
proxy path (added in the Item 4 fix): backend mode is only active when
both `USE_BACKEND` is true **and** `WORKER_URL` is a real `.convex.site`
URL, so the placeholder can no longer cause a silent bogus-host failure.

## 5. UI-driving helper surface — complete for the vision's worked example

The service exposes the primitives `MessagingAutomation` needs to drive a
real app the way a person would:

- `clickText(text)`, `clickElementByDescription(desc)` — tap by label.
- `setTextInField(marker, text)` — type into an editable field.
- `findTextOnScreen()` — read-back to verify text landed.
- `getStructuredScreenData()` — structured node tree (used to find an
  `editable` field).
- `scroll(direction)` — navigate lists.
- `screenSignature()` — before/after verification of a screen change.
- `performGlobal`/`back`/`home`/`recents` — system navigation.

These are general (not per-app hardcoded), which matches the vision's
"generalize, don't hard-code" principle. The only app-specific knowledge
in `MessagingAutomation` is the launch package id and a small ordered set
of locale-agnostic send-button affordance labels it tries in turn —
acceptable and necessary.

## Summary

| Area | Status |
|------|--------|
| Service instance lifecycle | Correct |
| Overlay → orchestrator path | Correct (critical connection wired) |
| Duplicate-id crash check | Correct; one duplicate removed this audit |
| Active provider path | Correct, fail-closed; needs Rork key configured |
| UI-driving helper surface | Complete for the worked example |

No code changes are required for Item 6 beyond what Items 1 and 3 already
delivered. The remaining action is operational: the owner must configure
the Rork key (and rotate the leaked keys per `docs/SECURITY.md`).
