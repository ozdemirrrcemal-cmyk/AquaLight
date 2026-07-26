from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_force_stop_evidence import (
    ForceStopEvidenceFailure,
    SCENARIOS,
    validate,
)

COMMIT = "e" * 40
PACKAGE_NAME = "com.aqua.aqualight.smoke"


class ForceStopEvidenceTest(unittest.TestCase):
    def evidence(self, directory: Path) -> Path:
        prefix = directory / "release-smoke-api-27-account-deletion"
        for index, (scenario, stage) in enumerate(SCENARIOS, start=1):
            scenario_prefix = Path(f"{prefix}-{scenario}")
            prepare_pid = 100 + index
            resume_pid = 200 + index
            Path(f"{scenario_prefix}-prepare-start.txt").write_text(
                "Status: ok\n"
                "Activity: com.aqua.aqualight.smoke/"
                "com.aqua.aqualight.smoke.AccountDeletionProcessDeathSmokeActivity\n",
                encoding="utf-8",
            )
            Path(f"{scenario_prefix}-prepare-window.xml").write_text(
                f'<node text="ACCOUNT_DELETION_PROCESS_DEATH_PREPARED:'
                f'{scenario}:{stage}" />\n',
                encoding="utf-8",
            )
            Path(f"{scenario_prefix}-resume-start.txt").write_text(
                "Status: ok\n"
                "Activity: com.aqua.aqualight.smoke/"
                "com.aqua.aqualight.smoke.AccountDeletionProcessDeathSmokeActivity\n",
                encoding="utf-8",
            )
            Path(f"{scenario_prefix}-resume-window.xml").write_text(
                f'<node text="ACCOUNT_DELETION_PROCESS_DEATH_PASS:{scenario}:'
                f'pid-{prepare_pid}-to-{resume_pid}" />\n',
                encoding="utf-8",
            )
            Path(f"{scenario_prefix}-logcat.txt").write_text(
                f"I AquaLight: {scenario} recovered\n",
                encoding="utf-8",
            )
        return prefix

    def test_api_27_force_stop_matrix_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            summary = validate(self.evidence(Path(temporary)), 27, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(5, summary["scenarioCount"])
        self.assertTrue(
            all(
                scenario["checks"]["forceStopCreatedNewProcess"]
                for scenario in summary["scenarios"]
            )
        )

    def test_api_37_force_stop_matrix_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            summary = validate(self.evidence(Path(temporary)), 37, COMMIT)

        self.assertEqual(37, summary["apiLevel"])

    def test_missing_scenario_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix = self.evidence(Path(temporary))
            Path(f"{prefix}-started-logcat.txt").unlink()

            with self.assertRaisesRegex(ForceStopEvidenceFailure, "cannot read"):
                validate(prefix, 27, COMMIT)

    def test_same_process_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix = self.evidence(Path(temporary))
            window = Path(f"{prefix}-started-resume-window.xml")
            window.write_text(
                '<node text="ACCOUNT_DELETION_PROCESS_DEATH_PASS:'
                'started:pid-101-to-101" />\n',
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ForceStopEvidenceFailure, "new process"):
                validate(prefix, 27, COMMIT)

    def test_wrong_checkpoint_stage_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix = self.evidence(Path(temporary))
            window = Path(f"{prefix}-cloud-cleared-prepare-window.xml")
            window.write_text(
                '<node text="ACCOUNT_DELETION_PROCESS_DEATH_PREPARED:'
                'cloud-cleared:STARTED" />\n',
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ForceStopEvidenceFailure, "CLOUD_CLEARED"):
                validate(prefix, 27, COMMIT)

    def test_crash_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix = self.evidence(Path(temporary))
            Path(f"{prefix}-account-deleted-logcat.txt").write_text(
                "FATAL EXCEPTION: main\n"
                f"Process: {PACKAGE_NAME}, PID: 205\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ForceStopEvidenceFailure,
                "AndroidRuntime crash",
            ):
                validate(prefix, 27, COMMIT)

    def test_api_and_commit_are_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prefix = self.evidence(Path(temporary))
            with self.assertRaisesRegex(ForceStopEvidenceFailure, "api-level"):
                validate(prefix, 35, COMMIT)
            with self.assertRaisesRegex(ForceStopEvidenceFailure, "40-character"):
                validate(prefix, 27, "abc")


if __name__ == "__main__":
    unittest.main()
