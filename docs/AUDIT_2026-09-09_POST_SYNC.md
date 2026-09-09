# JARVIS — Post-Sync Audit Report

**Date:** 2026-09-09
**Branch:** `arena/01a07967-mirror` @ `3d0e263` (8 commits on top of `main` @ `272f511`)
**Verification:** CI green — `Build Debug APK` success, `JARVIS Brain Eval` **42/42**, tool manifest 74/74 in sync, Room migration policy pass.

---

## 1. Executive Summary

`main` was rewritten into a single squashed commit while this branch carried 18 granular
commits. The branch has been re-based onto that new `main`, the five genuine conflicts
resolved by hand, and the result verified by a build and a live routing eval that both pass.

The sync surfaced something more valuable than a merge: **the verification layer was not
verifying anything.** Three independent defects each made a CI gate report success while
doing no work —

1. `eval/jarvis_eval.py` had lost its `if __name__ == "__main__"` entry point. The gate ran
   for nine seconds, called no model, printed nothing, and exited 0. It had been green and
   empty for several commits.
2. The harness read the NVIDIA key with a regex that stopped matching when the key moved
   into a constant, returned `""`, and treated that as "no credentials — skip". Skipping
   also exits 0.
3. Build failures were undiagnosable from automation: Actions logs and artifacts cannot be
   fetched in this environment, and the only annotation said "Process completed with exit
   code 1".

Once the gate actually ran, it immediately found a **live defect on `main` that no amount of
reading had surfaced**: the provider fallback cannot work. `streamNVIDIA` and
`executeNVIDIA` hardcode Google's endpoint and force a Gemini model while accepting — and
ignoring — `provider` and `model` parameters. `chatDirect` walks the provider chain, picks
an NVIDIA key, and posts it to `generativelanguage.googleapis.com`: a guaranteed 401. The
compiled-in NVIDIA key could never be used, so a device with no usable Gemini key had **no
brain at all** — precisely the situation the fallback exists for.

Two further production defects came out of the same gate, both invisible until failure lines
printed raw values: model-supplied tool names arrive malformed (`'app_launch\n</parameter'`),
which failed the registry lookup for a routing decision that was correct — and, through
`getTool`, silently downgraded risk classification below the confirmation threshold.

**Net:** 8 commits, 3 production defects fixed, 3 CI false-greens fixed, 1 dead-code
migration made safe, and the eval gate extended from 34 to 42 cases covering the six tools
`main` added. Everything below is reproducible from the commit comments CI now posts.

---

## 2. Audit Report

### A. Provider and AI plumbing

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| A1 | **Critical** | `JarvisApiClient.streamNVIDIA` / `executeNVIDIA` hardcoded the Gemini endpoint and coerced every model to `GEMINI_FLASH_MODEL`, ignoring their own `provider`/`model` parameters. The multi-provider fallback was decorative: any non-Gemini provider received a Gemini endpoint plus a mismatched bearer key. | **Fixed** `863dfee` |
| A2 | **Critical** | `PROVIDER_FALLBACK_CHAIN` contained only `gemini_flash`, `gemini_pro`. The hardcoded NVIDIA key was unreachable by the chain walk. | **Fixed** `863dfee` |
| A3 | High | `getNextProvider` tested `currentGeminiKey` for **every** candidate, so a device with an exhausted Gemini pool skipped NVIDIA too — the one provider whose key is always present. | **Fixed** `863dfee` |
| A4 | High | `resolveModel`'s `else` arm returned a Gemini id for any provider; `nvidia_super` resolved to `gemini-2.5-flash`, which NVIDIA does not serve. | **Fixed** `863dfee` |
| A5 | High | `activeProvider` returned `"gemini_flash"` unconditionally, making `activeApiKey` the only key path and the NVIDIA constant dead weight. | **Fixed** `1b9e881` |
| A6 | Medium | `activeApiKey` always returned a Gemini key regardless of provider — once A5 was fixed, the fallback would have sent a Gemini key to NVIDIA. | **Fixed** `1b9e881` |
| A7 | Medium | Two separate per-provider key `when` blocks in the client; the streaming one had no `openai` arm, so a user's OpenAI key could be paired with the wrong endpoint. | **Fixed** `863dfee` |
| A8 | Low | `getProviderLabel` returned "Gemini AI" for every provider — Diagnostics would name the wrong brain exactly when someone opens it to ask why behaviour changed. | **Fixed** `863dfee` |
| A9 | Low | `LegacyProviderCatalog` (16 lines) is unreferenced **and wrong**: it lists `nvidia/nemotron-3-super`, while the working id is `nvidia/nemotron-3-super-120b-a12b` (proven by 42 eval cases). Wiring it up would break the fallback. | **Open** |

### B. Tool layer

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| B1 | **Critical** | `ToolRegistry` looked up tool ids untrimmed. Model output carrying stray characters failed the lookup, producing "Tool with ID … is not registered" for a correct routing decision. | **Fixed** `ba32967`, `70f2048` |
| B2 | **Critical** | Same lookup feeds risk classification: `AgentExecutor` uses `getTool(name)?.riskLevel ?: RiskLevel.LEVEL_1`, and LEVEL_1 sits *below* the `>= LEVEL_2` confirmation threshold. A malformed name on a LEVEL_3 tool **skipped the user confirmation prompt**. | **Fixed** `ba32967`, `70f2048` |
| B3 | High | The six tools `main` added had no `ARG_HINTS` entries, so `defaultArgs()` offered all of them a generic `query`/`text` pair that none of their handlers read: `currency` wants `amount`/`from`/`to`, `generate_image` wants `prompt`, `news` wants `topic`, `wait_for_screen` wants `app`/`timeout_ms`. The model called them with arguments that were then discarded. **This affects production, not just the eval.** | **Fixed** `1b9e881` |
| B4 | High | `ToolSchema.defaultArgs()` maps any id containing `"open"` to `listOf("app")`. `open_recents` takes no arguments at all — its handler is `{ _, _ -> }`. | **Fixed** `1b9e881` |
| B5 | Medium | 5 overlapping tool groups across 74 tools: `open_app`/`app_launch`, `device_storage`/`storage_report`, `read_notifications`/`get_recent_notifications`, `send_sms`/`send_message`/`send_whatsapp`, `click_text`/`click_element`. `click_text` is a pure alias that forwards to `click_element`. Duplication costs context tokens and creates routing ambiguity. | **Open** |
| B6 | Medium | `ToolRegistry.register` silently overwrites an existing id, so a duplicate registration loses the first implementation with no signal. | **Open** |
| B7 | Low | `LifeTools` contained two keyless Open-Meteo geocoders (`geocodeCity` and `geocode`) and two HTTP clients with different timeouts after `main` and this branch independently fixed the same weather bug. | **Fixed** `1b9e881` |

### C. CI and verification

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| C1 | **Critical** | `eval/jarvis_eval.py` had no `__main__` entry point. The gate passed in ~9s having called no model, and posted an empty transcript. Lost during the 18→34 case rewrite on this branch. | **Fixed** `60fe90f` |
| C2 | **Critical** | The harness extracted the NVIDIA key with a regex matching only `get() = "nvapi-…"`. With the key in a constant it returned `""`, which `main()` read as "no credentials" and skipped — exit 0. | **Fixed** `1b9e881` |
| C3 | High | A no-output run was indistinguishable from a pass. `eval.yml` now fails if the transcript is empty, whatever the exit code. | **Fixed** `60fe90f` |
| C4 | High | Build failures were undiagnosable: Actions log/artifact downloads fail in this environment and annotations only said "exit code 1". `android.yml` now captures the Gradle log and posts compiler errors to the commit. It caught the sync's one compile error on its first run. | **Fixed** `ba32967` |
| C5 | High | `scripts/generate_eval_tools.py` parsed `ARG_HINTS` with a `listOf()`-only regex and then did `hints.get(id) or default_args(id)`. Two independent faults both dropped zero-argument tools: `emptyList()` never parsed, and an empty list is falsy in Python where Kotlin's elvis only falls back on null. `press_back`, `press_home`, `screen_read` grew a bogus `query`+`text` pair. | **Fixed** `1b9e881` |
| C6 | Medium | `TEMPERATURE = 0.2` in a regression gate: the same code scored 42/42 then 40/42 on consecutive runs. Now greedy by default, overridable. Production sends no explicit temperature for routing, so 0.2 matched nothing. | **Fixed** `3d4432e` |
| C7 | Medium | Failure lines printed tool names with `str()`, so an invisible character made a FAIL look self-contradictory (`got=device_flashlight` against a set containing `device_flashlight`). Now `repr()`. This is what exposed B1. | **Fixed** `3d4432e` |
| C8 | Medium | Eval had no coverage for the six new tools; and `"who is the president of France"` expected only `web_search`, so `main`'s new `wikipedia` tool would have scored a correct answer as a regression. | **Fixed** `1b9e881` |
| C9 | Low | The eval job existed in both `android.yml` and `eval.yml`, running the live endpoint twice per push. Consolidated into `eval.yml`. | **Fixed** `1b9e881` |
| C10 | Operational | The NVIDIA endpoint returns HTTP 429 often enough to appear in consecutive runs. The harness retries 4× and classifies these as infrastructure (tolerated ≤20%), separate from routing regressions. On `3d0e263` all 42 cases passed with no infra errors. | Working as designed |

### D. UI and Compose

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| D1 | High | 31 `collectAsState()` calls across 9 files and **zero** `collectAsStateWithLifecycle()`, despite `lifecycle-runtime-compose` already being a declared dependency. Flows kept collecting while backgrounded — including `VoiceBus.audioLevel`, a microphone-level stream, in `HomeScreen` and `VoiceActiveScreen`. On an all-day voice assistant that is measurable battery. | **Fixed** `3d0e263` (25 calls, 7 files) |
| D2 | High | `BottomNavigationBar` has **no `WindowInsets` handling** — only fixed `dp` padding — and is bottom-anchored across 4 live screens (`ChatScreen`, `HomeScreen`, `MemoryPeopleScreen`, `SettingsScreen`). `targetSdk 36` enforces edge-to-edge with no opt-out, so the bar draws under the system navigation/gesture area. 7 other screens do handle insets; this one and its hosts do not. | **Open** |
| D3 | Medium | `JarvisFloatingOrbService` cannot use the lifecycle-aware collector: it is a plain `Service`, not a `LifecycleOwner`, and provides no `LocalLifecycleOwner`. A blanket conversion would have crashed the overlay. Left as-is and now documented in-source. | **Documented** `3d0e263` |
| D4 | Medium | `MainActivity` does not call `enableEdgeToEdge()` and the manifest sets no `enableOnBackInvokedCallback`. Both are implied by `targetSdk 36`, but inset handling is then entirely per-screen — which is why D2 is a real defect rather than a theoretical one. | **Open** |
| D5 | Low | No blocking I/O or `runBlocking`/`Thread.sleep` found in `feature/` composables. Clean. | No action |

### E. Dead code and repository hygiene

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| E1 | Medium | `DualModeHost.kt` — **1339 lines**, the largest file in the feature layer. Its only public declarations (`DualModeHost`, `StageMode`) have zero references outside the file; `MainActivity` composes `HomeScreen`/`ChatScreen` directly. Compiles into the APK, never shown. **Not deleted**: `modify_ui.py` was still rewriting it by regex on 2026-09-08, so the migration looks unfinished rather than abandoned. Marked in-source; needs an owner decision. | **Flagged** `3d0e263` |
| E2 | Medium | `modify_ui.py` at the repository root — an orphaned one-shot script that mutates Kotlin source with regexes. Nothing references it, it is not idempotent, and re-running it would corrupt its target. | **Open** |
| E3 | Medium | `LegacyProviderCatalog` unreferenced (see A9). | **Open** |
| E4 | Low | Removed during the sync: `convex/` (9 files), `BackendConfig`, `CloudSttEngine`, `JarvisVoice`, `test_ai.sh`, the Convex proxy path, `fetchElevenLabsVoices`, `VoicePreferences`/`ElevenLabsVoice`, the Rork Toolkit constants, a block of empty aspirational connector constants (`GOOGLE_STT_API_KEY`, `HOME_ASSISTANT_*`, `LIVEKIT_*`) with zero references, and the unreferenced `OLD_PRESET_VOICES` table. | **Fixed** `1b9e881` |
| E5 | Low | `docs/BUILD.md` documented a `backend/` Node.js service that has never existed in this repository and told readers to set a backend URL in `ApiConfig.kt`. `docs/SECURITY.md` asserted there were no hardcoded keys in source, which the owner decision made false. | **Fixed** `1b9e881` |
| E6 | Low | 9 further docs still describe ElevenLabs/Convex as live. They are point-in-time records worth keeping for rationale, so they were indexed rather than rewritten: `docs/README.md` now separates current documentation from historical records. | **Fixed** `1b9e881` |
| E7 | Low | `local.properties.example` and `.env.example` advertised `NVIDIA_API_KEY` for the app build, which no longer reads it (only the eval harness does). | **Fixed** `1b9e881` |

### F. Data and persistence

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| F1 | High | Room destructive-migration fallbacks were unscoped, so a missing migration path could wipe user data on a current install. Now scoped to specific old versions via `fallbackToDestructiveMigrationFrom`, with explicit `Migrations.kt` and exported schema `5.json`. | **Fixed** (pre-sync, carried through) |
| F2 | Medium | `scripts/check_room_migrations.py` runs in CI both before Gradle (source check, fails in seconds) and after KSP (exported-schema check). Passing on `3d0e263`: version 5, baseline 5. | Working |
| F3 | Low | The legacy SharedPreferences key `"elevenlabs_voice_id"` was deliberately **kept** although ElevenLabs is gone: renaming it would discard every existing user's saved voice selection. | Intentional |

### G. Platform compliance (targetSdk 36)

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| G1 | High | Edge-to-edge is enforced and cannot be opted out of. See D2/D4 — the concrete gap is `BottomNavigationBar` and its four hosts. | **Open** |
| G2 | Medium | Predictive back is default-on at `targetSdk 36`. `MainActivity` uses `rememberSaveable` for navigation state and system Back dismisses Task Execution to Home, but there is no `OnBackPressedCallback`/predictive-back verification. Needs a device pass. | **Open** |
| G3 | Medium | `ROLE_ASSISTANT` is not requestable by third-party apps (`requestable=false`), so the previous `createRequestRoleIntent(ROLE_ASSISTANT)` call was a guaranteed no-op. Replaced with an `ACTION_ASSIST` intent filter path. | **Fixed** (pre-sync) |
| G4 | Medium | Safer Intents enforcement and stricter foreground-service quotas at API 36 not yet verified against `WakeWordForegroundService`. | **Open** |

---

## 3. Prioritized Action Plan

### Critical — all closed

| # | Item | Commit |
| --- | --- | --- |
| C-1 | Make the provider fallback actually reach NVIDIA (A1–A4) | `863dfee` |
| C-2 | Restore the eval gate's entry point; fail on empty output (C1, C3) | `60fe90f` |
| C-3 | Fix the silent key-extraction skip (C2) | `1b9e881` |
| C-4 | Sanitise malformed tool names; close the risk-downgrade hole (B1, B2) | `ba32967`, `70f2048` |

### High — next up

| # | Item | Why it matters | Effort |
| --- | --- | --- | --- |
| H-1 | **D2/G1** — add `WindowInsets` handling to `BottomNavigationBar` and its four hosts | Enforced edge-to-edge at `targetSdk 36`; the nav bar currently draws under the gesture area. Play deadline 31 Aug 2026. | Small — `Modifier.navigationBarsPadding()` plus inset-aware height |
| H-2 | **B5** — collapse the 5 overlapping tool groups, starting with `click_text` (a pure alias) | Every duplicate costs context tokens on all 74 tools and creates routing ambiguity the eval has to paper over with acceptable-sets | Medium — needs the eval re-baselined afterwards |
| H-3 | **B6** — make `ToolRegistry.register` refuse or log duplicate ids | Silent overwrite loses an implementation with no signal | Small |
| H-4 | **G2** — device pass on predictive back and the `rememberSaveable` navigation stack | Mandatory at 36; untestable from CI | Medium — needs hardware |
| H-5 | **G4** — verify `WakeWordForegroundService` against API 36 FGS quotas and Safer Intents | Wake word is the product's headline feature | Medium — needs hardware |

### Medium

| # | Item | Notes |
| --- | --- | --- |
| M-1 | **E1/E2** — owner decision on `DualModeHost.kt` (1339 dead lines) and `modify_ui.py` | Either re-wire the host or delete both together. Deliberately not done unilaterally: the migration was still in progress on 2026-09-08 |
| M-2 | **A9/E3** — delete `LegacyProviderCatalog` or correct its model ids | It is dead *and* wrong; the danger is someone wiring it up |
| M-3 | Extend eval coverage beyond 40 of 74 tools | Currently 42 cases; the uncovered tools are mostly device-control and accessibility primitives |
| M-4 | **D4** — call `enableEdgeToEdge()` explicitly in `MainActivity` | Makes the inset contract explicit rather than implied |
| M-5 | Carry-over backlog from the pre-sync audit: dead wake-word intent path, duplicate wake-lock loops, `ProactiveReceiver` TTS leak | Previously identified; unaffected by the sync |

### Low

| # | Item |
| --- | --- |
| L-1 | Migrate `actions/setup-java@v4` → `v5` (CI warns v4 is deprecated and unfixed) |
| L-2 | Address the Node 20 deprecation warnings: `actions/checkout@v5`, `actions/setup-python@v6` |
| L-3 | `VoiceInteractionService` feasibility study for a true default-assistant experience |
| L-4 | Fold the historical docs' stale provider tables into `docs/README.md`'s index as they are next touched |

---

## 4. Implemented Changes

Eight commits on `arena/01a07967-mirror`, all verified by CI (build + 42/42 eval).

| Commit | Change |
| --- | --- |
| `1b9e881` | **The sync.** Re-based onto rewritten `main`, resolved 5 conflicts, removed Convex/ElevenLabs/Rork, made NVIDIA a real fallback in `ApiConfig`, fixed the generator's `emptyList()`/falsy-empty-list bugs, added `ARG_HINTS` for the 6 new tools, eval 34→42 cases, manifest 69→74 tools, docs corrected |
| `60fe90f` | Restored the eval's `__main__` entry point; `eval.yml` fails on empty output |
| `ba32967` | `ToolRegistry` trims model-supplied ids; `android.yml` posts compiler errors to the commit |
| `fb8bf3d` | The sync's one compile error (`?.takeIf` on a nullable receiver) |
| `3d4432e` | Eval made deterministic (temperature 0); failure lines print `repr()` |
| `863dfee` | `endpointFor`/`keyFor`/`resolveModel` made provider-aware; NVIDIA added to the chain; `getNextProvider` checks the candidate's own key; provider labels corrected |
| `70f2048` | Tool ids sanitised to the leading identifier run, in the registry and the harness alike |
| `3d0e263` | 25 `collectAsState()` → `collectAsStateWithLifecycle()` across 7 screens; Service and dead-code exceptions documented in-source |

### Why the merge strategy changed

Rebasing 18 commits onto the squashed `main` was attempted and abandoned: several commits
contradicted each other across the sync (the NVIDIA key is removed in one and restored in a
later one), so replaying them produced unresolvable churn across 11 overlapping files. The
branch was instead reset to `main` and the **net** work applied as a single patch, yielding
one conflict pass over 5 files. `main`'s new UI layer — `ChatScreen` (1058 lines),
`HomeScreen` (528), `BottomNavigationBar`, `VoiceActiveScreen`, `WebSearchLiveScreen`,
`KnowledgeTools` (333) — is untouched and intact.

The granular pre-sync history is preserved as the pushed tag **`archive/pre-sync-2026-09-09`**
(19 commits, tip `945a9f8`).

### Evidence

Every claim above is reproducible from what CI posts to the commits:

```bash
# eval transcript for any commit
gh api repos/macaulay-spec/Mirror-/commits/<sha>/comments --jq '.[].body'

# build failure detail (only present when the build fails)
gh api repos/macaulay-spec/Mirror-/commits/<sha>/comments --jq '.[].body'

# local gates
python3 scripts/generate_eval_tools.py --check   # manifest drift
python3 scripts/check_room_migrations.py         # Room migration policy
```

The defect trail is worth recording because each fix was caused by the previous one:
restoring the entry point (C1) made the gate run; running it exposed the key-extraction skip
(C2); fixing that exposed malformed tool names (B1), which were only readable once failures
printed `repr()` (C7); and `repr()` was only added because a FAIL line had looked
self-contradictory twice. A verification layer that cannot fail is not a slow gate — it is
an assertion that the code is correct, made by nobody.
