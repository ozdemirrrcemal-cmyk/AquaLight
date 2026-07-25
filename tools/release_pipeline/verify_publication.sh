#!/usr/bin/env bash
set -Eeuo pipefail
(cd final-release/artifacts && sha256sum --check --strict SHA256SUMS)
(cd final-release/supply-chain && sha256sum --check --strict SHA256SUMS)

test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab.sha256"
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}-mapping.txt"
test -s "final-release/supply-chain/stage14-validation-policy.json"
test -s "final-release/supply-chain/security/codeql/codeql-summary.json"
test -s "final-release/supply-chain/final-evidence.json"
test -s "final-release/supply-chain/final-evidence.md"
test -s "final-release/validation/release-blocker-inventory.json"
test -s "final-release/validation/manual-acceptance.json"
test -d "final-release/validation/quality"
test -d "final-release/validation/instrumentation"
test -n "$(
  find final-release/supply-chain/security/codeql \
    -type f \
    -name '*.sarif' \
    -size +0c \
    -print \
    -quit
)"
test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.aab.spdx.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.provenance.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.sbom.json"

if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk.sha256"
  test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.apk.spdx.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.provenance.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.sbom.json"
fi

python3 - \
  final-release/RELEASE.json \
  final-release/supply-chain/stage14-validation-policy.json \
  final-release/supply-chain/security/codeql/codeql-summary.json \
  final-release/supply-chain/final-evidence.json \
  final-release/supply-chain/final-evidence.md \
  final-release/validation/release-blocker-inventory.json \
  final-release/validation/manual-acceptance.json \
  "$AQL_RELEASE_TAG" \
  "$AQL_RELEASE_COMMIT" \
  "${AQL_INCLUDE_APK:-false}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

release_path = Path(sys.argv[1])
policy_path = Path(sys.argv[2])
codeql_path = Path(sys.argv[3])
final_path = Path(sys.argv[4])
final_markdown_path = Path(sys.argv[5])
blocker_path = Path(sys.argv[6])
manual_path = Path(sys.argv[7])
expected_tag = sys.argv[8]
expected_commit = sys.argv[9]
include_apk = sys.argv[10] == "true"
release = json.loads(release_path.read_text(encoding="utf-8"))
policy = json.loads(policy_path.read_text(encoding="utf-8"))
codeql = json.loads(codeql_path.read_text(encoding="utf-8"))
final = json.loads(final_path.read_text(encoding="utf-8"))
blocker = json.loads(blocker_path.read_text(encoding="utf-8"))
manual = json.loads(manual_path.read_text(encoding="utf-8"))

if release.get("status") != "approved-for-publication":
    raise SystemExit("Release package is not approved for publication.")
if release.get("releaseTag") != expected_tag:
    raise SystemExit("Release tag identity mismatch.")
if release.get("releaseCommit") != expected_commit:
    raise SystemExit("Release commit identity mismatch.")
if release.get("includeApk") is not include_apk:
    raise SystemExit("Release APK mode mismatch.")

for label, value, commit_required in (
    ("Stage 14 policy", policy, False),
    ("CodeQL", codeql, True),
    ("final evidence", final, True),
    ("release blocker inventory", blocker, True),
    ("manual acceptance", manual, True),
):
    if value.get("passed") is not True:
        raise SystemExit(f"{label} evidence is not approved.")
    if commit_required and value.get("releaseCommit") != expected_commit:
        raise SystemExit(f"{label} evidence does not belong to the release commit.")

release_policy = release.get("stage14Policy", {})
for field in ("policyId", "sourceSha256", "canonicalSha256"):
    if release_policy.get(field) != policy.get(field):
        raise SystemExit(f"Release policy identity mismatch: {field}")
    if final.get("stage14Policy", {}).get(field) != policy.get(field):
        raise SystemExit(f"Final evidence policy identity mismatch: {field}")

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

release_evidence = release.get("finalEvidence", {})
expected_hashes = {
    "jsonSha256": sha256(final_path),
    "markdownSha256": sha256(final_markdown_path),
    "releaseBlockerInventorySha256": sha256(blocker_path),
    "manualAcceptanceSha256": sha256(manual_path),
}
if release_evidence != expected_hashes:
    raise SystemExit("Release final-evidence digest contract mismatch.")

required_artifacts = policy.get("requiredArtifacts")
if not isinstance(required_artifacts, list):
    raise SystemExit("Stage 14 required artifact contract is missing.")
contract_ids = [artifact.get("id") for artifact in required_artifacts]
artifacts = final.get("artifacts")
if not isinstance(artifacts, list):
    raise SystemExit("Final artifact manifest is missing.")
if [artifact.get("id") for artifact in artifacts] != contract_ids:
    raise SystemExit("Final artifact order does not match the Stage 14 policy.")
if final.get("artifactContractCount") != len(contract_ids):
    raise SystemExit("Final artifact count does not match the Stage 14 policy.")

release_root = Path("final-release").resolve()
for artifact in artifacts:
    required = artifact.get("requiredThisRelease")
    status = artifact.get("status")
    files = artifact.get("files")
    if not isinstance(required, bool) or not isinstance(files, list):
        raise SystemExit(f"Invalid artifact manifest row: {artifact.get('id')}")
    if required and status not in {"verified", "generated"}:
        raise SystemExit(f"Required artifact is not verified: {artifact.get('id')}")
    if not required and status != "not-requested":
        raise SystemExit(f"Optional artifact status mismatch: {artifact.get('id')}")
    if required and not files:
        raise SystemExit(f"Required artifact has no files: {artifact.get('id')}")
    for item in files:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            raise SystemExit(f"Invalid artifact file row: {artifact.get('id')}")
        path = (release_root / item["path"]).resolve()
        try:
            path.relative_to(release_root)
        except ValueError as error:
            raise SystemExit(f"Artifact path escapes release root: {path}") from error
        if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
            raise SystemExit(f"Artifact file is missing or unsafe: {path}")
        if item.get("selfHashExcluded") is True:
            if path != final_path.resolve():
                raise SystemExit("Only final-evidence.json may exclude its self hash.")
            continue
        if item.get("sha256") != sha256(path):
            raise SystemExit(f"Artifact SHA-256 mismatch: {path}")

supplemental = final.get("supplementalEvidence")
if not isinstance(supplemental, list):
    raise SystemExit("Final supplemental evidence inventory is missing.")
if final.get("supplementalFileCount") != len(supplemental):
    raise SystemExit("Final supplemental evidence count mismatch.")
for item in supplemental:
    if not isinstance(item, dict) or not isinstance(item.get("path"), str):
        raise SystemExit("Invalid supplemental evidence row.")
    path = (release_root / item["path"]).resolve()
    try:
        path.relative_to(release_root)
    except ValueError as error:
        raise SystemExit(f"Supplemental path escapes release root: {path}") from error
    if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
        raise SystemExit(f"Supplemental evidence is missing or unsafe: {path}")
    if item.get("sha256") != sha256(path):
        raise SystemExit(f"Supplemental evidence SHA-256 mismatch: {path}")
PY
