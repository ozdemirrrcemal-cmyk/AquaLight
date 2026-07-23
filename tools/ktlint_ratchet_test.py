#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.ktlint_ratchet import area_for, compare, read_baseline, read_policy, source_set_for

BASELINE = """<?xml version="1.0" encoding="utf-8"?>
<baseline version="1.0">
  <file name="src/main/java/com/aqua/aqualight/data/One.kt">
    <error line="1" column="1" source="standard:indent" />
    <error line="2" column="1" source="standard:max-line-length" />
  </file>
  <file name="src/test/java/com/aqua/aqualight/OneTest.kt">
    <error line="1" column="1" source="standard:indent" />
  </file>
</baseline>
"""


class KtlintRatchetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.baseline = self.root / "baseline.xml"
        self.baseline.write_text(BASELINE, encoding="utf-8")

    def write_policy(
        self,
        *,
        total: int = 3,
        indent: int = 2,
        data_area: int = 2,
    ) -> Path:
        policy = {
            "schemaVersion": 1,
            "totalViolations": total,
            "rules": {
                "standard:indent": indent,
                "standard:max-line-length": 1,
            },
            "sourceSets": {"src/main": 2, "src/test": 1},
            "areas": {"src/main/data": data_area, "src/test": 1},
        }
        path = self.root / "policy.json"
        path.write_text(json.dumps(policy), encoding="utf-8")
        return path

    def test_matching_snapshot_passes(self) -> None:
        current = read_baseline(self.baseline)
        approved = read_policy(self.write_policy())

        self.assertEqual([], compare(current, approved))

    def test_rule_increase_fails(self) -> None:
        current = read_baseline(self.baseline)
        approved = read_policy(self.write_policy(indent=1))

        scopes = {item.scope for item in compare(current, approved)}

        self.assertIn("rule standard:indent", scopes)

    def test_area_increase_fails(self) -> None:
        current = read_baseline(self.baseline)
        approved = read_policy(self.write_policy(data_area=1))

        scopes = {item.scope for item in compare(current, approved)}

        self.assertIn("area src/main/data", scopes)

    def test_improvement_is_allowed(self) -> None:
        current = read_baseline(self.baseline)
        approved = read_policy(self.write_policy(total=4, indent=3, data_area=3))

        self.assertEqual([], compare(current, approved))

    def test_scope_detection(self) -> None:
        self.assertEqual("src/releaseSmoke", source_set_for("src/releaseSmoke/A.kt"))
        self.assertEqual(
            "src/main/application",
            area_for("src/main/java/com/aqua/aqualight/application/A.kt"),
        )
        self.assertEqual("other", source_set_for("generated/A.kt"))


if __name__ == "__main__":
    unittest.main()
