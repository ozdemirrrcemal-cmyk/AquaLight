#!/usr/bin/env python3
"""Reject rendered-resource drift in the Stage 11 zero-visual-regression PR."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VISUAL_VALUE_FILES = {
    "colors.xml",
    "dimens.xml",
    "styles.xml",
    "themes.xml",
    "card_colors.xml",
    "component_semantic_colors.xml",
}
ALLOWED_LAYOUT_ATTRIBUTE_PREFIXES = (
    "android:contentDescription=",
    "android:importantForAccessibility=",
    "android:accessibilityLiveRegion=",
    "android:screenReaderFocusable=",
    "android:accessibilityHeading=",
    "android:labelFor=",
)


def git(*args: str) -> str:
    return subprocess.check_output(
        ["git", *args],
        cwd=ROOT,
        text=True,
        stderr=subprocess.STDOUT,
    )


def resolve_base() -> str | None:
    explicit = os.environ.get("STAGE11_BASE_REF", "").strip()
    if explicit:
        return explicit

    base_branch = os.environ.get("GITHUB_BASE_REF", "").strip()
    if base_branch:
        remote_ref = f"origin/{base_branch}"
        try:
            git("rev-parse", "--verify", remote_ref)
            return remote_ref
        except subprocess.CalledProcessError:
            return None

    return None


def changed_files(base: str) -> list[str]:
    output = git("diff", "--name-only", f"{base}...HEAD")
    return [line.strip() for line in output.splitlines() if line.strip()]


def layout_patch(base: str, path: str) -> str:
    return git("diff", "--unified=0", f"{base}...HEAD", "--", path)


def validate_layout_patch(base: str, path: str) -> list[str]:
    errors: list[str] = []
    for line in layout_patch(base, path).splitlines():
        if line.startswith(("+++", "---", "@@")):
            continue
        if not line.startswith(("+", "-")):
            continue

        changed = line[1:].strip()
        if not changed or changed.startswith("<!--") or changed.startswith("-->"):
            continue
        if any(changed.startswith(prefix) for prefix in ALLOWED_LAYOUT_ATTRIBUTE_PREFIXES):
            continue

        errors.append(f"{path}: visual XML line changed: {changed}")
    return errors


def main() -> int:
    base = resolve_base()
    if base is None:
        print("STAGE11_VISUAL_DIFF_GUARD_SKIP: base ref unavailable")
        return 0

    errors: list[str] = []
    for path in changed_files(base):
        file_path = Path(path)
        parts = file_path.parts

        if path.startswith("app/src/main/res/layout/") and path.endswith(".xml"):
            errors.extend(validate_layout_patch(base, path))
            continue

        if path.startswith("app/src/main/res/drawable"):
            errors.append(f"{path}: drawable changes are forbidden in Stage 11")
            continue

        if path.startswith("app/src/main/res/values") and file_path.name in VISUAL_VALUE_FILES:
            errors.append(f"{path}: visual value changes are forbidden in Stage 11")
            continue

        if path.startswith("app/src/main/res/font/"):
            errors.append(f"{path}: font changes are forbidden in Stage 11")

    if errors:
        print("STAGE11_VISUAL_DIFF_GUARD_FAILED", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("STAGE11_VISUAL_DIFF_GUARD_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
