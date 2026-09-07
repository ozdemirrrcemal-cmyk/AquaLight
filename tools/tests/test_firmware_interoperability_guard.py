from __future__ import annotations

import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE_PATH = ROOT / "tools/firmware_interoperability_guard_core.py"
SPEC = importlib.util.spec_from_file_location("firmware_interoperability_guard_core", CORE_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


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

    def test_shared_fixture_blob_pins_are_derived_from_exact_bytes(self) -> None:
        shared = {
            name: expected[1]
            for name, expected in GUARD.EXPECTED_FIXTURES.items()
            if expected[1] is not None
        }

        for fixture_name, firmware_blob in shared.items():
            fixture = ROOT / "protocol/fixtures" / fixture_name
            fixture_bytes = fixture.read_bytes()
            self.assertEqual(firmware_blob, GUARD.git_blob_sha_bytes(fixture_bytes))
            self.assertNotEqual(
                firmware_blob,
                GUARD.git_blob_sha_bytes(fixture_bytes + b"\n"),
                msg=f"{fixture_name} must reject a suppressed trailing blank line",
            )


if __name__ == "__main__":
    unittest.main()
