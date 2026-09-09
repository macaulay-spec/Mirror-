#!/usr/bin/env python3
"""
Generate `eval/tools.json` — the exact tool surface the app sends to the model.

Why this exists
---------------
`eval/jarvis_eval.py` used to declare its own 12-tool list by hand. The app sends
`ToolSchema.forOpenAI()`: every registered tool whose category is in
`ToolSchema.EXPOSED_CATEGORIES`, with descriptions and parameter schemas derived from
`ToolSchema.ARG_HINTS` / `defaultArgs()`. So the harness tested a different, much easier
problem than production — no near-duplicate pairs to disambiguate, and tools missing
entirely. That produced false results: "what time is it" was expected to call no tool,
but the harness did not offer `device_time`, so the model reasonably picked `weather`.

This script parses the Kotlin sources and emits the same JSON the app would send, so the
harness and the app cannot drift. CI regenerates it and fails on a diff, which means
adding or renaming a tool without updating the exported list breaks the build.

Pure source parsing: no Android SDK, no Gradle, no network.

Usage:
    python3 scripts/generate_eval_tools.py            # write eval/tools.json
    python3 scripts/generate_eval_tools.py --check     # exit 1 if it would change
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

OUT_PATH = os.path.join("eval", "tools.json")
TOOL_SCHEMA = "app/src/main/java/com/jarvis/agent/ai/ToolSchema.kt"
DESC_LIMIT = 400  # ToolSchema does description.take(400)

STRING = r'"(?:[^"\\]|\\.)*"'
CONCAT = rf'((?:{STRING}\s*\+?\s*)+)'


def kotlin_string_literal(text: str) -> str:
    """Join a possibly-concatenated Kotlin string expression into one Python string."""
    parts = re.findall(STRING, text)
    out = []
    for raw in parts:
        body = raw[1:-1]
        body = body.replace('\\"', '"').replace("\\\\", "\\").replace("\\n", "\n")
        body = body.replace("\\t", "\t")
        out.append(body)
    return "".join(out)


def repo_files() -> list[str]:
    out = subprocess.run(
        ["git", "grep", "-l", "ToolDefinition(", "--", "app/src"],
        capture_output=True, text=True, check=True,
    ).stdout
    return [f for f in out.split() if f.endswith(".kt")]


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"^\s*//.*$", "", src, flags=re.M)
    return src


def parse_tools() -> list[dict]:
    """Every ToolDefinition registration in the app, with id/name/description/category."""
    tools: dict[str, dict] = {}
    for path in repo_files():
        code = strip_comments(open(path, encoding="utf-8").read())
        for m in re.finditer(r"ToolDefinition\s*\(", code):
            # Take a window from the opening paren; fields always precede the handler.
            window = code[m.end(): m.end() + 4000]
            tid = re.search(rf"\bid\s*=\s*({STRING})", window)
            name = re.search(rf"\bname\s*=\s*({STRING})", window)
            desc = re.search(rf"\bdescription\s*=\s*{CONCAT}", window)
            cat = re.search(rf"\bcategory\s*=\s*({STRING})", window)
            if not (tid and desc and cat):
                continue
            tool_id = kotlin_string_literal(tid.group(1))
            if tool_id in tools:
                # ToolRegistry.register() hard-fails on duplicates at runtime, so seeing
                # one here means the parser matched the same declaration twice.
                continue
            tools[tool_id] = {
                "id": tool_id,
                "name": kotlin_string_literal(name.group(1)) if name else tool_id,
                "description": kotlin_string_literal(desc.group(1)),
                "category": kotlin_string_literal(cat.group(1)),
                "source": os.path.relpath(path),
            }
    return [tools[k] for k in sorted(tools)]


def parse_exposed_categories(schema_src: str) -> set[str]:
    m = re.search(r"EXPOSED_CATEGORIES\s*=\s*setOf\s*\((.*?)\)\s*\n", schema_src, re.S)
    if not m:
        raise SystemExit(f"could not parse EXPOSED_CATEGORIES from {TOOL_SCHEMA}")
    return set(re.findall(r'"([^"]*)"', m.group(1)))


def parse_arg_hints(schema_src: str) -> dict[str, list[str]]:
    m = re.search(r"ARG_HINTS[^=]*=\s*mapOf\s*\((.*?)\n    \)", schema_src, re.S)
    if not m:
        raise SystemExit(f"could not parse ARG_HINTS from {TOOL_SCHEMA}")
    body = m.group(1)
    hints: dict[str, list[str]] = {}
    # Both listOf(...) and emptyList() are valid map values here. This used to match only
    # listOf(), which silently dropped every zero-argument tool; those then fell through to
    # ToolSchema.defaultArgs() and picked up parameters their handlers never read -- e.g.
    # open_recents was advertised with an `app` argument purely because its id contains
    # "open", and press_back/press_home/screen_read all grew a bogus query+text pair.
    for tool_id, value in re.findall(
        r'"([a-z_0-9]+)"\s*to\s*(emptyList\s*\(\s*\)|listOf\s*\([^)]*\))', body
    ):
        hints[tool_id] = re.findall(r'"([^"]*)"', value)
    # Fail loudly rather than drifting again: every declared entry must have been parsed.
    declared = re.findall(r'"([a-z_0-9]+)"\s*to\s*', body)
    missing = [t for t in declared if t not in hints]
    if missing:
        raise SystemExit(f"ARG_HINTS entries the parser does not understand: {missing}")
    return hints


def parse_type_sets(schema_src: str) -> tuple[set[str], set[str], set[str]]:
    def grab(name: str) -> set[str]:
        m = re.search(rf"{name}\s*=\s*setOf\s*\(([^)]*)\)", schema_src, re.S)
        return set(re.findall(r'"([^"]*)"', m.group(1))) if m else set()

    return grab("INTEGER_ARGS"), grab("NUMBER_ARGS"), grab("BOOLEAN_ARGS")


def default_args(tool_id: str) -> list[str]:
    """Mirror of ToolSchema.defaultArgs(). Order of checks matters."""
    if tool_id.startswith("device_") or tool_id.startswith("get_"):
        return []
    if "search" in tool_id:
        return ["query"]
    if "click" in tool_id or "tap" in tool_id:
        return ["text", "target"]
    if "type" in tool_id:
        return ["text"]
    if "send" in tool_id or "reply" in tool_id:
        return ["message"]
    if "open" in tool_id or "launch" in tool_id:
        return ["app"]
    return ["query", "text"]


def parameters_for(tool_id: str, hints: dict[str, list[str]],
                   ints: set[str], nums: set[str], bools: set[str]) -> dict:
    # Kotlin is `ARG_HINTS[toolId] ?: defaultArgs(toolId)` -- elvis falls back only on null.
    # `or` falls back on any falsy value, so an explicit emptyList() hint (a tool that takes
    # no arguments) was thrown away and replaced by defaultArgs() guesses. Must stay `is None`.
    names = hints.get(tool_id)
    if names is None:
        names = default_args(tool_id)
    props = {}
    for n in names:
        if n in ints:
            t = "integer"
        elif n in nums:
            t = "number"
        elif n in bools:
            t = "boolean"
        else:
            t = "string"
        props[n] = {"type": t}
    # Deliberately no "required" array: ToolSchema.parametersFor() does not emit one, and
    # reproducing the app's real payload is the whole point of this generator.
    return {"type": "object", "properties": props}


def build() -> dict:
    schema_src = strip_comments(open(TOOL_SCHEMA, encoding="utf-8").read())
    exposed = parse_exposed_categories(schema_src)
    hints = parse_arg_hints(schema_src)
    ints, nums, bools = parse_type_sets(schema_src)

    all_tools = parse_tools()
    exported = [t for t in all_tools if t["category"] in exposed]
    hidden = [t for t in all_tools if t["category"] not in exposed]

    return {
        "_comment": [
            "GENERATED FILE -- do not edit by hand.",
            "Regenerate with: python3 scripts/generate_eval_tools.py",
            "Mirrors ToolSchema.forOpenAI(): every registered tool whose category is in",
            "ToolSchema.EXPOSED_CATEGORIES, with descriptions truncated to 400 chars and",
            "parameters derived from ARG_HINTS or defaultArgs(). No 'required' arrays,",
            "because the app does not emit any.",
            "CI regenerates this and fails on a diff, so the eval harness cannot drift",
            "away from the tool surface the app actually sends.",
        ],
        "source_of_truth": [TOOL_SCHEMA, "every ToolDefinition registration under app/src"],
        "registered_total": len(all_tools),
        "exposed_total": len(exported),
        "excluded_categories": sorted({t["category"] for t in hidden}),
        "excluded_tools": [{"id": t["id"], "category": t["category"]} for t in hidden],
        "tools": [
            {
                "type": "function",
                "function": {
                    "name": t["id"],
                    "description": t["description"][:DESC_LIMIT],
                    "parameters": parameters_for(t["id"], hints, ints, nums, bools),
                },
                "_category": t["category"],
                "_source": t["source"],
            }
            for t in exported
        ],
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true",
                    help="exit 1 if eval/tools.json differs from what would be generated")
    ap.add_argument("--out", default=OUT_PATH)
    args = ap.parse_args()

    doc = build()
    text = json.dumps(doc, indent=2, sort_keys=False) + "\n"

    if args.check:
        if not os.path.isfile(args.out):
            print(f"::error::{args.out} does not exist. Run scripts/generate_eval_tools.py.")
            return 1
        current = open(args.out, encoding="utf-8").read()
        if current != text:
            print(f"::error::{args.out} is stale: the app's tool surface changed but the "
                  "exported list was not regenerated. Run scripts/generate_eval_tools.py "
                  "and commit the result.")
            import difflib
            for line in list(difflib.unified_diff(
                current.splitlines(), text.splitlines(),
                "committed", "generated", lineterm="", n=1,
            ))[:40]:
                print("   " + line)
            return 1
        print(f"{args.out} is up to date "
              f"({doc['exposed_total']}/{doc['registered_total']} tools exposed).")
        return 0

    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(f"wrote {args.out}: {doc['exposed_total']} exposed of "
          f"{doc['registered_total']} registered tools")
    if doc["excluded_tools"]:
        print("excluded (category not in EXPOSED_CATEGORIES): "
              + ", ".join(t["id"] for t in doc["excluded_tools"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
