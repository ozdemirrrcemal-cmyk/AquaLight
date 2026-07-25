#!/usr/bin/env bash
set -Eeuo pipefail
(cd final-release/artifacts && sha256sum --check --strict SHA256SUMS)
(cd final-release/supply-chain && sha256sum --check --strict SHA256SUMS)

test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab.sha256"
test -s "final-release/supply-chain/stage14-validation-policy.json"
test -s "final-release/supply-chain/security/codeql/codeql-summary.json"
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
  "$AQL_RELEASE_COMMIT" <<'PY'
import json
import sys
from pathlib import Path

release_path = Path(sys.argv[1])
policy_path = Path(sys.argv[2])
codeql_path = Path(sys.argv[3])
expected_commit = sys.argv[4]
release = json.loads(release_path.read_text(encoding="utf-8"))
policy = json.loads(policy_path.read_text(encoding="utf-8"))
codeql = json.loads(codeql_path.read_text(encoding="utf-8"))
if policy.get("passed") is not True:
    raise SystemExit("Stage 14 policy evidence is not approved.")
if codeql.get("passed") is not True:
    raise SystemExit("CodeQL evidence is not approved.")
if codeql.get("releaseCommit") != expected_commit:
    raise SystemExit("CodeQL evidence does not belong to the release commit.")
release_policy = release.get("stage14Policy", {})
for field in ("policyId", "sourceSha256", "canonicalSha256"):
    if release_policy.get(field) != policy.get(field):
        raise SystemExit(f"Release policy identity mismatch: {field}")
PY
