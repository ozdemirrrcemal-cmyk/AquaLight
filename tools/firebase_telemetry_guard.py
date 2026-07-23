#!/usr/bin/env python3
"""Fail CI if an unapproved Firebase or telemetry runtime is introduced."""

from __future__ import annotations

from pathlib import Path
import argparse
import sys


ROOT = Path(__file__).resolve().parents[1]
APP_GRADLE = ROOT / "app" / "build.gradle"
SETTINGS_GRADLE = ROOT / "settings.gradle"
SOURCE_ROOT = ROOT / "app" / "src"

REQUIRED_RUNTIME_COORDINATES = (
    "com.google.firebase:firebase-auth",
    "com.google.firebase:firebase-firestore",
)

FORBIDDEN_GRADLE_TOKENS = (
    "com.google.firebase:firebase-analytics",
    "com.google.firebase:firebase-database",
    "com.google.firebase:firebase-messaging",
    "com.google.firebase:firebase-config",
    "com.google.firebase:firebase-perf",
    "com.google.firebase:firebase-crashlytics",
    "com.google.firebase.crashlytics",
    "com.google.firebase.firebase-perf",
)

FORBIDDEN_SOURCE_TOKENS = (
    "com.google.firebase.analytics",
    "com.google.firebase.database",
    "com.google.firebase.messaging",
    "com.google.firebase.remoteconfig",
    "com.google.firebase.perf",
    "com.google.firebase.crashlytics",
)

FORBIDDEN_MERGED_MANIFEST_TOKENS = (
    "com.google.firebase.analytics",
    "com.google.firebase.crashlytics",
    "com.google.firebase.perf",
    "com.google.firebase.messaging",
    "com.google.firebase.database",
    "com.google.firebase.remoteconfig",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--scan-build-output",
        action="store_true",
        help="also require and inspect Android merged manifests after a build",
    )
    args = parser.parse_args()
    failures: list[str] = []
    gradle_text = "\n".join(
        (
            APP_GRADLE.read_text(encoding="utf-8"),
            SETTINGS_GRADLE.read_text(encoding="utf-8"),
        )
    )

    for coordinate in REQUIRED_RUNTIME_COORDINATES:
        if coordinate not in gradle_text:
            failures.append(f"required Firebase runtime is missing: {coordinate}")

    for token in FORBIDDEN_GRADLE_TOKENS:
        if token in gradle_text:
            failures.append(f"forbidden Firebase/telemetry Gradle token: {token}")

    for path in SOURCE_ROOT.rglob("*"):
        if path.suffix not in {".kt", ".java", ".xml"} or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for token in FORBIDDEN_SOURCE_TOKENS:
            if token in text:
                failures.append(
                    f"forbidden Firebase/telemetry source token {token}: "
                    f"{path.relative_to(ROOT)}"
                )

    if args.scan_build_output:
        manifests = sorted(
            (ROOT / "app" / "build" / "intermediates").rglob("AndroidManifest.xml")
        )
        if not manifests:
            failures.append("no merged Android manifest was found after the build")
        for path in manifests:
            text = path.read_text(encoding="utf-8")
            for token in FORBIDDEN_MERGED_MANIFEST_TOKENS:
                if token in text:
                    failures.append(
                        f"forbidden merged-manifest telemetry token {token}: "
                        f"{path.relative_to(ROOT)}"
                    )

    if failures:
        print("Firebase telemetry policy guard failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(
        "Firebase telemetry policy guard passed: runtime is limited to "
        "Auth and Firestore."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
