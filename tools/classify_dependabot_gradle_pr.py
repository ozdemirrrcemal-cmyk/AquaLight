#!/usr/bin/env python3
"""Fail-closed auto-merge classification for trusted Dependabot Gradle PRs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

SEMVER_RE = re.compile(
    r"^(?P<major>0|[1-9][0-9]*)\."
    r"(?P<minor>0|[1-9][0-9]*)\."
    r"(?P<patch>0|[1-9][0-9]*)$"
)
DEPENDENCY_LINE_RE = re.compile(
    r"""
    ^\s*
    (?P<configuration>testImplementation|androidTestImplementation)
    \s*(?:\(\s*)?
    (?P<quote>['"])
    (?P<dependency>[^:'"\s]+:[^:'"\s]+)
    :
    (?P<version>[^'"\s]+)
    (?P=quote)
    \s*\)?\s*(?://.*)?$
    """,
    re.VERBOSE,
)
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
HEAD_REF_RE = re.compile(r"^dependabot/gradle/[A-Za-z0-9._/-]+$")


class PolicyError(ValueError):
    """Raised when policy inputs are malformed or unauditable."""


@dataclass(frozen=True)
class Policy:
    allowed_configurations: frozenset[str]
    allowed_dependencies: frozenset[str]
    allowed_update_types: frozenset[str]
    always_manual_dependencies: frozenset[str]
    always_manual_prefixes: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--base-manifest", required=True, type=Path)
    parser.add_argument("--head-manifest", required=True, type=Path)
    parser.add_argument(
        "--trust-state-changed",
        required=True,
        choices=("true", "false"),
    )
    parser.add_argument("--summary", required=True, type=Path)
    parser.add_argument("--github-output", type=Path)
    return parser.parse_args()


def read_json(path: Path, description: str) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PolicyError(f"cannot read {description} {path}: {error}") from error
    if not isinstance(value, dict):
        raise PolicyError(f"{description} must be a JSON object")
    return value


def require_exact_keys(
    value: dict[str, object],
    expected: set[str],
    description: str,
) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        details = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if unexpected:
            details.append("unexpected " + ", ".join(unexpected))
        raise PolicyError(f"{description} fields are invalid: {'; '.join(details)}")


def string_set(value: object, field: str) -> frozenset[str]:
    if not isinstance(value, list) or not value:
        raise PolicyError(f"{field} must be a non-empty array")
    if any(not isinstance(item, str) or not item.strip() for item in value):
        raise PolicyError(f"{field} entries must be non-empty strings")
    normalized = [item.strip() for item in value]
    if len(normalized) != len(set(normalized)):
        raise PolicyError(f"{field} entries must be unique")
    return frozenset(normalized)


def load_policy(path: Path) -> Policy:
    raw = read_json(path, "auto-merge policy")
    require_exact_keys(
        raw,
        {
            "schemaVersion",
            "allowedConfigurations",
            "allowedDependencies",
            "allowedUpdateTypes",
            "alwaysManualDependencies",
            "alwaysManualPrefixes",
        },
        "auto-merge policy",
    )
    if raw["schemaVersion"] != 1:
        raise PolicyError("auto-merge policy schemaVersion must be 1")
    prefixes = string_set(raw["alwaysManualPrefixes"], "alwaysManualPrefixes")
    return Policy(
        allowed_configurations=string_set(
            raw["allowedConfigurations"],
            "allowedConfigurations",
        ),
        allowed_dependencies=string_set(
            raw["allowedDependencies"],
            "allowedDependencies",
        ),
        allowed_update_types=string_set(
            raw["allowedUpdateTypes"],
            "allowedUpdateTypes",
        ),
        always_manual_dependencies=string_set(
            raw["alwaysManualDependencies"],
            "alwaysManualDependencies",
        ),
        always_manual_prefixes=tuple(sorted(prefixes)),
    )


def load_metadata(path: Path) -> dict[str, object]:
    metadata = read_json(path, "Dependabot metadata")
    require_exact_keys(
        metadata,
        {
            "schemaVersion",
            "pull_request",
            "head_ref",
            "head_sha",
            "base_sha",
            "source_run_id",
            "dependency_names",
            "dependency_type",
            "update_type",
            "package_ecosystem",
            "maintainer_changes",
            "initial_pr_files",
        },
        "Dependabot metadata",
    )
    if metadata["schemaVersion"] != 1:
        raise PolicyError("Dependabot metadata schemaVersion must be 1")
    if not isinstance(metadata["pull_request"], int) or metadata["pull_request"] < 1:
        raise PolicyError("Dependabot metadata pull_request is invalid")
    if not isinstance(metadata["source_run_id"], int) or metadata["source_run_id"] < 1:
        raise PolicyError("Dependabot metadata source_run_id is invalid")
    for field in ("head_sha", "base_sha"):
        if not isinstance(metadata[field], str) or not SHA_RE.fullmatch(metadata[field]):
            raise PolicyError(f"Dependabot metadata {field} is invalid")
    if (
        not isinstance(metadata["head_ref"], str)
        or not HEAD_REF_RE.fullmatch(metadata["head_ref"])
        or ".." in metadata["head_ref"]
        or metadata["head_ref"].endswith("/")
    ):
        raise PolicyError("Dependabot metadata head_ref is invalid")
    for field in (
        "dependency_type",
        "update_type",
        "package_ecosystem",
    ):
        if not isinstance(metadata[field], str) or not metadata[field].strip():
            raise PolicyError(f"Dependabot metadata {field} is invalid")
    if not isinstance(metadata["maintainer_changes"], bool):
        raise PolicyError("Dependabot metadata maintainer_changes must be boolean")
    dependency_names = string_set(
        metadata["dependency_names"],
        "dependency_names",
    )
    initial_pr_files = string_set(
        metadata["initial_pr_files"],
        "initial_pr_files",
    )
    metadata["dependency_names"] = sorted(dependency_names)
    metadata["initial_pr_files"] = sorted(initial_pr_files)
    return metadata


def semver(value: str) -> tuple[int, int, int] | None:
    match = SEMVER_RE.fullmatch(value)
    if match is None:
        return None
    return tuple(int(match.group(name)) for name in ("major", "minor", "patch"))


def classify_manifest_changes(
    base_manifest: str,
    head_manifest: str,
    metadata_names: set[str],
    policy: Policy,
) -> tuple[list[dict[str, object]], list[str]]:
    base_lines = base_manifest.splitlines()
    head_lines = head_manifest.splitlines()
    if len(base_lines) != len(head_lines):
        return [], ["app/build.gradle changed line structure"]

    changes: list[dict[str, object]] = []
    reasons: list[str] = []
    for index, (base_line, head_line) in enumerate(
        zip(base_lines, head_lines, strict=True),
        start=1,
    ):
        if base_line == head_line:
            continue
        base_match = DEPENDENCY_LINE_RE.fullmatch(base_line)
        head_match = DEPENDENCY_LINE_RE.fullmatch(head_line)
        if base_match is None or head_match is None:
            reasons.append(
                f"app/build.gradle line {index} is not an isolated test dependency pin"
            )
            continue
        base_values = base_match.groupdict()
        head_values = head_match.groupdict()
        dependency = base_values["dependency"]
        if dependency != head_values["dependency"]:
            reasons.append(f"app/build.gradle line {index} changes dependency identity")
            continue
        if base_values["configuration"] != head_values["configuration"]:
            reasons.append(f"app/build.gradle line {index} changes dependency scope")
            continue
        if base_values["configuration"] not in policy.allowed_configurations:
            reasons.append(f"{dependency} is not in an allowed test configuration")
            continue
        if dependency not in policy.allowed_dependencies:
            reasons.append(f"{dependency} is not on the test dependency allowlist")
            continue

        previous = semver(base_values["version"])
        updated = semver(head_values["version"])
        if previous is None or updated is None:
            reasons.append(f"{dependency} does not use strict MAJOR.MINOR.PATCH versions")
            continue
        if updated <= previous:
            reasons.append(f"{dependency} is not an upgrade")
            continue
        if updated[0] != previous[0]:
            reasons.append(f"{dependency} changes major version")
            continue
        if previous[0] == 0 and updated[1] != previous[1]:
            reasons.append(
                f"{dependency} changes a pre-1.0 minor version and may be breaking"
            )
            continue
        update_type = (
            "version-update:semver-minor"
            if updated[1] != previous[1]
            else "version-update:semver-patch"
        )
        changes.append(
            {
                "dependency": dependency,
                "configuration": base_values["configuration"],
                "previousVersion": base_values["version"],
                "newVersion": head_values["version"],
                "updateType": update_type,
                "line": index,
            }
        )

    changed_names = {change["dependency"] for change in changes}
    if changed_names != metadata_names:
        reasons.append(
            "manifest dependency changes do not exactly match Dependabot metadata"
        )
    if not changes:
        reasons.append("no isolated allowlisted test dependency change was found")
    return changes, reasons


def evaluate(
    policy: Policy,
    metadata: dict[str, object],
    base_manifest: str,
    head_manifest: str,
    trust_state_changed: bool,
) -> dict[str, object]:
    reasons: list[str] = []
    dependency_names = set(metadata["dependency_names"])

    if not trust_state_changed:
        reasons.append("Gradle trust refresh did not create a new final-head commit")
    if metadata["package_ecosystem"] != "gradle":
        reasons.append("package ecosystem is not Gradle")
    if metadata["update_type"] not in policy.allowed_update_types:
        reasons.append("Dependabot update type is not patch or minor")
    if metadata["dependency_type"] != "direct:development":
        reasons.append("Dependabot dependency type is not direct development")
    if metadata["maintainer_changes"]:
        reasons.append("Dependabot reports maintainer changes")
    if set(metadata["initial_pr_files"]) != {"app/build.gradle"}:
        reasons.append("initial PR scope is not limited to app/build.gradle")

    for dependency in sorted(dependency_names):
        if dependency in policy.always_manual_dependencies or any(
            dependency.startswith(prefix)
            for prefix in policy.always_manual_prefixes
        ):
            reasons.append(f"{dependency} always requires manual review")
        elif dependency not in policy.allowed_dependencies:
            reasons.append(f"{dependency} is not on the test dependency allowlist")

    changes, manifest_reasons = classify_manifest_changes(
        base_manifest,
        head_manifest,
        dependency_names,
        policy,
    )
    reasons.extend(manifest_reasons)
    expected_update_type = (
        "version-update:semver-minor"
        if any(
            change["updateType"] == "version-update:semver-minor"
            for change in changes
        )
        else "version-update:semver-patch"
    )
    if changes and metadata["update_type"] != expected_update_type:
        reasons.append("Dependabot update type does not match manifest version changes")

    return {
        "schemaVersion": 1,
        "eligible": not reasons,
        "pullRequest": metadata["pull_request"],
        "headSha": metadata["head_sha"],
        "baseSha": metadata["base_sha"],
        "dependencies": sorted(dependency_names),
        "changes": changes,
        "reasons": list(dict.fromkeys(reasons)),
    }


def main() -> int:
    args = parse_args()
    try:
        policy = load_policy(args.policy)
        metadata = load_metadata(args.metadata)
        base_manifest = args.base_manifest.read_text(encoding="utf-8")
        head_manifest = args.head_manifest.read_text(encoding="utf-8")
        summary = evaluate(
            policy,
            metadata,
            base_manifest,
            head_manifest,
            args.trust_state_changed == "true",
        )
    except (OSError, PolicyError) as error:
        print(f"Dependabot Gradle auto-merge policy failed: {error}", file=sys.stderr)
        return 1

    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    if args.github_output is not None:
        with args.github_output.open("a", encoding="utf-8") as handle:
            handle.write(f"eligible={'true' if summary['eligible'] else 'false'}\n")

    if summary["eligible"]:
        print("Dependabot Gradle update is eligible for controlled auto-merge.")
    else:
        print(
            "Dependabot Gradle update requires manual review: "
            + "; ".join(summary["reasons"])
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
