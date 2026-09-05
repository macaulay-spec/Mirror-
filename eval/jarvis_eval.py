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
import sys
import time
import urllib.request

API_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
MODEL = os.environ.get("EVAL_MODEL", "nvidia/nemotron-3-super-120b-a12b")

# (utterance, expected tool or None for pure conversation)
CASES = [
    ("hello", None),
    ("tell me about yourself", None),
    ("what time is it", None),
    ("open WhatsApp", "open_app"),
    ("open WhatsApp and tell Sarah I'm late", "open_app"),  # multi-step: starts with app
    ("remind me to call Sarah tomorrow at 8am", "set_reminder"),
    ("wake me up at 7 tomorrow morning", "set_alarm"),
    ("set a timer for 10 minutes", "set_timer"),
    ("what's the weather like outside", "weather"),
    ("text Daniel I'll be late", "send_sms"),
    ("read my notifications", "read_notifications"),
    ("turn on the flashlight", "device_flashlight"),
    ("boost my phone", "phone_boost"),
    ("which apps use the most battery", "app_hog_report"),
    ("how much storage do I have left", "storage_report"),
    ("what's on my calendar today", "calendar_read"),
    ("make my phone faster", "phone_boost"),
    ("don't do that", None),
]

TOOLS = [
    {"type": "function", "function": {"name": "open_app", "description": "Open an installed app",
     "parameters": {"type": "object", "properties": {"app": {"type": "string"}}, "required": ["app"]}}},
    {"type": "function", "function": {"name": "send_sms", "description": "Send an SMS message to a contact",
     "parameters": {"type": "object", "properties": {"contact": {"type": "string"}, "message": {"type": "string"}}, "required": ["contact", "message"]}}},
    {"type": "function", "function": {"name": "set_alarm", "description": "Set a device alarm. 'when' accepts natural time like 'tomorrow 7am'",
     "parameters": {"type": "object", "properties": {"when": {"type": "string"}, "label": {"type": "string"}}, "required": ["when"]}}},
    {"type": "function", "function": {"name": "set_timer", "description": "Start a countdown timer, e.g. duration '10 minutes'",
     "parameters": {"type": "object", "properties": {"duration": {"type": "string"}}, "required": ["duration"]}}},
    {"type": "function", "function": {"name": "set_reminder", "description": "Create a reminder at a natural time",
     "parameters": {"type": "object", "properties": {"what": {"type": "string"}, "when": {"type": "string"}}, "required": ["what", "when"]}}},
    {"type": "function", "function": {"name": "calendar_read", "description": "Read upcoming calendar events",
     "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "weather", "description": "Current weather at the user's location",
     "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "read_notifications", "description": "Read recent notifications aloud",
     "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "device_flashlight", "description": "Toggle the flashlight",
     "parameters": {"type": "object", "properties": {"enabled": {"type": "boolean"}}, "required": ["enabled"]}}},
    {"type": "function", "function": {"name": "phone_boost", "description": "Boost the phone: hibernate background apps, efficient brightness",
     "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "app_hog_report", "description": "Report which apps used the most screen time today",
     "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "storage_report", "description": "Report storage usage",
     "parameters": {"type": "object", "properties": {}}}},
]

SYSTEM = (
    "You are JARVIS, an elite Android assistant. Use tools when the user wants "
    "an action performed or information fetched. For pure conversation, greetings, "
    "or questions about yourself, reply directly with NO tool call."
)


def call_model(utterance: str, key: str) -> dict:
    body = json.dumps({
        "model": MODEL,
        "temperature": 0.2,
        "max_tokens": 256,
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
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.load(resp)
    message = data["choices"][0]["message"]
    calls = message.get("tool_calls") or []
    return {"tools": [c["function"]["name"] for c in calls], "text": message.get("content", "")}


# Hardcoded per the owner's decision (repo is being made private): the key
# ships inline; an env var still overrides it for CI.
DEFAULT_NVIDIA_API_KEY = "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"


def main() -> int:
    key = os.environ.get("NVIDIA_API_KEY", "").strip() or DEFAULT_NVIDIA_API_KEY
    if not key:
        print("NVIDIA_API_KEY not set — eval SKIPPED (not a failure).")
        return 0

    passed, failed = 0, []
    print(f"JARVIS Brain Eval — model: {MODEL}, cases: {len(CASES)}\n")
    for utterance, expected in CASES:
        start = time.time()
        try:
            result = call_model(utterance, key)
            got = result["tools"][0] if result["tools"] else None
            ok = got == expected
        except Exception as exc:  # noqa: BLE001
            got, ok = f"ERROR: {exc}", False
        ms = int((time.time() - start) * 1000)
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {ms:>5}ms  {utterance!r:<48} expected={expected} got={got}")
        if ok:
            passed += 1
        else:
            failed.append((utterance, expected, got))

    total = len(CASES)
    print(f"\n{passed}/{total} passed")
    if failed:
        print("\nREGRESSIONS:")
        for utterance, expected, got in failed:
            print(f"  - {utterance!r}: expected {expected}, got {got}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
