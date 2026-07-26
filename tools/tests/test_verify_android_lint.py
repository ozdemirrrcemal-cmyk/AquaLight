from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_android_lint import ALL_VARIANTS, LintFailure, validate_reports


class AndroidLintEvidenceTest(unittest.TestCase):
    def write_report(
        self,
        directory: Path,
        variant: str,
        issues: str = "",
    ) -> Path:
        path = directory / f"lint-results-{variant}.xml"
        path.write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            f"<issues>{issues}</issues>\n",
            encoding="utf-8",
        )
        return path

    def complete_reports(self, directory: Path) -> list[Path]:
        return [
            self.write_report(directory, variant)
            for variant in ALL_VARIANTS
        ]

    def test_zero_blockers_passes_and_preserves_warning_count(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = self.complete_reports(directory)
            reports[0] = self.write_report(
                directory,
                "debug",
                '<issue id="NewApi" severity="Warning" message="review">'
                '<location file="Example.kt" line="12" /></issue>',
            )

            summary = validate_reports(reports)

        self.assertTrue(summary["passed"])
        self.assertFalse(summary["baselineApplied"])
        self.assertEqual(1, summary["totals"]["Warning"])
        self.assertEqual(0, summary["totals"]["Error"])

    def test_error_is_a_blocker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = self.complete_reports(directory)
            reports[-1] = self.write_report(
                directory,
                "release",
                '<issue id="UnsafeOptInUsageError" severity="Error" message="unsafe">'
                '<location file="Release.kt" line="7" /></issue>',
            )

            summary = validate_reports(reports)

        self.assertFalse(summary["passed"])
        self.assertEqual(1, summary["totals"]["Error"])
        self.assertEqual("release", summary["blockers"][0]["variant"])

    def test_missing_variant_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            reports = self.complete_reports(Path(temporary))[:-1]

            with self.assertRaisesRegex(LintFailure, "missing release"):
                validate_reports(reports)

    def test_duplicate_variant_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = self.complete_reports(directory)
            reports.append(directory / "lint-results-debug.xml")

            with self.assertRaisesRegex(LintFailure, "duplicate"):
                validate_reports(reports)

    def test_partial_required_variant_set_is_supported_for_pr_ci(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = [
                self.write_report(directory, "debug"),
                self.write_report(directory, "staging"),
            ]

            summary = validate_reports(reports, ("debug", "staging"))

        self.assertTrue(summary["passed"])
        self.assertEqual(["debug", "staging"], summary["requiredVariants"])

    def test_malformed_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = self.complete_reports(directory)
            reports[0].write_text("<html />", encoding="utf-8")

            with self.assertRaisesRegex(LintFailure, "unexpected Android Lint root"):
                validate_reports(reports)

    def test_unknown_severity_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            reports = self.complete_reports(directory)
            reports[0] = self.write_report(
                directory,
                "debug",
                '<issue id="FutureSeverity" severity="Critical" message="unknown" />',
            )

            with self.assertRaisesRegex(LintFailure, "unsupported severity"):
                validate_reports(reports)


if __name__ == "__main__":
    unittest.main()
