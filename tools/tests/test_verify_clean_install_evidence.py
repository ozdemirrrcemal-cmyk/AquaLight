from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_clean_install_evidence import (
    CleanInstallFailure,
    REQUIRED_CHECKS,
    REQUIRED_COUNTS,
    validate,
)

COMMIT = "b" * 40


class CleanInstallEvidenceTest(unittest.TestCase):
    def evidence_files(self, directory: Path, api_level: int = 27) -> tuple[Path, ...]:
        activity = directory / "activity.json"
        install = directory / "install.txt"
        launch = directory / "launch.txt"
        window = directory / "window.xml"
        logcat = directory / "logcat.txt"
        activity.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "passed": True,
                    "packageName": "com.aqua.aqualight.smoke",
                    "versionName": "1.2.3-smoke",
                    "versionCode": 42,
                    "apiLevel": api_level,
                    "checks": {name: True for name in REQUIRED_CHECKS},
                    "counts": {name: 0 for name in REQUIRED_COUNTS},
                }
            ),
            encoding="utf-8",
        )
        install.write_text("Performing Streamed Install\nSuccess\n", encoding="utf-8")
        launch.write_text(
            "Status: ok\nActivity: com.aqua.aqualight.smoke/.CleanInstallSmokeActivity\n",
            encoding="utf-8",
        )
        window.write_text(
            '<node text="CLEAN_INSTALL_PASS" content-desc="CLEAN_INSTALL_PASS" />\n',
            encoding="utf-8",
        )
        logcat.write_text("I AquaLight: clean install complete\n", encoding="utf-8")
        return activity, install, launch, window, logcat

    def test_api_27_clean_install_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence_files(Path(temporary), 27)

            summary = validate(*paths, 27, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertFalse(summary["candidate"]["debuggable"])
        self.assertEqual(0, sum(summary["counts"].values()))

    def test_api_37_clean_install_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence_files(Path(temporary), 37)

            summary = validate(*paths, 37, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(37, summary["apiLevel"])

    def test_nonzero_private_state_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            paths = self.evidence_files(directory)
            evidence = json.loads(paths[0].read_text(encoding="utf-8"))
            evidence["counts"]["knownDevices"] = 1
            paths[0].write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CleanInstallFailure, "non-zero"):
                validate(*paths, 27, COMMIT)

    def test_debuggable_candidate_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            paths = self.evidence_files(directory)
            evidence = json.loads(paths[0].read_text(encoding="utf-8"))
            evidence["checks"]["nonDebuggable"] = False
            paths[0].write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CleanInstallFailure, "nonDebuggable"):
                validate(*paths, 27, COMMIT)

    def test_failed_install_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence_files(Path(temporary))
            paths[1].write_text("Failure [INSTALL_FAILED]\n", encoding="utf-8")

            with self.assertRaisesRegex(CleanInstallFailure, "not installed"):
                validate(*paths, 27, COMMIT)

    def test_candidate_crash_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence_files(Path(temporary))
            paths[4].write_text(
                "FATAL EXCEPTION: main\n"
                "Process: com.aqua.aqualight.smoke, PID: 123\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(CleanInstallFailure, "AndroidRuntime crash"):
                validate(*paths, 27, COMMIT)

    def test_noncanonical_commit_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence_files(Path(temporary))

            with self.assertRaisesRegex(CleanInstallFailure, "40-character"):
                validate(*paths, 27, "abc")


if __name__ == "__main__":
    unittest.main()
