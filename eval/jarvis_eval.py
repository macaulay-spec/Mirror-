#!/usr/bin/env python3
"""
JARVIS Brain Eval — Wave C regression harness.

Runs the spec's acceptance utterances (and the everyday commands) against the
live NVIDIA endpoint and verifies the model picks the right tool (or NO tool
for pure conversation). This is the "training loop" gate: any prompt, schema,
or model change that makes JARVIS dumber fails CI before it reaches you.

Usage:
    NVIDIA_API_KEY=nvapi-... python eval/jarvis_eval.py

Exit code 0 = all passed (or skipped, no key), 1 = regressions found.
"""

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

API_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
MODEL = os.environ.get("EVAL_MODEL", "nvidia/nemotron-3-super-120b-a12b")

# ---------------------------------------------------------------------------
# Cases: (utterance, set of acceptable tool ids, or None for pure conversation)
#
# The acceptable set is a set, not a single id, because the app genuinely registers
# near-duplicate tools -- open_app AND app_launch, device_storage AND storage_report,
# read_notifications AND get_recent_notifications, send_sms AND send_message AND
# send_whatsapp, device_flashlight AND toggle_setting. Several of them answer the same
# question correctly, so insisting on one specific id would fail the gate for a
# behaviour that works. The duplication itself is a known defect (see the audit's tool
# inventory) and deduplicating the registry is the real fix; until then this records
# what "correct" actually means.
# ---------------------------------------------------------------------------
CASES = [
    # Pure conversation -- must NOT call a tool.
    ("hello", None),
    ("tell me about yourself", None),
    ("don't do that", None),
    ("thanks", None),

    # Device information
    ("what time is it", {"device_time"}),
    ("how much battery do I have", {"device_battery"}),
    ("how much storage do I have left", {"storage_report", "device_storage"}),
    ("which apps use the most battery", {"app_hog_report", "get_daily_usage"}),
    ("am I connected to wifi", {"device_connectivity"}),
    ("where am I", {"device_location"}),

    # Apps
    ("open WhatsApp", {"open_app", "app_launch"}),
    ("launch YouTube", {"open_app", "app_launch"}),

    # Messaging -- multi-step commands should start with the app or the message
    ("open WhatsApp and tell Sarah I'm late", {"open_app", "app_launch", "send_whatsapp", "send_message", "send_sms"}),
    ("text Daniel I'll be late", {"send_sms", "send_message"}),
    ("read my notifications", {"read_notifications", "get_recent_notifications"}),

    # Scheduling
    ("remind me to call Sarah tomorrow at 8am", {"set_reminder"}),
    ("wake me up at 7 tomorrow morning", {"set_alarm"}),
    ("set a timer for 10 minutes", {"set_timer"}),
    ("what's on my calendar today", {"calendar_read"}),

    # Device control
    ("turn on the flashlight", {"device_flashlight", "toggle_setting"}),
    ("turn on do not disturb", {"set_dnd", "toggle_setting"}),
    ("put my phone on silent", {"set_ringer_mode", "set_dnd"}),
    ("turn up the volume", {"device_volume"}),
    ("set brightness to 50 percent", {"set_brightness"}),
    ("pause the music", {"device_media_control"}),

    # Optimization
    ("boost my phone", {"phone_boost"}),
    ("make my phone faster", {"phone_boost"}),

    # Information
    ("what's the weather like outside", {"weather"}),
    ("what's the weather in Abuja", {"weather"}),
    # Widened during the sync: KnowledgeTools added `wikipedia`, which is an equally
    # correct route for a factual lookup. Expecting only web_search would have turned a
    # good answer into a scored routing regression.
    ("who is the president of France", {"web_search", "wikipedia"}),
    ("remember that my wife's birthday is June 4th", {"memory_remember"}),
    ("what do you remember about my birthday", {"memory_recall"}),
    ("take me home", {"navigate_to"}),
    ("find a fuel station near me", {"nearby_search"}),

    # Knowledge & generative -- added by KnowledgeTools.kt on main. These had zero
    # coverage, and two of them were being offered to the model with parameters their
    # handlers never read (see ToolSchema.ARG_HINTS), so they could not have worked.
    ("convert 100 dollars to naira", {"currency"}),
    ("what is 50 euros in pounds", {"currency"}),
    ("what's the news", {"news"}),
    ("any headlines about technology", {"news", "web_search"}),
    ("tell me about the Eiffel Tower", {"wikipedia", "web_search"}),
    ("generate an image of a sunset over Lagos", {"generate_image"}),

    # On-screen control (JarvisAccessibilityService)
    ("click the button that says Save", {"click_text", "click_element"}),
    ("wait for WhatsApp to open", {"wait_for_screen", "open_app", "app_launch"}),
]


def load_tools() -> list:
    """
    Load the tool surface the app actually sends.

    CHANGED: this list used to be 12 hand-written schemas. The app sends
    ToolSchema.forOpenAI() -- every tool in EXPOSED_CATEGORIES, with parameters from
    ARG_HINTS/defaultArgs -- so the harness was testing an easier, fictional problem and
    produced false failures ("what time is it" was expected to call nothing because the
    harness did not offer device_time).

    The count is deliberately not hardcoded here: it was 69, then main added
    KnowledgeTools and it became 74. Read it from the manifest instead.

    eval/tools.json is generated by scripts/generate_eval_tools.py from the Kotlin
    sources, and CI fails if it goes stale.
    """
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tools.json")
    if not os.path.exists(path):
        raise SystemExit(
            f"{path} is missing. Generate it with: python3 scripts/generate_eval_tools.py"
        )
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    tools = doc["tools"]
    # Strip the generator's bookkeeping keys; the model must see exactly what the app sends.
    return [{"type": t["type"], "function": t["function"]} for t in tools]


TOOLS = load_tools()
TOOL_IDS = {t["function"]["name"] for t in TOOLS}

# Fail loudly rather than silently scoring a case against a tool that does not exist.
for _utterance, _expected in CASES:
    if _expected is None:
        continue
    _unknown = _expected - TOOL_IDS
    if _unknown:
        raise SystemExit(
            f"case {_utterance!r} expects unknown tool(s) {sorted(_unknown)}. "
            "Either eval/tools.json is stale (regenerate it) or the expectation is wrong."
        )
del _utterance, _expected, _unknown


SYSTEM = (
    "You are JARVIS, an elite Android assistant. Use tools when the user wants "
    "an action performed or information fetched. For pure conversation, greetings, "
    "or questions about yourself, reply directly with NO tool call."
)


# Request shape must match what the app actually sends -- see
# JarvisApiClient.applyInferenceControls(). The harness used to send max_tokens=256
# with no thinking control, which is not the production request: Nemotron-3 is a
# reasoning model, so it spent the whole 256-token budget on internal reasoning and
# returned an empty message with no tool_calls. Every case then "failed" for a reason
# that had nothing to do with tool selection.
#
#   enable_thinking=false  <- production fast tier; makes room for the tool call
#   max_tokens=1024        <- production fast tier cap
#
# temperature deliberately stays low (0.2 vs the app's 0.7) so the gate is
# reproducible; it does not change whether a tool call is emitted.
MAX_TOKENS = int(os.environ.get("EVAL_MAX_TOKENS", "1024"))
TEMPERATURE = float(os.environ.get("EVAL_TEMPERATURE", "0.2"))
HTTP_ATTEMPTS = int(os.environ.get("EVAL_HTTP_ATTEMPTS", "4"))
RETRY_BACKOFF_SECONDS = float(os.environ.get("EVAL_RETRY_BACKOFF", "1.5"))
RETRYABLE_STATUS = {429, 500, 502, 503, 504}
# Share of cases allowed to fail on infrastructure before the run is treated as
# broken rather than flaky.
INFRA_TOLERANCE = float(os.environ.get("EVAL_INFRA_TOLERANCE", "0.2"))


def call_model(utterance: str, key: str) -> dict:
    body = json.dumps({
        "model": MODEL,
        "temperature": TEMPERATURE,
        "max_tokens": MAX_TOKENS,
        "chat_template_kwargs": {"enable_thinking": False},
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": utterance},
        ],
        "tools": TOOLS,
    }).encode()

    req = urllib.request.Request(API_URL, data=body, headers={
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    })
    # NVIDIA NIM returns transient 500s on this endpoint often enough to matter: the
    # first CI run of this harness scored 10/18 with 7 of the 8 failures being HTTP 500
    # in ~250ms, i.e. the request never reached the model. Without retries the gate
    # mostly measured provider flakiness rather than routing.
    last_error = None
    for attempt in range(1, HTTP_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                data = json.load(resp)
            break
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                detail = exc.read().decode("utf-8", "replace")[:300]
            except Exception:  # noqa: BLE001
                pass
            last_error = RuntimeError(f"HTTP {exc.code} {exc.reason} — {detail}")
            if exc.code in RETRYABLE_STATUS and attempt < HTTP_ATTEMPTS:
                time.sleep(RETRY_BACKOFF_SECONDS * attempt)
                continue
            # Surface the provider's own message: it distinguishes a dead key (401) from
            # a retired model id (404) from rate limiting (429) from a provider outage.
            raise last_error from exc

    message = data["choices"][0]["message"]
    calls = message.get("tool_calls") or []
    finish = data["choices"][0].get("finish_reason")
    return {
        "tools": [c["function"]["name"] for c in calls],
        "text": message.get("content") or "",
        "finish_reason": finish,
    }


# OWNER DECISION (2026-09-07): the NVIDIA key is hardcoded in the app again. Rather
# than committing a second copy of it here, this harness reads the single source of
# truth out of ApiConfig.kt. One place to rotate. NVIDIA_API_KEY in the environment
# still overrides it, so CI can run against a different key without editing source.
APICONFIG_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "java", "com", "jarvis", "app", "config", "ApiConfig.kt",
)


def _read_apiconfig() -> str:
    try:
        with open(APICONFIG_PATH, encoding="utf-8") as fh:
            return fh.read()
    except OSError:
        return ""


def default_nvidia_api_key() -> str:
    """
    Pull the hardcoded NVIDIA key out of ApiConfig.kt ("" if the app has none).

    Handles both shapes the source has used:

        val NVIDIA_API_KEY: String
            get() = "nvapi-..."                       # key inline in the getter

        val NVIDIA_API_KEY: String get() = HARDCODED_NVIDIA_KEY
        private const val HARDCODED_NVIDIA_KEY =
            "nvapi-..."                               # getter delegates to a constant

    FIXED during the sync: only the first shape was matched. When the key moved into a
    constant, the regex stopped matching and returned "", which main() interpreted as "no
    credentials available" and skipped the entire eval with exit code 0 -- a green CI check
    that had tested nothing. The indirection is now followed, and a parse failure is
    reported by apiconfig_key_unparsed() so it can fail loudly instead.
    """
    src = _read_apiconfig()
    if not src:
        return ""

    getter = re.search(
        r'val\s+NVIDIA_API_KEY\s*:\s*String\s*(?:\n\s*)?get\(\)\s*=\s*(.+)', src
    )
    if not getter:
        return ""
    rhs = getter.group(1).strip()

    literal = re.match(r'"([^"]*)"', rhs)
    if literal:
        return literal.group(1)

    # getter delegates to a named constant -- resolve it
    name = re.match(r'([A-Za-z_][A-Za-z_0-9]*)', rhs)
    if not name:
        return ""
    const = re.search(
        rf'(?:const\s+)?val\s+{re.escape(name.group(1))}\s*(?::\s*String)?\s*=\s*\n?\s*"([^"]*)"',
        src,
    )
    return const.group(1) if const else ""


def apiconfig_key_unparsed() -> bool:
    """True when ApiConfig.kt plainly holds an nvapi key that we failed to extract."""
    src = _read_apiconfig()
    return bool(src) and "nvapi-" in src and not default_nvidia_api_key()


def main() -> int:
    key = os.environ.get("NVIDIA_API_KEY", "").strip() or default_nvidia_api_key()
    if not key:
        # Distinguish "the app genuinely has no key" (skip) from "the key is there but this
        # script could not read it" (a real defect -- and the one that silently disabled the
        # gate). Only the second should ever turn CI red.
        if apiconfig_key_unparsed():
            print("::error file=eval/jarvis_eval.py,line=1::ApiConfig.kt contains an "
                  "nvapi- key that default_nvidia_api_key() could not parse. Refusing to "
                  "skip: an unparseable key would report a green eval that tested nothing.")
            return 1
        print("NVIDIA_API_KEY not set and ApiConfig.kt holds no hardcoded key"
              " — eval SKIPPED (not a failure).")
        print("::warning file=eval/jarvis_eval.py,line=1::JARVIS brain eval skipped: "
              "no API key available.")
        return 0

    passed, failed = 0, []
    print(f"JARVIS Brain Eval — model: {MODEL}, cases: {len(CASES)}, tools: {len(TOOLS)}\n")
    for utterance, expected in CASES:
        start_t = time.time()
        try:
            for attempt in (1, 2):
                try:
                    result = call_model(utterance, key)
                    break
                except (urllib.error.URLError, TimeoutError) as exc:
                    if attempt == 2:
                        raise
                    print(f"    (transient: {exc} — retrying)")
                    time.sleep(2)
            got = result["tools"][0] if result["tools"] else None
            ok = (got in expected) if isinstance(expected, set) else (got == expected)
            if not got and result.get("finish_reason") == "length":
                got = "NO_TOOL (finish_reason=length — reply was truncated)"
        except Exception as exc:  # noqa: BLE001
            got, ok = f"ERROR: {exc}", False
        ms = int((time.time() - start_t) * 1000)
        mark = "PASS" if ok else "FAIL"
        want = "/".join(sorted(expected)) if isinstance(expected, set) else str(expected)
        print(f"[{mark}] {ms:>5}ms  {utterance!r:<44} want={want:<42} got={got}")
        if ok:
            passed += 1
        else:
            failed.append((utterance, expected, got))

    total = len(CASES)
    print(f"\n{passed}/{total} passed")

    # Two different kinds of failure, and only one of them is a regression.
    #
    # A wrong tool means the model routed an utterance incorrectly -- that is exactly
    # what this gate exists to catch, and any occurrence should fail the build.
    #
    # An ERROR means the request never produced an answer: HTTP 500 from the provider, a
    # timeout, a dead key. That is infrastructure, not brain quality. The first CI run
    # scored 10/18 with 7 of the 8 failures being transient HTTP 500s in ~250ms, so
    # treating those as regressions made the gate mostly measure provider flakiness.
    # They are reported and tolerated up to a threshold, above which something real is
    # wrong (quota, key, retired model id) and the run fails anyway.
    infra = [(u, e, g) for (u, e, g) in failed if str(g).startswith("ERROR:")]
    routing = [(u, e, g) for (u, e, g) in failed if not str(g).startswith("ERROR:")]

    if routing:
        print(f"\nROUTING REGRESSIONS ({len(routing)}):")
        for utterance, expected, got in routing:
            want = "/".join(sorted(expected)) if isinstance(expected, set) else expected
            print(f"  - {utterance!r}: expected {want}, got {got}")

    if infra:
        print(f"\nINFRASTRUCTURE ERRORS ({len(infra)}) — not counted as regressions:")
        for utterance, _expected, got in infra:
            print(f"  - {utterance!r}: {got}")

    infra_budget = max(1, int(total * INFRA_TOLERANCE))
    if len(infra) > infra_budget:
        print(f"\n{len(infra)} infrastructure errors exceeds the budget of {infra_budget} "
              f"({INFRA_TOLERANCE:.0%} of {total}). The endpoint, key or model id is "
              "probably broken rather than flaky.")
        return 1
    if routing:
        return 1
    return 0
