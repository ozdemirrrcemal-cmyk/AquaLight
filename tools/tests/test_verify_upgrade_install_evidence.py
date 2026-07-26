from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_upgrade_install_evidence import (
    REQUIRED_CHECKS,
    UpgradeInstallFailure,
    validate,
)

COMMIT = "c" * 40
SIGNER = "d" * 64
BASELINE_NONCE = "11111111-1111-4111-8111-111111111111"
CANDIDATE_NONCE = "22222222-2222-4222-8222-222222222222"


class UpgradeInstallEvidenceTest(unittest.TestCase):
    def evidence(self, directory: Path, api_level: int = 27) -> dict[str, Path]:
        paths = {
            name: directory / name
            for name in (
                "baseline.json",
                "candidate.json",
                "baseline.apk",
                "candidate.apk",
                "baseline-install.txt",
                "candidate-install.txt",
                "baseline-launch.txt",
                "candidate-launch.txt",
                "baseline-window.xml",
                "candidate-window.xml",
                "baseline-package.txt",
                "candidate-package.txt",
                "baseline-logcat.txt",
                "candidate-logcat.txt",
            )
        }
        baseline_identity = {
            "versionName": "1.2.3-smoke",
            "versionCode": 41,
            "processId": 100,
            "processNonce": BASELINE_NONCE,
            "signerSha256": SIGNER,
        }
        candidate_identity = {
            **baseline_identity,
            "versionCode": 42,
            "processId": 200,
            "processNonce": CANDIDATE_NONCE,
        }
        paths["baseline.json"].write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "passed": True,
                    "phase": "baseline-seed",
                    "baselineMode": "same-commit-lower-version-code",
                    "packageName": "com.aqua.aqualight.smoke",
                    **baseline_identity,
                }
            ),
            encoding="utf-8",
        )
        paths["candidate.json"].write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "passed": True,
                    "phase": "candidate-verify",
                    "baselineMode": "same-commit-lower-version-code",
                    "packageName": "com.aqua.aqualight.smoke",
                    "baseline": baseline_identity,
                    "candidate": candidate_identity,
                    "checks": {name: True for name in REQUIRED_CHECKS},
                    "credentialCleanup": {
                        "discardedStagedCount": 1,
                        "removedOrphanCount": 1,
                    },
                }
            ),
            encoding="utf-8",
        )
        paths["baseline.apk"].write_bytes(b"baseline-apk")
        paths["candidate.apk"].write_bytes(b"candidate-apk")
        paths["baseline-install.txt"].write_text("Success\n", encoding="utf-8")
        paths["candidate-install.txt"].write_text("Success\n", encoding="utf-8")
        paths["baseline-launch.txt"].write_text("Status: ok\n", encoding="utf-8")
        paths["candidate-launch.txt"].write_text("Status: ok\n", encoding="utf-8")
        paths["baseline-window.xml"].write_text(
            '<node text="UPGRADE_INSTALL_BASELINE_PASS" />\n',
            encoding="utf-8",
        )
        paths["candidate-window.xml"].write_text(
            '<node text="UPGRADE_INSTALL_CANDIDATE_PASS" />\n',
            encoding="utf-8",
        )
        paths["baseline-package.txt"].write_text(
            "Package [com.aqua.aqualight.smoke]\nversionCode=41 minSdk=27\n",
            encoding="utf-8",
        )
        paths["candidate-package.txt"].write_text(
            "Package [com.aqua.aqualight.smoke]\nversionCode=42 minSdk=27\n",
            encoding="utf-8",
        )
        paths["baseline-logcat.txt"].write_text(
            f"I UpgradeInstall: baseline complete on API {api_level}\n",
            encoding="utf-8",
        )
        paths["candidate-logcat.txt"].write_text(
            f"I UpgradeInstall: complete on API {api_level}\n",
            encoding="utf-8",
        )
        return paths

    def validate_paths(
        self,
        paths: dict[str, Path],
        api_level: int = 27,
        commit: str = COMMIT,
    ) -> dict[str, object]:
        return validate(
            baseline_evidence=paths["baseline.json"],
            candidate_evidence=paths["candidate.json"],
            baseline_apk=paths["baseline.apk"],
            candidate_apk=paths["candidate.apk"],
            baseline_install_log=paths["baseline-install.txt"],
            candidate_install_log=paths["candidate-install.txt"],
            baseline_launch_log=paths["baseline-launch.txt"],
            candidate_launch_log=paths["candidate-launch.txt"],
            baseline_window=paths["baseline-window.xml"],
            candidate_window=paths["candidate-window.xml"],
            baseline_package_dump=paths["baseline-package.txt"],
            candidate_package_dump=paths["candidate-package.txt"],
            baseline_logcat=paths["baseline-logcat.txt"],
            candidate_logcat=paths["candidate-logcat.txt"],
            api_level=api_level,
            commit=commit,
        )

    def test_api_27_upgrade_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            summary = self.validate_paths(self.evidence(Path(temporary)), 27)

        self.assertTrue(summary["passed"])
        self.assertEqual(41, summary["baseline"]["versionCode"])
        self.assertEqual(42, summary["candidate"]["versionCode"])

    def test_api_37_upgrade_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            summary = self.validate_paths(self.evidence(Path(temporary), 37), 37)

        self.assertTrue(summary["passed"])
        self.assertEqual(37, summary["apiLevel"])

    def test_same_signer_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            document = json.loads(paths["candidate.json"].read_text())
            document["candidate"]["signerSha256"] = "e" * 64
            document["checks"]["signerUnchanged"] = False
            paths["candidate.json"].write_text(json.dumps(document))

            with self.assertRaisesRegex(UpgradeInstallFailure, "signer"):
                self.validate_paths(paths)

    def test_version_code_must_increase_exactly_once(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            document = json.loads(paths["candidate.json"].read_text())
            document["candidate"]["versionCode"] = 43
            paths["candidate.json"].write_text(json.dumps(document))

            with self.assertRaisesRegex(UpgradeInstallFailure, "baseline \\+ 1"):
                self.validate_paths(paths)

    def test_same_process_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            document = json.loads(paths["candidate.json"].read_text())
            document["candidate"]["processNonce"] = BASELINE_NONCE
            paths["candidate.json"].write_text(json.dumps(document))

            with self.assertRaisesRegex(UpgradeInstallFailure, "process nonce"):
                self.validate_paths(paths)

    def test_staged_credential_cleanup_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            document = json.loads(paths["candidate.json"].read_text())
            document["credentialCleanup"]["discardedStagedCount"] = 0
            paths["candidate.json"].write_text(json.dumps(document))

            with self.assertRaisesRegex(UpgradeInstallFailure, "cleanup counts"):
                self.validate_paths(paths)

    def test_byte_identical_apks_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            paths["candidate.apk"].write_bytes(paths["baseline.apk"].read_bytes())

            with self.assertRaisesRegex(UpgradeInstallFailure, "byte-identical"):
                self.validate_paths(paths)

    def test_candidate_crash_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            paths["candidate-logcat.txt"].write_text(
                "FATAL EXCEPTION: main\n"
                "Process: com.aqua.aqualight.smoke, PID: 200\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(UpgradeInstallFailure, "AndroidRuntime crash"):
                self.validate_paths(paths)

    def test_baseline_crash_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.evidence(Path(temporary))
            paths["baseline-logcat.txt"].write_text(
                "FATAL EXCEPTION: main\n"
                "Process: com.aqua.aqualight.smoke, PID: 100\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(UpgradeInstallFailure, "baseline"):
                self.validate_paths(paths)


if __name__ == "__main__":
    unittest.main()
