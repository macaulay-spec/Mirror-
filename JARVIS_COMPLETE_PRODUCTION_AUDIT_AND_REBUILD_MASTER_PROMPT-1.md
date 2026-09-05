# JARVIS — COMPLETE PRODUCTION AUDIT, REPAIR PLAN & MASTER CODING-AGENT PROMPT

## Repository
`Mirror--main`

## Purpose

This is the master handoff specification for rebuilding the existing Android JARVIS application into the assistant it is intended to be.

This document combines the full audit of:

- AI brain / reasoning behavior
- conversation behavior
- tool selection and tool execution
- agent selection / agent architecture
- memory
- voice / STT / TTS
- wake word
- barge-in
- audio lifecycle
- Android accessibility
- Android notifications
- notification reading
- notification replies
- permissions
- default assistant role
- app lifecycle
- coroutine lifecycle
- race conditions
- state ownership
- proactive behavior
- UI/Orb state
- diagnostics
- exact file ownership
- replacement file structure
- migration strategy
- test plan
- acceptance criteria

The goal is NOT to make the current code merely compile.

The goal is to make JARVIS behave like one coherent intelligent Android assistant.

---

# 1. NON-NEGOTIABLE PRODUCT DEFINITION

JARVIS is NOT:

- a command parser;
- a collection of Android utilities;
- a chatbot with buttons;
- a tool-calling demo;
- a voice memo transcription application;
- a list of isolated agents;
- an LLM sitting beside deterministic scripts.

JARVIS IS:

> A persistent, conversational, context-aware, goal-driven Android assistant that understands the user's intent, decides what needs to happen, uses capabilities when necessary, observes the device, verifies actions, remembers useful context, recovers from failures, and communicates naturally through text and voice.

The user should be able to speak naturally.

The user should not need to know tool names, function names, provider names, API schemas, Android implementation details, or the internal agent architecture.

---

# 2. THE FUNDAMENTAL PROBLEM

The existing repository has many good components, but responsibility is fragmented.

There are multiple places that can:

- interpret intent;
- classify conversation;
- decide whether tools are used;
- execute tools;
- manage task state;
- manage memory;
- own voice state;
- own microphone recognition;
- play speech;
- mutate visual state;
- manage notifications;
- access accessibility;
- create background coroutine scopes.

This produces a system that can individually look functional while collectively behaving incorrectly.

The central repair principle is:

## ONE BRAIN

`JarvisCore`

owns understanding, goals, planning, memory/context assembly, tool decisions, task lifecycle and final response policy.

## ONE VOICE AUTHORITY

`JarvisVoiceController`

owns wake detection, microphone, STT, VAD, audio focus, TTS, playback, barge-in and voice state.

## ONE TASK AUTHORITY

`TaskManager`

owns active goals, steps, cancellation, verification and recovery.

## ONE TOOL AUTHORITY

`ToolRegistry` + structured `ToolExecutor`

owns capabilities and execution.

## ONE MEMORY AUTHORITY

`MemoryManager`

owns working, session, episodic, preference, explicit and task memory.

## ONE STATE MODEL

A central state machine feeds the UI/Orb.

No subsystem should invent its own independent truth about what JARVIS is doing.

---

# 3. CURRENT AI BRAIN AUDIT

## A-001 — DialogueManager can intercept the request before the main AI

`AssistantOrchestrator` currently uses `DialogueManager` before the main AI engine.

This creates a competing brain.

The user says something natural.

Instead of the central intelligence deciding what it means, a deterministic layer can decide that it has already handled it.

### Required fix

`DialogueManager` must no longer be the primary interpreter.

It may survive as a supporting component for:

- explicit confirmation state;
- missing-slot handling;
- deterministic safety policy;
- temporary dialogue state.

It must not decide the meaning of ordinary user input before `JarvisCore`.

---

# 4. A-002 — isConversational() is far too primitive

The current implementation uses hard-coded strings to decide whether something is conversational.

Examples include:

- hi
- hello
- how are you
- thank you
- tell me a joke
- etc.

and checks for command words such as:

- open
- send
- call
- message
- tap
- screen
- enable
- disable

This is not natural-language understanding.

A sentence can be simultaneously conversational and task-oriented.

Example:

> "I'm going to sleep, turn off the lights and remind me at seven."

The system must identify the entire goal, not force the sentence into a "chat" or "command" bucket.

### Required fix

Delete semantic dependence on `isConversational()`.

Every user utterance enters the same JARVIS Core.

The core determines:

- conversational response;
- question;
- action;
- multi-goal request;
- clarification;
- confirmation;
- continuation of an existing task;
- correction;
- cancellation.

---

# 5. A-003 — fastPath() undermines the assistant model

Fast paths are acceptable only as internal performance optimizations.

They must never become a separate semantic system.

The current implementation directly maps phrases such as battery/time/flashlight to tools.

That can be retained only if:

1. the semantic intent is already established;
2. the shortcut is behaviorally equivalent to the main path;
3. it does not steal utterances that the core needs to understand;
4. it produces the same memory/task/verification events.

The AI must remain the authority.

---

# 6. A-004 — tool execution is too close to the conversation layer

Tools are exposed as implementation objects.

The assistant must instead reason in terms of capabilities.

Internal:

`send_sms`

Human-facing:

"I've got the message ready for Sarah."

Never expose raw tool identifiers.

---

# 7. A-005 — tool results are not sufficiently structured

The model needs a true tool-result protocol.

Correct:

assistant:
tool_call(id=123, name=send_message, args=...)

tool:
tool_result(id=123, success=true, data=..., verification=...)

assistant:
final response

Incorrect:

tool result injected as if it were another user message.

This destroys conversational semantics and can confuse the model.

---

# 8. A-006 — AgentExecutor is not a complete autonomous goal manager

The current `AgentExecutor` performs a model/tool loop, but it is not the complete task lifecycle.

A real JARVIS task requires:

- goal;
- constraints;
- context;
- plan;
- current step;
- observation;
- result;
- verification;
- recovery;
- completion;
- cancellation.

Create a dedicated `TaskManager`.

---

# 9. A-007 — MAX_STEPS is an inadequate agent policy

The current executor has a small hard-coded step limit.

A universal fixed number is not a valid representation of task complexity.

Replace it with task budgets:

- max tool calls;
- max wall time;
- max repeated failure;
- max same-action retry;
- max plan depth;
- cancellation;
- user interruption.

A simple task should terminate quickly.

A complex Android task should be allowed to use more steps when justified.

---

# 10. A-008 — no robust observe → act → verify loop

Android automation must be grounded.

Example:

1. inspect current screen;
2. locate Accessibility;
3. click;
4. wait for state change;
5. inspect again;
6. verify desired screen/state;
7. continue.

A successful `performAction()` call does NOT mean the intended result occurred.

The agent must verify.

---

# 11. A-009 — recovery is underdeveloped

If one tool fails, JARVIS must reason about alternatives.

Example:

Primary:
notification RemoteInput reply.

Failure:
no RemoteInput.

Alternative:
open app and use accessibility.

Failure:
app UI changed.

Alternative:
ask user to complete one step or provide a different route.

The system must classify failures as:

- transient;
- permission;
- unavailable;
- ambiguous;
- unsupported;
- verification failure;
- fatal.

---

# 12. A-010 — memory is mostly explicit keyword memory

`JarvisMemoryManager` only promotes a small class of messages to long-term memory when the user explicitly says things such as:

"remember..."

This is useful, but insufficient.

JARVIS needs multiple memory layers.

---

# 13. REQUIRED MEMORY MODEL

## Working Memory

Current turn and immediate context.

## Session Memory

Current conversation.

## Episodic Memory

Important past events.

## Preference Memory

Stable user preferences.

## Explicit Memory

Things the user explicitly requested JARVIS remember.

## Entity Memory

People, apps, places, objects, relationships.

## Task Memory

Active and recently completed tasks.

## Device Context

Current app, screen, connectivity, permissions and capability state.

Memory retrieval must be relevance-based.

Do not inject the entire database into every AI request.

---

# 14. A-011 — memory recall is lexical, not semantic

Current recall logic splits the query into words and checks whether words occur in memory content.

This is a weak search strategy.

Replace with:

- normalized keyword search as fallback;
- embeddings/vector retrieval if backend supports it;
- metadata filtering;
- recency weighting;
- importance weighting;
- entity matching;
- task relevance.

A memory should be retrieved because it is relevant, not because it happens to contain the same three letters.

---

# 15. A-012 — session context is mutable and unsafely centralized

`SessionContext` is a mutable object.

It should become immutable snapshots emitted by a state store.

Use:

`StateFlow<JarvisContext>`

rather than arbitrary components mutating fields.

---

# 16. A-013 — active session identity must be explicit

Do not rely on `"default"` as the conversational identity.

Every conversation needs:

- session ID;
- turn ID;
- task ID;
- response generation ID.

These IDs are essential for cancellation and race prevention.

---

# 17. A-014 — provider selection is too entangled with behavior

AI provider/model selection is infrastructure.

It must not determine the assistant's personality, task architecture or memory behavior.

Use:

`ModelProvider`

behind a stable interface.

The brain should not care whether the provider is NVIDIA, OpenAI-compatible, Anthropic-compatible, local, proxy or another backend.

---

# 18. A-015 — streaming response handling must separate text from tool calls

Streaming can contain:

- text deltas;
- tool call deltas;
- tool arguments;
- finish signals.

Do not speak partial internal tool-call content.

The response stream must be classified before being sent to the voice engine.

---

# 19. A-016 — voice speaking must not begin merely because an arbitrary text delta arrives

The AI can stream text.

JARVIS voice should use sentence/phrase boundaries.

Correct:

AI stream:
"Sure. I'll check"
" your notifications."
→ TTS sentence:
"Sure. I'll check your notifications."

Not:

"Sure."
"I'll"
"check"
"your"
"notifications."

---

# 20. A-017 — the assistant must preserve conversational continuity after tool execution

Example:

User:
"Who messaged me?"

JARVIS reads notifications.

JARVIS:
"Sarah and Daniel messaged you."

User:
"What did Sarah say?"

JARVIS must know Sarah refers to the notification just discussed.

This requires task/entity/context continuity.

---

# 21. CHOOSE-AN-AGENT PROBLEM

The app must NOT expose a confusing "choose an agent" architecture if JARVIS is intended to behave as one assistant.

The user should not need to manually select:

- messaging agent;
- phone agent;
- device agent;
- research agent;
- voice agent;
- memory agent.

Those are internal capabilities.

If multiple specialized agents are retained internally, JARVIS Core is the supervisor.

Correct:

USER
→ JARVIS CORE
→ chooses capability/agent internally
→ executes
→ verifies
→ responds

Not:

USER
→ choose agent
→ choose tool
→ configure task
→ execute.

The user asked for JARVIS, not a software architecture exam.

---

# 22. INTERNAL AGENT ARCHITECTURE

If specialized agents are needed:

`JarvisCore`
- ConversationAgent
- ResearchAgent
- AndroidAgent
- CommunicationAgent
- DeviceAgent
- MemoryAgent
- ProactiveAgent

But they are NOT separate personalities.

They are workers under one JARVIS identity.

The user sees one assistant.

The supervisor owns:

- task selection;
- context;
- delegation;
- cancellation;
- result synthesis;
- verification.

---

# 23. VOICE AUDIT

The voice system currently has too many independent components.

Current relevant files:

- `app/src/main/java/com/jarvis/android/voice/JarvisVoiceEngine.kt`
- `app/src/main/java/com/jarvis/android/voice/CloudSttEngine.kt`
- `app/src/main/java/com/jarvis/android/voice/VoiceOrchestratorBridge.kt`
- `app/src/main/java/com/jarvis/app/voice/ElevenLabsTts.kt`
- `app/src/main/java/com/jarvis/app/voice/ElevenLabsVoicePlayer.kt`
- `app/src/main/java/com/jarvis/app/voice/JarvisVoice.kt`
- `app/src/main/java/com/jarvis/app/voice/SpeechOutput.kt`
- `app/src/main/java/com/jarvis/app/voice/VoiceBus.kt`
- `app/src/main/java/com/jarvis/app/voice/VoiceDiagnostics.kt`
- `app/src/main/java/com/jarvis/app/voice/WakeWordEngine.kt`
- `app/src/main/java/com/jarvis/app/voice/WakeWordForegroundService.kt`

This is too fragmented.

---

# 24. V-001 — multiple microphone owners

There are multiple speech input pathways:

1. wake `SpeechRecognizer`;
2. main `SpeechRecognizer`;
3. cloud `AudioRecord`.

Only one should own the microphone at any given time.

Create:

`JarvisVoiceController`

It owns microphone access.

---

# 25. V-002 — wake detection can lose the command

Current wake flow:

wake detected
→ `VoiceBus.onWakeWord()`
→ `VoiceBus.clearTranscript()`
→ stop wake recognizer
→ launch MainActivity
→ start foreground recognition.

Therefore:

"Hey JARVIS, turn on Bluetooth"

can lose "turn on Bluetooth".

### Correct behavior

Wake detector must return:

`WakeEvent`
- detected;
- timestamp;
- confidence;
- residual transcript/command.

If residual command exists, immediately send it to JARVIS Core.

---

# 26. V-003 — wake detection is not a true wake-word system

The current implementation uses Android speech recognition and searches for the word "jarvis".

This is not a real always-on wake detector.

It depends on recognition quality and can generate:

- false positives;
- missed detections;
- network dependency;
- battery drain;
- conflicts with normal STT.

Use a dedicated local wake-word engine when practical.

Potential implementation options can be selected by the coding agent after checking current Android compatibility.

The architecture must remain provider-independent.

---

# 27. V-004 — Cloud STT is incorrectly tied to ElevenLabs configuration

`CloudSttEngine` checks an ElevenLabs key.

That creates an incorrect dependency between STT and TTS.

STT configuration must be independent.

---

# 28. V-005 — fake Gateway fallback

`ElevenLabsVoicePlayer.tryGatewaySpeech()` currently returns `null`.

A function documented as a fallback cannot be a placeholder returning null.

Either implement it or remove it from the active fallback chain.

---

# 29. V-006 — multiple TTS engines

Current output paths include:

- ElevenLabs TTS;
- ElevenLabs VoicePlayer;
- SpeechOutput;
- Android TextToSpeech.

This creates inconsistent behavior.

Replace with:

`JarvisSpeechController`

with provider implementations.

---

# 30. V-007 — voice preview and actual voice playback must use the same profile

Create:

`JarvisVoiceProfile`

Fields:

- provider;
- voice ID;
- model;
- locale;
- speed;
- stability;
- style;
- volume;
- fallback policy.

The Settings preview and actual JARVIS responses must use the exact same profile.

---

# 31. V-008 — audio focus must have one owner

Only `JarvisVoiceController` should manage:

- audio focus;
- microphone focus;
- playback lifecycle.

---

# 32. V-009 — cloud STT behaves like voice memo transcription

Current cloud path:

record
→ save WAV
→ upload
→ wait
→ transcribe.

This causes unnecessary latency.

Prefer streaming STT.

---

# 33. V-010 — fixed VAD threshold is unreliable

A fixed amplitude threshold is device-dependent.

Use:

- adaptive noise-floor;
- provider VAD;
- streaming endpoint VAD;
- calibrated silence detection.

---

# 34. V-011 — barge-in must be real

When JARVIS is speaking:

USER SPEAKS
→ detect speech
→ immediately stop TTS
→ cancel current response generation
→ discard queued audio
→ start STT
→ process new request.

`stopSpeaking()` being callable is not sufficient.

---

# 35. V-012 — TTS queue must be generation-aware

Every response gets:

`responseGenerationId`

All speech fragments belong to it.

If interrupted:

cancel generation
→ stop player
→ clear queue
→ ignore late callbacks.

This prevents old audio from resurfacing after the user has moved on.

---

# 36. V-013 — continuous listening has lifecycle races

Current components can independently restart recognition.

Only the voice controller should transition:

`SPEAKING → IDLE → LISTENING`

No other component should restart the microphone.

---

# 37. V-014 — full audio download before playback increases latency

Prefer:

AI stream
→ sentence segmentation
→ TTS request
→ audio streaming/buffering
→ playback.

---

# 38. V-015 — proactive speech bypasses main voice system

`ProactiveReceiver` currently has its own speech pathway.

All proactive speech must pass through `JarvisSpeechController`.

---

# 39. V-016 — voice diagnostics are insufficient

Create an event timeline.

Required events:

- MIC_PERMISSION_CHECK
- MIC_OPEN
- MIC_OPEN_FAILED
- WAKE_STARTED
- WAKE_DETECTED
- WAKE_COMMAND_EXTRACTED
- STT_STARTED
- STT_PARTIAL
- STT_FINAL
- STT_ERROR
- AI_STARTED
- AI_FIRST_TOKEN
- TOOL_CALL_STARTED
- TOOL_RESULT
- TTS_STARTED
- TTS_FIRST_AUDIO
- PLAYBACK_STARTED
- PLAYBACK_FINISHED
- BARGE_IN
- RESPONSE_CANCELLED
- VOICE_ERROR
- RECOVERY_STARTED
- RECOVERY_FINISHED

Every event:

- timestamp;
- session ID;
- turn ID;
- task ID;
- response ID;
- provider;
- state;
- error code;
- message.

---

# 40. ACCESSIBILITY AUDIT

Primary file:

`app/src/main/java/com/jarvis/android/accessibility/JarvisAccessibilityService.kt`

The service contains useful repairs, but the architecture still relies heavily on a singleton:

`instance`

This is fragile.

The agent needs an abstraction:

`AccessibilityController`

which provides:

- current window;
- current package;
- screen snapshot;
- nodes;
- text;
- clickable elements;
- editable elements;
- scrollable elements;
- action execution;
- verification.

The service is merely Android's adapter.

---

# 41. ACCESSIBILITY — INSTANCE LIFECYCLE

Do not treat:

`instance != null`

as proof that accessibility is healthy.

A service object can exist while the expected UI state is stale.

Expose:

`AccessibilityState`

with:

- connected;
- last event time;
- current package;
- current window;
- root available;
- last successful observation;
- last action;
- last action verification;
- error.

---

# 42. ACCESSIBILITY — SCREEN SNAPSHOT

Create immutable:

`ScreenSnapshot`

containing:

- package name;
- activity/window;
- timestamp;
- visible text;
- node IDs;
- bounds;
- clickable;
- editable;
- enabled;
- selected;
- checked;
- content description;
- class name;
- scrollable.

The AI receives a compact structured representation.

Do not repeatedly query arbitrary nodes from different tools without a coherent snapshot.

---

# 43. ACCESSIBILITY — ACTION VERIFICATION

Every important action must support:

`ActionResult`
- accepted;
- observed;
- verified;
- reason;
- before snapshot;
- after snapshot.

Example:

tap "Allow"

must not be reported as success merely because:

`performAction(ACTION_CLICK) == true`

Instead:

tap
→ wait
→ observe
→ confirm desired state.

---

# 44. ACCESSIBILITY — RETRY STRATEGY

If a node disappears after a screen transition:

1. refresh snapshot;
2. re-identify target;
3. retry once;
4. use coordinate fallback only if grounded;
5. verify.

Never blindly repeat taps.

---

# 45. ACCESSIBILITY — PERMISSION/SETUP

The app must distinguish:

- service disabled;
- service enabled but not connected;
- connected but no root;
- connected and usable;
- permission/setup incomplete.

The AI must report the actual state.

It must never claim it can see the screen when it cannot.

---

# 46. ANDROID NOTIFICATION AUDIT

Primary files:

- `app/src/main/java/com/jarvis/app/notifications/JarvisNotificationListener.kt`
- `app/src/main/java/com/jarvis/app/notifications/NotificationRepository.kt`
- `app/src/main/java/com/jarvis/app/memory/NotificationEntity.kt`
- `app/src/main/java/com/jarvis/app/notifications/OtpExtractor.kt`

Current code has useful functionality for:

- notification collection;
- notification persistence;
- RemoteInput replies;
- OTP extraction;
- exact notification keys.

But the architecture needs strengthening.

---

# 47. N-001 — notification listener singleton is not reliable state ownership

`instance` is useful as a bridge but should not be the permanent data authority.

Create:

`NotificationController`

The service feeds events into it.

The repository stores state.

Tools query the repository/controller.

---

# 48. N-002 — onNotificationPosted does two separate things

It:

- refreshes all active notifications;
- persists the posted notification.

This can create redundant work and duplicate database records.

Use an upsert keyed by:

`StatusBarNotification.key`

plus update timestamp.

---

# 49. N-003 — notification persistence needs deduplication

The database must treat the Android notification key as the identity.

Do not insert identical notifications every time the listener refreshes.

---

# 50. N-004 — notification state needs lifecycle

Track:

- posted;
- updated;
- removed;
- dismissed;
- replied;
- read;
- actionable;
- stale.

---

# 51. N-005 — RemoteInput reply is not universally supported

A notification can exist without an inline reply action.

JARVIS must inspect:

- actions;
- RemoteInputs;
- result keys;
- pending intent;
- target notification.

If unsupported, return a structured capability failure.

Do not claim the message was sent.

---

# 52. N-006 — notification reply must always target the exact conversation

The existing code improved this by accepting notification key.

Keep that behavior.

Preferred order:

1. explicit notification key;
2. exact conversation identity;
3. recent notification only if unambiguous.

Never blindly reply to the first notification from an app.

---

# 53. N-007 — notification content extraction must preserve sender/message semantics

Do not flatten complex MessagingStyle notifications into ambiguous text.

Represent:

- app;
- conversation;
- sender;
- message;
- timestamp;
- notification key;
- reply capability.

This makes queries such as:

"What did Sarah say?"

possible.

---

# 54. N-008 — notification listener reconnection must be observable

`requestRebind()` is useful, but it is not proof that Android actually reconnected.

Diagnostics should expose:

- connected;
- disconnected;
- last event;
- last successful refresh;
- active notification count.

---

# 55. N-009 — notification permission/setup state must be explicit

The setup UI should distinguish:

- not granted;
- granted but disconnected;
- connected;
- restricted by OEM;
- unsupported action.

---

# 56. ANDROID DEFAULT ASSISTANT / CHOOSE-AGENT PROBLEM

`AssistantRoleManager` correctly uses Android `RoleManager` on supported versions.

However, the product must distinguish:

## Android default assistant role

This is an OS integration role.

## JARVIS internal agent selection

This is an AI architecture concern.

They are not the same thing.

Do not expose Android role selection as if it were selecting an internal JARVIS agent.

---

# 57. DEFAULT ASSISTANT BEHAVIOR

When JARVIS holds the assistant role:

Android can route assistant actions to JARVIS.

The app must then create an assistant entry point that:

- receives the invocation;
- captures invocation context;
- enters the same JARVIS Core;
- does not create a second assistant session.

The assistant invocation must become a new turn in the existing conversation/task context where appropriate.

---

# 58. ANDROID LIFECYCLE AUDIT

The repository contains multiple manually created scopes.

Examples include:

`CoroutineScope(SupervisorJob() + Dispatchers.Main)`

and raw:

`CoroutineScope(Dispatchers.IO).launch`

inside application/service code.

This creates work that can outlive the component that started it.

---

# 59. L-001 — no unifying lifecycle owner

Every long-lived component must have an explicit lifecycle.

Recommended:

- Application scope only for genuinely application-global work;
- service scope for service-owned work;
- activity scope for activity work;
- ViewModel scope for UI state;
- task scope for a JARVIS task;
- response scope for a single response;
- voice session scope for one voice interaction.

---

# 60. L-002 — raw CoroutineScope launches must be eliminated

Do not use:

`CoroutineScope(Dispatchers.IO).launch { ... }`

inside feature code unless that scope is intentionally owned and cancelled.

Use injected/owned scopes.

---

# 61. L-003 — response cancellation must be global

If the user interrupts or cancels a response:

- AI stream stops;
- tool loop stops;
- TTS stops;
- queued speech stops;
- callbacks from old generation are ignored.

Use:

`TurnCancellationToken`

or a coroutine `Job` owned by the current turn.

---

# 62. L-004 — stale callbacks must be rejected

Every asynchronous callback should carry:

- session ID;
- turn ID;
- generation ID.

Before applying result:

if result generation != current generation:
ignore it.

This prevents old network/audio/tool callbacks from corrupting new state.

---

# 63. L-005 — MainActivity must not be the assistant brain

`MainActivity` should:

- render UI;
- receive Android entry intents;
- connect UI events to the application controller;
- observe state.

It should not own the assistant's long-term task state.

---

# 64. L-006 — Orb service must not own assistant state

`JarvisFloatingOrbService` should observe `JarvisStateStore`.

It must not independently decide:

- listening;
- thinking;
- speaking;
- error.

---

# 65. L-007 — service shutdown must release resources

Every service must cancel:

- coroutines;
- microphone;
- audio focus;
- media players;
- wake locks;
- callbacks;
- listeners.

---

# 66. L-008 — wake lock ownership must be explicit

The wake-word service currently renews a wake lock.

The rebuilt architecture should make wake-lock use conditional and justified.

Never hold a wake lock indefinitely merely because the app wants to feel always-on.

Follow Android foreground-service and battery restrictions.

---

# 67. CENTRAL STATE MACHINE

Create:

`JarvisStateStore`

with:

`IDLE`
`WAKE_DETECTED`
`LISTENING`
`TRANSCRIBING`
`THINKING`
`PLANNING`
`EXECUTING`
`VERIFYING`
`SPEAKING`
`INTERRUPTED`
`WAITING_FOR_USER`
`ERROR`

State transitions are centralized.

Example:

IDLE
→ WAKE_DETECTED
→ LISTENING
→ TRANSCRIBING
→ THINKING
→ PLANNING
→ EXECUTING
→ VERIFYING
→ SPEAKING
→ IDLE

For simple conversation:

IDLE
→ LISTENING
→ TRANSCRIBING
→ THINKING
→ SPEAKING
→ IDLE

For interruption:

SPEAKING
→ INTERRUPTED
→ LISTENING

For clarification:

THINKING
→ WAITING_FOR_USER
→ LISTENING/INPUT
→ THINKING

---

# 68. EXACT NEW ARCHITECTURE

Create the following package structure:

`com.jarvis.core`

## Core

`JarvisCore.kt`
`JarvisStateStore.kt`
`TurnManager.kt`
`TaskManager.kt`
`ContextManager.kt`
`CancellationManager.kt`

## Core models

`JarvisTurn.kt`
`JarvisTask.kt`
`JarvisContext.kt`
`JarvisState.kt`
`Goal.kt`
`Plan.kt`
`PlanStep.kt`
`Observation.kt`
`VerificationResult.kt`
`CapabilityResult.kt`

## AI

`ai/JarvisBrain.kt`
`ai/ModelProvider.kt`
`ai/ModelResponse.kt`
`ai/ToolCall.kt`
`ai/PromptBuilder.kt`
`ai/ResponseComposer.kt`

## Agent

`agent/AgentSupervisor.kt`
`agent/AgentType.kt`
`agent/AgentCapability.kt`

Optional specialized workers:

`agent/AndroidAgent.kt`
`agent/CommunicationAgent.kt`
`agent/ResearchAgent.kt`
`agent/DeviceAgent.kt`
`agent/MemoryAgent.kt`

These are internal.

## Tools

`tool/ToolRegistry.kt`
`tool/ToolExecutor.kt`
`tool/ToolDefinition.kt`
`tool/ToolRiskPolicy.kt`
`tool/ToolVerification.kt`

## Memory

`memory/MemoryManager.kt`
`memory/MemoryRetriever.kt`
`memory/MemoryWriter.kt`
`memory/WorkingMemory.kt`
`memory/SessionMemory.kt`
`memory/EpisodicMemory.kt`
`memory/PreferenceMemory.kt`
`memory/EntityMemory.kt`
`memory/TaskMemory.kt`

## Voice

`voice/JarvisVoiceController.kt`
`voice/VoiceState.kt`
`voice/WakeWordProvider.kt`
`voice/SttProvider.kt`
`voice/TtsProvider.kt`
`voice/AudioController.kt`
`voice/BargeInController.kt`
`voice/VoiceDiagnostics.kt`
`voice/providers/AndroidSttProvider.kt`
`voice/providers/StreamingSttProvider.kt`
`voice/providers/ElevenLabsTtsProvider.kt`
`voice/providers/AndroidTtsProvider.kt`

## Android

`android/accessibility/AccessibilityController.kt`
`android/accessibility/AccessibilitySnapshot.kt`
`android/accessibility/AccessibilityAction.kt`
`android/accessibility/AccessibilityVerification.kt`

`android/notifications/NotificationController.kt`
`android/notifications/NotificationSnapshot.kt`
`android/notifications/NotificationAction.kt`
`android/notifications/NotificationVerification.kt`

`android/device/DeviceController.kt`

## Diagnostics

`diagnostics/JarvisEvent.kt`
`diagnostics/JarvisEventLog.kt`
`diagnostics/JarvisDiagnostics.kt`

---

# 69. EXACT OLD → NEW FILE MIGRATION

## Replace/retire as primary authorities

### Old
`agent/orchestrator/AssistantOrchestrator.kt`

### New
`core/JarvisCore.kt`

AssistantOrchestrator can become a thin compatibility adapter temporarily, then be removed.

---

### Old
`agent/dialogue/DialogueManager.kt`

### New
`core/ContextManager.kt`
and confirmation logic inside `TaskManager`/`ToolRiskPolicy`.

Do not retain DialogueManager as the main interpreter.

---

### Old
`agent/nlu/IntentRouter.kt`

### New
`ai/JarvisBrain.kt`

IntentRouter may remain only as an optional deterministic optimization underneath the brain.

---

### Old
`agent/ai/AgentExecutor.kt`

### New
`core/TaskManager.kt`
plus
`agent/AgentSupervisor.kt`
plus
`tool/ToolExecutor.kt`

Do not leave all responsibilities inside AgentExecutor.

---

### Old
`agent/ai/JarvisAIEngine.kt`

### New
`ai/JarvisBrain.kt`

---

### Old
`agent/ai/plan/AgentPlan.kt`

### New
`core/model/Plan.kt`
`core/model/PlanStep.kt`

---

### Old
`agent/memory/JarvisMemoryManager.kt`

### New
`memory/MemoryManager.kt`

---

### Old
`app/memory/MemoryRepository.kt`

### New
`memory/MemoryRepository.kt`

Keep Room/database implementation details behind the memory layer.

---

### Old
`app/voice/VoiceBus.kt`

### New
`voice/JarvisVoiceController.kt`
and
`core/JarvisStateStore.kt`

VoiceBus must not remain a global mutable event bus as the primary authority.

---

### Old
`android/voice/JarvisVoiceEngine.kt`

### New
`voice/JarvisVoiceController.kt`

---

### Old
`android/voice/CloudSttEngine.kt`

### New
`voice/providers/StreamingSttProvider.kt`

---

### Old
`app/voice/ElevenLabsTts.kt`

### New
`voice/providers/ElevenLabsTtsProvider.kt`

---

### Old
`app/voice/ElevenLabsVoicePlayer.kt`

### New
`voice/AudioController.kt`
plus TTS provider.

---

### Old
`app/voice/SpeechOutput.kt`

### New
All speech output must route through `JarvisVoiceController`.

---

### Old
`app/voice/WakeWordEngine.kt`

### New
`voice/WakeWordProvider.kt`
plus concrete provider.

---

### Old
`app/voice/WakeWordForegroundService.kt`

### New
`voice/JarvisVoiceService.kt`

The service should only host the controller lifecycle.

---

### Old
`android/voice/VoiceOrchestratorBridge.kt`

### New
Remove after migration.

There should not be a bridge between two competing orchestrators.

---

### Old
`app/notifications/JarvisNotificationListener.kt`

### New
`android/notifications/JarvisNotificationListenerService.kt`

The Android service becomes an adapter into:

`NotificationController`.

---

### Old
`app/notifications/NotificationRepository.kt`

### New
`android/notifications/NotificationRepository.kt`

---

### Old
`android/accessibility/JarvisAccessibilityService.kt`

### New
Keep as Android adapter, but move actual logic into:

`AccessibilityController`.

---

### Old
`app/proactive/ProactiveReceiver.kt`

### New
`proactive/ProactiveController.kt`

Receiver only dispatches lifecycle events into controller.

---

# 70. FILES THAT SHOULD REMAIN BUT LOSE BRAIN OWNERSHIP

These can remain as capability implementations:

- `DeviceSettingTools.kt`
- `LifeTools.kt`
- `PhoneTools.kt`
- `ProactiveTools.kt`
- `WebTools.kt`
- `DeviceToolExecutors.kt`
- `AppLauncher.kt`
- `ContactsResolver.kt`
- `ContactsToolkit.kt`
- `DeviceToolkit.kt`
- `LocationToolkit.kt`
- `MessagingAutomation.kt`
- `ImageAnalyzer.kt`
- `TimeParser.kt`

They are hands, not the brain.

---

# 71. TOOL REGISTRY REBUILD

Current `ToolRegistry` contains duplicate-registration history and aliases.

Keep the hard duplicate protection.

But redesign tool definitions to include:

- id;
- display name;
- semantic description;
- category;
- parameters;
- risk;
- side effects;
- permission requirements;
- preconditions;
- verification;
- retry policy;
- timeout;
- cancellation support.

Example:

`send_message`

must declare:

- communication;
- external side effect;
- requires recipient;
- requires message;
- confirmation policy;
- verification strategy.

---

# 72. TOOL EXECUTION CONTRACT

Every tool returns:

`ToolExecutionResult`

with:

- tool ID;
- execution ID;
- success;
- data;
- error category;
- error message;
- observation;
- verification;
- retryable;
- alternative capability;
- timestamp.

Do not return generic strings only.

---

# 73. HUMAN RESPONSE CONTRACT

After tool execution, JARVIS decides what to tell the user.

Examples:

Success:
"Done. I sent Sarah the message."

Verification failure:
"I tapped the control, but Android didn't confirm the setting changed."

Permission failure:
"I need Accessibility access before I can control that screen."

Unsupported:
"That app doesn't expose an action I can use from its notification."

Never:

`Tool reply_notification executed successfully.`

---

# 74. CONVERSATION MEMORY PIPELINE

Every turn:

1. create turn ID;
2. store user message;
3. capture current context;
4. retrieve relevant memory;
5. send structured context to brain;
6. execute task if necessary;
7. store tool observations;
8. store final assistant response;
9. evaluate whether the turn contains durable memory;
10. update memory asynchronously.

Memory writing must not block the user-facing response unless necessary.

---

# 75. MEMORY PROMOTION

The memory system should detect durable information such as:

- user preferences;
- recurring habits;
- important people;
- long-term projects;
- explicit instructions;
- stable device preferences.

Do not store every random sentence.

Do not store secrets by default.

Do not store API keys in memory.

---

# 76. NOTIFICATION PIPELINE

Correct architecture:

Android NotificationListenerService
→ NotificationController
→ repository
→ NotificationSnapshot
→ tool capability
→ JARVIS Core

For replies:

JARVIS Core
→ notification reply capability
→ exact notification/conversation
→ RemoteInput
→ observe resulting notification/update
→ verify
→ report.

---

# 77. ACCESSIBILITY PIPELINE

Correct:

Android AccessibilityService
→ AccessibilityController
→ ScreenSnapshot
→ JARVIS Core / AndroidAgent
→ Action
→ wait for event
→ new ScreenSnapshot
→ Verification
→ continue.

---

# 78. VOICE PIPELINE

Correct:

Microphone
→ WakeWordProvider
→ WakeEvent
→ VoiceController
→ STT
→ final transcript
→ JARVIS Core
→ response/task
→ speech chunks
→ TTS provider
→ AudioController
→ speaker

During speaking:

microphone/VAD
→ barge-in
→ cancel response generation
→ stop audio
→ STT.

---

# 79. VOICE STATE AND AI STATE MUST BE CONNECTED

Do not have one state for the Orb and another for the engine.

The authoritative state should describe the entire interaction.

For example:

`EXECUTING`

means JARVIS is actually executing a goal.

`SPEAKING`

means actual audio is playing.

`LISTENING`

means the microphone/STT pipeline is actually listening.

The UI simply renders this state.

---

# 80. RESPONSE CANCELLATION

Every user turn receives:

`turnId`

Every AI response receives:

`responseId`

Every TTS generation receives:

`responseId`

Every task receives:

`taskId`

If the user says:

"Stop."

the current response/task is cancelled.

Late network/audio/tool callbacks must be ignored if their IDs no longer match.

---

# 81. PROACTIVE JARVIS

Proactive features must not become random notification spam.

A proactive event should have:

- reason;
- priority;
- relevance;
- cooldown;
- user preference;
- quiet hours;
- whether speech is appropriate;
- whether the user is currently busy.

Proactive speech uses the same voice controller.

---

# 82. JARVIS PERSONALITY

The personality must be defined centrally in the system prompt / response policy.

It should be:

- intelligent;
- concise when simple;
- detailed when useful;
- natural;
- confident without lying;
- aware of context;
- slightly human in conversational rhythm;
- never robotic;
- never expose internal tools.

Personality must not be scattered across tool implementations.

---

# 83. WHAT JARVIS MUST NEVER DO

Never:

- claim an action succeeded without verification;
- claim to see a screen when accessibility is unavailable;
- expose raw tool names;
- expose JSON;
- dump stack traces;
- ask the user to choose an internal agent for ordinary requests;
- require command syntax;
- forget the previous sentence immediately after executing a tool;
- speak old responses after an interruption;
- keep listening after the user expects privacy;
- silently lose the microphone;
- silently stop wake detection;
- silently swallow a provider failure;
- treat a notification as successfully replied to without evidence;
- treat a click as successful merely because Android returned true;
- store secrets as normal memory;
- let stale async callbacks mutate current state.

---

# 84. IMPLEMENTATION RULES FOR THE CODING AI

Before editing:

1. inspect all relevant files;
2. map dependencies;
3. identify duplicate authorities;
4. identify all callers of each old class;
5. identify all state mutations;
6. identify all microphone/audio ownership;
7. identify all notification entry points;
8. identify all accessibility entry points;
9. identify all tool registration paths;
10. identify all memory writes/reads.

Then create the new architecture.

Do not perform a blind rewrite.

Migrate incrementally.

---

# 85. MIGRATION ORDER

## Step 1 — State and IDs

Create:

- JarvisStateStore
- JarvisTurn
- JarvisTask
- cancellation/generation IDs.

Do this first.

---

## Step 2 — Brain

Create `JarvisBrain`.

Move semantic interpretation into it.

Remove DialogueManager as primary authority.

---

## Step 3 — Task manager

Create `TaskManager`.

Move planning/execution lifecycle out of AgentExecutor.

---

## Step 4 — Tool contract

Refactor ToolRegistry and ToolDefinition.

Add verification/retry/risk metadata.

---

## Step 5 — Memory

Create layered MemoryManager.

Move existing Room data behind it.

---

## Step 6 — Accessibility

Create AccessibilityController and ScreenSnapshot.

Make UI automation observation-driven.

---

## Step 7 — Notifications

Create NotificationController and proper notification identity/upsert.

---

## Step 8 — Voice

Create JarvisVoiceController.

Move all STT/TTS ownership into it.

---

## Step 9 — Barge-in

Add speech detection while speaking.

Tie interruption to response cancellation.

---

## Step 10 — Wake word

Replace speech-recognition-as-wake-word with a dedicated local provider where possible.

---

## Step 11 — Proactive

Route proactive events through the core and voice controller.

---

## Step 12 — UI/Orb

Make UI observe the central state.

---

## Step 13 — Delete old competing authorities

Only after all call sites are migrated:

- remove old bridges;
- remove duplicate speech paths;
- remove old intent authority;
- remove old state buses;
- remove unused compatibility layers.

---

# 86. TEST MATRIX

## AI

Test:

"hello"

"how are you?"

"what did I ask you earlier?"

"open WhatsApp"

"open WhatsApp and tell Sarah I'm late"

"don't do that"

"actually, send it to Daniel instead"

"what did Sarah say?"

"remember I prefer..."

"forget that"

"do the same thing again"

"what is on my screen?"

"turn that off"

"send it"

"no, don't send it"

---

# 87. VOICE TESTS

Test:

"Hey JARVIS."

"Hey JARVIS, what's the time?"

"Hey JARVIS, open WhatsApp."

Long utterances.

Utterances containing pauses.

Accents.

Background noise.

Silence.

Repeated wake words.

Speaking while JARVIS speaks.

Interrupting JARVIS.

Rapid consecutive commands.

Screen locked.

Screen unlocked.

App in foreground.

Another app in foreground.

Microphone permission denied.

Microphone permission granted.

TTS provider unavailable.

STT provider unavailable.

Network unavailable.

---

# 88. ACCESSIBILITY TESTS

Test:

- service disabled;
- service enabled;
- service disconnected;
- service reconnects;
- root null;
- screen changes;
- node disappears;
- dynamic UI;
- scrolling;
- editable field;
- click;
- long click;
- coordinate tap;
- back;
- verification failure;
- app-specific UI.

---

# 89. NOTIFICATION TESTS

Test:

- listener disabled;
- listener enabled;
- notification posted;
- notification updated;
- notification removed;
- duplicate update;
- multiple WhatsApp conversations;
- notification with RemoteInput;
- notification without RemoteInput;
- exact conversation reply;
- failed reply;
- listener disconnect/reconnect;
- OTP extraction;
- sensitive-content setting.

---

# 90. LIFECYCLE TESTS

Test:

- rotate/activity recreation where applicable;
- background app;
- foreground app;
- service restart;
- process death;
- Android memory pressure;
- network interruption;
- TTS interruption;
- microphone interruption;
- phone call interruption;
- Bluetooth headset connect/disconnect;
- audio focus loss;
- notification listener disconnect;
- accessibility service restart.

---

# 91. RACE CONDITION TESTS

Test:

- two user turns rapidly;
- user interrupts TTS;
- TTS completion arrives after cancellation;
- tool result arrives after cancellation;
- network response arrives after a new turn;
- wake word fires while JARVIS is speaking;
- wake word fires while JARVIS is executing;
- service restarts during listening;
- accessibility screen changes during an action;
- notification changes while replying.

Every stale result must be ignored.

---

# 92. DIAGNOSTICS SCREEN

The diagnostics UI should show a timeline such as:

09:30:01
WAKE_DETECTED

09:30:01
STT_STARTED

09:30:02
STT_FINAL
"Open WhatsApp and message Sarah"

09:30:02
AI_STARTED

09:30:03
PLAN_CREATED

09:30:03
TOOL_CALL
launch_app

09:30:03
TOOL_RESULT
success

09:30:04
TOOL_CALL
send_message

09:30:05
TOOL_RESULT
success

09:30:05
VERIFICATION
success

09:30:05
TTS_STARTED

09:30:06
PLAYBACK_STARTED

09:30:08
PLAYBACK_FINISHED

This is vastly more useful than a generic "voice error."

---

# 93. EXACT SUCCESS CRITERIA

The project is NOT complete when:

- APK builds;
- screen looks good;
- a tool works in isolation;
- AI returns text;
- TTS can speak a test sentence.

It is complete when:

1. JARVIS understands natural language.
2. JARVIS maintains conversation.
3. JARVIS chooses capabilities internally.
4. JARVIS can execute multi-step goals.
5. JARVIS verifies actions.
6. JARVIS recovers from failures.
7. JARVIS remembers relevant information.
8. JARVIS voice works end-to-end.
9. Wake word works reliably.
10. Wake word + command works in one utterance.
11. JARVIS can be interrupted.
12. JARVIS can resume listening naturally.
13. Notification reading works.
14. Notification replies target the correct conversation.
15. Accessibility state is truthful.
16. Accessibility actions are verified.
17. Default assistant integration enters the same brain.
18. Internal agents are invisible to the user.
19. Old asynchronous callbacks cannot corrupt current state.
20. Proactive speech uses the same voice system.
21. Diagnostics identify actual failure points.
22. API/provider configuration is separate from assistant behavior.

---

# 94. IMPORTANT: DO NOT CONFUSE "WORKING TOOL" WITH "WORKING JARVIS"

A tool can work perfectly while JARVIS is still broken.

Example:

`send_message()` works.

That does NOT mean:

"JARVIS messaging works."

The real test is:

User:
"Tell Sarah I'm running late."

JARVIS must:

- understand the goal;
- resolve Sarah;
- formulate or preserve the message;
- determine whether confirmation is required;
- choose the messaging capability;
- execute;
- verify;
- remember the relevant turn;
- respond naturally;
- speak it if voice mode is active.

That is JARVIS.

---

# 95. CRITICAL CURRENT REPOSITORY OBSERVATIONS

The current codebase already contains several attempted production repairs.

Examples include:

- duplicate tool registration hard-fail;
- exact notification key targeting;
- notification listener rebind attempt;
- accessibility click ancestor fallback;
- accessibility verification comments;
- wake-lock renewal;
- continuous voice restart;
- TTS fallback;
- reply sanitization;
- screen context injection;
- NVIDIA provider fallback.

These repairs should not simply be discarded.

However, they are currently layered onto a fragmented architecture.

The rebuild should preserve the useful behavior while moving ownership into the new architecture.

---

# 96. DO NOT OVERWRITE WORKING CAPABILITIES WITHOUT REASON

The following should be preserved where functionally correct:

- existing Room database;
- existing tool implementations;
- existing accessibility actions;
- existing notification extraction;
- existing contact resolution;
- existing device tools;
- existing web tools;
- existing Android assistant-role integration;
- existing UI components where they correctly observe state.

Refactor ownership before rewriting working low-level capabilities.

---

# 97. SECURITY / PRIVACY REQUIREMENTS

Do not store:

- API keys in memory;
- authentication tokens;
- passwords;
- private secrets.

Sensitive notification data should be handled according to user settings.

Voice recordings should not be persisted unnecessarily.

Temporary audio files must be deleted after use.

Logs must redact:

- tokens;
- credentials;
- OTPs where appropriate;
- private message content when diagnostics do not require it.

---

# 98. PROVIDER ABSTRACTION

Create interfaces.

AI:

`interface ModelProvider`

STT:

`interface SttProvider`

TTS:

`interface TtsProvider`

Wake:

`interface WakeWordProvider`

The rest of JARVIS must not contain provider-specific behavior.

Provider fallback should be handled by provider managers, not scattered through the assistant.

---

# 99. JARVIS CORE CONTRACT

The central API should conceptually be:

`process(input: JarvisInput): Flow<JarvisEvent>`

Where input can be:

- text;
- voice transcript;
- assistant invocation;
- proactive event;
- UI request.

Events include:

- understood;
- clarification required;
- plan created;
- tool requested;
- tool result;
- verification;
- response chunk;
- speech started;
- speech finished;
- task completed;
- task failed;
- cancelled.

This gives every surface one pipeline.

---

# 100. FINAL MASTER INSTRUCTION TO THE CODING AI

You are not being asked to "fix a few bugs."

You are taking ownership of an existing Android JARVIS project whose infrastructure contains many partially working subsystems but whose architecture currently prevents those subsystems from behaving like one coherent assistant.

Your job is to audit the complete repository and implement the architecture in this document.

Do not:

- blindly patch symptoms;
- add another bridge;
- add another intent parser;
- add another voice engine;
- add another state bus;
- add another competing agent;
- expose internal tools;
- claim success without verification;
- treat the build as proof of correctness.

First map the repository.

Then identify:

- current authority;
- duplicate authority;
- lifecycle owner;
- state owner;
- microphone owner;
- TTS owner;
- memory owner;
- task owner;
- notification owner;
- accessibility owner.

Then migrate toward:

`ONE JARVIS CORE`
`ONE TASK MANAGER`
`ONE MEMORY MANAGER`
`ONE TOOL EXECUTOR`
`ONE ACCESSIBILITY CONTROLLER`
`ONE NOTIFICATION CONTROLLER`
`ONE VOICE CONTROLLER`
`ONE STATE STORE`

Specialized agents may exist internally, but only JARVIS Core chooses them.

The user must experience:

**one assistant.**

The assistant should feel like it is continuously aware of:

- who is speaking;
- what was just discussed;
- what it is currently doing;
- why it is doing it;
- what happened;
- whether it succeeded;
- what it should do next.

If something fails, JARVIS should know it failed.

If an action succeeds, JARVIS should know it succeeded.

If the screen changes, JARVIS should observe the new screen.

If the user interrupts speech, JARVIS should stop.

If a notification reply cannot be sent, JARVIS should not lie.

If the user asks a normal question, JARVIS should simply converse.

If the user asks for an action, JARVIS should decide how to accomplish it.

If the task requires several actions, JARVIS should plan and execute them.

If the task changes halfway through, JARVIS should adapt.

If the user says "no, I meant Daniel", JARVIS should update the current goal instead of starting a completely unrelated conversation.

If the user says "do it again", JARVIS should understand what "it" means from task context.

If the user says "stop", the current task/response/speech should actually stop.

That is the difference between:

**an app that has AI tools**

and

**JARVIS.**

---

# 101. FINAL TARGET

The final system should behave like this:

USER
↓
VOICE / TEXT / ASSISTANT INVOCATION
↓
JARVIS CORE
↓
CONTEXT + MEMORY
↓
UNDERSTAND
↓
GOAL / CONVERSATION
↓
PLAN
↓
INTERNAL AGENT/CAPABILITY SELECTION
↓
TOOL EXECUTION
↓
OBSERVE
↓
VERIFY
↓
RECOVER / CONTINUE
↓
FINAL RESPONSE
↓
VOICE CONTROLLER
↓
SPEAKER
↓
USER

With memory and diagnostics surrounding the entire lifecycle.

Android accessibility and notifications are environmental interfaces.

Tools are capabilities.

Agents are internal workers.

The AI is the decision-maker.

The voice system is the conversational interface.

The state store is the source of truth.

The task manager owns execution.

The user sees one JARVIS.

---

# 102. DEFINITION OF DONE

Do not mark this task complete until all critical paths have been tested on the actual target Android device.

A green Gradle build is necessary.

It is not sufficient.

The final validation must include:

- text conversation;
- voice conversation;
- wake word;
- wake + command;
- TTS;
- STT;
- interruption;
- continuous conversation;
- AI reasoning;
- multi-step task;
- tool execution;
- tool verification;
- memory;
- notification reading;
- notification replying;
- accessibility;
- screen observation;
- Android assistant role;
- lifecycle recovery;
- provider failure;
- service restart;
- process restart;
- stale callback rejection.

The final question is not:

"Does the tool work?"

The final question is:

**"Does JARVIS behave like JARVIS?"**
