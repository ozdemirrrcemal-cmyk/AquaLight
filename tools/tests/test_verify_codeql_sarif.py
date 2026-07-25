from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_codeql_sarif import CodeQlFailure, validate_sarif_directory

COMMIT = "a" * 40


class CodeQlSarifEvidenceTest(unittest.TestCase):
    def write_sarif(
        self,
        directory: Path,
        security_severity: str | None = None,
        include_result: bool = True,
    ) -> Path:
        properties = (
            {"security-severity": security_severity}
            if security_severity is not None
            else {}
        )
        results = (
            [
                {
                    "ruleId": "java/example",
                    "message": {"text": "example finding"},
                    "locations": [
                        {
                            "physicalLocation": {
                                "artifactLocation": {"uri": "Example.kt"},
                                "region": {"startLine": 9},
                            }
                        }
                    ],
                }
            ]
            if include_result
            else []
        )
        document = {
            "version": "2.1.0",
            "runs": [
                {
                    "tool": {
                        "driver": {
                            "name": "CodeQL",
                            "rules": [
                                {
                                    "id": "java/example",
                                    "properties": properties,
                                }
                            ],
                        }
                    },
                    "results": results,
                }
            ],
        }
        path = directory / "java-kotlin.sarif"
        path.write_text(json.dumps(document), encoding="utf-8")
        return path

    def test_zero_findings_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, include_result=False)

            summary = validate_sarif_directory(directory, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(0, summary["counts"]["critical"])
        self.assertEqual(0, summary["counts"]["high"])

    def test_medium_security_finding_is_evidence_but_not_a_blocker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "6.9")

            summary = validate_sarif_directory(directory, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(1, summary["counts"]["belowHigh"])

    def test_high_security_finding_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "7.0")

            summary = validate_sarif_directory(directory, COMMIT)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["high"])
        self.assertEqual("Example.kt", summary["blockingFindings"][0]["file"])

    def test_critical_security_finding_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "9.0")

            summary = validate_sarif_directory(directory, COMMIT)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["critical"])

    def test_missing_sarif_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(CodeQlFailure, "no CodeQL SARIF"):
                validate_sarif_directory(Path(temporary), COMMIT)

    def test_unknown_rule_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = self.write_sarif(directory, "7.5")
            document = json.loads(path.read_text(encoding="utf-8"))
            document["runs"][0]["results"][0]["ruleId"] = "java/unknown"
            path.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(CodeQlFailure, "unknown ruleId"):
                validate_sarif_directory(directory, COMMIT)

    def test_noncanonical_commit_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, include_result=False)

            with self.assertRaisesRegex(CodeQlFailure, "40-character"):
                validate_sarif_directory(directory, "ABC123")


if __name__ == "__main__":
    unittest.main()
