#!/usr/bin/env python3
"""Verify required Gradle JUnit variants and publish deterministic release evidence."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


class VerificationFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class VariantInput:
    name: str
    directory: Path


def parse_variant(raw: str) -> VariantInput:
    name, separator, directory = raw.partition("=")
    normalized = name.strip().lower()
    if not separator or not normalized or not directory.strip():
        raise argparse.ArgumentTypeError("variant must use name=report-directory")
    if not normalized.replace("-", "").isalnum():
        raise argparse.ArgumentTypeError(f"invalid variant name: {name}")
    return VariantInput(normalized, Path(directory.strip()))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", action="append", type=parse_variant, required=True)
    parser.add_argument("--allowlist", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def load_allowlist(path: Path) -> set[str]:
    if not path.is_file():
        raise VerificationFailure(f"Skipped-test allowlist is missing: {path}")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise VerificationFailure(f"Invalid skipped-test allowlist: {path}: {error}") from error
    if payload.get("schemaVersion") != 1 or not isinstance(payload.get("allowed"), list):
        raise VerificationFailure("Skipped-test allowlist must use schemaVersion=1 and allowed=[].")
    allowed = payload["allowed"]
    if any(not isinstance(item, str) or "#" not in item for item in allowed):
        raise VerificationFailure("Allowed skipped tests must use fully.qualified.Class#testName.")
    if len(allowed) != len(set(allowed)):
        raise VerificationFailure("Skipped-test allowlist contains duplicates.")
    return set(allowed)


def integer_attribute(node: ET.Element, key: str, path: Path) -> int:
    raw = node.attrib.get(key, "0")
    try:
        value = int(raw)
    except ValueError as error:
        raise VerificationFailure(f"{path} has a non-integer {key}={raw!r}.") from error
    if value < 0:
        raise VerificationFailure(f"{path} has a negative {key}={value}.")
    return value


def suites(root: ET.Element, path: Path) -> Iterable[ET.Element]:
    if root.tag == "testsuite":
        return (root,)
    if root.tag == "testsuites":
        found = tuple(child for child in root if child.tag == "testsuite")
        if not found:
            raise VerificationFailure(f"{path} contains no testsuite entries.")
        return found
    raise VerificationFailure(f"{path} root must be testsuite or testsuites, got {root.tag}.")


def verify_variant(
    variant: VariantInput,
    allowlist: set[str],
    output_root: Path,
) -> dict[str, object]:
    if not variant.directory.is_dir():
        raise VerificationFailure(
            f"{variant.name} JUnit report directory is missing: {variant.directory}"
        )
    xml_paths = sorted(
        path
        for path in variant.directory.rglob("*.xml")
        if path.is_file() and path.stat().st_size > 0
    )
    if not xml_paths:
        raise VerificationFailure(
            f"{variant.name} produced no non-empty JUnit XML reports in {variant.directory}."
        )

    total_tests = total_failures = total_errors = total_skipped = 0
    skipped_tests: set[str] = set()
    suite_count = 0
    source_files: list[str] = []

    evidence_xml = output_root / variant.name / "xml"
    if evidence_xml.exists():
        shutil.rmtree(evidence_xml)
    evidence_xml.mkdir(parents=True, exist_ok=True)

    for index, path in enumerate(xml_paths, start=1):
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            raise VerificationFailure(f"Invalid JUnit XML {path}: {error}") from error

        for suite in suites(root, path):
            suite_count += 1
            tests = integer_attribute(suite, "tests", path)
            failures = integer_attribute(suite, "failures", path)
            errors = integer_attribute(suite, "errors", path)
            skipped = integer_attribute(suite, "skipped", path)
            testcases = tuple(suite.findall("testcase"))
            if tests != len(testcases):
                raise VerificationFailure(
                    f"{path} declares {tests} tests but contains {len(testcases)} testcases."
                )
            total_tests += tests
            total_failures += failures
            total_errors += errors
            total_skipped += skipped

            discovered_skipped = 0
            for testcase in testcases:
                if testcase.find("skipped") is None:
                    continue
                discovered_skipped += 1
                class_name = testcase.attrib.get("classname", "").strip()
                test_name = testcase.attrib.get("name", "").strip()
                if not class_name or not test_name:
                    raise VerificationFailure(f"{path} has an unnamed skipped testcase.")
                skipped_tests.add(f"{class_name}#{test_name}")
            if discovered_skipped != skipped:
                raise VerificationFailure(
                    f"{path} declares {skipped} skipped tests but exposes {discovered_skipped}."
                )

        destination = evidence_xml / f"{index:04d}-{path.name}"
        shutil.copyfile(path, destination)
        source_files.append(path.as_posix())

    if total_tests <= 0:
        raise VerificationFailure(f"{variant.name} ran zero unit tests.")
    if total_failures or total_errors:
        raise VerificationFailure(
            f"{variant.name} failed: failures={total_failures}, errors={total_errors}."
        )

    unexpected_skips = sorted(skipped_tests - allowlist)
    if unexpected_skips:
        raise VerificationFailure(
            f"{variant.name} has unexpected skipped tests: {unexpected_skips}"
        )

    summary = {
        "variant": variant.name,
        "approved": True,
        "reportDirectory": variant.directory.as_posix(),
        "reportFiles": source_files,
        "suiteCount": suite_count,
        "tests": total_tests,
        "failures": total_failures,
        "errors": total_errors,
        "skipped": total_skipped,
        "allowedSkippedTests": sorted(skipped_tests),
    }
    write_json(output_root / variant.name / "summary.json", summary)
    return summary


def main() -> int:
    args = parse_args()
    output_root: Path = args.output
    try:
        variants: list[VariantInput] = args.variant
        names = [variant.name for variant in variants]
        if len(names) != len(set(names)):
            raise VerificationFailure("Each unit-test variant may be supplied only once.")
        if set(names) != {"debug", "staging", "release"}:
            raise VerificationFailure(
                "Commercial release requires exactly debug, staging, and release unit-test variants."
            )
        allowlist = load_allowlist(args.allowlist)
        summaries = [verify_variant(variant, allowlist, output_root) for variant in variants]
        all_summary = {
            "schemaVersion": 1,
            "approved": True,
            "variants": summaries,
            "totalTests": sum(int(item["tests"]) for item in summaries),
            "totalFailures": 0,
            "totalErrors": 0,
            "totalSkipped": sum(int(item["skipped"]) for item in summaries),
        }
        write_json(output_root / "all-variants-summary.json", all_summary)
        print(
            "Commercial unit-test variants approved: "
            + ", ".join(f"{item['variant']}={item['tests']}" for item in summaries)
        )
        return 0
    except VerificationFailure as error:
        write_json(
            output_root / "all-variants-summary.json",
            {"schemaVersion": 1, "approved": False, "failure": str(error)},
        )
        print(f"Unit-test report verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
