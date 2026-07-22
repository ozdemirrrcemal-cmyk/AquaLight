#!/usr/bin/env python3
"""Fail closed for Google Play production publication without blocking release builds."""

from __future__ import annotations

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CHECKLIST = ROOT / "docs/commercial/privacy-legal-release-checklist.md"
LEGAL_ASSETS = (
    ROOT / "app/src/main/assets/privacy_policy_en.html",
    ROOT / "app/src/main/assets/privacy_policy_tr.html",
    ROOT / "app/src/main/assets/terms_of_use_en.html",
    ROOT / "app/src/main/assets/terms_of_use_tr.html",
)
SECURE_WEB_SOURCE = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/ui/common/web/SecureLocalWebContent.kt"
)
PENDING_MARKER = "AQL_GOOGLE_PLAY_PUBLICATION_PENDING"
EMPTY_PUBLICATION_FIELD = re.compile(
    r'<span\s+data-aql-publication-field="[^"]+"\s*>\s*</span>',
    re.IGNORECASE,
)


def main() -> int:
    failures: list[str] = []

    checklist_text = CHECKLIST.read_text(encoding="utf-8")
    open_items = [
        line.strip()[6:].strip()
        for line in checklist_text.splitlines()
        if line.strip().startswith("- [ ] ")
    ]
    if open_items:
        failures.append(f"{len(open_items)} mandatory checklist item(s) remain open")
        failures.extend(f"open checklist item: {item}" for item in open_items)

    for asset in LEGAL_ASSETS:
        asset_text = asset.read_text(encoding="utf-8")
        relative = asset.relative_to(ROOT)
        if PENDING_MARKER in asset_text:
            failures.append(f"pending publication marker remains: {relative}")
        if EMPTY_PUBLICATION_FIELD.search(asset_text):
            failures.append(f"empty controller/contact publication field remains: {relative}")

    secure_web_text = SECURE_WEB_SOURCE.read_text(encoding="utf-8")
    if 'PUBLICATION_SUPPORT_EMAIL = ""' in secure_web_text:
        failures.append("secure WebView publication support-email allowlist is empty")

    if failures:
        print("Google Play production publication is blocked:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        print(
            "Release builds remain permitted for internal testing; do not upload this artifact "
            "to Google Play production.",
            file=sys.stderr,
        )
        return 1

    print("Google Play production publication guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
