from __future__ import annotations

import struct
import sys
import tempfile
import unittest
from pathlib import Path
import zlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_accessibility_evidence import (
    AccessibilityEvidenceFailure,
    PROFILES,
    SCREENS,
    validate,
)

COMMIT = "f" * 40
PACKAGE_NAME = "com.aqua.aqualight.smoke"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    checksum = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    return (
        struct.pack(">I", len(data))
        + chunk_type
        + data
        + struct.pack(">I", checksum)
    )


def fake_png(seed: str) -> bytes:
    width = 320
    height = 480
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    rows = b"".join(b"\x00" + (b"\x00" * width * 4) for _ in range(height))
    text = (f"profile={seed};" + ("x" * 1400)).encode("utf-8")
    return (
        PNG_SIGNATURE
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"tEXt", text)
        + png_chunk(b"IDAT", zlib.compress(rows))
        + png_chunk(b"IEND", b"")
    )


class AccessibilityEvidenceTest(unittest.TestCase):
    def evidence(self, directory: Path) -> tuple[Path, Path]:
        prefix = directory / "release-smoke-api-27"
        screens = directory / "screens"
        screens.mkdir()
        for profile in PROFILES:
            Path(f"{prefix}-{profile}-start.txt").write_text(
                "Status: ok\n"
                "Activity: com.aqua.aqualight.smoke/"
                "com.aqua.aqualight.smoke.ReleaseSmokeActivity\n",
                encoding="utf-8",
            )
            Path(f"{prefix}-{profile}-window.xml").write_text(
                f'<node text="RELEASE_SMOKE_PASS:{profile}" />\n',
                encoding="utf-8",
            )
            Path(f"{prefix}-{profile}-logcat.txt").write_text(
                f"I AquaLight: {profile} complete\n",
                encoding="utf-8",
            )
            for screen in SCREENS:
                (screens / f"{profile}-{screen}.png").write_bytes(
                    fake_png(f"{profile}-{screen}")
                )
        return prefix, screens

    def test_api_27_profiles_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            summary = validate(prefix, screens, 27, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(6, summary["profileCount"])
        self.assertEqual(24, summary["screenshotCount"])

    def test_api_37_profiles_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            summary = validate(prefix, screens, 37, COMMIT)

        self.assertEqual(37, summary["apiLevel"])

    def test_missing_screenshot_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            (screens / "rtl-dark-settings.png").unlink()

            with self.assertRaisesRegex(AccessibilityEvidenceFailure, "mismatch"):
                validate(prefix, screens, 27, COMMIT)

    def test_byte_identical_profile_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            (screens / "dark-aquarium.png").write_bytes(
                (screens / "light-aquarium.png").read_bytes()
            )

            with self.assertRaisesRegex(
                AccessibilityEvidenceFailure,
                "byte-identical",
            ):
                validate(prefix, screens, 27, COMMIT)

    def test_corrupt_png_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            screenshot = screens / "large-font-light-devices.png"
            raw = bytearray(screenshot.read_bytes())
            raw[-5] ^= 0xFF
            screenshot.write_bytes(raw)

            with self.assertRaisesRegex(
                AccessibilityEvidenceFailure,
                "checksum mismatch",
            ):
                validate(prefix, screens, 27, COMMIT)

    def test_profile_crash_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            Path(f"{prefix}-rtl-light-logcat.txt").write_text(
                "FATAL EXCEPTION: main\n"
                f"Process: {PACKAGE_NAME}, PID: 99\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                AccessibilityEvidenceFailure,
                "AndroidRuntime crash",
            ):
                validate(prefix, screens, 27, COMMIT)

    def test_wrong_marker_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            Path(f"{prefix}-large-font-dark-window.xml").write_text(
                '<node text="RELEASE_SMOKE_FAIL" />\n',
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                AccessibilityEvidenceFailure,
                "pass marker",
            ):
                validate(prefix, screens, 27, COMMIT)

    def test_api_and_commit_are_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix, screens = self.evidence(Path(temporary))
            with self.assertRaisesRegex(AccessibilityEvidenceFailure, "api-level"):
                validate(prefix, screens, 35, COMMIT)
            with self.assertRaisesRegex(AccessibilityEvidenceFailure, "40-character"):
                validate(prefix, screens, 27, "abc")


if __name__ == "__main__":
    unittest.main()
