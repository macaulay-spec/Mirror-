JARVIS — COMPLETE UI/UX REDESIGN FROM SCRATCH
BASED DIRECTLY ON THE ATTACHED JARVIS DESIGN REFERENCE
========================================================

IMPORTANT:

The image attached to this prompt is the PRIMARY DESIGN REFERENCE
for the JARVIS application.

DO NOT ignore the attached image.

DO NOT treat it as a generic inspiration image.

STUDY THE ACTUAL IMAGE carefully before designing anything.

I want the entire JARVIS application UI/UX rebuilt from scratch
around the design system, visual language, layouts, hierarchy,
components, Orb behavior, navigation, spacing, typography,
animations, and interaction patterns demonstrated in that image.

The final application should feel like the same product shown in
the reference image, but expanded into a complete real-world
Android application.

Do NOT simply reproduce six or fifteen static screenshots.

Understand the design system shown in the image and turn it into
a complete, consistent, functional JARVIS interface.

========================================================
THE ATTACHED IMAGE IS THE SOURCE OF TRUTH FOR VISUAL DIRECTION
========================================================

Study every visible section of the attached image.

The reference contains, among other things:

1. SPLASH
2. HOME
3. LISTENING
4. THINKING
5. CHAT
6. OVERLAY WHILE USING ANOTHER APP
7. DEVICE ACTIONS
8. SCREEN AWARENESS
9. WEB SEARCH
10. TASK EXECUTION
11. ONBOARDING
12. PERMISSIONS
13. PERSONALIZATION
14. MEMORY
15. SETTINGS
16. ORB STATES
17. JARVIS BRANDING / IDENTITY

These are NOT independent designs.

They are different states and surfaces of the SAME assistant.

Your implementation must preserve that relationship.

========================================================
1. VISUAL CHARACTER OF THE REFERENCE
========================================================

The attached design establishes a very specific visual identity.

Preserve it.

The application should feel:

- futuristic
- premium
- cinematic
- intelligent
- minimal
- dark
- calm
- precise
- technologically advanced
- spatial
- polished
- sophisticated

The reference uses:

- near-black backgrounds
- deep dark surfaces
- cyan/blue illumination
- luminous Orb elements
- subtle purple thinking states
- thin technical lines
- concentric circular interfaces
- restrained glass-like cards
- subtle borders
- soft shadows
- controlled glow
- clean typography
- rounded interfaces
- compact technical metadata
- waveform visualizations
- small status indicators
- strong visual hierarchy

Recreate this design language consistently throughout the application.

Do NOT turn it into:

- a generic ChatGPT clone
- a generic Android settings application
- a neon gaming interface
- a cyberpunk dashboard
- a collection of glowing cards
- a conventional chatbot UI

The reference is sophisticated because it uses glow selectively.

========================================================
2. DO NOT COPY THE IMAGE AS STATIC UI
========================================================

The goal is NOT:

"Make screens that look like the screenshot."

The goal is:

"Build the actual JARVIS product represented by the screenshot."

Therefore every important visual element must correspond to
real application state or functionality.

For example:

The LISTENING screen must appear when JARVIS is actually listening.

The THINKING screen must appear when the assistant is actually
processing/reasoning.

The EXECUTING state must appear while an actual task is executing.

The SPEAKING state must correspond to actual voice output.

The ERROR state must represent an actual error.

The Orb must therefore be state-driven.

Do not fake assistant states using arbitrary timers.

========================================================
3. BUILD A REAL DESIGN SYSTEM FIRST
========================================================

Before creating individual screens, establish a reusable JARVIS
design system.

Create reusable components/tokens for:

- background
- surfaces
- cards
- buttons
- text
- labels
- icons
- navigation
- input fields
- chips
- status indicators
- progress indicators
- waveform
- Orb
- Orb glow
- Orb rings
- dialogs
- sheets
- confirmation UI
- permission cards
- memory cards
- task cards
- search result cards
- execution timeline
- chat bubbles
- overlay interface

Define:

- colors
- typography
- spacing
- corner radius
- border treatment
- shadows
- glow intensity
- animation timing
- icon sizing
- component states

Everything should use this system.

Do not manually style every screen separately.

========================================================
4. THE JARVIS ORB IS THE CENTRAL UI ELEMENT
========================================================

The attached image clearly establishes the Orb as the visual identity
of JARVIS.

Treat it as a first-class component.

Build ONE reusable Orb system.

Do not create separate unrelated Orb components for:

- Home
- Listening
- Thinking
- Chat
- Overlay
- Settings
- Onboarding

They must all use the same underlying Orb component.

The Orb should change according to actual assistant state.

Required states:

IDLE
LISTENING
THINKING
PLANNING
EXECUTING
OBSERVING
VERIFYING
SPEAKING
SUCCESS
ERROR
WAITING
CONFIRMATION

The visual language should follow the attached reference.

IDLE:

Calm cyan/blue Orb.
Slow orbital movement.
Low-energy glow.

LISTENING:

Brighter blue.
Visible waveform activity.
Responsive pulse.
Clearly communicates that JARVIS is hearing the user.

THINKING:

Purple illumination like the reference.
More complex orbital movement.
Subtle internal energy movement.

EXECUTING:

Active cyan/blue motion.
Communicates that JARVIS is performing an action.

OBSERVING:

Focused Orb state while reading/inspecting the screen.

VERIFYING:

Controlled analytical animation.

SPEAKING:

Waveform responds to actual speech output.

SUCCESS:

Short elegant completion animation.

ERROR:

Controlled red state.
No dramatic flashing or gaming-style effects.

========================================================
5. SPLASH SCREEN
========================================================

Recreate the visual concept shown in the attached image.

Dark screen.

Central Orb.

Subtle concentric rings.

JARVIS wordmark.

Minimal subtitle.

Small loading/progress treatment.

The transition into Home should be smooth.

Do not make it feel like a video-game loading screen.

========================================================
6. HOME SCREEN
========================================================

Use the Home screen shown in the attached image as the structural
reference.

The Home screen should contain:

TOP BAR

- hamburger/menu
- J.A.R.V.I.S branding
- settings

GREETING

Example:

"Good evening, Macaulay."

"How can I help you?"

CENTRAL ORB

The Orb should dominate the visual hierarchy.

STATUS / ACTIVITY

Show relevant assistant state.

RECENT TASK

Show useful recent activity.

MEMORY

Show relevant remembered preferences/facts.

QUICK ACTIONS

Examples from the reference:

- Open App
- Volume
- Flashlight
- Screenshot

But keep this section clean.

The home screen should NOT become a dashboard overloaded with
widgets.

========================================================
7. LISTENING EXPERIENCE
========================================================

Use the attached LISTENING design directly as the visual reference.

When JARVIS enters real listening mode:

- simplify the interface
- enlarge the Orb
- show "Listening..."
- display a real audio waveform
- allow cancellation
- provide a clear "Speak now" state

The waveform should respond to actual microphone input when possible.

Do not animate a fake waveform while nothing is being captured.

The listening screen must connect to the real voice pipeline.

========================================================
8. THINKING EXPERIENCE
========================================================

Use the attached THINKING screen as the visual reference.

When JARVIS is processing:

Show:

"Thinking..."

Then provide subtle task context.

The reference includes:

"Understanding your request"

with items such as:

- analyzing context
- checking device state
- determining best action

Implement this concept dynamically.

If JARVIS is actually checking device state, show that.

If it is actually planning a task, show that.

Do not invent fake processing messages merely to make the screen
look active.

The UI should reflect actual assistant stages.

========================================================
9. CHAT EXPERIENCE
========================================================

Use the CHAT screen from the attached image as the foundation.

The chat interface should combine:

- natural conversation
- assistant responses
- tool/task results
- cards
- status
- Orb presence
- text input
- voice input

The assistant should not communicate only through plain text.

For actions, use rich result cards when useful.

Example:

USER:

"Make the volume a little louder."

JARVIS:

"Increasing volume."

Then display a volume card showing the actual resulting volume.

The card should represent REAL device state.

========================================================
10. DEVICE ACTION UI
========================================================

Use the DEVICE ACTIONS section in the attached image as the
reference.

Actions such as:

- volume
- flashlight
- screenshot
- brightness
- navigation
- notifications

should produce beautiful compact result cards.

Example:

FLASHLIGHT

ON

Done.

But again:

The card must only say "Done" if the real device action succeeded.

The UI must be connected to the actual execution result.

========================================================
11. SCREEN AWARENESS
========================================================

Use the SCREEN AWARENESS design shown in the reference.

This is a major JARVIS capability.

When JARVIS has legitimate access to the current screen, the UI
should communicate that it can observe relevant UI information.

Display:

"Analyzing screen..."

Then show meaningful observations.

Example:

"I can see this is WhatsApp."

"I can read the messages."

"I can tap, scroll and type when permitted."

The visual representation should be based on actual Accessibility
data/state when available.

Do not create a fake screenshot-analysis animation that is
disconnected from the real system.

========================================================
12. WEB SEARCH
========================================================

Use the WEB SEARCH screen in the attached image as the design
reference.

Search UI should contain:

- search status
- Orb
- query
- result cards
- source
- time/date when useful
- concise summaries
- View all results

Search should connect to the real backend search capability.

The visual design should match the reference.

========================================================
13. TASK EXECUTION
========================================================

This is one of the most important screens.

Use the TASK EXECUTION panel in the attached image as the reference.

JARVIS should visually communicate the task pipeline.

Example:

Understanding request
      ↓
Opening WhatsApp
      ↓
Finding conversation with John
      ↓
Reading latest message
      ↓
Preparing response

Each stage should have a state:

PENDING
ACTIVE
COMPLETED
FAILED

This should be connected to the REAL agent execution pipeline.

Do not hardcode fake timelines.

========================================================
14. OVERLAY EXPERIENCE
========================================================

The attached image shows JARVIS operating over another application.

This must become a real JARVIS overlay experience.

Example:

User is inside WhatsApp.

They invoke JARVIS.

A compact JARVIS panel appears above WhatsApp.

The underlying application remains visible.

The Orb remains recognizable.

The overlay should support:

- listening
- thinking
- speaking
- task progress
- short responses
- cancellation
- dismissal

It should feel like JARVIS has appeared on top of the phone rather
than launching a completely separate application.

Respect Android overlay and background restrictions.

Do not attempt to bypass Android security.

========================================================
15. BOTTOM NAVIGATION
========================================================

The attached image establishes a compact bottom navigation system.

Preserve the concept.

Core destinations should include appropriate versions of:

HOME
CHAT
ORB / ASSISTANT
MEMORY
SETTINGS

The center Orb action should be visually dominant.

The navigation must remain minimal.

Do not add unnecessary tabs simply because the application has many
features.

========================================================
16. ONBOARDING
========================================================

Use the ONBOARDING screens from the attached image as the foundation.

The onboarding should introduce JARVIS progressively.

Suggested flow:

WELCOME TO JARVIS

→

MEET THE ORB

→

POWERFUL CAPABILITIES

→

LET'S GET STARTED

Each screen should have:

- Orb/visual
- concise explanation
- clean typography
- Skip
- Next

The onboarding should explain what JARVIS can actually do.

Do not promise capabilities the application does not possess.

========================================================
17. PERMISSIONS
========================================================

Use the PERMISSIONS screen from the attached image.

Create a clear permission center showing statuses for capabilities
such as:

Accessibility
Notifications
Overlay
Microphone
Usage Access

Each permission should show:

- what it enables
- current state
- appropriate action
- success indicator

Do not make permissions feel like technical Android setup screens.

Make them feel like part of the JARVIS onboarding experience.

========================================================
18. PERSONALIZATION
========================================================

Use the PERSONALIZATION screen in the reference.

Allow appropriate user preferences such as:

- voice
- assistant personality/style
- name
- response preferences
- appearance preferences where supported

Voice selection should visually match the reference.

Preview should use the actual configured voice system.

========================================================
19. MEMORY
========================================================

Use the MEMORY screen shown in the attached image.

Memory should feel like a personal knowledge layer, not a database.

Organize useful memories into categories where appropriate:

- Preferences
- Facts
- Context
- Routines

Example:

"You prefer concise answers."

"You are studying Computer Science."

"Your preferred messaging app is WhatsApp."

Each memory should be editable/deletable.

Memory must be connected to the actual JARVIS memory system.

Do not display fake placeholder memories.

========================================================
20. SETTINGS
========================================================

Use the SETTINGS screen in the attached image as the visual
reference.

Keep the settings structure clean.

Potential sections:

Assistant
Voice
AI Providers
Memory
Accessibility
Notifications
Overlay
Privacy & Security
Advanced
About

Do not expose provider API secrets to ordinary users.

AI provider credentials must remain server-side.

The UI should never encourage users to paste secret provider keys
into the APK when the architecture is supposed to use a backend.

========================================================
21. EMPTY STATES
========================================================

Design proper empty states.

Examples:

No memories yet.

No recent tasks.

No search results.

No notifications.

No conversation history.

Do not leave blank screens.

Use the same JARVIS visual language.

========================================================
22. LOADING STATES
========================================================

Every asynchronous operation needs a deliberate state.

Examples:

Loading
Thinking
Searching
Executing
Observing
Verifying
Connecting
Speaking

Use the Orb and subtle motion instead of generic Android spinners
where appropriate.

========================================================
23. ERROR STATES
========================================================

Errors must be designed, not dumped into a Toast.

Example:

"JARVIS couldn't complete that."

Then provide the useful reason.

Possible actions:

Retry
Cancel
Open Settings
Grant Permission

Error visuals should use the restrained red state from the Orb
system.

========================================================
24. ANIMATION SYSTEM
========================================================

The reference relies heavily on subtle motion.

Create a consistent motion language.

Use:

- orbital rotation
- pulsing
- breathing
- waveform movement
- soft glow transitions
- card transitions
- navigation transitions
- state transitions

Animations must be:

- smooth
- purposeful
- restrained
- performant

Do not animate everything.

Animation should communicate state.

========================================================
25. RESPONSIVE ANDROID DESIGN
========================================================

The UI must work across different Android screen sizes.

Do not hardcode the exact dimensions from the reference image.

Use responsive layouts.

Support:

- small phones
- normal phones
- large phones
- different aspect ratios
- system navigation areas
- safe areas
- portrait layouts

The attached image defines the visual language, not fixed pixel
coordinates.

========================================================
26. ACCESSIBILITY
========================================================

Despite the futuristic visual design, the application itself must
remain accessible.

Use:

- readable contrast
- semantic labels
- sensible touch targets
- screen-reader descriptions
- scalable text where appropriate
- accessible controls

JARVIS's own AccessibilityService and the application's UI
accessibility are separate concerns.

Do not confuse them.

========================================================
27. PERFORMANCE
========================================================

The Orb and visual effects must not destroy performance.

Avoid:

- unnecessary recomposition
- excessive blur
- huge continuously rendered assets
- runaway animations
- memory leaks
- background work without lifecycle control

The application must remain usable on modest Android hardware.

========================================================
28. REAL FUNCTIONALITY OVER VISUAL MOCKUPS
========================================================

This is critical.

Every redesigned screen must connect to the actual application
architecture.

Do not create:

- fake buttons
- fake toggles
- fake search
- fake Orb states
- fake task execution
- fake memory
- fake device actions
- fake permissions
- fake voice
- fake AI responses

If a capability does not currently work, identify it and connect
the UI to the correct implementation as part of the redesign where
appropriate.

Do not hide broken functionality behind beautiful UI.

========================================================
29. ONE UNIFIED JARVIS EXPERIENCE
========================================================

All screens must feel like the same assistant.

The user should be able to move naturally between:

HOME
→ LISTENING
→ THINKING
→ EXECUTING
→ VERIFYING
→ RESPONSE

or:

HOME
→ CHAT
→ ACTION
→ RESULT

or:

ANOTHER APP
→ OVERLAY
→ LISTENING
→ EXECUTION
→ RESPONSE
→ DISMISS

There should be no visual or architectural feeling that these are
separate mini-applications.

========================================================
30. DESIGN THE COMPLETE APP, NOT ONLY THE VISIBLE SCREENS
========================================================

After reproducing the visual language of the a

note you can see that this prompt is not complete so you will see the image design of the app in mistra image.md