import gzip
import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
APP_SOURCE_ROOT = ROOT / "app/src"
MAIN_SOURCE_ROOT = APP_SOURCE_ROOT / "main"
DEBUG_SOURCE_ROOT = APP_SOURCE_ROOT / "debug"
LINT_BASELINE = ROOT / "app/lint-baseline.xml.gz"
DETEKT_BASELINE = ROOT / "config/detekt/advisory-debt-baseline.json"

ANALYSIS_SUPPRESSION = re.compile(
    r"@(?:file:\s*)?(?:Suppress|SuppressLint)\b|"
    r"noinspection|detekt\s*:\s*(?:disable|off)|ktlint-disable|tools:ignore",
    re.IGNORECASE,
)
BROAD_EXCEPTION_CATCH = re.compile(
    r"catch\s*\([^)]*:\s*(?:Exception|Throwable)\b"
)
PRODUCTION_DEBUG_CODE = re.compile(
    r"\bBuildConfig\b|@Preview\b|\bLocalInspectionMode\b|"
    r"\bDebugFixture\w*\b|\bandroid\.util\.Log\b|"
    r"\b(?:println|printStackTrace)\s*\("
)
DEBUG_COOLING_WIRING = re.compile(
    r"\bDeviceFamily\.COOLING\b|\bDeviceCooling\w*\b|"
    r"\bDebugFixtureCooling\w*\b|\bcoolingControlOperations\b|"
    r"\.devices\.cooling\b|\.detail\.cooling\b"
)


def cooling_owned_sources() -> list[Path]:
    return [
        path
        for path in APP_SOURCE_ROOT.rglob("*")
        if path.is_file()
        and path.suffix in {".kt", ".xml"}
        and "cooling" in path.relative_to(APP_SOURCE_ROOT).as_posix().lower()
    ]


class CoolingQualityContractTest(unittest.TestCase):
    def test_cooling_sources_have_no_static_analysis_suppressions(self) -> None:
        violations = self._source_violations(ANALYSIS_SUPPRESSION)

        self.assertEqual([], violations)

    def test_cooling_sources_have_no_broad_exception_catches(self) -> None:
        violations = self._source_violations(BROAD_EXCEPTION_CATCH, suffixes={".kt"})

        self.assertEqual([], violations)

    def test_production_cooling_sources_have_no_debug_or_preview_code(self) -> None:
        violations: list[str] = []
        for path in MAIN_SOURCE_ROOT.rglob("*"):
            if not path.is_file() or path.suffix not in {".kt", ".java", ".xml"}:
                continue
            if "cooling" not in path.relative_to(MAIN_SOURCE_ROOT).as_posix().lower():
                continue
            source = path.read_text(encoding="utf-8", errors="ignore")
            if PRODUCTION_DEBUG_CODE.search(source):
                violations.append(path.relative_to(ROOT).as_posix())

        build_script = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
        if "androidx.compose.ui:ui-tooling-preview" in build_script:
            violations.append("app/build.gradle: ui-tooling-preview")

        self.assertEqual([], violations)

    def test_debug_source_set_has_no_cooling_fixture_or_wiring(self) -> None:
        violations: list[str] = []
        for path in DEBUG_SOURCE_ROOT.rglob("*"):
            if not path.is_file() or path.suffix not in {".kt", ".java", ".xml"}:
                continue
            relative = path.relative_to(ROOT).as_posix()
            source = path.read_text(encoding="utf-8", errors="ignore")
            if "cooling" in path.name.casefold() or DEBUG_COOLING_WIRING.search(source):
                violations.append(relative)

        self.assertEqual([], violations)

    def test_cooling_has_no_android_lint_baseline_entries(self) -> None:
        with gzip.open(LINT_BASELINE, "rt", encoding="utf-8") as baseline:
            cooling_lines = [
                line.strip()
                for line in baseline
                if "cooling" in line.lower()
            ]

        self.assertEqual([], cooling_lines)

    def test_cooling_has_no_detekt_debt_baseline_entries(self) -> None:
        baseline = json.loads(DETEKT_BASELINE.read_text(encoding="utf-8"))
        cooling_entries = [
            fingerprint
            for fingerprint in baseline["fingerprints"]
            if "cooling" in fingerprint["path"].lower()
        ]

        self.assertEqual([], cooling_entries)

    def _source_violations(
        self,
        pattern: re.Pattern[str],
        suffixes: set[str] | None = None,
    ) -> list[str]:
        violations: list[str] = []
        for path in cooling_owned_sources():
            if suffixes is not None and path.suffix not in suffixes:
                continue
            source = path.read_text(encoding="utf-8")
            for line_number, line in enumerate(source.splitlines(), start=1):
                if pattern.search(line):
                    relative_path = path.relative_to(ROOT).as_posix()
                    violations.append(f"{relative_path}:{line_number}")
        return violations


if __name__ == "__main__":
    unittest.main()
