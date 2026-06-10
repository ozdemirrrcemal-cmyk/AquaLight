#!/usr/bin/env python3
"""Static navigation contract guard for AquaLight.

This guard prevents returning to the old pattern of navigating with raw
R.id.action_* values from Kotlin. Navigation actions should be expressed with
Safe Args Directions so argument names and required values are checked at
compile time.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app" / "src" / "main" / "java"
violations = []

for path in SOURCE_ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if "R.id.action_" in text:
        violations.append(path.relative_to(ROOT))

if violations:
    print("Navigation guard failed: raw R.id.action_* navigation references remain:")
    for path in violations:
        print(f" - {path}")
    sys.exit(1)

print("Navigation guard passed: Safe Args action references are enforced.")
