from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ElementTree

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_stage14_junit_evidence import (
    Stage14JunitFailure,
    parse_report_specs,
    read_contract,
    validate,
)

CONTRACT = ROOT / "config/commercial/stage14-junit-evidence-contract.json"
COMMIT = "d" * 40


class Stage14JunitEvidenceTest(unittest.TestCase):
    def required_tests(self, evidence_set: str) -> list[dict[str, str]]:
        contracts, _ = read_contract(CONTRACT)
        return contracts[evidence_set]["requiredTests"]

    def write_report(
        self,
        directory: Path,
        evidence_set: str,
        *,
        omit_last: bool = False,
        failed_index: int | None = None,
        skipped_index: int | None = None,
    ) -> Path:
        directory.mkdir(parents=True, exist_ok=True)
        required = self.required_tests(evidence_set)
        if omit_last:
            required = required[:-1]
        suite = ElementTree.Element(
            "testsuite",
            {
                "name": "commercial",
                "tests": str(len(required)),
                "failures": "1" if failed_index is not None else "0",
                "errors": "0",
            },
        )
        for index, test in enumerate(required):
            case = ElementTree.SubElement(
                suite,
                "testcase",
                {
                    "classname": test["className"],
                    "name": test["methodName"],
                },
            )
            if index == failed_index:
                ElementTree.SubElement(case, "failure", {"message": "boom"})
            if index == skipped_index:
                ElementTree.SubElement(case, "skipped")
        path = directory / "TEST-commercial.xml"
        ElementTree.ElementTree(suite).write(
            path,
            encoding="utf-8",
            xml_declaration=True,
        )
        return directory

    def test_pull_request_unit_evidence_passes_two_variants(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "rapid-account-switch-unit"
            reports = {
                label: self.write_report(root / label, evidence_set)
                for label in ("debug", "staging")
            }

            summary = validate(
                contract_path=CONTRACT,
                evidence_set_id=evidence_set,
                reports=reports,
                api_level=None,
                commit=COMMIT,
            )

        self.assertTrue(summary["passed"])
        self.assertEqual("pull-request", summary["execution"]["profile"])
        self.assertEqual(2, len(summary["reports"]))

    def test_release_unit_evidence_requires_all_four_variants(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "websocket-account-cleanup-unit"
            reports = {
                label: self.write_report(root / label, evidence_set)
                for label in ("debug", "staging", "release-smoke", "release")
            }

            summary = validate(
                contract_path=CONTRACT,
                evidence_set_id=evidence_set,
                reports=reports,
                api_level=None,
                commit=COMMIT,
            )

        self.assertEqual("release", summary["execution"]["profile"])
        self.assertEqual(4, len(summary["reports"]))

    def test_instrumentation_evidence_is_bound_to_api_level(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "tank-care-corruption-instrumentation"
            reports = {
                "api-27": self.write_report(root / "api-27", evidence_set)
            }

            summary = validate(
                contract_path=CONTRACT,
                evidence_set_id=evidence_set,
                reports=reports,
                api_level=27,
                commit=COMMIT,
            )

        self.assertEqual(27, summary["execution"]["apiLevel"])

    def test_missing_required_test_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "permission-permanent-denial-unit"
            reports = {
                "debug": self.write_report(
                    root / "debug",
                    evidence_set,
                    omit_last=True,
                ),
                "staging": self.write_report(root / "staging", evidence_set),
            }

            with self.assertRaisesRegex(Stage14JunitFailure, "exactly one passing"):
                validate(
                    contract_path=CONTRACT,
                    evidence_set_id=evidence_set,
                    reports=reports,
                    api_level=None,
                    commit=COMMIT,
                )

    def test_failed_or_skipped_test_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "process-recreation-unit"
            failed_reports = {
                "debug": self.write_report(
                    root / "failed-debug",
                    evidence_set,
                    failed_index=0,
                ),
                "staging": self.write_report(root / "failed-staging", evidence_set),
            }
            with self.assertRaisesRegex(Stage14JunitFailure, "failures"):
                validate(
                    contract_path=CONTRACT,
                    evidence_set_id=evidence_set,
                    reports=failed_reports,
                    api_level=None,
                    commit=COMMIT,
                )

            skipped_reports = {
                "debug": self.write_report(
                    root / "skipped-debug",
                    evidence_set,
                    skipped_index=0,
                ),
                "staging": self.write_report(root / "skipped-staging", evidence_set),
            }
            with self.assertRaisesRegex(Stage14JunitFailure, "did not pass"):
                validate(
                    contract_path=CONTRACT,
                    evidence_set_id=evidence_set,
                    reports=skipped_reports,
                    api_level=None,
                    commit=COMMIT,
                )

    def test_weakened_variant_matrix_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "rapid-account-switch-unit"
            reports = {
                "debug": self.write_report(root / "debug", evidence_set)
            }

            with self.assertRaisesRegex(Stage14JunitFailure, "exactly debug"):
                validate(
                    contract_path=CONTRACT,
                    evidence_set_id=evidence_set,
                    reports=reports,
                    api_level=None,
                    commit=COMMIT,
                )

    def test_unknown_contract_field_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            copied = Path(temporary) / "contract.json"
            document = json.loads(CONTRACT.read_text(encoding="utf-8"))
            document["temporaryBypass"] = True
            copied.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(Stage14JunitFailure, "schema mismatch"):
                read_contract(copied)

    def test_removed_evidence_set_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            copied = Path(temporary) / "contract.json"
            document = json.loads(CONTRACT.read_text(encoding="utf-8"))
            document["evidenceSets"].pop()
            copied.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(Stage14JunitFailure, "evidence set mismatch"):
                read_contract(copied)

    def test_duplicate_report_label_and_bad_commit_are_rejected(self) -> None:
        with self.assertRaisesRegex(Stage14JunitFailure, "duplicate report label"):
            parse_report_specs(["debug=/tmp/one", "debug=/tmp/two"])

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence_set = "rapid-account-switch-unit"
            reports = {
                label: self.write_report(root / label, evidence_set)
                for label in ("debug", "staging")
            }
            with self.assertRaisesRegex(Stage14JunitFailure, "40-character"):
                validate(
                    contract_path=CONTRACT,
                    evidence_set_id=evidence_set,
                    reports=reports,
                    api_level=None,
                    commit="abc",
                )


if __name__ == "__main__":
    unittest.main()
