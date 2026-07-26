#!/usr/bin/env python3
"""Validate AquaLight GitHub Actions runtime and Android SDK pins.

This guard replaces the setup-android-specific pin check that was coupled to the
legacy Node 20 action SHA in architecture_guard.py. It deliberately scans only
workflow YAML, because action runtime warnings are produced by executable
``uses:`` entries rather than documentation or historical guard constants.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_DIR = ROOT / ".github/workflows"

OLD_ACTION_REFS = (
    "actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
    "gradle/wrapper-validation-action@b5418f5a58f5fd2eb486dd7efb368fe7be7eae45",
    "android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407",
    "actions/cache@0057852bfaa89a56745cba8c7296529d2fc39830",
    "actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0",
)

EXPECTED_ACTION_REFS = {
    "checkout": "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd",
    "wrapper-validation": (
        "gradle/actions/wrapper-validation@"
        "3f131e8634966bd73d06cc69884922b02e6faf92"
    ),
    "setup-android": (
        "android-actions/setup-android@"
        "40fd30fb8d7440372e1316f5d1809ec01dcd3699"
    ),
    "cache": "actions/cache@27d5ce7f107fe9357f9df03efb73ab90386fccae",
    "download-artifact": (
        "actions/download-artifact@"
        "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c"
    ),
}

MINIMUM_COUNTS = {
    "checkout": 8,
    "wrapper-validation": 5,
    "setup-android": 6,
    "cache": 1,
    "download-artifact": 1,
}

CMDLINE_TOOLS_PIN = 'cmdline-tools-version: "15859902"'
FORBIDDEN_MUTABLE_ANDROID_INPUTS = (
    "cmdline-tools;latest",
    "sdkmanager --update",
)


def main() -> int:
    errors: list[str] = []
    totals = {name: 0 for name in EXPECTED_ACTION_REFS}
    workflows = sorted(WORKFLOW_DIR.glob("*.yml"))

    if not workflows:
        errors.append(".github/workflows contains no YAML workflows")

    for workflow in workflows:
        text = workflow.read_text(encoding="utf-8", errors="ignore")
        relative = workflow.relative_to(ROOT)

        for old_ref in OLD_ACTION_REFS:
            if old_ref in text:
                errors.append(f"{relative}: deprecated Node 20 action pin remains: {old_ref}")

        for name, expected_ref in EXPECTED_ACTION_REFS.items():
            totals[name] += text.count(expected_ref)

        setup_count = text.count(EXPECTED_ACTION_REFS["setup-android"])
        if setup_count:
            pin_count = text.count(CMDLINE_TOOLS_PIN)
            if pin_count != setup_count:
                errors.append(
                    f"{relative}: each setup-android use must retain reviewed pin "
                    f"{CMDLINE_TOOLS_PIN}; setup={setup_count}, pins={pin_count}"
                )

        for token in FORBIDDEN_MUTABLE_ANDROID_INPUTS:
            if token in text:
                errors.append(f"{relative}: mutable Android SDK operation is forbidden: {token}")

    for name, minimum in MINIMUM_COUNTS.items():
        actual = totals[name]
        if actual < minimum:
            errors.append(
                f"Expected at least {minimum} pinned {name} action uses, found {actual}"
            )

    if errors:
        print("Node 24 action guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    counts = ", ".join(f"{name}={count}" for name, count in sorted(totals.items()))
    print(f"Node 24 action guard passed ({counts}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
