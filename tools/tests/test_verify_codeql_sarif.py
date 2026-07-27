from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_codeql_sarif import (
    CodeQlFailure,
    parse_diff_ranges,
    resolve_gate_mode,
    validate_sarif_directory,
)

COMMIT = "a" * 40


class CodeQlSarifEvidenceTest(unittest.TestCase):
    def write_sarif(
        self,
        directory: Path,
        security_severity: str | None = None,
        include_result: bool = True,
        file_path: str = "Example.kt",
        start_line: int = 9,
        related_file: str | None = None,
        related_line: int = 15,
    ) -> Path:
        properties = (
            {"security-severity": security_severity}
            if security_severity is not None
            else {}
        )
        result = {
            "ruleId": "java/example",
            "message": {"text": "example finding"},
            "locations": [
                {
                    "physicalLocation": {
                        "artifactLocation": {
                            "uri": file_path,
                            "uriBaseId": "%SRCROOT%",
                        },
                        "region": {
                            "startLine": start_line,
                            "endLine": start_line,
                        },
                    }
                }
            ],
        }
        if related_file is not None:
            result["relatedLocations"] = [
                {
                    "id": 1,
                    "physicalLocation": {
                        "artifactLocation": {
                            "uri": related_file,
                            "uriBaseId": "%SRCROOT%",
                        },
                        "region": {
                            "startLine": related_line,
                            "endLine": related_line,
                        },
                    },
                }
            ]
        results = [result] if include_result else []
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

    def validate(
        self,
        directory: Path,
        *,
        effective_mode: str = "full",
        diff_ranges: dict[str, list[tuple[int, int]]] | None = None,
        fallback_reason: str | None = None,
    ) -> dict:
        return validate_sarif_directory(
            directory,
            COMMIT,
            requested_mode=effective_mode,
            effective_mode=effective_mode,
            diff_ranges=diff_ranges,
            diff_fallback_reason=fallback_reason,
            workspace=Path.cwd(),
        )

    def test_zero_findings_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, include_result=False)
            summary = self.validate(directory)

        self.assertTrue(summary["passed"])
        self.assertEqual(0, summary["counts"]["critical"])
        self.assertEqual(0, summary["counts"]["high"])

    def test_medium_security_finding_is_evidence_but_not_a_blocker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "6.9")
            summary = self.validate(directory)

        self.assertTrue(summary["passed"])
        self.assertEqual(1, summary["observedCounts"]["belowHigh"])

    def test_high_security_finding_blocks_in_full_mode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "7.0")
            summary = self.validate(directory)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["high"])
        self.assertEqual("Example.kt", summary["blockingFindings"][0]["file"])

    def test_critical_security_finding_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "9.0")
            summary = self.validate(directory)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["critical"])

    def test_unchanged_high_passes_pr_mode_but_full_mode_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "8.2")

            pr_summary = self.validate(
                directory,
                effective_mode="pr-diff",
                diff_ranges={"Other.kt": [(1, 2)]},
            )
            full_summary = self.validate(directory)

        self.assertTrue(pr_summary["passed"])
        self.assertEqual(0, pr_summary["counts"]["high"])
        self.assertEqual(1, pr_summary["observedCounts"]["high"])
        self.assertEqual(
            "outside-pr-diff",
            pr_summary["ignoredFindings"][0]["gateReason"],
        )
        self.assertFalse(full_summary["passed"])
        self.assertEqual(1, full_summary["counts"]["high"])

    def test_changed_primary_location_blocks_pr_mode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "8.2", start_line=9)
            summary = self.validate(
                directory,
                effective_mode="pr-diff",
                diff_ranges={"Example.kt": [(9, 9)]},
            )

        self.assertFalse(summary["passed"])
        self.assertEqual(
            "intersects-pr-diff",
            summary["blockingFindings"][0]["gateReason"],
        )

    def test_changed_related_location_blocks_pr_mode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(
                directory,
                "8.2",
                file_path="Unchanged.kt",
                start_line=3,
                related_file="ChangedManifest.xml",
                related_line=20,
            )
            summary = self.validate(
                directory,
                effective_mode="pr-diff",
                diff_ranges={"ChangedManifest.xml": [(20, 20)]},
            )

        self.assertFalse(summary["passed"])
        self.assertEqual(
            "intersects-pr-diff",
            summary["blockingFindings"][0]["gateReason"],
        )

    def test_missing_diff_evidence_falls_back_to_full(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "8.2")
            missing = directory / "missing.yml"

            effective_mode, ranges, reason = resolve_gate_mode(
                "pr-diff",
                "pull_request",
                missing,
                Path.cwd(),
            )
            summary = validate_sarif_directory(
                directory,
                COMMIT,
                requested_mode="pr-diff",
                effective_mode=effective_mode,
                diff_ranges=ranges,
                diff_fallback_reason=reason,
                workspace=Path.cwd(),
            )

        self.assertEqual("full", summary["effectiveMode"])
        self.assertIsNotNone(summary["diffFallbackReason"])
        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["high"])

    def test_malformed_diff_evidence_falls_back_to_full(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, "8.2")
            malformed = directory / "pr-diff-range.yml"
            malformed.write_text(
                "extensions:\n  - data:\n      - [\"Example.kt\", bad, 9]\n",
                encoding="utf-8",
            )
            effective_mode, ranges, reason = resolve_gate_mode(
                "pr-diff",
                "pull_request",
                malformed,
                Path.cwd(),
            )

        self.assertEqual("full", effective_mode)
        self.assertIsNone(ranges)
        self.assertIn("malformed", reason or "")

    def test_push_and_release_events_always_use_full_mode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            diff_file = Path(temporary) / "pr-diff-range.yml"
            diff_file.write_text(
                "extensions:\n  - data:\n      - [\"Example.kt\", 9, 9]\n",
                encoding="utf-8",
            )
            for event_name in ("push", "schedule", "workflow_dispatch"):
                effective_mode, ranges, reason = resolve_gate_mode(
                    "auto",
                    event_name,
                    diff_file,
                    Path.cwd(),
                )
                self.assertEqual("full", effective_mode)
                self.assertIsNone(ranges)
                self.assertIsNone(reason)

    def test_diff_ranges_resolve_workspace_paths_and_whole_file_ranges(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary) / "repo"
            workspace.mkdir()
            diff_file = Path(temporary) / "pr-diff-range.yml"
            diff_file.write_text(
                "extensions:\n"
                "  - data:\n"
                f"      - [\"{workspace.as_posix()}/Example.kt\", 9, 12]\n"
                f"      - [\"{workspace.as_posix()}/Binary.kt\", 0, 0]\n"
                "      - [\"\", 0, 0]\n",
                encoding="utf-8",
            )

            ranges = parse_diff_ranges(diff_file, workspace)

        self.assertEqual([(9, 12)], ranges["Example.kt"])
        self.assertEqual([(0, 0)], ranges["Binary.kt"])

    def test_codeql_extension_rule_reference_is_resolved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = self.write_sarif(directory, "8.2")
            document = json.loads(path.read_text(encoding="utf-8"))
            run = document["runs"][0]
            rule = run["tool"]["driver"]["rules"].pop()
            run["tool"]["extensions"] = [
                {
                    "name": "codeql-action/pr-diff-range",
                    "rules": [],
                },
                {
                    "name": "codeql/java-queries",
                    "rules": [rule],
                },
            ]
            run["results"][0]["rule"] = {
                "id": "java/example",
                "index": 0,
                "toolComponent": {"index": 1},
            }
            path.write_text(json.dumps(document), encoding="utf-8")

            summary = self.validate(directory)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["high"])
        self.assertEqual(1, summary["reports"][0]["runs"][0]["ruleCount"])

    def test_invalid_extension_rule_reference_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = self.write_sarif(directory, "8.2")
            document = json.loads(path.read_text(encoding="utf-8"))
            result = document["runs"][0]["results"][0]
            result["rule"] = {
                "id": "java/example",
                "index": 0,
                "toolComponent": {"index": 3},
            }
            path.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(CodeQlFailure, "extension index"):
                self.validate(directory)

    def test_missing_sarif_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(CodeQlFailure, "no CodeQL SARIF"):
                self.validate(Path(temporary))

    def test_unknown_rule_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = self.write_sarif(directory, "7.5")
            document = json.loads(path.read_text(encoding="utf-8"))
            document["runs"][0]["results"][0]["ruleId"] = "java/unknown"
            path.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(CodeQlFailure, "unknown ruleId"):
                self.validate(directory)

    def test_noncanonical_commit_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.write_sarif(directory, include_result=False)

            with self.assertRaisesRegex(CodeQlFailure, "40-character"):
                validate_sarif_directory(directory, "ABC123")


if __name__ == "__main__":
    unittest.main()
