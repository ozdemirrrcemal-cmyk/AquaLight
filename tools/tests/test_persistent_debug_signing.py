from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/installable_debug_apk.yml"
CONTRACT = ROOT / "config/signing/debug-certificate.properties"
PROVISIONER = ROOT / "tools/provision_persistent_debug_keystore.sh"


class PersistentDebugSigningTest(unittest.TestCase):
    def test_installable_workflow_requires_persistent_key(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")

        self.assertIn(
            "AQL_DEBUG_KEYSTORE_BASE64: ${{ secrets.AQL_DEBUG_KEYSTORE_BASE64 }}",
            workflow,
        )
        self.assertIn(
            "bash tools/provision_persistent_debug_keystore.sh",
            workflow,
        )
        self.assertIn("Verify APK certificate contract", workflow)
        self.assertIn("AquaLight-installable-debug-${{ github.run_number }}", workflow)
        self.assertNotIn("keytool -genkeypair", workflow)

    def test_certificate_contract_has_exact_public_identity(self) -> None:
        values = {}
        for line in CONTRACT.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                key, value = line.split("=", maxsplit=1)
                values[key] = value

        self.assertEqual("AndroidDebugKey", values["alias"])
        self.assertRegex(
            values["sha1"],
            re.compile(r"^(?:[0-9A-F]{2}:){19}[0-9A-F]{2}$"),
        )
        self.assertRegex(
            values["sha256"],
            re.compile(r"^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$"),
        )

    def test_provisioner_fails_closed(self) -> None:
        provisioner = PROVISIONER.read_text(encoding="utf-8")

        self.assertIn("AQL_DEBUG_KEYSTORE_BASE64 is missing", provisioner)
        self.assertIn("SHA-1 certificate fingerprint", provisioner)
        self.assertIn("SHA-256 certificate fingerprint", provisioner)
        self.assertNotIn("-genkeypair", provisioner)


if __name__ == "__main__":
    unittest.main()
