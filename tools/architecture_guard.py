#!/usr/bin/env python3
"""AquaLight architecture guard.

Fails CI when lower layers start depending on UI packages.
This keeps persistence, background work, runtime device control and app bootstrap
usable without Fragment/View/ViewModel dependencies.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
GUARDED_DIRS = [
    SOURCE_ROOT / "app",
    SOURCE_ROOT / "base",
    SOURCE_ROOT / "data",
]
FORBIDDEN_IMPORT = re.compile(r"^import\s+com\.aqua\.aqualight\.ui(?:\.|$)", re.MULTILINE)

errors: list[str] = []

for guarded_dir in GUARDED_DIRS:
    if not guarded_dir.exists():
        continue
    for kotlin_file in guarded_dir.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        if FORBIDDEN_IMPORT.search(text):
            rel = kotlin_file.relative_to(ROOT)
            errors.append(f"{rel}: data/app/base layer must not import com.aqua.aqualight.ui.*")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.exists():
    manifest_text = manifest.read_text(encoding="utf-8", errors="ignore")
    old_receivers = [
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskReminderReceiver",
        "com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskBootReceiver",
    ]
    for receiver in old_receivers:
        if receiver in manifest_text:
            errors.append(
                f"{manifest.relative_to(ROOT)}: receiver still points to UI package: {receiver}"
            )

if errors:
    print("Architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("Architecture guard passed: guarded layers do not depend on UI packages.")
