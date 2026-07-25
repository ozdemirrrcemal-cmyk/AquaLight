#!/usr/bin/env python3
"""Produce fail-closed Stage 14 evidence from Gradle JUnit XML reports."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any
import xml.etree.ElementTree as ElementTree

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
LABEL_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]*$")
POLICY_ID = "aqualight-stage14-junit-evidence"
SUPPORTED_API_LEVELS = (27, 36)
UNIT_REPORT_PROFILES = {
    frozenset({"debug", "staging"}): "pull-request",
    frozenset({"debug", "staging", "release-smoke", "release"}): "release",
}
REQUIRED_EVIDENCE_SETS = {
    "permission-permanent-denial-unit": (
        "permission-permanent-denial",
        "unit",
    ),
    "process-recreation-instrumentation": (
        "process-recreation-rotation-force-stop",
        "instrumentation",
    ),
    "process-recreation-unit": (
        "process-recreation-rotation-force-stop",
        "unit",
    ),
    "rapid-account-switch-unit": ("rapid-account-switch", "unit"),
    "tank-care-corruption-instrumentation": (
        "tank-care-corruption",
        "instrumentation",
    ),
    "tank-care-corruption-unit": ("tank-care-corruption", "unit"),
    "websocket-account-cleanup-unit": ("websocket-account-cleanup", "unit"),
}
ROOT_KEYS = {"schemaVersion", "policyId", "evidenceSets"}
EVIDENCE_SET_KEYS = {"id", "suite", "runner", "requiredTests"}
TEST_KEYS = {"className", "methodName"}

TestIdentity = tuple[str, str]


class Stage14JunitFailure(ValueError):
    """Raised when JUnit results cannot satisfy the commercial contract."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--evidence-set", required=True)
    parser.add_argument(
        "--report",
        action="append",
        required=True,
        help="Report set in LABEL=DIRECTORY form.",
    )
    parser.add_argument("--api-level", type=int)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise Stage14JunitFailure(f"{path} must be an object")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise Stage14JunitFailure(
            f"{path} schema mismatch; missing={missing}, unknown={unknown}"
        )


def read_contract(path: Path) -> tuple[dict[str, dict[str, Any]], str]:
    try:
        raw = path.read_bytes()
        document = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise Stage14JunitFailure(f"cannot read JUnit contract {path}: {error}") from error

    root = require_object(document, str(path))
    require_exact_keys(root, ROOT_KEYS, str(path))
    if root["schemaVersion"] != 1:
        raise Stage14JunitFailure("JUnit contract schemaVersion must be 1")
    if root["policyId"] != POLICY_ID:
        raise Stage14JunitFailure(f"JUnit contract policyId must be {POLICY_ID!r}")
    raw_sets = root["evidenceSets"]
    if not isinstance(raw_sets, list) or not raw_sets:
        raise Stage14JunitFailure("JUnit contract evidenceSets must be a non-empty array")

    evidence_sets: dict[str, dict[str, Any]] = {}
    previous_id: str | None = None
    for index, raw_set in enumerate(raw_sets):
        item_path = f"evidenceSets[{index}]"
        evidence_set = require_object(raw_set, item_path)
        require_exact_keys(evidence_set, EVIDENCE_SET_KEYS, item_path)
        evidence_id = evidence_set["id"]
        if not isinstance(evidence_id, str) or not LABEL_PATTERN.fullmatch(evidence_id):
            raise Stage14JunitFailure(f"{item_path}.id must be a canonical identifier")
        if previous_id is not None and evidence_id <= previous_id:
            raise Stage14JunitFailure("JUnit contract evidenceSets must be sorted by id")
        previous_id = evidence_id
        if evidence_id in evidence_sets:
            raise Stage14JunitFailure(f"duplicate JUnit evidence set: {evidence_id}")

        expected_identity = REQUIRED_EVIDENCE_SETS.get(evidence_id)
        actual_identity = (evidence_set["suite"], evidence_set["runner"])
        if expected_identity is None or actual_identity != expected_identity:
            raise Stage14JunitFailure(
                f"{item_path} has unsupported suite/runner identity: {actual_identity}"
            )
        required_tests = evidence_set["requiredTests"]
        if not isinstance(required_tests, list) or not required_tests:
            raise Stage14JunitFailure(f"{item_path}.requiredTests must not be empty")

        normalized_tests: list[dict[str, str]] = []
        previous_test: TestIdentity | None = None
        for test_index, raw_test in enumerate(required_tests):
            test_path = f"{item_path}.requiredTests[{test_index}]"
            test = require_object(raw_test, test_path)
            require_exact_keys(test, TEST_KEYS, test_path)
            class_name = test["className"]
            method_name = test["methodName"]
            if (
                not isinstance(class_name, str)
                or not class_name.startswith("com.aqua.aqualight.")
                or not isinstance(method_name, str)
                or not method_name.strip()
            ):
                raise Stage14JunitFailure(
                    f"{test_path} must identify a non-empty AquaLight test"
                )
            identity = (class_name, method_name)
            if previous_test is not None and identity <= previous_test:
                raise Stage14JunitFailure(
                    f"{item_path}.requiredTests must be unique and sorted"
                )
            previous_test = identity
            normalized_tests.append(
                {"className": class_name, "methodName": method_name}
            )

        evidence_sets[evidence_id] = {
            "id": evidence_id,
            "suite": evidence_set["suite"],
            "runner": evidence_set["runner"],
            "requiredTests": normalized_tests,
        }

    if set(evidence_sets) != set(REQUIRED_EVIDENCE_SETS):
        missing = sorted(set(REQUIRED_EVIDENCE_SETS) - set(evidence_sets))
        unknown = sorted(set(evidence_sets) - set(REQUIRED_EVIDENCE_SETS))
        raise Stage14JunitFailure(
            f"JUnit contract evidence set mismatch; missing={missing}, unknown={unknown}"
        )
    return evidence_sets, hashlib.sha256(raw).hexdigest()


def parse_report_specs(values: list[str]) -> dict[str, Path]:
    reports: dict[str, Path] = {}
    for value in values:
        label, separator, raw_path = value.partition("=")
        if (
            not separator
            or not LABEL_PATTERN.fullmatch(label)
            or not raw_path.strip()
        ):
            raise Stage14JunitFailure(
                f"report must use canonical LABEL=DIRECTORY form: {value!r}"
            )
        if label in reports:
            raise Stage14JunitFailure(f"duplicate report label: {label}")
        reports[label] = Path(raw_path)
    return reports


def validate_execution(
    runner: str,
    reports: dict[str, Path],
    api_level: int | None,
) -> dict[str, Any]:
    labels = frozenset(reports)
    if runner == "unit":
        if api_level is not None:
            raise Stage14JunitFailure("unit evidence must not declare an API level")
        profile = UNIT_REPORT_PROFILES.get(labels)
        if profile is None:
            raise Stage14JunitFailure(
                "unit evidence reports must be exactly debug+staging or "
                "debug+staging+release-smoke+release"
            )
        return {
            "runner": "unit",
            "profile": profile,
            "variants": sorted(labels),
        }

    if runner != "instrumentation":
        raise Stage14JunitFailure(f"unsupported JUnit runner: {runner!r}")
    if api_level not in SUPPORTED_API_LEVELS:
        raise Stage14JunitFailure(
            f"instrumentation API level must be one of {SUPPORTED_API_LEVELS}"
        )
    expected_label = f"api-{api_level}"
    if labels != {expected_label}:
        raise Stage14JunitFailure(
            f"instrumentation evidence must use exactly report label {expected_label!r}"
        )
    return {"runner": "instrumentation", "apiLevel": api_level}


def integer_attribute(element: ElementTree.Element, name: str, path: Path) -> int:
    raw = element.attrib.get(name, "0")
    try:
        value = int(raw)
    except ValueError as error:
        raise Stage14JunitFailure(
            f"{path} contains a non-integer {name} attribute"
        ) from error
    if value < 0:
        raise Stage14JunitFailure(f"{path} contains a negative {name} attribute")
    return value


def parse_junit_file(path: Path) -> tuple[Counter[TestIdentity], int]:
    if path.is_symlink():
        raise Stage14JunitFailure(f"JUnit report must not be a symlink: {path}")
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise Stage14JunitFailure(f"cannot read JUnit report {path}: {error}") from error
    if not raw:
        raise Stage14JunitFailure(f"JUnit report is empty: {path}")
    if b"<!DOCTYPE" in raw or b"<!ENTITY" in raw:
        raise Stage14JunitFailure(f"JUnit report contains forbidden XML declarations: {path}")
    try:
        root = ElementTree.fromstring(raw)
    except ElementTree.ParseError as error:
        raise Stage14JunitFailure(f"JUnit report is malformed XML {path}: {error}") from error
    if root.tag not in {"testsuite", "testsuites"}:
        raise Stage14JunitFailure(f"JUnit report has unsupported root {root.tag!r}: {path}")

    for suite in root.iter("testsuite"):
        for attribute in ("failures", "errors"):
            if integer_attribute(suite, attribute, path) != 0:
                raise Stage14JunitFailure(
                    f"JUnit report contains {attribute}: {path}"
                )

    cases: Counter[TestIdentity] = Counter()
    for case in root.iter("testcase"):
        class_name = case.attrib.get("classname", "").strip()
        method_name = case.attrib.get("name", "").strip()
        if not class_name or not method_name:
            raise Stage14JunitFailure(f"JUnit testcase identity is incomplete: {path}")
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
            raise Stage14JunitFailure(
                f"JUnit testcase did not pass: {class_name}.{method_name}"
            )
        cases[(class_name, method_name)] += 1
    if not cases:
        raise Stage14JunitFailure(f"JUnit report contains no testcases: {path}")
    return cases, len(raw)


def validate_report_directory(
    label: str,
    directory: Path,
    required_tests: list[dict[str, str]],
) -> dict[str, Any]:
    if not directory.is_dir() or directory.is_symlink():
        raise Stage14JunitFailure(
            f"JUnit report directory is missing or unsafe for {label}: {directory}"
        )
    xml_files = sorted(directory.rglob("TEST-*.xml"))
    if not xml_files:
        raise Stage14JunitFailure(f"no TEST-*.xml reports found for {label}")

    cases: Counter[TestIdentity] = Counter()
    aggregate = hashlib.sha256()
    total_bytes = 0
    for path in xml_files:
        relative = path.relative_to(directory).as_posix()
        file_cases, byte_count = parse_junit_file(path)
        raw_digest = hashlib.sha256(path.read_bytes()).hexdigest()
        aggregate.update(relative.encode("utf-8"))
        aggregate.update(b"\0")
        aggregate.update(raw_digest.encode("ascii"))
        aggregate.update(b"\n")
        cases.update(file_cases)
        total_bytes += byte_count

    matched: list[dict[str, str]] = []
    for required in required_tests:
        identity = (required["className"], required["methodName"])
        count = cases[identity]
        if count != 1:
            raise Stage14JunitFailure(
                f"{label} must contain exactly one passing {identity[0]}.{identity[1]}, "
                f"found {count}"
            )
        matched.append(required)

    return {
        "label": label,
        "directory": str(directory),
        "reportSetSha256": aggregate.hexdigest(),
        "xmlFileCount": len(xml_files),
        "xmlBytes": total_bytes,
        "testCaseCount": sum(cases.values()),
        "requiredTests": matched,
    }


def validate(
    *,
    contract_path: Path,
    evidence_set_id: str,
    reports: dict[str, Path],
    api_level: int | None,
    commit: str,
) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(commit):
        raise Stage14JunitFailure("commit must be a lowercase 40-character Git SHA")
    evidence_sets, contract_sha = read_contract(contract_path)
    evidence_set = evidence_sets.get(evidence_set_id)
    if evidence_set is None:
        raise Stage14JunitFailure(f"unknown JUnit evidence set: {evidence_set_id}")
    execution = validate_execution(evidence_set["runner"], reports, api_level)
    report_summaries = [
        validate_report_directory(
            label,
            reports[label],
            evidence_set["requiredTests"],
        )
        for label in sorted(reports)
    ]
    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": evidence_set["suite"],
        "evidenceSet": evidence_set_id,
        "releaseCommit": commit,
        "contractSha256": contract_sha,
        "execution": execution,
        "reports": report_summaries,
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        reports = parse_report_specs(args.report)
        summary = validate(
            contract_path=args.contract,
            evidence_set_id=args.evidence_set,
            reports=reports,
            api_level=args.api_level,
            commit=args.commit,
        )
    except Stage14JunitFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "evidenceSet": args.evidence_set,
                "releaseCommit": args.commit,
                "failure": str(error),
            },
        )
        print(f"Stage 14 JUnit evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Stage 14 JUnit evidence passed: "
        f"{summary['evidenceSet']} across {len(summary['reports'])} report set(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
