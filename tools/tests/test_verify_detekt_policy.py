from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from update_detekt_debt_baseline import build_baseline
from verify_detekt_policy import DetektPolicyFailure, validate_policy

COMMIT = "a" * 40


class DetektPolicyTest(unittest.TestCase):
    def write_sarif(
        self,
        path: Path,
        findings: list[tuple[str, str, str]],
        tool_name: str = "detekt",
    ) -> Path:
        path.write_text(
            json.dumps(
                {
                    "version": "2.1.0",
                    "runs": [
                        {
                            "tool": {"driver": {"name": tool_name}},
                            "results": [
                                {
                                    "level": "warning",
                                    "ruleId": rule,
                                    "message": {"text": message},
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {"uri": source},
                                                "region": {"startLine": index + 1},
                                            }
                                        }
                                    ],
                                }
                                for index, (rule, source, message) in enumerate(
                                    findings
                                )
                            ],
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        return path

    def write_baseline(
        self,
        path: Path,
        sarif: Path,
        source_commit: str = COMMIT,
    ) -> Path:
        path.write_text(
            json.dumps(build_baseline(sarif, source_commit), indent=2) + "\n",
            encoding="utf-8",
        )
        return path

    def test_existing_debt_and_zero_blockers_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            finding = (
                "detekt.style.MagicNumber",
                "app/src/main/Example.kt",
                "Use a named constant.",
            )
            original = self.write_sarif(directory / "original.sarif", [finding])
            baseline = self.write_baseline(directory / "baseline.json", original)
            blocker = self.write_sarif(directory / "blocker.sarif", [])
            advisory = self.write_sarif(directory / "advisory.sarif", [finding])

            summary = validate_policy(baseline, blocker, advisory, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(1, summary["counts"]["remainingAdvisoryFindings"])
        self.assertEqual(0, summary["counts"]["newAdvisoryDebt"])

    def test_resolved_debt_passes_without_rebaselining(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            finding = (
                "detekt.style.MagicNumber",
                "app/src/main/Example.kt",
                "Use a named constant.",
            )
            original = self.write_sarif(directory / "original.sarif", [finding])
            baseline = self.write_baseline(directory / "baseline.json", original)
            blocker = self.write_sarif(directory / "blocker.sarif", [])
            advisory = self.write_sarif(directory / "advisory.sarif", [])

            summary = validate_policy(baseline, blocker, advisory, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(1, summary["counts"]["resolvedAdvisoryFindings"])

    def test_new_advisory_fingerprint_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            original_finding = (
                "detekt.style.MagicNumber",
                "app/src/main/Example.kt",
                "Use a named constant.",
            )
            new_finding = (
                "detekt.style.ReturnCount",
                "app/src/main/New.kt",
                "Too many returns.",
            )
            original = self.write_sarif(
                directory / "original.sarif",
                [original_finding],
            )
            baseline = self.write_baseline(directory / "baseline.json", original)
            blocker = self.write_sarif(directory / "blocker.sarif", [])
            advisory = self.write_sarif(
                directory / "advisory.sarif",
                [original_finding, new_finding],
            )

            summary = validate_policy(baseline, blocker, advisory, COMMIT)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["newAdvisoryDebt"])

    def test_duplicate_fingerprint_growth_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            finding = (
                "detekt.style.MagicNumber",
                "app/src/main/Example.kt",
                "Use a named constant.",
            )
            original = self.write_sarif(directory / "original.sarif", [finding])
            baseline = self.write_baseline(directory / "baseline.json", original)
            blocker = self.write_sarif(directory / "blocker.sarif", [])
            advisory = self.write_sarif(
                directory / "advisory.sarif",
                [finding, finding],
            )

            summary = validate_policy(baseline, blocker, advisory, COMMIT)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["newAdvisoryDebt"])

    def test_blocker_finding_blocks_even_when_advisory_is_baselined(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            finding = (
                "detekt.potential-bugs.UnreachableCode",
                "app/src/main/Example.kt",
                "Unreachable code.",
            )
            original = self.write_sarif(directory / "original.sarif", [finding])
            baseline = self.write_baseline(directory / "baseline.json", original)
            blocker = self.write_sarif(directory / "blocker.sarif", [finding])
            advisory = self.write_sarif(directory / "advisory.sarif", [finding])

            summary = validate_policy(baseline, blocker, advisory, COMMIT)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["counts"]["blockerFindings"])

    def test_unknown_baseline_field_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            finding = (
                "detekt.style.MagicNumber",
                "app/src/main/Example.kt",
                "Use a named constant.",
            )
            original = self.write_sarif(directory / "original.sarif", [finding])
            baseline = self.write_baseline(directory / "baseline.json", original)
            document = json.loads(baseline.read_text(encoding="utf-8"))
            document["temporaryBypass"] = True
            baseline.write_text(json.dumps(document), encoding="utf-8")
            blocker = self.write_sarif(directory / "blocker.sarif", [])

            with self.assertRaisesRegex(DetektPolicyFailure, "schema mismatch"):
                validate_policy(baseline, blocker, blocker, COMMIT)

    def test_absolute_sarif_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            invalid = self.write_sarif(
                directory / "invalid.sarif",
                [
                    (
                        "detekt.style.MagicNumber",
                        "/tmp/Example.kt",
                        "Use a named constant.",
                    )
                ],
            )

            with self.assertRaisesRegex(DetektPolicyFailure, "must not escape"):
                build_baseline(invalid, COMMIT)

    def test_non_detekt_sarif_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            invalid = self.write_sarif(
                directory / "invalid.sarif",
                [
                    (
                        "detekt.style.MagicNumber",
                        "Example.kt",
                        "Use a named constant.",
                    )
                ],
                tool_name="not-detekt",
            )

            with self.assertRaisesRegex(DetektPolicyFailure, "produced by detekt"):
                build_baseline(invalid, COMMIT)


if __name__ == "__main__":
    unittest.main()
