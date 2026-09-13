from __future__ import annotations

import hashlib
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class TimerV1ContractGuardTest(unittest.TestCase):
    def test_guard_accepts_pinned_firmware_contract_and_android_data_layer(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/timer_v1_contract_guard.py")],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        self.assertEqual(
            0,
            result.returncode,
            msg=f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}",
        )
        self.assertIn("Timer V1 parity guard passed", result.stdout)

    def test_shared_timer_fixture_matches_pinned_git_blob_bytes(self) -> None:
        fixture = ROOT / "protocol/fixtures/aql_timer_contract_v1.json"
        payload = fixture.read_bytes()
        blob = hashlib.sha1(
            f"blob {len(payload)}\0".encode("ascii") + payload
        ).hexdigest()

        self.assertEqual("541b2194001fed7abecdd61106d02c8c8a197c2f", blob)

    def test_timer_scope_has_no_suppression_or_baseline_escape_hatch(self) -> None:
        guard = (ROOT / "tools/timer_v1_contract_guard.py").read_text(encoding="utf-8")

        self.assertIn("verify_zero_timer_suppression_debt()", guard)
        self.assertIn("DETEKT_DEBT_BASELINE_PATH", guard)
        self.assertIn("LINT_BASELINE_PATH", guard)


if __name__ == "__main__":
    unittest.main()
