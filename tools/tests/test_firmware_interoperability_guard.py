from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class FirmwareInteroperabilityGuardTest(unittest.TestCase):
    def test_guard_passes_final_firmware_matrix(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/firmware_interoperability_guard.py")],
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
        self.assertIn("Firmware interoperability guard passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
