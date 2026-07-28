from __future__ import annotations

import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class RepositoryHygieneTest(unittest.TestCase):
    def test_ignored_workspace_state_is_not_tracked(self) -> None:
        result = subprocess.run(
            ["git", "ls-files", "-ci", "--exclude-standard"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual(
            "",
            result.stdout,
            "Ignored workspace state must never be committed:\n"
            + result.stdout,
        )


if __name__ == "__main__":
    unittest.main()
