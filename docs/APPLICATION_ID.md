# Application ID — Audit Note (Item 5)

## Current state

- `app/build.gradle.kts` sets:
  - `namespace = "com.jarvis"`
  - `applicationId = "com.jarvis"`
- All Kotlin source uses `com.jarvis.*` packages
  (e.g. `com.jarvis.app`, `com.jarvis.android.accessibility`).
- `BuildConfig` is generated under the namespace `com.jarvis`,
  accessible from all `com.jarvis.*` subpackages without explicit imports.

## What this means

In Android, `applicationId` is the **installed app's identity** — the
string other apps, intents, and the system use to refer to the app. The
Kotlin package names are independent of it and may differ. The app
installs and is known to the system as `com.jarvis`, and the code lives
under `com.jarvis.*`. This compiles and runs correctly.

The previous `com.rork.jarvisaiassistant` identifier came from the Rork
Toolkit scaffolding that generated the project. It has been aligned with
the actual source package structure.

## History

The original project was scaffolded with `namespace = "com.rork.jarvisaiassistant"`
while all source code was written under `com.jarvis.*` packages. This
caused a build-breaking mismatch: the generated `R` and `BuildConfig`
classes lived in `com.rork.jarvisaiassistant`, but most Kotlin files
in `com.jarvis.*` could not access them without explicit (and error-prone)
imports.

The namespace and applicationId have been unified under `com.jarvis` so
that:
- All source packages can access `R` and `BuildConfig` without imports.
- The installed app identity matches the code structure.
- No hardcoded package strings remain in business logic.
