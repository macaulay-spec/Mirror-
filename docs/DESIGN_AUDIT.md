# JARVIS Design Audit

Audit of the implemented UI against `JARVIS_DESIGN.md` and the
`jarvis-reference-design.png` reference grid. Performed on branch
`audit/security-voice-messaging` at HEAD `4fc21f9`.

The design doc states the rule for this audit explicitly:

> Sections 2, 3, and 6 (orb structure, color, motion) are load-bearing —
> build the orb as one parametric component driven by a state enum, not
> one-off per screen. Section 5 (result cards) is what makes the
> "real multi-step reasoning, verified actions" architecture visible
> … build it as a small set of reusable card variants, not bespoke
> layout per tool. Everything in section 7 should be checkable against
> sections 2-6; if a screen doesn't match the established color system,
> orb behavior, or card patterns, that's a build bug, not a new design
> decision to make locally.

---

## 1. The Orb — one parametric component (Sections 2 & 6) — PASS

**Verdict: the Orb is correctly one parametric component driven by a
state enum. This is the most important structural requirement in the
spec and it is met.**

File: `app/src/main/java/com/jarvis/core/ui/JarvisCore.kt`

`JarvisCore` is a single `@Composable fun JarvisCore(state, audioLevel,
modifier, size, onClick)`. All visual variation comes from one
`JarvisVisualState` enum:

`IDLE, WAKING, LISTENING, THINKING, EXECUTING, SPEAKING, SUCCESS,
ERROR, OFFLINE`

The component draws, in one `Canvas`, all the layers the spec calls out
under "bloom / rings / spokes / core":

- `drawAmbientGlow` — the bloom. Base alpha scales with state
  brightness; LISTENING/SPEAKING breathe with `audioLevel`; ERROR
  flashes on a 600 ms loop; OFFLINE collapses it to a dim residual.
- Outer ring — state-dependent color (`orbColor()`), thickness, and
  brightness. LISTENING adds a live waveform ring (`drawWaveformRing`)
  driven by `audioLevel`.
- `drawThinkingParticles` — 14 particles that gather inward for
  THINKING, the "spokes" reading the spec describes.
- `drawExecutingArc` — a rotating ~90° arc segment for EXECUTING.
- `drawLuminousCore` — the core: a radial gradient core with a dark
  pupil and a thin iris ring; brightness and pupil size scale with
  state.

This is exactly "one continuous object changing — never two different
graphics cross-fading" and "one parametric component (color, particle
count, ring behavior, bloom intensity as variables) driven by a state
enum." Motion is spring-based for state transitions and
linear/ease-in-out for ambient loops, matching Section 6.

**Minor gap (not a structural defect):** the spec calls for
reduced-motion support ("ambient rotation/particles should stop or
drastically slow; state color changes still happen at full speed").
`JarvisCore` does not currently read
`LocalAccessibilityManager`/`isReduceMotionEnabled`. Ambient motion
runs at full speed regardless of the system setting. Recommended fix:
gate the infinite-transition-driven ambient layers (particle gather,
executing-arc rotation, breathing) behind a reduced-motion flag while
keeping `orbColor()` transitions intact.

---

## 2. Color system (Section 3) — PASS (with one named-token drift)

File: `app/src/main/java/com/jarvis/core/theme/JarvisTheme.kt` — object
`JarvisColors`.

The spec defines a palette anchored on a deep graphite-blue base ("not
true black"), two accents (ice-blue presence + soft amber warmth), and
per-state hues (never pure cyan, never pure red). The implementation
matches:

| Spec intent            | Token              | Value        | Match |
|------------------------|--------------------|--------------|-------|
| Root background, not #000 | `VoidBlack`     | `#0B0F17`    | yes   |
| Cards/sheets           | `DarkSpace`        | `#121826`    | yes   |
| Raised surface         | `SurfaceCard`      | `#1A2232`    | yes   |
| Glass panels (rgba)    | `SurfaceGlass`     | `#8C121826`  | yes   |
| Ice-blue presence      | `Presence`         | `#6FD3FF`    | yes   |
| Soft amber warmth      | `Warmth`           | `#F5B87A`    | yes   |
| Idle (visible presence)| `StateIdle`        | `#AA6FD3FF`  | yes   |
| Listening (bright)     | `StateListening`   | `#E66FD3FF`  | yes   |
| Thinking (soft violet) | `StateThinking`    | `#FFB79CFF`  | yes   |
| Executing (amber)      | `StateExecuting`   | `#FFF5B87A`  | yes   |
| Speaking (full presence)| `StateSpeaking`  | `#FF6FD3FF`  | yes   |
| Error (warm coral)     | `StateError`       | `#FFFF8A80`  | yes — never `#FF0000` |
| Success (soft green)   | `StateSuccess`     | `#FF7EE8B8`  | yes   |
| Hairline (white 8%, never cyan) | `Hairline` | `#14FFFFFF` | yes |

The glass card (`GlassComponents.kt -> GlassCard`) correctly uses a
translucent background with a 1px top-edge inner highlight
(`Color.White 6% -> Transparent`) plus soft shadow, and no flat cyan
border — exactly the spec's "Color appears in a panel because something
inside it is that color, not because the container is outlined in it."

**Drift to note (cosmetic, not functional):** the spec's hex tokens are
written as `--surface #0F131A` and `--amber #FFB25E`. The implementation
uses `#121826` for surfaces and `#F5B87A` for warmth. These are
near-identical graphite-blue and soft-amber respectively and read the
same on-device; the difference is well within design tolerance, but if
the team wants byte-exact token parity with the doc, `DarkSpace`/
`SurfaceCard` and `Warmth` should be retargeted to the doc's hex values.
`SurfaceGlass` is also specified at `rgba(15,19,26,0.7)` in the doc but
implemented at `rgba(18,24,38,0.55)` (`#8C121826`) — slightly lighter
and more transparent. Again cosmetic.

Legacy aliases (`CyanPrimary`, `BorderCyan`, etc.) are kept as
deprecation bridges to `Presence`/`Hairline`, which is the right
migration strategy and does not violate the spec.

---

## 3. Typography (Section 4) — PARTIAL (fonts not bundled)

File: `app/src/main/java/com/jarvis/core/theme/JarvisTheme.kt` —
`JarvisTypography`.

The spec defines a three-family system:

- **Inter** — primary UI sans.
- **Space Grotesk** — display/numbers (orb-adjacent, the "large number"
  in value cards).
- **JetBrains Mono / Roboto Mono** — monospace, reserved for technical
  readouts and result-card category tags only.

The implementation defines the correct **scale and roles**
(displayLarge 28/34, displayMedium 24/30, headlineMedium 20/26,
titleMedium 15/22, bodyLarge 15/22, bodyMedium 14/20, labelMedium 13/18,
labelSmall 12/16) with the right weight and color assignments. The
shape of the type system is correct.

**But the actual font families are not present.** Every `TextStyle`
uses `FontFamily.Default` with an inline comment
("Space Grotesk would be ideal; using system sans"). There is no
`app/src/main/res/font/` directory and no font dependency in
`build.gradle.kts` / `libs.versions.toml`. Technical readouts
(`DiagnosticsActivity`, `GlassComponents.TerminalBadge`) use
`FontFamily.Monospace`, which maps to the device's default monospace
(Roboto Mono on AOSP) — acceptable as a fallback, but not the
intentional JetBrains Mono the spec names.

**This is a real gap.** On a stock device `FontFamily.Default` is
Roboto, not Inter, so the app does not currently render in its
intended typeface. The scale and hierarchy are right, but the
letterforms are the OS default.

Recommended fix (low effort, high fidelity): add the Google Fonts
dependency or download Inter (400/500), Space Grotesk (500/700), and
JetBrains Mono (400) into `res/font/`, declare a `JarvisFontFamily`
value, and swap `FontFamily.Default` → the named families in
`JarvisTypography` (Inter for body/title, Space Grotesk for display,
JetBrains Mono for the monospace spots). The scale/weight/color values
already in place do not need to change.

---

## 4. Result cards (Section 5) — PARTIAL (variants missing; not inline in chat)

This is the section the spec flags as load-bearing for making the
"observe, don't just act" architecture visible. The spec defines five
reusable card variants that render **inline in chat**:

1. **Value results** (volume, brightness, battery): large Space Grotesk
   number + thin progress track, in state color.
2. **Toggle results** (flashlight, wifi, DND): setting name + a pill
   showing the resulting state, amber-tinted when active.
3. **Multi-step task results**: vertical checklist — completed =
   filled check-circle + muted; current = glowing dot + full
   brightness; future = hollow gray dot.
4. **Retrieved content** (quoted message, search result): recessed
   inner card with colored left border, source/timestamp in caption
   text.
5. **Screen-awareness results**: small screen thumbnail + leader-line
   callouts.

What exists today:

- `feature/actions/DeviceActionCards.kt` — a single generic
  `ActionCard` with a status icon (✓/✗/○/●), title, description, and a
  status word ("Done"/"Failed"/"Pending"/"Working..."). It consumes
  `ToolExecutionResult` via `toActionCardData()`. This is a reasonable
  **status card** but it is not any of the five spec variants — there
  is no large-number value card, no toggle pill, no recessed
  retrieved-content card, no screen thumbnail.
- `feature/tasks/TaskExecutionScreen.kt` — a full-screen vertical
  timeline of `TaskStep`s with `StatusIndicator`
  (✓ filled / ● glowing / ○ hollow / ✗). **This genuinely implements
  variant #3 (multi-step checklist)** with the correct completed/
  current/future dot semantics and the orb in EXECUTING/SUCCESS/ERROR
  above it. Good. But it is a dedicated full screen, not an inline chat
  card.
- `feature/awareness/ScreenAwarenessScreen.kt` — a full-screen
  "Analyzing screen…" view with an app badge and a capabilities
  checklist. It conveys screen awareness conceptually but does **not**
  render an actual screen thumbnail with leader-line callouts (variant
  #5). It is a static capability list, not the "I can see this screen"
  visual proof the spec asks for.
- `feature/home/DualModeHost.kt` (the chat surface) renders message
  bubbles as text, plus a **tool-confirmation card** (amber-tinted,
  "Confirm"/"Cancel" buttons) when a tool `requiresConfirmation`. That
  confirmation card is well-built and on-palette, but when a tool
  *completes*, the result is shown as plain assistant text — there is
  **no inline result card** of any of the five variants rendered in the
  chat stream. The "observe, don't just act" principle is therefore
  true in the background (tool results carry `verificationDetails`) but
  **not made visible inline** as the spec requires.

**Summary of the card gap:**

| Spec variant        | Exists? | Where |
|---------------------|---------|-------|
| #1 Value (number + track) | No  | — |
| #2 Toggle (name + state pill, amber when active) | No | — |
| #3 Multi-step checklist | **Yes** (as full screen, not inline) | `TaskExecutionScreen` |
| #4 Retrieved content (recessed, left border, source) | No | — |
| #5 Screen-awareness (thumbnail + callouts) | No (capability list only) | `ScreenAwarenessScreen` |
| Tool confirmation card (not in spec, but needed) | Yes, inline in chat | `DualModeHost` |

Recommended fix: build a small `ResultCard` composable family
(`ValueResultCard`, `ToggleResultCard`, `MultiStepResultCard`,
`RetrievedContentCard`, `ScreenAwarenessResultCard`) on top of the
existing `GlassCard`, each with the monospace category tag at top per
the spec, and have the chat renderer in `DualModeHost` pick the variant
from the `ToolExecutionResult` category instead of dumping results as
plain text. `MultiStepResultCard` can reuse the exact
`StatusIndicator` already in `TaskExecutionScreen`. This converts the
existing full-screen views into the inline, chat-embedded cards the
spec describes, without throwing away the good work already done.

---

## 5. Screens (Section 7) — MOSTLY ALIGNED

- **Home (`DualModeHost`)**: orb is present and dominant; greeting and
  quick-action chips are present; a quick-actions row ("Read
  notifications", "What's on screen?", "Battery status", "Flashlight",
  "Volume up", "Set alarm", "Open WhatsApp", "Remember this") sits
  above the input bar per spec. The "two-card row (recent task /
  relevant memory)" and "4-icon quick-actions grid" described in the
  spec are implemented as a horizontal chip row rather than the
  grid/two-card layout — a layout choice, not a palette/orb violation.
- **Listening / Thinking**: handled as stage modes inside `DualModeHost`
  with the orb growing and a state label — consistent with "near
  full-screen moments."
- **Chat**: message list renders with sender labels and timestamps;
  input bar has the mic + text field + send. The spec's explicit
  warning — "message list fills available height properly (verify this
  — it was a literal layout bug in an earlier build)" — should be
  re-verified on a real device with a long conversation; the layout
  uses a `LazyColumn` with weight分配 so it should fill correctly, but
  this is the one spot the spec flags as historically buggy and it is
  worth a manual scroll test.
- **Floating overlay**: `JarvisFloatingOrbService` renders the orb as a
  small overlay; consistent with "never covers or interferes beyond its
  own small footprint."
- **Onboarding / Voice picker / Settings / Memory / Diagnostics**:
  present as separate screens; Diagnostics correctly uses monospace
  for technical readouts. Voice picker (`VoiceSelectionScreen`) exists
  but was not confirmed to run the orb through its speaking state during
  a sample preview (spec: "playing a sample also runs the orb through
  its speaking state live") — worth a targeted check.

---

## Overall design verdict

The **load-bearing structural requirements are met**: the Orb is one
parametric state-driven component (Sections 2 & 6 — pass), and the
color system is faithful to the spec's palette and philosophy
(Section 3 — pass with cosmetic hex drift). These are the parts the
spec says must not be one-off per screen, and they are not.

The two real gaps are:

1. **Typography (Section 4)** — correct scale/roles, but Inter / Space
   Grotesk / JetBrains Mono are not bundled; the app renders in the
   system default sans. Low-effort, high-fidelity fix.
2. **Result cards (Section 5)** — only the multi-step checklist variant
   exists (and as a full screen, not inline in chat); the value,
   toggle, retrieved-content, and screen-awareness variants are
   missing, and completed tool results render as plain text in chat
   instead of as structured inline cards. This is the section the spec
   calls load-bearing for making verified actions visible, so it is the
   higher-priority design debt.

Neither gap is a regression from the "Already Fixed" work; both are
incomplete implementations of the design spec, not breakages. They are
noted here for the owner to prioritize.
