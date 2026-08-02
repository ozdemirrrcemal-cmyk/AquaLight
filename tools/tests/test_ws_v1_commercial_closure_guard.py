from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class WsV1CommercialClosureGuardTest(unittest.TestCase):
    def test_guard_passes_repository_contract(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/ws_v1_commercial_closure_guard.py")],
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
        self.assertIn("WS v1 commercial closure guard passed.", result.stdout)


if __name__ == "__main__":
    unittest.main()
