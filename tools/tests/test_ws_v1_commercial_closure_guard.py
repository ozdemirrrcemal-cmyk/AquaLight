from __future__ import annotations

import subprocess
import sys
import tempfile
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


    def test_guard_rejects_new_raw_event_and_transport_consumer(self) -> None:
        source_dir = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/data/devices/menu"
        )
        with tempfile.NamedTemporaryFile(
            mode="w",
            suffix=".kt",
            prefix="WsClosureBoundaryMutation",
            dir=source_dir,
            encoding="utf-8",
            delete=False,
        ) as mutation:
            mutation.write(
                "package com.aqua.aqualight.data.devices.menu\n"
                "import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent\n"
                "import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport\n"
                "internal class ForbiddenBoundary(\n"
                "    val transport: AqlWsTransport,\n"
                "    val event: AqlWsEvent\n"
                ")\n"
            )
            mutation_path = Path(mutation.name)

        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / "tools/ws_v1_commercial_closure_guard.py")],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
        finally:
            mutation_path.unlink(missing_ok=True)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("raw AqlWsEvent boundary allowlist", result.stderr)
        self.assertIn("WebSocket transport boundary allowlist", result.stderr)


if __name__ == "__main__":
    unittest.main()
