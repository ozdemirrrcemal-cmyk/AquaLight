#!/usr/bin/env python3
"""Prevent AquaLight's approved brownfield ktlint debt from increasing."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class KtlintRatchetError(ValueError):
    """Raised when the ktlint baseline or ratchet policy is invalid."""


@dataclass(frozen=True)
class KtlintSnapshot:
    total: int
    rules: dict[str, int]
    source_sets: dict[str, int]
    areas: dict[str, int]


@dataclass(frozen=True)
class RatchetViolation:
    scope: str
    current: int
    approved: int

    @property
    def increase(self) -> int:
        return self.current - self.approved


def source_set_for(path: str) -> str:
    parts = path.split("/")
    if len(parts) >= 2 and parts[0] == "src":
        return "/".join(parts[:2])
    return "other"


def area_for(path: str) -> str:
    parts = path.split("/")
    main_prefix = ["src", "main", "java", "com", "aqua", "aqualight"]
    if len(parts) >= 7 and parts[:6] == main_prefix:
        return f"src/main/{parts[6]}"
    return source_set_for(path)


def read_baseline(path: Path) -> KtlintSnapshot:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise KtlintRatchetError(f"Cannot read ktlint baseline {path}: {exc}") from exc

    if root.tag != "baseline":
        raise KtlintRatchetError("ktlint baseline root must be <baseline>.")

    rule_counts: Counter[str] = Counter()
    source_set_counts: Counter[str] = Counter()
    area_counts: Counter[str] = Counter()

    for file_element in root.findall("file"):
        name = file_element.attrib.get("name")
        if not name:
            raise KtlintRatchetError("ktlint baseline contains a file without a name.")
        errors = file_element.findall("error")
        source_set_counts[source_set_for(name)] += len(errors)
        area_counts[area_for(name)] += len(errors)
        for error in errors:
            source = error.attrib.get("source")
            if not source:
                raise KtlintRatchetError(
                    f"ktlint baseline error in {name} has no source rule."
                )
            rule_counts[source] += 1

    return KtlintSnapshot(
        total=sum(rule_counts.values()),
        rules=dict(sorted(rule_counts.items())),
        source_sets=dict(sorted(source_set_counts.items())),
        areas=dict(sorted(area_counts.items())),
    )


def _read_count_map(raw: Any, name: str) -> dict[str, int]:
    if not isinstance(raw, dict):
        raise KtlintRatchetError(f"ktlint ratchet {name} must be an object.")
    result: dict[str, int] = {}
    for key, value in raw.items():
        if not isinstance(key, str) or not key:
            raise KtlintRatchetError(f"ktlint ratchet {name} has an invalid key.")
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise KtlintRatchetError(
                f"ktlint ratchet {name}[{key}] must be a non-negative integer."
            )
        result[key] = value
    return result


def read_policy(path: Path) -> KtlintSnapshot:
    try:
        raw: Any = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise KtlintRatchetError(f"Cannot read ktlint ratchet {path}: {exc}") from exc

    if not isinstance(raw, dict) or raw.get("schemaVersion") != 1:
        raise KtlintRatchetError("ktlint ratchet must use schemaVersion 1.")
    total = raw.get("totalViolations")
    if not isinstance(total, int) or isinstance(total, bool) or total < 0:
        raise KtlintRatchetError(
            "ktlint ratchet totalViolations must be a non-negative integer."
        )
    return KtlintSnapshot(
        total=total,
        rules=_read_count_map(raw.get("rules"), "rules"),
        source_sets=_read_count_map(raw.get("sourceSets"), "sourceSets"),
        areas=_read_count_map(raw.get("areas"), "areas"),
    )


def compare(current: KtlintSnapshot, approved: KtlintSnapshot) -> list[RatchetViolation]:
    violations: list[RatchetViolation] = []
    if current.total > approved.total:
        violations.append(
            RatchetViolation("total violations", current.total, approved.total)
        )

    for label, current_map, approved_map in (
        ("rule", current.rules, approved.rules),
        ("source set", current.source_sets, approved.source_sets),
        ("area", current.areas, approved.areas),
    ):
        for key, count in current_map.items():
            approved_count = approved_map.get(key, 0)
            if count > approved_count:
                violations.append(
                    RatchetViolation(f"{label} {key}", count, approved_count)
                )

    return sorted(violations, key=lambda item: (-item.increase, item.scope))


def render_summary(
    current: KtlintSnapshot,
    approved: KtlintSnapshot,
    violations: list[RatchetViolation],
) -> str:
    status = "PASS" if not violations else "FAIL"
    lines = [
        "## Ktlint debt ratchet",
        "",
        f"- Result: **{status}**",
        f"- Current violations: **{current.total}**",
        f"- Approved ceiling: **{approved.total}**",
        f"- Improvement: **{max(approved.total - current.total, 0)}**",
    ]
    if violations:
        lines.extend(
            [
                "",
                "| Scope | Current | Approved | Increase |",
                "|---|---:|---:|---:|",
            ]
        )
        for violation in violations[:50]:
            lines.append(
                f"| `{violation.scope}` | {violation.current} | "
                f"{violation.approved} | +{violation.increase} |"
            )
        if len(violations) > 50:
            lines.append(
                f"\nOnly the first 50 of {len(violations)} increases are shown."
            )
    return "\n".join(lines) + "\n"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Fail when measured ktlint debt exceeds the approved ratchet."
    )
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--summary-file", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        current = read_baseline(args.baseline)
        approved = read_policy(args.policy)
        violations = compare(current, approved)
    except KtlintRatchetError as exc:
        print(f"ktlint ratchet error: {exc}", file=sys.stderr)
        return 2

    summary = render_summary(current, approved, violations)
    print(summary, end="")
    if args.summary_file is not None:
        args.summary_file.parent.mkdir(parents=True, exist_ok=True)
        args.summary_file.write_text(summary, encoding="utf-8")

    if violations:
        print(
            f"Ktlint debt increased in {len(violations)} ratchet scope(s).",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
