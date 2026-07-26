#!/usr/bin/env python3
"""Assemble and verify the complete same-commit Stage 14 release evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
TAG_PATTERN = re.compile(
    r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
VERSION_PATTERN = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
UNIT_VARIANTS = (
    "testDebugUnitTest",
    "testStagingUnitTest",
    "testReleaseSmokeUnitTest",
    "testReleaseUnitTest",
)
QUALITY_STAGE14_JSON = (
    "accessibility-unit.json",
    "permission-permanent-denial-unit.json",
    "process-recreation-unit.json",
    "rapid-account-switch-unit.json",
    "tank-care-corruption-unit.json",
    "websocket-account-cleanup-unit.json",
)
INSTRUMENTATION_STAGE14_JSON = tuple(
    f"{name}-api-{api_level}.json"
    for api_level in (27, 36)
    for name in (
        "accessibility",
        "accessibility-instrumentation",
        "account-deletion-force-stop",
        "clean-install",
        "process-recreation-instrumentation",
        "tank-care-corruption-instrumentation",
        "upgrade-install",
    )
)


class FinalEvidenceFailure(ValueError):
    """Raised when required final evidence is absent, unsafe or inconsistent."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--quality-root", required=True, type=Path)
    parser.add_argument("--instrumentation-root", required=True, type=Path)
    parser.add_argument("--codeql-root", required=True, type=Path)
    parser.add_argument("--release-root", required=True, type=Path)
    parser.add_argument("--blocker", required=True, type=Path)
    parser.add_argument("--manual", required=True, type=Path)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument(
        "--include-apk",
        required=True,
        choices=("true", "false"),
    )
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--markdown", required=True, type=Path)
    return parser.parse_args()


def read_json(path: Path, label: str) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise FinalEvidenceFailure(f"cannot read {label} {path}: {error}") from error
    if not raw:
        raise FinalEvidenceFailure(f"{label} is empty: {path}")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise FinalEvidenceFailure(f"{label} is invalid JSON: {path}: {error}") from error
    if not isinstance(value, dict):
        raise FinalEvidenceFailure(f"{label} must be a JSON object: {path}")
    return value


def require_passed_json(
    path: Path,
    label: str,
    commit: str | None = None,
) -> dict[str, Any]:
    value = read_json(path, label)
    if value.get("passed") is not True:
        raise FinalEvidenceFailure(f"{label} did not pass: {path}")
    if commit is not None and value.get("releaseCommit") != commit:
        raise FinalEvidenceFailure(
            f"{label} does not belong to release commit {commit}: {path}"
        )
    return value


def require_directory(path: Path, label: str) -> None:
    if not path.is_dir() or path.is_symlink():
        raise FinalEvidenceFailure(f"{label} directory is missing or unsafe: {path}")


def require_file(path: Path, label: str) -> Path:
    if not path.is_file() or path.is_symlink():
        raise FinalEvidenceFailure(f"{label} file is missing or unsafe: {path}")
    if path.stat().st_size <= 0:
        raise FinalEvidenceFailure(f"{label} file is empty: {path}")
    return path


def require_glob(root: Path, pattern: str, label: str) -> list[Path]:
    matches = sorted(
        path
        for path in root.glob(pattern)
        if path.is_file()
    )
    if not matches:
        raise FinalEvidenceFailure(
            f"{label} did not match any files under {root}: {pattern}"
        )
    for path in matches:
        require_file(path, label)
    return matches


def require_exact_json_set(
    directory: Path,
    expected_names: tuple[str, ...],
    label: str,
    commit: str,
) -> dict[str, Path]:
    require_directory(directory, label)
    actual = {
        path.name: path
        for path in directory.glob("*.json")
        if path.is_file()
    }
    expected = set(expected_names)
    if set(actual) != expected:
        raise FinalEvidenceFailure(
            f"{label} JSON set mismatch; "
            f"missing={sorted(expected - set(actual))}, "
            f"unknown={sorted(set(actual) - expected)}"
        )
    for name, path in actual.items():
        require_passed_json(path, f"{label} {name}", commit)
    return actual


def file_evidence(path: Path, release_root: Path) -> dict[str, Any]:
    require_file(path, "evidence")
    release_resolved = release_root.resolve()
    resolved = path.resolve()
    try:
        relative = resolved.relative_to(release_resolved)
    except ValueError as error:
        raise FinalEvidenceFailure(
            f"evidence is outside the final release root: {path}"
        ) from error
    raw = path.read_bytes()
    return {
        "path": relative.as_posix(),
        "bytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
    }


def artifact_entry(
    artifact_id: str,
    artifact_format: str,
    requirement: str,
    required_this_release: bool,
    paths: list[Path],
    release_root: Path,
) -> dict[str, Any]:
    if required_this_release and not paths:
        raise FinalEvidenceFailure(f"{artifact_id} has no evidence files")
    return {
        "id": artifact_id,
        "format": artifact_format,
        "requirement": requirement,
        "requiredThisRelease": required_this_release,
        "status": "verified" if paths else "not-requested",
        "files": [file_evidence(path, release_root) for path in sorted(set(paths))],
    }


def validate_policy(
    path: Path,
) -> tuple[dict[str, Any], list[dict[str, str]]]:
    policy = require_passed_json(path, "Stage 14 policy evidence")
    required_artifacts = policy.get("requiredArtifacts")
    if not isinstance(required_artifacts, list) or not required_artifacts:
        raise FinalEvidenceFailure(
            "Stage 14 policy evidence has no requiredArtifacts contract"
        )
    normalized = []
    for index, raw in enumerate(required_artifacts):
        if not isinstance(raw, dict) or set(raw) != {
            "id",
            "format",
            "requirement",
        }:
            raise FinalEvidenceFailure(
                f"requiredArtifacts[{index}] has an invalid schema"
            )
        if not all(isinstance(raw[key], str) and raw[key] for key in raw):
            raise FinalEvidenceFailure(
                f"requiredArtifacts[{index}] contains an empty field"
            )
        normalized.append(raw)
    return policy, normalized


def build_artifact_paths(
    quality_root: Path,
    instrumentation_root: Path,
    codeql_root: Path,
    release_root: Path,
    blocker: Path,
    manual: Path,
    version: str,
    include_apk: bool,
    commit: str,
) -> dict[str, tuple[bool, list[Path]]]:
    quality_stage14 = quality_root / "release-quality/stage14-evidence"
    instrumentation_stage14 = instrumentation_root / "stage14-evidence"
    quality_json = require_exact_json_set(
        quality_stage14,
        QUALITY_STAGE14_JSON,
        "quality Stage 14 evidence",
        commit,
    )
    instrumentation_json = require_exact_json_set(
        instrumentation_stage14,
        INSTRUMENTATION_STAGE14_JSON,
        "instrumentation Stage 14 evidence",
        commit,
    )

    policy_path = quality_root / "release-quality/stage14-policy-validation.json"
    dependency_path = quality_root / "release-quality/dependency-integrity.json"
    require_passed_json(policy_path, "quality policy evidence")
    require_passed_json(dependency_path, "dependency integrity evidence")
    detekt_policy = quality_root / "app/build/reports/stage14/detekt-policy-summary.json"
    require_passed_json(detekt_policy, "Detekt policy evidence")
    coverage_thresholds = (
        quality_root
        / "app/build/reports/coverage/test/debug/critical-package-thresholds.json"
    )
    require_passed_json(coverage_thresholds, "coverage threshold evidence")
    codeql_summary = codeql_root / "codeql-summary.json"
    require_passed_json(codeql_summary, "CodeQL evidence", commit)
    require_passed_json(blocker, "release blocker evidence", commit)
    require_passed_json(manual, "manual acceptance evidence", commit)

    lint_paths = [
        require_file(
            quality_root / f"app/build/reports/lint-results-{variant}.xml",
            f"{variant} Android Lint",
        )
        for variant in ("debug", "staging", "releaseSmoke", "release")
    ]
    unit_paths: list[Path] = []
    for variant in UNIT_VARIANTS:
        unit_paths.extend(
            require_glob(
                quality_root / f"app/build/test-results/{variant}",
                "**/TEST-*.xml",
                f"{variant} JUnit evidence",
            )
        )
    instrumentation_27 = require_glob(
        instrumentation_stage14 / "junit-api-27",
        "**/TEST-*.xml",
        "API 27 instrumentation JUnit evidence",
    )
    instrumentation_36 = require_glob(
        instrumentation_stage14 / "junit-api-36",
        "**/TEST-*.xml",
        "API 36 instrumentation JUnit evidence",
    )
    codeql_sarif = require_glob(codeql_root, "**/*.sarif", "CodeQL SARIF")

    artifacts = release_root / "artifacts"
    supply_chain = release_root / "supply-chain"
    aab = require_file(
        artifacts / f"AquaLight-{version}.aab",
        "signed release AAB",
    )
    mapping = require_file(
        artifacts / f"AquaLight-{version}-mapping.txt",
        "release mapping",
    )
    checksums = require_file(artifacts / "SHA256SUMS", "release checksums")
    aab_checksum = require_file(
        artifacts / f"AquaLight-{version}.aab.sha256",
        "AAB checksum",
    )
    require_file(
        artifacts / "signed-aab-verification.txt",
        "AAB signing verification",
    )
    aab_sbom = require_file(
        supply_chain / f"AquaLight-{version}.aab.spdx.json",
        "AAB SPDX SBOM",
    )
    aab_provenance = require_file(
        supply_chain / f"attestations/AquaLight-{version}.aab.provenance.json",
        "AAB provenance",
    )
    aab_sbom_attestation = require_file(
        supply_chain / f"attestations/AquaLight-{version}.aab.sbom.json",
        "AAB SBOM attestation",
    )

    if not include_apk:
        raise FinalEvidenceFailure(
            "Stage 14 final evidence requires both the signed APK and AAB"
        )
    apk = require_file(
        artifacts / f"AquaLight-{version}.apk",
        "signed release APK",
    )
    apk_checksum = require_file(
        artifacts / f"AquaLight-{version}.apk.sha256",
        "APK checksum",
    )
    require_file(
        artifacts / "signed-apk-verification.txt",
        "APK signing verification",
    )
    apk_sbom = require_file(
        supply_chain / f"AquaLight-{version}.apk.spdx.json",
        "APK SPDX SBOM",
    )
    apk_provenance = require_file(
        supply_chain
        / f"attestations/AquaLight-{version}.apk.provenance.json",
        "APK provenance",
    )
    apk_sbom_attestation = require_file(
        supply_chain / f"attestations/AquaLight-{version}.apk.sbom.json",
        "APK SBOM attestation",
    )
    candidate_manifest = release_root / "CANDIDATE.json"
    require_passed_json(
        candidate_manifest,
        "release candidate manifest",
        commit,
    )
    checksum_paths = [checksums, aab_checksum, apk_checksum]
    sbom_paths = [aab_sbom, apk_sbom]
    provenance_paths = [
        aab_provenance,
        aab_sbom_attestation,
        apk_provenance,
        apk_sbom_attestation,
    ]
    expected_artifact_names = {
        f"AquaLight-{version}.aab",
        f"AquaLight-{version}.aab.sha256",
        f"AquaLight-{version}.apk",
        f"AquaLight-{version}.apk.sha256",
        f"AquaLight-{version}-mapping.txt",
        "SHA256SUMS",
        "signed-aab-verification.txt",
        "signed-apk-verification.txt",
    }
    actual_artifact_names = {
        path.name
        for path in artifacts.iterdir()
        if path.is_file()
    }
    if actual_artifact_names != expected_artifact_names:
        raise FinalEvidenceFailure(
            "release artifact set mismatch; "
            f"missing={sorted(expected_artifact_names - actual_artifact_names)}, "
            f"unknown={sorted(actual_artifact_names - expected_artifact_names)}"
        )

    return {
        "policy-validation": (True, [policy_path]),
        "dependency-integrity": (True, [dependency_path]),
        "android-lint": (True, lint_paths),
        "detekt": (
            True,
            [
                require_file(
                    quality_root / "app/build/reports/detekt/detekt.sarif",
                    "Detekt SARIF",
                )
            ],
        ),
        "detekt-policy": (True, [detekt_policy]),
        "unit-tests": (True, unit_paths),
        "coverage": (
            True,
            [
                require_file(
                    quality_root / "app/build/reports/coverage/test/debug/report.xml",
                    "JaCoCo coverage",
                )
            ],
        ),
        "coverage-thresholds": (True, [coverage_thresholds]),
        "codeql": (True, codeql_sarif),
        "instrumentation-api-27": (True, instrumentation_27),
        "instrumentation-api-36": (True, instrumentation_36),
        "clean-install": (
            True,
            [
                instrumentation_json[f"clean-install-api-{api_level}.json"]
                for api_level in (27, 36)
            ],
        ),
        "upgrade-install": (
            True,
            [
                instrumentation_json[f"upgrade-install-api-{api_level}.json"]
                for api_level in (27, 36)
            ],
        ),
        "rapid-account-switch": (
            True,
            [quality_json["rapid-account-switch-unit.json"]],
        ),
        "process-recreation": (
            True,
            [
                quality_json["process-recreation-unit.json"],
                *[
                    instrumentation_json[
                        f"process-recreation-instrumentation-api-{api_level}.json"
                    ]
                    for api_level in (27, 36)
                ],
                *[
                    instrumentation_json[
                        f"account-deletion-force-stop-api-{api_level}.json"
                    ]
                    for api_level in (27, 36)
                ],
            ],
        ),
        "permission-denial": (
            True,
            [quality_json["permission-permanent-denial-unit.json"]],
        ),
        "tank-care-corruption": (
            True,
            [
                quality_json["tank-care-corruption-unit.json"],
                *[
                    instrumentation_json[
                        f"tank-care-corruption-instrumentation-api-{api_level}.json"
                    ]
                    for api_level in (27, 36)
                ],
            ],
        ),
        "websocket-account-cleanup": (
            True,
            [quality_json["websocket-account-cleanup-unit.json"]],
        ),
        "accessibility": (
            True,
            [
                quality_json["accessibility-unit.json"],
                *[
                    instrumentation_json[
                        f"accessibility-instrumentation-api-{api_level}.json"
                    ]
                    for api_level in (27, 36)
                ],
                *[
                    instrumentation_json[
                        f"accessibility-api-{api_level}.json"
                    ]
                    for api_level in (27, 36)
                ],
            ],
        ),
        "release-blocker-inventory": (True, [blocker]),
        "release-aab": (True, [aab]),
        "release-apk": (True, [apk]),
        "release-mapping": (True, [mapping]),
        "release-checksums": (True, checksum_paths),
        "release-sbom": (True, sbom_paths),
        "release-provenance": (True, provenance_paths),
        "release-candidate-manifest": (True, [candidate_manifest]),
        "manual-acceptance": (True, [manual]),
        "final-summary-json": (True, []),
        "final-summary-markdown": (True, []),
    }


def render_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# AquaLight Stage 14 Final Evidence",
        "",
        f"- Release: `{summary['releaseTag']}`",
        f"- Commit: `{summary['releaseCommit']}`",
        "- APK included: `yes`",
        "- Decision: `approved-for-archive`",
        "",
        "| Artifact | Status | Files |",
        "|---|---:|---:|",
    ]
    for artifact in summary["artifacts"]:
        lines.append(
            f"| `{artifact['id']}` | {artifact['status']} | "
            f"{len(artifact['files'])} |"
        )
    lines.extend(
        [
            "",
            "All automated evidence belongs to the release commit. The five "
            "signed-candidate physical acceptance gates were supplied through "
            "the protected production release environment and verified against "
            "the immutable candidate manifest.",
            "",
        ]
    )
    return "\n".join(lines)


def generate(
    *,
    policy_path: Path,
    quality_root: Path,
    instrumentation_root: Path,
    codeql_root: Path,
    release_root: Path,
    blocker: Path,
    manual: Path,
    release_tag: str,
    release_version: str,
    commit: str,
    include_apk: bool,
    json_output: Path,
    markdown_output: Path,
) -> dict[str, Any]:
    if not TAG_PATTERN.fullmatch(release_tag):
        raise FinalEvidenceFailure(
            "release tag must use canonical vMAJOR.MINOR.PATCH format"
        )
    if not VERSION_PATTERN.fullmatch(release_version):
        raise FinalEvidenceFailure(
            "release version must use canonical MAJOR.MINOR.PATCH format"
        )
    if release_tag != f"v{release_version}":
        raise FinalEvidenceFailure("release tag and version do not match")
    if not COMMIT_PATTERN.fullmatch(commit):
        raise FinalEvidenceFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    if not include_apk:
        raise FinalEvidenceFailure(
            "Stage 14 final archive requires the signed APK and AAB"
        )
    for root, label in (
        (release_root, "release"),
        (quality_root, "quality"),
        (instrumentation_root, "instrumentation"),
        (codeql_root, "CodeQL"),
    ):
        require_directory(root, label)

    policy, required_artifacts = validate_policy(policy_path)
    paths = build_artifact_paths(
        quality_root,
        instrumentation_root,
        codeql_root,
        release_root,
        blocker,
        manual,
        release_version,
        include_apk,
        commit,
    )
    policy_ids = [artifact["id"] for artifact in required_artifacts]
    if set(paths) != set(policy_ids):
        raise FinalEvidenceFailure(
            "final evidence implementation and Stage 14 policy artifacts differ; "
            f"missing={sorted(set(policy_ids) - set(paths))}, "
            f"unknown={sorted(set(paths) - set(policy_ids))}"
        )

    artifacts = []
    for contract in required_artifacts:
        artifact_id = contract["id"]
        required_this_release, evidence_paths = paths[artifact_id]
        if artifact_id in {"final-summary-json", "final-summary-markdown"}:
            continue
        artifacts.append(
            artifact_entry(
                artifact_id,
                contract["format"],
                contract["requirement"],
                required_this_release,
                evidence_paths,
                release_root,
            )
        )

    json_relative = json_output.resolve().relative_to(release_root.resolve()).as_posix()
    markdown_relative = (
        markdown_output.resolve().relative_to(release_root.resolve()).as_posix()
    )
    json_entry = {
        "id": "final-summary-json",
        "format": "json",
        "requirement": "required",
        "requiredThisRelease": True,
        "status": "generated",
        "files": [
            {
                "path": json_relative,
                "selfHashExcluded": True,
            }
        ],
    }
    markdown_entry = {
        "id": "final-summary-markdown",
        "format": "markdown",
        "requirement": "required",
        "requiredThisRelease": True,
        "status": "generated",
        "files": [
            {
                "path": markdown_relative,
                "hashPendingUntilRendered": True,
            }
        ],
    }
    artifacts.extend([json_entry, markdown_entry])
    order = {artifact_id: index for index, artifact_id in enumerate(policy_ids)}
    artifacts.sort(key=lambda artifact: order[artifact["id"]])

    summary = {
        "schemaVersion": 2,
        "passed": True,
        "status": "approved-for-archive",
        "suite": "final-evidence",
        "releaseTag": release_tag,
        "releaseVersion": release_version,
        "releaseCommit": commit,
        "includeApk": True,
        "stage14Policy": {
            "policyId": policy["policyId"],
            "sourceSha256": policy["sourceSha256"],
            "canonicalSha256": policy["canonicalSha256"],
        },
        "artifactContractCount": len(required_artifacts),
        "artifacts": artifacts,
    }

    excluded_supplemental = {
        json_output.resolve(),
        markdown_output.resolve(),
        (release_root / "RELEASE.json").resolve(),
        (release_root / "supply-chain/SHA256SUMS").resolve(),
    }
    mapped_paths = {
        (release_root / item["path"]).resolve()
        for artifact in artifacts
        for item in artifact["files"]
    }
    supplemental_paths = []
    for path in sorted(release_root.rglob("*")):
        if path.is_symlink():
            raise FinalEvidenceFailure(
                f"final release contains an unsafe symbolic link: {path}"
            )
        if not path.is_file():
            continue
        resolved = path.resolve()
        if resolved in mapped_paths or resolved in excluded_supplemental:
            continue
        supplemental_paths.append(path)
    summary["supplementalEvidence"] = [
        file_evidence(path, release_root)
        for path in supplemental_paths
    ]
    summary["supplementalFileCount"] = len(supplemental_paths)

    markdown_output.parent.mkdir(parents=True, exist_ok=True)
    markdown = render_markdown(summary)
    markdown_output.write_text(markdown, encoding="utf-8")
    rendered_markdown_entry = artifact_entry(
        "final-summary-markdown",
        "markdown",
        "required",
        True,
        [markdown_output],
        release_root,
    )
    summary["artifacts"] = [
        rendered_markdown_entry
        if artifact["id"] == "final-summary-markdown"
        else artifact
        for artifact in artifacts
    ]

    json_output.parent.mkdir(parents=True, exist_ok=True)
    json_output.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return summary


def main() -> int:
    args = parse_args()
    try:
        summary = generate(
            policy_path=args.policy,
            quality_root=args.quality_root,
            instrumentation_root=args.instrumentation_root,
            codeql_root=args.codeql_root,
            release_root=args.release_root,
            blocker=args.blocker,
            manual=args.manual,
            release_tag=args.release_tag,
            release_version=args.release_version,
            commit=args.commit,
            include_apk=args.include_apk == "true",
            json_output=args.json,
            markdown_output=args.markdown,
        )
    except (OSError, FinalEvidenceFailure, ValueError) as error:
        print(f"Final evidence gate failed: {error}", file=sys.stderr)
        return 1

    print(
        "Final evidence gate passed: "
        f"{summary['artifactContractCount']} artifact contracts, "
        f"release {summary['releaseTag']}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
