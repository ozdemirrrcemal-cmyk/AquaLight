#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.coverage_gate import CoverageGateError, evaluate, read_policy, read_report

REPORT = """<?xml version="1.0"?>
<report name="test">
  <package name="com/aqua/auth">
    <counter type="LINE" missed="20" covered="80" />
    <counter type="BRANCH" missed="5" covered="5" />
  </package>
  <package name="com/aqua/auth/session">
    <counter type="LINE" missed="10" covered="90" />
    <counter type="BRANCH" missed="2" covered="8" />
  </package>
  <package name="com/aqua/metadata" />
  <package name="com/aqua/other">
    <counter type="LINE" missed="100" covered="0" />
    <counter type="BRANCH" missed="10" covered="0" />
  </package>
  <counter type="LINE" missed="130" covered="170" />
  <counter type="BRANCH" missed="17" covered="13" />
</report>
"""


class CoverageGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.report = self.root / "report.xml"
        self.report.write_text(REPORT, encoding="utf-8")

    def write_policy(self, rules: list[dict[str, object]]) -> Path:
        path = self.root / "policy.json"
        path.write_text(
            json.dumps({"schemaVersion": 1, "rules": rules}),
            encoding="utf-8",
        )
        return path

    def test_aggregates_nested_package_prefix(self) -> None:
        policy = self.write_policy(
            [
                {
                    "name": "auth",
                    "packagePrefix": "com.aqua.auth",
                    "minimumPercent": 85.0,
                }
            ]
        )
        overall, packages = read_report(self.report)
        rule = read_policy(policy)[0]

        result = evaluate(overall, packages, rule)

        self.assertEqual(2, result.matched_packages)
        self.assertEqual(170, result.value.covered)
        self.assertEqual(200, result.value.total)
        self.assertTrue(result.passed)

    def test_accepts_dot_separated_package_prefix(self) -> None:
        policy = self.write_policy(
            [
                {
                    "name": "auth",
                    "packagePrefix": "com.aqua.auth",
                    "minimumPercent": 85.0,
                }
            ]
        )

        self.assertEqual("com/aqua/auth", read_policy(policy)[0].package_prefix)

    def test_fails_below_threshold(self) -> None:
        policy = self.write_policy(
            [{"name": "overall", "minimumPercent": 57.0}]
        )
        overall, packages = read_report(self.report)
        result = evaluate(overall, packages, read_policy(policy)[0])

        self.assertFalse(result.passed)

    def test_rejects_missing_package_scope(self) -> None:
        policy = self.write_policy(
            [
                {
                    "name": "missing",
                    "packagePrefix": "com.aqua.missing",
                    "minimumPercent": 1.0,
                }
            ]
        )
        overall, packages = read_report(self.report)

        with self.assertRaises(CoverageGateError):
            evaluate(overall, packages, read_policy(policy)[0])

    def test_supports_branch_counter(self) -> None:
        policy = self.write_policy(
            [
                {
                    "name": "auth branches",
                    "packagePrefix": "com/aqua/auth",
                    "counter": "BRANCH",
                    "minimumPercent": 65.0,
                }
            ]
        )
        overall, packages = read_report(self.report)
        result = evaluate(overall, packages, read_policy(policy)[0])

        self.assertEqual(13, result.value.covered)
        self.assertEqual(20, result.value.total)
        self.assertTrue(result.passed)

    def test_ignores_metadata_only_package(self) -> None:
        policy = self.write_policy(
            [
                {
                    "name": "aqua",
                    "packagePrefix": "com/aqua",
                    "minimumPercent": 56.0,
                }
            ]
        )
        overall, packages = read_report(self.report)
        result = evaluate(overall, packages, read_policy(policy)[0])

        self.assertEqual(3, result.matched_packages)
        self.assertTrue(result.passed)


if __name__ == "__main__":
    unittest.main()
