#!/usr/bin/env python3
"""Enforce measured line-coverage thresholds against a JaCoCo-compatible XML report."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class CoverageGateError(ValueError):
    """Raised when coverage input or policy is invalid."""


@dataclass(frozen=True)
class CounterValue:
    missed: int
    covered: int

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def percentage(self) -> float:
        return 100.0 * self.covered / self.total if self.total else 0.0

    def __add__(self, other: "CounterValue") -> "CounterValue":
        return CounterValue(
            missed=self.missed + other.missed,
            covered=self.covered + other.covered,
        )


@dataclass(frozen=True)
class CoverageRule:
    name: str
    minimum_percent: float
    counter_type: str = "LINE"
    package_prefix: str | None = None


@dataclass(frozen=True)
class CoverageResult:
    rule: CoverageRule
    value: CounterValue
    matched_packages: int

    @property
    def passed(self) -> bool:
        return self.value.percentage + 1e-9 >= self.rule.minimum_percent


def normalize_package_prefix(prefix: str) -> str:
    return prefix.strip().strip("./").replace(".", "/")


def parse_counter(element: ET.Element, counter_type: str) -> CounterValue:
    for counter in element.findall("counter"):
        if counter.attrib.get("type") == counter_type:
            try:
                return CounterValue(
                    missed=int(counter.attrib["missed"]),
                    covered=int(counter.attrib["covered"]),
                )
            except (KeyError, ValueError) as exc:
                raise CoverageGateError(
                    f"Invalid {counter_type} counter in JaCoCo XML."
                ) from exc
    raise CoverageGateError(f"Missing {counter_type} counter in JaCoCo XML.")


def read_report(path: Path) -> tuple[CounterValue, dict[str, dict[str, CounterValue]]]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise CoverageGateError(f"Cannot read coverage report {path}: {exc}") from exc

    packages: dict[str, dict[str, CounterValue]] = {}
    for package in root.findall("package"):
        name = package.attrib.get("name")
        if not name:
            raise CoverageGateError("JaCoCo package element is missing its name.")
        package_counters: dict[str, CounterValue] = {}
        for counter in package.findall("counter"):
            counter_type = counter.attrib.get("type")
            if not counter_type:
                continue
            try:
                package_counters[counter_type] = CounterValue(
                    missed=int(counter.attrib["missed"]),
                    covered=int(counter.attrib["covered"]),
                )
            except (KeyError, ValueError) as exc:
                raise CoverageGateError(
                    f"Invalid counter for package {name}."
                ) from exc
        packages[name] = package_counters

    if not packages:
        raise CoverageGateError("Coverage report contains no package counters.")

    overall_line = parse_counter(root, "LINE")
    return overall_line, packages


def read_policy(path: Path) -> list[CoverageRule]:
    try:
        raw: Any = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CoverageGateError(f"Cannot read coverage policy {path}: {exc}") from exc

    if not isinstance(raw, dict) or raw.get("schemaVersion") != 1:
        raise CoverageGateError("Coverage policy must use schemaVersion 1.")
    raw_rules = raw.get("rules")
    if not isinstance(raw_rules, list) or not raw_rules:
        raise CoverageGateError("Coverage policy must contain at least one rule.")

    rules: list[CoverageRule] = []
    seen_names: set[str] = set()
    for index, item in enumerate(raw_rules, start=1):
        if not isinstance(item, dict):
            raise CoverageGateError(f"Coverage rule #{index} must be an object.")
        name = item.get("name")
        minimum = item.get("minimumPercent")
        counter_type = item.get("counter", "LINE")
        package_prefix = item.get("packagePrefix")
        if not isinstance(name, str) or not name.strip():
            raise CoverageGateError(f"Coverage rule #{index} has no valid name.")
        if name in seen_names:
            raise CoverageGateError(f"Duplicate coverage rule name: {name}")
        seen_names.add(name)
        if not isinstance(minimum, (int, float)) or isinstance(minimum, bool):
            raise CoverageGateError(f"Coverage rule {name} has no numeric minimumPercent.")
        minimum_float = float(minimum)
        if not 0.0 <= minimum_float <= 100.0:
            raise CoverageGateError(
                f"Coverage rule {name} minimumPercent must be in 0..100."
            )
        if not isinstance(counter_type, str) or not counter_type:
            raise CoverageGateError(f"Coverage rule {name} has an invalid counter.")
        if package_prefix is not None and (
            not isinstance(package_prefix, str) or not package_prefix.strip()
        ):
            raise CoverageGateError(
                f"Coverage rule {name} packagePrefix must be a non-empty string."
            )
        rules.append(
            CoverageRule(
                name=name.strip(),
                minimum_percent=minimum_float,
                counter_type=counter_type,
                package_prefix=(
                    normalize_package_prefix(package_prefix)
                    if package_prefix is not None
                    else None
                ),
            )
        )
    return rules


def evaluate(
    overall_line: CounterValue,
    packages: dict[str, dict[str, CounterValue]],
    rule: CoverageRule,
) -> CoverageResult:
    if rule.package_prefix is None:
        if rule.counter_type != "LINE":
            value = CounterValue(0, 0)
            for counters in packages.values():
                package_value = counters.get(rule.counter_type)
                if package_value is None:
                    raise CoverageGateError(
                        f"Package coverage report is missing {rule.counter_type}."
                    )
                value += package_value
        else:
            value = overall_line
        return CoverageResult(rule=rule, value=value, matched_packages=len(packages))

    prefix = rule.package_prefix
    value = CounterValue(0, 0)
    matched = 0
    for package_name, counters in packages.items():
        if package_name == prefix or package_name.startswith(prefix + "/"):
            package_value = counters.get(rule.counter_type)
            if package_value is None or package_value.total == 0:
                # JaCoCo can emit metadata-only packages without executable counters.
                continue
            value += package_value
            matched += 1

    if matched == 0:
        raise CoverageGateError(
            f"Coverage rule {rule.name} matched no executable package for prefix {prefix}."
        )
    if value.total == 0:
        raise CoverageGateError(
            f"Coverage rule {rule.name} matched packages with no executable lines."
        )
    return CoverageResult(rule=rule, value=value, matched_packages=matched)


def render_markdown(results: list[CoverageResult]) -> str:
    lines = [
        "| Coverage gate | Scope | Covered lines | Measured | Minimum | Result |",
        "|---|---|---:|---:|---:|---|",
    ]
    for result in results:
        scope = result.rule.package_prefix or "entire app"
        status = "PASS" if result.passed else "FAIL"
        lines.append(
            "| {name} | `{scope}` | {covered}/{total} | {measured:.2f}% | "
            "{minimum:.2f}% | {status} |".format(
                name=result.rule.name,
                scope=scope,
                covered=result.value.covered,
                total=result.value.total,
                measured=result.value.percentage,
                minimum=result.rule.minimum_percent,
                status=status,
            )
        )
    return "\n".join(lines) + "\n"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Enforce AquaLight package-level coverage thresholds."
    )
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--summary-file", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        overall_line, packages = read_report(args.report)
        rules = read_policy(args.policy)
        results = [evaluate(overall_line, packages, rule) for rule in rules]
    except CoverageGateError as exc:
        print(f"coverage gate error: {exc}", file=sys.stderr)
        return 2

    summary = render_markdown(results)
    print(summary, end="")
    if args.summary_file is not None:
        args.summary_file.parent.mkdir(parents=True, exist_ok=True)
        args.summary_file.write_text(summary, encoding="utf-8")

    failures = [result for result in results if not result.passed]
    if failures:
        print(
            "Coverage gate failed: "
            + ", ".join(result.rule.name for result in failures),
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
