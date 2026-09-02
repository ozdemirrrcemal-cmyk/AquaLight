#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / "tools/ota_payload_parity_guard.py"


class OtaPayloadParityGuardTest(unittest.TestCase):
    def test_current_android_contract_matches_pinned_firmware_revision(self) -> None:
        completed = subprocess.run(
            [sys.executable, str(GUARD)],
            cwd=ROOT,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        self.assertEqual(
            0,
            completed.returncode,
            msg=f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}",
        )
        self.assertIn("matches the pinned AquaLight-Firmware revision", completed.stdout)


if __name__ == "__main__":
    unittest.main()
