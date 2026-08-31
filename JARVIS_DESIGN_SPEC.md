# JARVIS DESIGN SPECIFICATION — VISUAL SYSTEM, ORB, MOTION, UX

This is the full design rebuild brief. It's opinionated on purpose — a
specific point of view, not a menu of options. Where it gives exact values
(colors, sizes, timing), treat them as the actual spec, not placeholders.

================================================================
THE PROBLEM WITH WHAT EXISTS NOW
================================================================

Real screenshots of the running app showed a specific, describable failure
mode, not just "needs to look nicer": ALL-CAPS monospace labels in thin
cyan-bordered boxes on flat black, everywhere, for everything. That's the
generic "hacker terminal" template — instantly recognizable, low effort to
build, and it's what a hundred other sci-fi-themed apps already look like.
It also had real bugs sitting inside it: a decorative graphic overlapping
and obscuring a text field and a chat message in two different places, a
large dead empty area in the main chat screen, and two visually unrelated
circular graphics both trying to be "the Orb" — a radar-dial in one place,
an atom-with-orbit-rings in another.

The direction below fixes both problems at once: a specific, cohesive
visual identity, and — because it's cohesive on purpose, not assembled
screen-by-screen — none of the "two things both claiming to be the Orb"
problem to begin with.

================================================================
1. DIRECTION
================================================================

Not a terminal. Not a hologram theme-pack. A calm, confident intelligence
that happens to be able to see and touch the phone. The reference isn't
"what does sci-fi UI usually look like" — it's "what would it actually feel
like to have something quietly competent paying attention." Quiet until it
has something to say. Never shouting for attention with neon and all-caps.

Three words to check every screen against: **calm, precise, alive.** Calm —
low visual noise, generous space, nothing pulses or glows without a reason
tied to actual state. Precise — every number, every edge, every transition
is deliberate; nothing is "close enough." Alive — it's allowed to feel like
something is happening in there; stillness and motion both carry meaning.

================================================================
2. COLOR
================================================================

Base is a deep graphite-blue, not true black — black reads dead, this
should read like a room with the lights off but something faintly glowing
in it.

```
--bg-void        #0B0F17   (root background)
--bg-surface     #121826   (cards, sheets)
--bg-surface-2   #1A2232   (raised/hover surface)
--bg-glass       rgba(18, 24, 38, 0.55)   (glass panels, use with blur)
--hairline       rgba(255, 255, 255, 0.08)   (borders — never solid cyan)
```

Two accents, not one — this is the single biggest departure from the
current monochrome-cyan look, and it's what makes the palette feel
considered instead of default:

```
--presence       #6FD3FF   (ice-blue — Jarvis's core identity color:
                             the Orb at rest, primary actions, links)
--warmth         #F5B87A   (soft amber — used sparingly: highlights,
                             the "thinking" accent warmth, personality
                             moments. Never the dominant color on a screen.)
```

State colors get their OWN hues, not just brightness steps of the same
blue — this is what lets someone recognize "thinking" vs "listening" at a
glance, at a distance, without reading a label:

```
--state-idle       #6FD3FF  (dim, 40% opacity)
--state-listening  #6FD3FF  (full brightness, ~90% opacity)
--state-thinking   #B79CFF  (soft violet)
--state-executing  #F5B87A  (warmth accent)
--state-speaking   #6FD3FF  (full brightness, animated)
--state-error      #FF8A80  (warm coral, never pure red — pure red reads
                             as alarming/aggressive; this reads as "this
                             needs attention" without shouting)
--state-success    #7EE8B8  (soft green, used briefly then fades — success
                             is a moment, not a persistent color)
```

Never pure `#00FFFF` cyan, never pure `#FF0000` red anywhere in the app.
Every color has depth (isn't fully saturated) — that's what separates
"premium" from "default Android accent color."

================================================================
3. TYPOGRAPHY
================================================================

Stop using all-caps monospace as the default UI voice. Reserve monospace
for what it's actually for: raw technical readouts (Diagnostics screen
values, API status codes, timestamps in a debug view). Everywhere else,
people are reading sentences, not a terminal log.

- **UI / body**: a clean geometric-humanist sans — Inter or equivalent
  (Manrope, Plus Jakarta Sans are close substitutes). Sentence case.
  Regular (400) for body, Medium (500) for emphasis, never bold-everywhere.
- **Display / wordmark**: a slightly more distinctive face for "JARVIS"
  branding moments and major screen titles only — something with engineered
  character without being a cliché "sci-fi font." Space Grotesk or a
  similar geometric display face at Medium/SemiBold weight, generous
  letter-spacing (+2 to +4%) ONLY at display size (24px+) — that
  letter-spacing is what currently gets mistaken for "the sci-fi look"
  when it's applied to every label at 12px; used sparingly at large sizes
  instead, it reads as considered rather than costume.
- **Monospace (technical contexts only)**: JetBrains Mono or Roboto Mono.

Type scale (SP, respecting system font-scale settings):
```
Display   28 / 34 line-height / Medium    — "JARVIS", major screen titles
Title     20 / 26 / Medium                — section headers
Body      15 / 22 / Regular               — chat text, descriptions
Label     13 / 18 / Medium                — buttons, form labels
Caption   12 / 16 / Regular               — timestamps, helper text
Mono      13 / 18 / Regular, monospace    — technical values only
```

================================================================
4. MATERIALITY — GLASS, NOT BOXES
================================================================

Replace flat `1dp cyan border on flat black card` with actual depth:
- Panels are translucent (`--bg-glass`) with backdrop blur (~24dp radius)
  over whatever's behind them, not opaque.
- Definition comes from a soft inner highlight (1px, `rgba(255,255,255,0.06)`
  top edge) plus a soft outer shadow, not a bright colored border. Color
  should appear in a panel because something inside it is that color
  (an active state, an icon), not because the container itself is outlined
  in it.
- Corner radius: 20dp for cards/sheets, 14dp for buttons/chips, 28dp+ for
  the Orb's own container when it has one. Consistent scale, not
  per-screen guessing.
- A very subtle grain/noise texture (2-3% opacity) over the base background
  — this is what separates "glass" from "flat translucent PNG," and costs
  nothing at runtime as a single tiled overlay.

================================================================
5. THE ORB — ONE IDENTITY, EVERYWHERE
================================================================

Currently two unrelated graphics both claim this role. There is exactly
ONE Orb visual system from here on, used identically in: the floating
overlay, the chat screen header, the idle/standing-by screen, and the
voice-preview control in Settings. Same geometry, same motion language,
same state-color mapping, every time it appears — if a screen needs a
smaller or larger version, it scales, it doesn't become a different graphic.

**Form**: a luminous core (a soft radial gradient, not a hard-edged circle)
with a single thin outer ring at ~1.3x the core's radius. That's it at
rest — no orbiting rings, no scattered decorative dots, no radar-sweep
ticks. Complexity is added by STATE, not by default:

- **Idle**: core at 40% brightness, slow breathing scale (96%→100%→96%,
  4200ms ease-in-out, looping). Ring barely visible (~15% opacity),
  static.
- **Listening**: core brightens to 90%, ring becomes an actual waveform
  driven by live mic amplitude (not a canned animation — bind it to real
  input level). Subtle outward ripple emitted every ~2s while active
  input is detected.
- **Thinking**: hue shifts to `--state-thinking` violet, core motion
  becomes a slow inward gather — small light particles drifting toward
  center, disappearing, respawning at the edge (12-16 particles, not more
  — restraint matters here). This is the one moment allowed to feel
  "processing," precisely because it doesn't happen anywhere else.
- **Executing**: hue shifts to `--state-executing` amber, ring becomes a
  single rotating arc segment (like a determinate-ish progress indicator,
  even though the underlying task may not report real progress —
  motion should still communicate "actively working," not spin
  indefinitely with no sense of direction).
- **Speaking**: back to `--state-speaking` blue, core pulses in sync with
  actual TTS output amplitude if available (not a generic loop) — outward
  emphasis on emphasis, not a metronome.
- **Error**: one sharp contraction + `--state-error` flash (~180ms), then
  settle to idle. Never a sustained red state — errors are communicated in
  text, the Orb just marks the moment.

**Interaction (floating overlay specifically)**:
- Idle: draggable anywhere on screen, releases with a soft spring
  settle; if released near a screen edge (within ~48dp), snaps to dock
  against it, showing only half the Orb protruding — this is what makes
  "minimize it" actually mean something, instead of it always occupying
  full space.
- Single tap: expands in place to a compact strip (Orb shrinks to 32dp,
  moves to the left of the strip; strip shows the last reply's first
  line + a mic button + a close affordance), auto-collapses back to just
  the Orb after ~6s of no interaction.
- Long-press (or tap on the compact strip's expand affordity): opens the
  full chat view.
- Never intercepts touches outside its own bounds — this seems obvious but
  is worth stating given the current overlap bugs found in review.

================================================================
6. MOTION PRINCIPLES
================================================================

- Spring-based easing as the default, not linear or generic ease-in-out —
  things should feel like they have mass. (Compose: `spring(dampingRatio
  = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)` as
  a starting point for state transitions; lower bounce for anything
  large/full-screen.)
- Timing scale: micro-interactions (button press, toggle) 120-180ms;
  component transitions (card expand, sheet open) 250-350ms; ambient/idle
  animations (Orb breathing) 3500-4500ms — three tiers, not one guessed
  number reused everywhere.
- Respect the system's reduced-motion accessibility setting: ambient loops
  (breathing, particle drift) should stop or drastically simplify; state
  COLOR changes should still happen, since that's information, not
  decoration.
- State transitions on the Orb are the SAME object continuously morphing
  (color, scale, ring behavior) — never a hard cut or crossfade between
  two different graphics. If it ever looks like two images swapping, the
  animation is wrong.

================================================================
7. SCREENS — SPECIFIC FIXES
================================================================

**Main chat screen**: the message list must actually fill available
vertical space (weight/fillMaxHeight on the list, not a fixed-height
container) — the current dead-space bug is exactly this being wrong. Empty
state (no messages yet) shows the Orb centered, larger, at idle, with
"Standing by." beneath it — not a blank screen. Quick-command chips live in
a horizontally-scrolling row directly above the input bar, always visible,
never overlapping message content (chat messages themselves never render
underneath the Orb graphic — the overlap bug in the current build was the
Orb's z-order not being scoped away from the message list).

**Settings ("Access Control")**: fix the decorative graphic overlapping
the name field — it should sit as a small static icon, not layered behind
active input. Provider status copy must say ONE consistent thing (don't
show "Provider: X" and then help text describing a different provider
list below it). Voice section gets an actual preview: tapping "preview"
plays a short sample through the selected voice AND animates the Orb
through its speaking state at the same time, so choosing a voice is also
previewing what Jarvis's presence feels like with that voice — this ties
sections 5 and 7 together on purpose.

**Onboarding**: keep it short. Each permission request explains the one
concrete thing it unlocks in a single sentence, not a feature list.

================================================================
8. ICONOGRAPHY
================================================================

One icon set, one stroke weight (1.5px), rounded caps/joins to match the
Orb's soft-edged language — not a mix of filled Material icons and custom
line icons. Icons are functional wayfinding, not decoration; if a screen
needs a decorative visual, it's a variation on the Orb's light/particle
language (section 5), not a new icon.

================================================================
HOW TO USE THIS
================================================================

This is a design system, not a moodboard — every screen should be
checkable against it (right colors, right type scale, right motion
tier, one Orb identity, no dead space, no overlap). Where the rebuild
prompt (GROK_BUILD_REBUILD_PROMPT.md) describes a screen or interaction
this document doesn't explicitly cover, extend from these principles
(calm / precise / alive; two accents not a rainbow; state color +
motion together, never color alone) rather than introducing a new visual
idea that doesn't trace back to them.
