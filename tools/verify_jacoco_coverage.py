#!/usr/bin/env python3
"""Fail-closed package coverage gate for AquaLight JaCoCo XML reports."""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def load_policy(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read policy {path}: {error}") from error
    if data.get("schemaVersion") != 1 or data.get("metricUnit") != "percent":
        raise ValueError("policy must use schemaVersion 1 and metricUnit percent")
    packages = data.get("packages")
    if not isinstance(packages, dict) or not packages:
        raise ValueError("policy must define packages")
    for name, thresholds in packages.items():
        if not isinstance(name, str) or not name or set(thresholds or {}) != {"line", "branch"}:
            raise ValueError(f"invalid package policy: {name!r}")
        for metric, minimum in thresholds.items():
            if isinstance(minimum, bool) or not isinstance(minimum, int) or not 0 <= minimum <= 100:
                raise ValueError(f"{name}.{metric} must be an integer from 0 to 100")
    return packages


def load_report(path):
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError(f"cannot read JaCoCo report {path}: {error}") from error
    if root.tag != "report":
        raise ValueError(f"unexpected JaCoCo root: {root.tag!r}")
    packages = {}
    for node in root.findall("package"):
        name = (node.get("name") or "").replace("/", ".")
        counters = {}
        for counter in node.findall("counter"):
            metric = (counter.get("type") or "").lower()
            if metric in {"line", "branch"}:
                counters[metric] = (int(counter.get("missed", "")), int(counter.get("covered", "")))
        if name:
            packages[name] = counters
    return packages


def main():
    args = arguments()
    try:
        policy = load_policy(args.policy)
        report = load_report(args.report)
    except (ValueError, TypeError) as error:
        print(f"Coverage policy error: {error}", file=sys.stderr)
        return 2

    failures = []
    results = []
    for name in sorted(policy):
        counters = report.get(name)
        if counters is None:
            failures.append(f"critical package missing from report: {name}")
            continue
        package_result = {"package": name, "metrics": {}}
        for metric in ("line", "branch"):
            if metric not in counters:
                failures.append(f"{name} has no {metric.upper()} counter")
                continue
            missed, covered = counters[metric]
            total = missed + covered
            if total == 0:
                failures.append(f"{name} {metric} counter has no executable items")
                continue
            minimum = policy[name][metric]
            actual = covered * 100.0 / total
            passed = covered * 100 >= minimum * total
            package_result["metrics"][metric] = {
                "minimumPercent": minimum,
                "actualPercent": round(actual, 2),
                "covered": covered,
                "missed": missed,
                "passed": passed,
            }
            if not passed:
                failures.append(f"{name} {metric} coverage {actual:.2f}% is below {minimum}%")
        results.append(package_result)

    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps({
        "schemaVersion": 1,
        "passed": not failures,
        "packages": results,
        "failures": failures,
    }, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    for item in results:
        values = [f"{metric}={data['actualPercent']:.2f}% (min {data['minimumPercent']}%)"
                  for metric, data in item["metrics"].items()]
        print(f"{item['package']}: " + ", ".join(values))
    if failures:
        print("Critical package coverage gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"Critical package coverage gate passed for {len(results)} packages.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
