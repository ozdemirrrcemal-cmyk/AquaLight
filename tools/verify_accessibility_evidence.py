#!/usr/bin/env python3
"""Validate Stage 14 visual-profile and automated accessibility evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
from typing import Any
import zlib

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
PACKAGE_NAME = "com.aqua.aqualight.smoke"
SUPPORTED_API_LEVELS = (27, 37)
PROFILES = (
    "light",
    "dark",
    "large-font-light",
    "large-font-dark",
    "rtl-light",
    "rtl-dark",
)
SCREENS = ("aquarium", "aquariummaintenance", "devices", "settings")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MIN_PNG_BYTES = 1024
MIN_SCREEN_WIDTH = 320
MIN_SCREEN_HEIGHT = 480


class AccessibilityEvidenceFailure(ValueError):
    """Raised when accessibility or visual-profile evidence is incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", required=True, type=Path)
    parser.add_argument("--screens", required=True, type=Path)
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def read_bytes(path: Path, label: str) -> bytes:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise AccessibilityEvidenceFailure(
            f"cannot read {label} {path}: {error}"
        ) from error
    if not raw:
        raise AccessibilityEvidenceFailure(f"{label} is empty: {path}")
    return raw


def read_text(path: Path, label: str) -> tuple[str, str]:
    raw = read_bytes(path, label)
    return raw.decode("utf-8", errors="replace"), hashlib.sha256(raw).hexdigest()


def parse_png(path: Path) -> dict[str, Any]:
    raw = read_bytes(path, "profile screenshot")
    if len(raw) < MIN_PNG_BYTES:
        raise AccessibilityEvidenceFailure(
            f"profile screenshot is smaller than {MIN_PNG_BYTES} bytes: {path}"
        )
    if not raw.startswith(PNG_SIGNATURE):
        raise AccessibilityEvidenceFailure(f"screenshot is not a PNG: {path}")

    offset = len(PNG_SIGNATURE)
    width = 0
    height = 0
    chunk_types: list[bytes] = []
    while offset < len(raw):
        if offset + 12 > len(raw):
            raise AccessibilityEvidenceFailure(f"truncated PNG chunk: {path}")
        length = struct.unpack(">I", raw[offset : offset + 4])[0]
        chunk_type = raw[offset + 4 : offset + 8]
        data_start = offset + 8
        data_end = data_start + length
        crc_end = data_end + 4
        if crc_end > len(raw):
            raise AccessibilityEvidenceFailure(f"truncated PNG payload: {path}")
        expected_crc = struct.unpack(">I", raw[data_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_type + raw[data_start:data_end]) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise AccessibilityEvidenceFailure(f"PNG checksum mismatch: {path}")
        if not chunk_types and (chunk_type != b"IHDR" or length != 13):
            raise AccessibilityEvidenceFailure(f"PNG must begin with IHDR: {path}")
        if chunk_type == b"IHDR":
            width, height = struct.unpack(">II", raw[data_start : data_start + 8])
        chunk_types.append(chunk_type)
        offset = crc_end
        if chunk_type == b"IEND":
            break

    if offset != len(raw) or not chunk_types or chunk_types[-1] != b"IEND":
        raise AccessibilityEvidenceFailure(f"PNG has missing or trailing chunks: {path}")
    if b"IDAT" not in chunk_types:
        raise AccessibilityEvidenceFailure(f"PNG has no image data: {path}")
    if width < MIN_SCREEN_WIDTH or height < MIN_SCREEN_HEIGHT:
        raise AccessibilityEvidenceFailure(
            f"PNG dimensions are below the commercial minimum: {path} ({width}x{height})"
        )
    return {
        "file": path.name,
        "sha256": hashlib.sha256(raw).hexdigest(),
        "bytes": len(raw),
        "width": width,
        "height": height,
    }


def validate_profile(prefix: Path, screens: Path, profile: str) -> dict[str, Any]:
    launch_path = Path(f"{prefix}-{profile}-start.txt")
    window_path = Path(f"{prefix}-{profile}-window.xml")
    logcat_path = Path(f"{prefix}-{profile}-logcat.txt")
    launch, launch_sha = read_text(launch_path, f"{profile} launch log")
    window, window_sha = read_text(window_path, f"{profile} window dump")
    logcat, logcat_sha = read_text(logcat_path, f"{profile} logcat")

    if not re.search(r"(?m)^Status:\s*ok\s*$", launch):
        raise AccessibilityEvidenceFailure(f"{profile} Activity launch was not successful")
    if f"RELEASE_SMOKE_PASS:{profile}" not in window:
        raise AccessibilityEvidenceFailure(f"{profile} did not expose its pass marker")
    if f"ANR in {PACKAGE_NAME}" in logcat:
        raise AccessibilityEvidenceFailure(f"{profile} produced an ANR")
    if "FATAL EXCEPTION" in logcat and f"Process: {PACKAGE_NAME}" in logcat:
        raise AccessibilityEvidenceFailure(
            f"{profile} produced an AndroidRuntime crash"
        )

    screenshot_evidence = [
        parse_png(screens / f"{profile}-{screen}.png")
        for screen in SCREENS
    ]
    return {
        "id": profile,
        "checks": {
            "activityLaunchSucceeded": True,
            "iconAccessibilityPassedInsideCandidate": True,
            "largeFontClippingCheckPassedInsideCandidate": True,
            "layoutDirectionCheckPassedInsideCandidate": True,
            "noCrashOrAnr": True,
            "allScreensCaptured": True,
        },
        "evidence": {
            "launchSha256": launch_sha,
            "windowSha256": window_sha,
            "logcatSha256": logcat_sha,
            "screenshots": screenshot_evidence,
        },
    }


def require_profile_differences(profiles: list[dict[str, Any]]) -> None:
    hashes = {
        profile["id"]: {
            screenshot["file"].removeprefix(f"{profile['id']}-"): screenshot["sha256"]
            for screenshot in profile["evidence"]["screenshots"]
        }
        for profile in profiles
    }
    comparisons = (
        ("light", "dark"),
        ("light", "large-font-light"),
        ("dark", "large-font-dark"),
        ("light", "rtl-light"),
        ("dark", "rtl-dark"),
    )
    for first, second in comparisons:
        unchanged = sorted(
            screen
            for screen in SCREENS
            if hashes[first][f"{screen}.png"] == hashes[second][f"{screen}.png"]
        )
        if unchanged:
            raise AccessibilityEvidenceFailure(
                f"{first} and {second} screenshots are byte-identical for {unchanged}"
            )


def validate(
    prefix: Path,
    screens: Path,
    api_level: int,
    commit: str,
) -> dict[str, Any]:
    if api_level not in SUPPORTED_API_LEVELS:
        raise AccessibilityEvidenceFailure(
            f"api-level must be one of {SUPPORTED_API_LEVELS}, got {api_level}"
        )
    if not COMMIT_PATTERN.fullmatch(commit):
        raise AccessibilityEvidenceFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    if not screens.is_dir() or screens.is_symlink():
        raise AccessibilityEvidenceFailure(f"screenshot directory is missing or unsafe: {screens}")

    expected_files = {
        f"{profile}-{screen}.png"
        for profile in PROFILES
        for screen in SCREENS
    }
    actual_files = {path.name for path in screens.glob("*.png") if path.is_file()}
    if actual_files != expected_files:
        missing = sorted(expected_files - actual_files)
        unknown = sorted(actual_files - expected_files)
        raise AccessibilityEvidenceFailure(
            f"screenshot set mismatch; missing={missing}, unknown={unknown}"
        )

    profiles = [
        validate_profile(prefix, screens, profile)
        for profile in PROFILES
    ]
    require_profile_differences(profiles)
    dimensions = {
        (screenshot["width"], screenshot["height"])
        for profile in profiles
        for screenshot in profile["evidence"]["screenshots"]
    }
    if len(dimensions) != 1:
        raise AccessibilityEvidenceFailure(
            f"profile screenshot dimensions are inconsistent: {sorted(dimensions)}"
        )

    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": "accessibility-profiles",
        "evidenceSet": "visual-accessibility-profiles",
        "releaseCommit": commit,
        "apiLevel": api_level,
        "packageName": PACKAGE_NAME,
        "profileCount": len(profiles),
        "screenshotCount": len(expected_files),
        "dimensions": {
            "width": next(iter(dimensions))[0],
            "height": next(iter(dimensions))[1],
        },
        "profiles": profiles,
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        summary = validate(
            args.prefix,
            args.screens,
            args.api_level,
            args.commit,
        )
    except AccessibilityEvidenceFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "suite": "accessibility-profiles",
                "evidenceSet": "visual-accessibility-profiles",
                "releaseCommit": args.commit,
                "apiLevel": args.api_level,
                "failure": str(error),
            },
        )
        print(f"Accessibility evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Accessibility commercial gate passed: "
        f"API {args.api_level}, {summary['profileCount']} profiles, "
        f"{summary['screenshotCount']} screenshots."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
