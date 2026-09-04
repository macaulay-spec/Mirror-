# JARVIS App - Comprehensive Audit Report

**Date:** 2026-09-04  
**Audit Scope:** Full application audit, NVIDIA multi-model integration research  
**Repository:** macaulay-spec/Mirror-  

---

## EXECUTIVE SUMMARY

This audit identifies **1 CRITICAL security issue**, **4 HIGH-priority bugs**, **3 MEDIUM-priority issues**, and **5 LOW-priority improvements** across the JARVIS Android application. The NVIDIA multi-model integration requirements have been analyzed and are ready for implementation.

**Overall Health:** ⚠️ **NEEDS ATTENTION** - Security vulnerability requires immediate fix

---

## 🔴 CRITICAL ISSUES (Must Fix Immediately)

### 1. **HARDCODED RORK API KEY IN SOURCE CODE**
- **File:** `app/src/main/java/com/jarvis/app/config/ApiConfig.kt:178`
- **Issue:** `RORK_KEY_FALLBACK = "rork_sk_ied1mfj2qty7j0sg0dm7mgca520gkg71"`
- **Impact:** 
  - **SECURITY BREACH** - Live API key exposed in public repository
  - According to NVIDIA_MULTI_MODEL_PROMPT.md: "Rork's gateway integration has turned out broken in practice, on top of the hardcoded-key problem from earlier"
  - The document explicitly warns: "don't let this NVIDIA work distract from it, but don't block this work on it either unless asked"
  - However, this is a **critical security vulnerability** that must be addressed
- **Evidence:** 
  ```kotlin
  private const val RORK_KEY_FALLBACK = "rork_sk_ied1mfj2qty7j0sg0dm7mgca520gkg71"
  get() = BuildConfig.RORK_TOOLKIT_KEY.ifBlank { RORK_KEY_FALLBACK }
  ```
- **Fix Required:** 
  - Remove the hardcoded key entirely
  - Change fallback behavior to return empty string (no key) instead of fallback
  - This forces users to configure their own keys

---

## 🟠 HIGH PRIORITY BUGS

### 2. **NO FALLBACK CHAIN FOR PROVIDER FAILURES**
- **File:** `app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt`
- **Issue:** Current implementation uses `ApiConfig.activeProvider` which returns only "rork" as default
- **Impact:** 
  - If Rork fails (429, 500, etc.), there's no automatic fallback to other providers
  - Users experience complete AI failure instead of graceful degradation
  - NVIDIA_MULTI_MODEL_PROMPT.md explicitly requires: "try the primary; on failure or rate-limit (HTTP 429), fall through to the next in the list"
- **Current Behavior:**
  ```kotlin
  val activeProvider: String
      get() {
          customProvider?.takeIf { it.isNotBlank() }?.let { return it }
          return "rork"  // Only Rork, no fallback chain
      }
  ```
- **Required:** Implement cascading fallback: GLM-5.2 → Nemotron → Mistral Nemotron → Llama 4 Maverick → existing chain

### 3. **STREAMING DOESN'T FALL BACK ON FAILURE**
- **File:** `app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt:114-128`
- **Issue:** `chatStream()` has fallback logic but only if NO deltas were emitted
- **Impact:** 
  - If stream fails after emitting some deltas, it returns the failure without completing
  - Partial responses are shown but error state is lost
  - No retry/fallback to next provider
- **Code:**
  ```kotlin
  if (streamed.isFailure && !emitted) fallbackBlocking() else streamed
  ```
- **Fix Required:** 
  - Track provider being used
  - On failure, try next provider in chain
  - Surface which provider actually responded (per NVIDIA doc requirement)

### 4. **MISSING NVIDIA PROVIDER IN DIAGNOSTICS**
- **File:** `app/src/main/java/com/jarvis/app/diagnostics/DiagnosticsActivity.kt:127-128`
- **Issue:** Provider test only includes: `listOf("gemini", "xai", "openai", "groq", "anthropic")`
- **Impact:** 
  - NVIDIA models cannot be tested from Diagnostics screen
  - NVIDIA_MULTI_MODEL_PROMPT.md requires: "Update DiagnosticsActivity's provider-test screen to test all four new models"
  - Uses SAME code path as real chat (requirement not met)
- **Fix Required:** 
  - Add NVIDIA provider testing
  - Add all four NVIDIA models to test list
  - Ensure tests use same code path as production chat

### 5. **BUILDCONFIG MISSING NVIDIA_API_KEY**
- **File:** `app/build.gradle.kts:14-17`
- **Issue:** Only defines XAI, GEMINI, ELEVENLABS, RORK keys
- **Impact:** 
  - No build-time injection for NVIDIA API key
  - NVIDIA_MULTI_MODEL_PROMPT.md requires: "Add an NVIDIA_API_KEY BuildConfig field, sourced from local.properties / CI secrets"
- **Current:**
  ```kotlin
  buildConfigField("String", "XAI_API_KEY", ...)
  buildConfigField("String", "GEMINI_API_KEY", ...)
  buildConfigField("String", "ELEVENLABS_API_KEY", ...)
  buildConfigField("String", "RORK_TOOLKIT_KEY", ...)
  ```
- **Fix Required:** Add `NVIDIA_API_KEY` BuildConfig field

---

## 🟡 MEDIUM PRIORITY ISSUES

### 6. **NO NVIDIA MODEL CONSTANTS**
- **File:** `app/src/main/java/com/jarvis/app/config/ApiConfig.kt:138-148`
- **Issue:** Model constants only include existing providers, no NVIDIA models
- **Impact:** 
  - NVIDIA_MULTI_MODEL_PROMPT.md requires named constants for all four models
  - Required models: GLM-5.2, Nemotron-3-Super/Ultra, Mistral Nemotron, Llama 4 Maverick
- **Fix Required:** Add constants:
  ```kotlin
  const val NVIDIA_GLM_MODEL = "zhipuai/glm-5.2"
  const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-super"
  const val NVIDIA_MISTRAL_NEMOTRON_MODEL = "mistralai/mistral-nemotron-7b"
  const val NVIDIA_LLAMA_MODEL = "meta/llama-4-maverick"
  const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
  ```

### 7. **NO PROVIDER SELECTION LOGIC FOR NVIDIA**
- **File:** `app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt:132-180`
- **Issue:** `streamOpenAICompatible()` doesn't include NVIDIA endpoint
- **Impact:** 
  - NVIDIA endpoint (`integrate.api.nvidia.com/v1`) is OpenAI-compatible
  - Should reuse existing OpenAI-compatible code path
  - NVIDIA_MULTI_MODEL_PROMPT.md: "reuse the EXISTING OpenAI-compatible code path"
- **Current Endpoint Mapping:**
  ```kotlin
  when (provider) {
      "rork" -> ApiConfig.RORK_GATEWAY_URL
      "xai" -> "https://api.x.ai/v1/chat/completions"
      // ... no nvidia
  }
  ```
- **Fix Required:** Add NVIDIA to endpoint mapping

### 8. **DIAGNOSTICS MISLEADING LABEL**
- **File:** `app/src/main/java/com/jarvis/app/diagnostics/DiagnosticsActivity.kt:116-120`
- **Issue:** Shows "Gemini key present" but checks `ApiConfig.activeApiKey` which may be Rork
- **Impact:** 
  - User thinks they're checking Gemini but it's actually checking active provider
  - Confusing UX when debugging which provider is configured
- **Code:**
  ```kotlin
  Text("Active provider: ${ApiConfig.getProviderLabel()}")
  Text("Gemini key present: ${if (ApiConfig.activeApiKey.isBlank()) ...}")
  ```
- **Fix Required:** Show accurate provider-specific status for each provider

---

## 🟢 LOW PRIORITY IMPROVEMENTS

### 9. **HARDCODED EMPTY KEYS STILL PRESENT**
- **File:** `app/src/main/java/com/jarvis/app/config/ApiConfig.kt:99-100`
- **Issue:** Empty string constants for Gemini and ElevenLabs
- **Impact:** 
  - Code comments reference removed keys
  - Could be cleaned up for clarity
- **Current:**
  ```kotlin
  private const val HARDCODED_GEMINI_KEY = ""
  private const val HARDCODED_ELEVENLABS_KEY = ""
  ```
- **Fix Optional:** Remove these entirely since they're empty

### 10. **LOCAL.PROPERTIES.EXAMPLE INCOMPLETE**
- **File:** `local.properties.example`
- **Issue:** Only shows XAI, GEMINI, ELEVENLABS placeholders
- **Impact:** 
  - No NVIDIA_API_KEY example
  - Users don't know what keys to configure
- **Fix Required:** Add NVIDIA_API_KEY to example

### 11. **NO RATE LIMIT HANDLING IN STREAMING**
- **File:** `app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt:187, 290, 331`
- **Issue:** Error messages for 429 are present but no automatic retry/fallback
- **Impact:** 
  - NVIDIA_MULTI_MODEL_PROMPT.md requires: "on failure or rate-limit (HTTP 429), fall through to the next"
  - Current: Returns error to user
  - Required: Automatic fallback to next provider

### 12. **PROVIDER LABEL MISSING NVIDIA**
- **File:** `app/src/main/java/com/jarvis/app/config/ApiConfig.kt:137-148`
- **Issue:** `getProviderLabel()` doesn't include NVIDIA
- **Impact:** 
  - NVIDIA provider would show as generic uppercase
  - Diagnostics won't show proper NVIDIA labels
- **Fix Required:** Add NVIDIA cases to provider label mapping

### 13. **TOOL SCHEMA NOT OPTIMIZED FOR NVIDIA**
- **File:** `app/src/main/java/com/jarvis/agent/ai/ToolSchema.kt`
- **Issue:** Tool schemas are generic, not NVIDIA-specific
- **Impact:** 
  - NVIDIA models may have different tool calling preferences
  - Should verify compatibility
- **Note:** NVIDIA endpoint is OpenAI-compatible, so existing `forOpenAI()` should work

---

## 📋 NVIDIA MULTI-MODEL INTEGRATION REQUIREMENTS

### From NVIDIA_MULTI_MODEL_PROMPT.md:

#### Model Lineup (Priority Order):
1. **Primary:** GLM-5.2 (`zhipuai/glm-5.2`) - 1M token context, long-horizon agentic reasoning
2. **Fallback 1:** Nemotron-3-Super or Nemotron-3-Ultra (NVIDIA's flagship)
3. **Fallback 2:** Mistral Nemotron - purpose-built for agentic workflows
4. **Fallback 3:** Llama 4 Maverick (or Llama 3.1 405B if unavailable)
5. **Kept:** Existing xAI/Gemini chain as final fallback

#### Implementation Requirements:
- ✅ Add `NVIDIA_API_KEY` BuildConfig field
- ✅ Add `NVIDIA_BASE_URL` constant (`https://integrate.api.nvidia.com/v1`)
- ✅ Add four model identifier constants
- ✅ Reuse existing OpenAI-compatible code path (NVIDIA is OpenAI-compatible)
- ✅ Provider selection: try primary, fall through on failure/429
- ✅ Surface which provider answered in diagnostics/logging
- ✅ Update DiagnosticsActivity to test all four new models
- ✅ Use SAME code path as real chat (not separate test path)
- ⚠️ **Do NOT hardcode fallback key** (CRITICAL - current Rork issue)

#### Speech (Separate Work - Not Now):
- NVIDIA Riva stack (Parakeet ASR, Magpie TTS) uses gRPC, not REST
- Requires new voice-engine implementation
- Sequence AFTER text-model integration

#### Image Generation (Lowest Priority):
- FLUX.1-Kontext-Dev and Qwen-Image available
- Same NVIDIA key covers it
- Note for later, don't build now

---

## 🔧 RECOMMENDED FIX ORDER

### Phase 1: Security (IMMEDIATE)
1. **Remove RORK_KEY_FALLBACK hardcoded key** from ApiConfig.kt
2. Change `rorkApiKey` to return empty string if BuildConfig key is blank

### Phase 2: NVIDIA Infrastructure
3. Add `NVIDIA_API_KEY` to BuildConfig in app/build.gradle.kts
4. Add `NVIDIA_BASE_URL` and model constants to ApiConfig.kt
5. Update local.properties.example with NVIDIA_API_KEY

### Phase 3: Provider Fallback Chain
6. Implement cascading provider selection in ApiConfig.kt
7. Update JarvisApiClient.kt to support NVIDIA in endpoint mapping
8. Add automatic fallback on 429/errors in streaming and blocking calls

### Phase 4: Diagnostics & UX
9. Update DiagnosticsActivity to test NVIDIA models
10. Fix provider status labels to be accurate
11. Add NVIDIA to provider label mapping

### Phase 5: Testing
12. Test each fallback tier triggers correctly
13. Verify NVIDIA models work with actual API key
14. Confirm diagnostics tests use same code path as production

---

## 📊 FILES REQUIRING CHANGES

| File | Changes Required | Priority |
|------|-----------------|----------|
| `app/src/main/java/com/jarvis/app/config/ApiConfig.kt` | Remove hardcoded key, add NVIDIA constants, implement fallback chain | CRITICAL |
| `app/build.gradle.kts` | Add NVIDIA_API_KEY BuildConfig field | HIGH |
| `app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt` | Add NVIDIA endpoint, implement fallback on errors | HIGH |
| `app/src/main/java/com/jarvis/app/diagnostics/DiagnosticsActivity.kt` | Add NVIDIA provider testing | HIGH |
| `local.properties.example` | Add NVIDIA_API_KEY example | LOW |

---

## ✅ VERIFICATION CHECKLIST

- [ ] RORK_KEY_FALLBACK removed from source code
- [ ] No hardcoded API keys remain in repository
- [ ] NVIDIA_API_KEY BuildConfig field added
- [ ] NVIDIA model constants defined
- [ ] Provider fallback chain implemented
- [ ] NVIDIA endpoint added to streamOpenAICompatible
- [ ] Automatic fallback on 429/failure working
- [ ] Diagnostics tests all NVIDIA models
- [ ] Same code path used for tests and production
- [ ] Provider labels accurate in diagnostics

---

## 🎯 API KEY PROVIDED FOR TESTING

The user provided API key: `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`

**⚠️ SECURITY NOTE:** This key appears to be a real NVIDIA API key. It should:
- NEVER be committed to the repository
- Only be used in local.properties (which is .gitignored)
- Or configured via environment variables in CI

---

## 📝 NOTES

1. **Architecture Decision:** The app supports both backend proxy (Convex) and direct API modes. The NVIDIA integration should work in both modes, but the primary focus is direct API mode with BuildConfig keys.

2. **Rork Status:** According to NVIDIA_MULTI_MODEL_PROMPT.md, "Rork drops out of the priority order entirely" - it should remain as a code path but not be reached before NVIDIA models.

3. **Testing Strategy:** The document emphasizes: "Confirm each fallback tier actually triggers correctly (test by temporarily using an invalid key for the primary and confirming it falls through, not just that each model works in isolation)"

4. **No New Dependencies:** NVIDIA integration should reuse existing OkHttp client and OpenAI-compatible code paths. No new HTTP libraries needed.

---

**Audit Complete**  
**Next Steps:** Address CRITICAL issue #1 immediately, then proceed with NVIDIA integration per the requirements document.
