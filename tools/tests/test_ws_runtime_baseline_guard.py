from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / "tools/ws_runtime_baseline_guard.py"


class WebSocketRuntimeBaselineGuardTest(unittest.TestCase):
    def test_protected_runtime_baseline_is_intact(self) -> None:
        completed = subprocess.run(
            [sys.executable, str(GUARD)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(
            0,
            completed.returncode,
            msg=(completed.stdout + "\n" + completed.stderr).strip(),
        )
        self.assertIn(
            "WebSocket runtime baseline guard passed.",
            completed.stdout,
        )


if __name__ == "__main__":
    unittest.main()
