#!/usr/bin/env python3
"""Create and verify the immutable AquaLight signed release-candidate manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

SCHEMA_VERSION = 1
APPLICATION_ID = "com.aqua.aqualight"
TAG_PATTERN = re.compile(
    r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
VERSION_PATTERN = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
RUN_ID_PATTERN = re.compile(r"^[1-9][0-9]*$")
REPOSITORY_PATTERN = re.compile(
    r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"
)
ROOT_KEYS = {
    "schemaVersion",
    "passed",
    "suite",
    "status",
    "repository",
    "workflowRunId",
    "releaseTag",
    "releaseVersion",
    "releaseCommit",
    "applicationId",
    "versionName",
    "versionCode",
    "signingCertificateSha256",
    "artifactDigests",
    "files",
}
FILE_KEYS = {"id", "path", "bytes", "sha256"}
ARTIFACT_DIGEST_KEYS = {
    "aabSha256",
    "apkSha256",
    "mappingSha256",
}


class CandidateManifestFailure(ValueError):
    """Raised when candidate identity or retained files are unsafe."""


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CandidateManifestFailure(f"{path} must be an object")
    return value


def require_exact_keys(
    value: dict[str, Any],
    expected: set[str],
    path: str,
) -> None:
    actual = set(value)
    if actual != expected:
        raise CandidateManifestFailure(
            f"{path} keys mismatch; missing={sorted(expected - actual)}, "
            f"unknown={sorted(actual - expected)}"
        )


def require_string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value:
        raise CandidateManifestFailure(f"{path} must be a non-empty string")
    if value != value.strip():
        raise CandidateManifestFailure(f"{path} must not contain edge whitespace")
    return value


def normalize_sha256(value: str, path: str) -> str:
    normalized = value.strip()
    if normalized.lower().startswith("sha256:"):
        normalized = normalized.split(":", 1)[1]
    normalized = normalized.replace(":", "").strip().lower()
    if not SHA256_PATTERN.fullmatch(normalized):
        raise CandidateManifestFailure(f"{path} must be a SHA-256 digest")
    return normalized


def validate_identity(
    release_tag: str,
    release_version: str,
    release_commit: str,
    workflow_run_id: str,
    repository: str,
    version_name: str,
    version_code: int,
    signing_certificate_sha256: str,
) -> str:
    if not TAG_PATTERN.fullmatch(release_tag):
        raise CandidateManifestFailure(
            "release tag must use canonical vMAJOR.MINOR.PATCH format"
        )
    if not VERSION_PATTERN.fullmatch(release_version):
        raise CandidateManifestFailure(
            "release version must use canonical MAJOR.MINOR.PATCH format"
        )
    if release_tag != f"v{release_version}":
        raise CandidateManifestFailure("release tag and version do not match")
    if not COMMIT_PATTERN.fullmatch(release_commit):
        raise CandidateManifestFailure(
            "release commit must be a lowercase 40-character Git SHA"
        )
    if not RUN_ID_PATTERN.fullmatch(workflow_run_id):
        raise CandidateManifestFailure(
            "workflow run ID must be a canonical positive integer"
        )
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise CandidateManifestFailure("repository must use owner/name form")
    if version_name != release_version:
        raise CandidateManifestFailure(
            "signed APK versionName does not match the release version"
        )
    if isinstance(version_code, bool) or not isinstance(version_code, int):
        raise CandidateManifestFailure("signed APK versionCode must be an integer")
    if version_code < 1 or version_code > 2_100_000_000:
        raise CandidateManifestFailure(
            "signed APK versionCode is outside the supported Android range"
        )
    return normalize_sha256(
        signing_certificate_sha256,
        "signing certificate",
    )


def expected_files(version: str) -> tuple[tuple[str, str], ...]:
    return (
        ("release-aab", f"artifacts/AquaLight-{version}.aab"),
        ("release-apk", f"artifacts/AquaLight-{version}.apk"),
        ("release-mapping", f"artifacts/AquaLight-{version}-mapping.txt"),
        ("release-checksums", "artifacts/SHA256SUMS"),
        ("aab-checksum", f"artifacts/AquaLight-{version}.aab.sha256"),
        ("apk-checksum", f"artifacts/AquaLight-{version}.apk.sha256"),
        ("aab-signature", "artifacts/signed-aab-verification.txt"),
        ("apk-signature", "artifacts/signed-apk-verification.txt"),
        (
            "aab-sbom",
            f"supply-chain/AquaLight-{version}.aab.spdx.json",
        ),
        (
            "apk-sbom",
            f"supply-chain/AquaLight-{version}.apk.spdx.json",
        ),
        (
            "aab-provenance",
            f"supply-chain/attestations/AquaLight-{version}.aab.provenance.json",
        ),
        (
            "aab-sbom-attestation",
            f"supply-chain/attestations/AquaLight-{version}.aab.sbom.json",
        ),
        (
            "apk-provenance",
            f"supply-chain/attestations/AquaLight-{version}.apk.provenance.json",
        ),
        (
            "apk-sbom-attestation",
            f"supply-chain/attestations/AquaLight-{version}.apk.sbom.json",
        ),
        (
            "stage14-policy",
            "supply-chain/stage14-validation-policy.json",
        ),
        (
            "codeql-summary",
            "supply-chain/security/codeql/codeql-summary.json",
        ),
        (
            "candidate-blocker-inventory",
            "validation/candidate-release-blocker-inventory.json",
        ),
    )


def safe_file(root: Path, relative: str, label: str) -> Path:
    root_resolved = root.resolve()
    path = (root / relative).resolve()
    try:
        path.relative_to(root_resolved)
    except ValueError as error:
        raise CandidateManifestFailure(
            f"{label} escapes the candidate root: {relative}"
        ) from error
    if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
        raise CandidateManifestFailure(
            f"{label} is missing, empty or unsafe: {relative}"
        )
    return path


def file_record(root: Path, artifact_id: str, relative: str) -> dict[str, Any]:
    path = safe_file(root, relative, artifact_id)
    raw = path.read_bytes()
    return {
        "id": artifact_id,
        "path": relative,
        "bytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
    }


def validate_checksum_contract(
    root: Path,
    version: str,
    records: dict[str, dict[str, Any]],
) -> None:
    expected_binaries = {
        f"AquaLight-{version}.aab": records["release-aab"]["sha256"],
        f"AquaLight-{version}.apk": records["release-apk"]["sha256"],
    }
    manifest = safe_file(
        root,
        "artifacts/SHA256SUMS",
        "binary checksum manifest",
    )
    actual: dict[str, str] = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        if match is None:
            raise CandidateManifestFailure(
                "artifacts/SHA256SUMS has a noncanonical row"
            )
        digest, name = match.groups()
        if name in actual:
            raise CandidateManifestFailure(
                f"artifacts/SHA256SUMS repeats {name}"
            )
        actual[name] = digest
    if actual != expected_binaries:
        raise CandidateManifestFailure(
            "artifacts/SHA256SUMS does not match the signed APK and AAB"
        )

    for artifact_id, name in (
        ("release-aab", f"AquaLight-{version}.aab"),
        ("release-apk", f"AquaLight-{version}.apk"),
    ):
        checksum = safe_file(
            root,
            f"artifacts/{name}.sha256",
            f"{artifact_id} checksum",
        ).read_text(encoding="utf-8")
        if checksum != f"{records[artifact_id]['sha256']}  {name}\n":
            raise CandidateManifestFailure(
                f"{artifact_id} checksum file is not canonical"
            )


def require_passed_json(
    root: Path,
    relative: str,
    label: str,
    release_commit: str | None = None,
) -> None:
    path = safe_file(root, relative, label)
    try:
        value = json.loads(path.read_bytes())
    except json.JSONDecodeError as error:
        raise CandidateManifestFailure(f"{label} is invalid JSON") from error
    if not isinstance(value, dict) or value.get("passed") is not True:
        raise CandidateManifestFailure(f"{label} did not pass")
    if (
        release_commit is not None
        and value.get("releaseCommit") != release_commit
    ):
        raise CandidateManifestFailure(
            f"{label} does not belong to the release commit"
        )


def build_manifest(
    *,
    root: Path,
    release_tag: str,
    release_version: str,
    release_commit: str,
    workflow_run_id: str,
    repository: str,
    version_name: str,
    version_code: int,
    signing_certificate_sha256: str,
) -> dict[str, Any]:
    if not root.is_dir() or root.is_symlink():
        raise CandidateManifestFailure(
            f"candidate root is missing or unsafe: {root}"
        )
    certificate = validate_identity(
        release_tag,
        release_version,
        release_commit,
        workflow_run_id,
        repository,
        version_name,
        version_code,
        signing_certificate_sha256,
    )
    file_rows = [
        file_record(root, artifact_id, relative)
        for artifact_id, relative in expected_files(release_version)
    ]
    by_id = {row["id"]: row for row in file_rows}
    validate_checksum_contract(root, release_version, by_id)
    require_passed_json(
        root,
        "supply-chain/stage14-validation-policy.json",
        "Stage 14 policy",
    )
    require_passed_json(
        root,
        "supply-chain/security/codeql/codeql-summary.json",
        "CodeQL summary",
        release_commit,
    )
    require_passed_json(
        root,
        "validation/candidate-release-blocker-inventory.json",
        "candidate blocker inventory",
        release_commit,
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "passed": True,
        "suite": "release-candidate",
        "status": "awaiting-physical-acceptance",
        "repository": repository,
        "workflowRunId": workflow_run_id,
        "releaseTag": release_tag,
        "releaseVersion": release_version,
        "releaseCommit": release_commit,
        "applicationId": APPLICATION_ID,
        "versionName": version_name,
        "versionCode": version_code,
        "signingCertificateSha256": certificate,
        "artifactDigests": {
            "aabSha256": by_id["release-aab"]["sha256"],
            "apkSha256": by_id["release-apk"]["sha256"],
            "mappingSha256": by_id["release-mapping"]["sha256"],
        },
        "files": file_rows,
    }


def parse_manifest(raw: bytes) -> dict[str, Any]:
    try:
        document = json.loads(raw)
    except json.JSONDecodeError as error:
        raise CandidateManifestFailure(
            f"candidate manifest is invalid JSON: {error}"
        ) from error
    root = require_object(document, "candidate manifest")
    require_exact_keys(root, ROOT_KEYS, "candidate manifest")
    if root["schemaVersion"] != SCHEMA_VERSION:
        raise CandidateManifestFailure(
            f"candidate manifest schemaVersion must equal {SCHEMA_VERSION}"
        )
    if root["passed"] is not True:
        raise CandidateManifestFailure("candidate manifest did not pass")
    if root["suite"] != "release-candidate":
        raise CandidateManifestFailure("candidate manifest suite is invalid")
    if root["status"] != "awaiting-physical-acceptance":
        raise CandidateManifestFailure("candidate manifest status is invalid")
    certificate = validate_identity(
        require_string(root["releaseTag"], "candidate manifest.releaseTag"),
        require_string(
            root["releaseVersion"],
            "candidate manifest.releaseVersion",
        ),
        require_string(
            root["releaseCommit"],
            "candidate manifest.releaseCommit",
        ),
        require_string(
            root["workflowRunId"],
            "candidate manifest.workflowRunId",
        ),
        require_string(root["repository"], "candidate manifest.repository"),
        require_string(
            root["versionName"],
            "candidate manifest.versionName",
        ),
        root["versionCode"],
        require_string(
            root["signingCertificateSha256"],
            "candidate manifest.signingCertificateSha256",
        ),
    )
    if root["applicationId"] != APPLICATION_ID:
        raise CandidateManifestFailure(
            "candidate manifest applicationId is invalid"
        )
    if root["signingCertificateSha256"] != certificate:
        raise CandidateManifestFailure(
            "candidate manifest signing digest is not canonical lowercase"
        )

    raw_digests = require_object(
        root["artifactDigests"],
        "candidate manifest.artifactDigests",
    )
    require_exact_keys(
        raw_digests,
        ARTIFACT_DIGEST_KEYS,
        "candidate manifest.artifactDigests",
    )
    for key, value in raw_digests.items():
        if (
            not isinstance(value, str)
            or not SHA256_PATTERN.fullmatch(value)
        ):
            raise CandidateManifestFailure(
                f"candidate manifest.artifactDigests.{key} is invalid"
            )

    raw_files = root["files"]
    if not isinstance(raw_files, list):
        raise CandidateManifestFailure("candidate manifest.files must be an array")
    expected = expected_files(root["releaseVersion"])
    expected_ids = [artifact_id for artifact_id, _ in expected]
    expected_paths = [path for _, path in expected]
    actual_ids: list[str] = []
    actual_paths: list[str] = []
    for index, raw_row in enumerate(raw_files):
        path = f"candidate manifest.files[{index}]"
        row = require_object(raw_row, path)
        require_exact_keys(row, FILE_KEYS, path)
        artifact_id = require_string(row["id"], f"{path}.id")
        relative = require_string(row["path"], f"{path}.path")
        if relative.startswith("/") or ".." in Path(relative).parts:
            raise CandidateManifestFailure(f"{path}.path is unsafe")
        size = row["bytes"]
        if isinstance(size, bool) or not isinstance(size, int) or size < 1:
            raise CandidateManifestFailure(f"{path}.bytes must be positive")
        digest = row["sha256"]
        if (
            not isinstance(digest, str)
            or not SHA256_PATTERN.fullmatch(digest)
        ):
            raise CandidateManifestFailure(f"{path}.sha256 is invalid")
        actual_ids.append(artifact_id)
        actual_paths.append(relative)
    if actual_ids != expected_ids or actual_paths != expected_paths:
        raise CandidateManifestFailure(
            "candidate manifest file contract is incomplete or out of order"
        )
    by_id = {row["id"]: row for row in raw_files}
    expected_digests = {
        "aabSha256": by_id["release-aab"]["sha256"],
        "apkSha256": by_id["release-apk"]["sha256"],
        "mappingSha256": by_id["release-mapping"]["sha256"],
    }
    if raw_digests != expected_digests:
        raise CandidateManifestFailure(
            "candidate manifest artifact digest summary is inconsistent"
        )
    return root


def verify_manifest(
    *,
    root: Path,
    manifest_path: Path,
    release_tag: str,
    release_commit: str,
    workflow_run_id: str,
    repository: str,
) -> dict[str, Any]:
    if not root.is_dir() or root.is_symlink():
        raise CandidateManifestFailure(
            f"candidate root is missing or unsafe: {root}"
        )
    if (
        not manifest_path.is_file()
        or manifest_path.is_symlink()
        or manifest_path.stat().st_size <= 0
    ):
        raise CandidateManifestFailure(
            f"candidate manifest is missing or unsafe: {manifest_path}"
        )
    raw = manifest_path.read_bytes()
    manifest = parse_manifest(raw)
    expected_identity = {
        "releaseTag": release_tag,
        "releaseCommit": release_commit,
        "workflowRunId": workflow_run_id,
        "repository": repository,
    }
    for key, expected_value in expected_identity.items():
        if manifest[key] != expected_value:
            raise CandidateManifestFailure(
                f"candidate manifest {key} does not match the requested release"
            )
    records: dict[str, dict[str, Any]] = {}
    for expected in manifest["files"]:
        actual = file_record(root, expected["id"], expected["path"])
        if actual != expected:
            raise CandidateManifestFailure(
                f"candidate file identity mismatch: {expected['path']}"
            )
        records[actual["id"]] = actual
    validate_checksum_contract(root, manifest["releaseVersion"], records)
    require_passed_json(
        root,
        "supply-chain/stage14-validation-policy.json",
        "Stage 14 policy",
    )
    require_passed_json(
        root,
        "supply-chain/security/codeql/codeql-summary.json",
        "CodeQL summary",
        release_commit,
    )
    require_passed_json(
        root,
        "validation/candidate-release-blocker-inventory.json",
        "candidate blocker inventory",
        release_commit,
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "passed": True,
        "suite": "release-candidate-verification",
        "releaseTag": release_tag,
        "releaseCommit": release_commit,
        "workflowRunId": workflow_run_id,
        "repository": repository,
        "manifestSha256": hashlib.sha256(raw).hexdigest(),
        "artifactDigests": manifest["artifactDigests"],
        "signingCertificateSha256": manifest["signingCertificateSha256"],
    }


def candidate_approval_identity(raw_manifest: bytes) -> dict[str, Any]:
    manifest = parse_manifest(raw_manifest)
    return {
        "workflowRunId": manifest["workflowRunId"],
        "manifestSha256": hashlib.sha256(raw_manifest).hexdigest(),
        "signingCertificateSha256": manifest["signingCertificateSha256"],
        **manifest["artifactDigests"],
    }


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create")
    create.add_argument("--root", required=True, type=Path)
    create.add_argument("--release-tag", required=True)
    create.add_argument("--release-version", required=True)
    create.add_argument("--commit", required=True)
    create.add_argument("--run-id", required=True)
    create.add_argument("--repository", required=True)
    create.add_argument("--version-name", required=True)
    create.add_argument("--version-code", required=True, type=int)
    create.add_argument("--signing-cert-sha256", required=True)
    create.add_argument("--output", required=True, type=Path)

    verify = subparsers.add_parser("verify")
    verify.add_argument("--root", required=True, type=Path)
    verify.add_argument("--manifest", required=True, type=Path)
    verify.add_argument("--release-tag", required=True)
    verify.add_argument("--commit", required=True)
    verify.add_argument("--run-id", required=True)
    verify.add_argument("--repository", required=True)
    verify.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.command == "create":
            manifest = build_manifest(
                root=args.root,
                release_tag=args.release_tag,
                release_version=args.release_version,
                release_commit=args.commit,
                workflow_run_id=args.run_id,
                repository=args.repository,
                version_name=args.version_name,
                version_code=args.version_code,
                signing_certificate_sha256=args.signing_cert_sha256,
            )
            write_json(args.output, manifest)
            print(
                "Release candidate manifest created: "
                f"{manifest['releaseTag']} run {manifest['workflowRunId']}."
            )
        else:
            summary = verify_manifest(
                root=args.root,
                manifest_path=args.manifest,
                release_tag=args.release_tag,
                release_commit=args.commit,
                workflow_run_id=args.run_id,
                repository=args.repository,
            )
            write_json(args.summary, summary)
            print(
                "Release candidate verified: "
                f"{summary['releaseTag']} run {summary['workflowRunId']}."
            )
    except (OSError, CandidateManifestFailure) as error:
        if args.command == "verify":
            write_json(
                args.summary,
                {
                    "schemaVersion": SCHEMA_VERSION,
                    "passed": False,
                    "suite": "release-candidate-verification",
                    "releaseTag": args.release_tag,
                    "releaseCommit": args.commit,
                    "workflowRunId": args.run_id,
                    "failure": str(error),
                },
            )
        print(f"Release candidate manifest failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
