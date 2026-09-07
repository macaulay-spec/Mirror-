#!/usr/bin/env python3
"""
Room migration guard.

Enforces the policy documented in `app/src/main/java/com/jarvis/app/memory/Migrations.kt`.

Why this exists
---------------
`AppDatabase` used to be built with `fallbackToDestructiveMigration()` and declared
`exportSchema = false`. Bumping the schema version therefore deleted every memory,
conversation, person, place and habit the user had accumulated -- silently, and with no
exported history to write a migration from. The blanket fallback is what made that
possible, so this script fails the build if it comes back, and fails the build if a
version bump has no migration path.

Checks
------
1. `@Database` declares `exportSchema = true`.
2. No blanket `fallbackToDestructiveMigration(` call. Destruction must be scoped
   (`fallbackToDestructiveMigrationFrom` / `...OnDowngrade`) so it can only ever fire for
   the versions it was written for.
3. `AppDatabase.EXPORTED_BASELINE` == `Migrations.BASELINE_VERSION`.
4. A migration path exists from the baseline to the declared version, assembled from
   `Migration(from, to)` objects, the `Migrations.addColumn(from = , to = )` helper, and
   `AutoMigration(from = , to = )` in the annotation.
5. With `--check-schemas DIR` (CI runs this after a build): a schema JSON exists for every
   version from the baseline to the declared version.

Runs on plain source text -- no Android SDK, no Gradle, no network -- so it works on a
laptop and in CI identically.

Exit code 0 = policy satisfied, 1 = violation.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import deque

APP_DB = "app/src/main/java/com/jarvis/app/memory/AppDatabase.kt"
MIGRATIONS = "app/src/main/java/com/jarvis/app/memory/Migrations.kt"
SCHEMA_PACKAGE_DIR = "com.jarvis.app.memory.AppDatabase"


def fail(msg: str) -> None:
    print(f"::error::{msg}" if os.environ.get("GITHUB_ACTIONS") else f"FAIL: {msg}")


def warn(msg: str) -> None:
    print(f"::warning::{msg}" if os.environ.get("GITHUB_ACTIONS") else f"WARN: {msg}")


def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def strip_comments(src: str) -> str:
    """Remove // and /* */ comments so documentation examples are not parsed as code.

    Migrations.kt deliberately contains a `Migration(5, 6)` snippet inside a KDoc block to
    show how to add one. Counting that as a real migration would let a version bump pass
    the guard with no actual migration registered.
    """
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"^\s*//.*$", "", src, flags=re.M)
    return src


def declared_version(db_src: str) -> int:
    m = re.search(r"@Database\s*\((.*?)\)\s*\nabstract class", db_src, re.S)
    block = m.group(1) if m else db_src
    v = re.search(r"\bversion\s*=\s*(\d+)", block)
    if not v:
        raise SystemExit(f"could not find `version = N` in the @Database annotation of {APP_DB}")
    return int(v.group(1))


def migration_edges(*sources: str) -> dict[int, set[int]]:
    """Build the from -> {to} graph from every migration declaration we understand."""
    edges: dict[int, set[int]] = {}

    def add(a: int, b: int) -> None:
        edges.setdefault(a, set()).add(b)

    for src in sources:
        code = strip_comments(src)
        # object : Migration(5, 6)  /  Migration(5, 6) { ... }
        for a, b in re.findall(r"\bMigration\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)", code):
            add(int(a), int(b))
        # Migrations.addColumn(from = 5, to = 6, ...)
        for a, b in re.findall(r"\baddColumn\s*\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)", code):
            add(int(a), int(b))
        # AutoMigration(from = 5, to = 6)
        for a, b in re.findall(r"\bAutoMigration\s*\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)", code):
            add(int(a), int(b))
    return edges


def path_exists(edges: dict[int, set[int]], start: int, goal: int) -> list[int] | None:
    if start == goal:
        return [start]
    prev: dict[int, int] = {}
    seen = {start}
    queue = deque([start])
    while queue:
        node = queue.popleft()
        for nxt in sorted(edges.get(node, ())):
            if nxt in seen:
                continue
            seen.add(nxt)
            prev[nxt] = node
            if nxt == goal:
                route = [goal]
                while route[-1] != start:
                    route.append(prev[route[-1]])
                return list(reversed(route))
            queue.append(nxt)
    return None


def const_int(src: str, name: str) -> int | None:
    m = re.search(rf"const val {re.escape(name)}\s*=\s*(\d+)", src)
    return int(m.group(1)) if m else None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--check-schemas",
        metavar="DIR",
        help="also require an exported schema JSON for every version from the baseline up "
             "(pass app/schemas; only meaningful after a build)",
    )
    ap.add_argument("--root", default=".", help="repository root")
    args = ap.parse_args()

    os.chdir(args.root)
    problems: list[str] = []

    db_src = read(APP_DB)
    mig_src = read(MIGRATIONS)

    version = declared_version(db_src)
    baseline = const_int(mig_src, "BASELINE_VERSION")
    exported = const_int(db_src, "EXPORTED_BASELINE")

    print(f"Room schema: version={version} baseline={baseline} exported_baseline={exported}")

    # 1. schemas must be exported, or there is no history to migrate from
    if not re.search(r"exportSchema\s*=\s*true", strip_comments(db_src)):
        problems.append(
            f"{APP_DB}: @Database must declare exportSchema = true. Without exported "
            "schemas there is no history to write migrations against, which is how every "
            "version bump ended up wiping user data."
        )

    # 2. no blanket destructive fallback
    if re.search(r"\.fallbackToDestructiveMigration\s*\(", strip_comments(db_src)):
        problems.append(
            f"{APP_DB}: blanket fallbackToDestructiveMigration() is banned. It silently "
            "deletes all user data whenever a migration is missing. Use "
            "fallbackToDestructiveMigrationFrom(true, <versions>) scoped to the versions "
            "that genuinely cannot be migrated."
        )

    # 3. the two baseline constants must agree
    if baseline is None or exported is None:
        problems.append("could not read BASELINE_VERSION / EXPORTED_BASELINE")
    elif baseline != exported:
        problems.append(
            f"Migrations.BASELINE_VERSION ({baseline}) != AppDatabase.EXPORTED_BASELINE "
            f"({exported}). These describe the same thing and must not drift."
        )

    # 4. migration coverage
    if baseline is not None and version > baseline:
        edges = migration_edges(mig_src, db_src)
        route = path_exists(edges, baseline, version)
        if route is None:
            have = ", ".join(f"{a}->{b}" for a, ts in sorted(edges.items()) for b in sorted(ts))
            problems.append(
                f"No migration path from {baseline} to {version}. Room will throw "
                "IllegalStateException at first open and JARVIS will be unusable until a "
                "migration ships. Known edges: "
                + (have or "(none)")
                + ". See Migrations.kt for how to add one."
            )
        else:
            print("migration path: " + " -> ".join(str(v) for v in route))
    elif version < (baseline or 0):
        problems.append(
            f"declared version {version} is below the exported baseline {baseline}; "
            "a downgrade would be destructive for every existing install."
        )

    # 5. exported schema files
    if args.check_schemas:
        schema_dir = os.path.join(args.check_schemas, SCHEMA_PACKAGE_DIR)
        if not os.path.isdir(schema_dir):
            problems.append(
                f"{schema_dir} does not exist. KSP did not export schemas -- check that "
                "`ksp { arg(\"room.schemaLocation\", ...) }` is set in app/build.gradle.kts."
            )
        else:
            for v in range(baseline or version, version + 1):
                if not os.path.isfile(os.path.join(schema_dir, f"{v}.json")):
                    problems.append(f"missing exported schema {schema_dir}/{v}.json")
            on_disk = sorted(
                int(f[:-5]) for f in os.listdir(schema_dir) if re.fullmatch(r"\d+\.json", f)
            )
            print(f"schemas on disk: {on_disk}")
            if not on_disk:
                problems.append("no schema JSON found at all")
    else:
        warn("skipping exported-schema file check (pass --check-schemas after a build)")

    for p in problems:
        fail(p)
    if problems:
        print(f"\n{len(problems)} problem(s).")
        return 1
    print("Room migration policy OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
