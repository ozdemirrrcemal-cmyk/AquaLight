#!/usr/bin/env python3
"""Summarize JaCoCo-compatible Kover XML coverage for threshold calibration."""

from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


def percentage(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 if total == 0 else round(covered * 100.0 / total, 2)


def line_counter(element: ET.Element) -> dict[str, Any]:
    counter = next(
        (item for item in element.findall("counter") if item.get("type") == "LINE"),
        None,
    )
    covered = int(counter.get("covered", "0")) if counter is not None else 0
    missed = int(counter.get("missed", "0")) if counter is not None else 0
    return {
        "covered": covered,
        "missed": missed,
        "total": covered + missed,
        "percent": percentage(covered, missed),
    }


def summarize(xml_path: Path) -> dict[str, Any]:
    root = ET.parse(xml_path).getroot()
    packages: list[dict[str, Any]] = []
    classes: list[dict[str, Any]] = []

    for package in root.findall("package"):
        package_name = package.get("name", "").replace("/", ".")
        package_entry = {"name": package_name, **line_counter(package)}
        packages.append(package_entry)
        for klass in package.findall("class"):
            class_name = klass.get("name", "").replace("/", ".")
            classes.append({"name": class_name, "package": package_name, **line_counter(klass)})

    packages.sort(key=lambda item: (-item["total"], item["name"]))
    classes.sort(key=lambda item: (-item["total"], item["name"]))
    return {"overall": line_counter(root), "packages": packages, "classes": classes}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xml", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    summary = summarize(args.xml)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary["overall"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
