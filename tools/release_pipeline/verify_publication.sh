#!/usr/bin/env bash
set -Eeuo pipefail
(cd final-release/artifacts && sha256sum --check --strict SHA256SUMS)
(cd final-release/supply-chain && sha256sum --check --strict SHA256SUMS)

test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab.sha256"
test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.aab.spdx.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.provenance.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.sbom.json"

test -s "final-release/evidence/security/codeql-analysis.json"
test -s "final-release/evidence/security/code-scanning-alerts.json"
test -s "final-release/evidence/security/codeql-summary.json"
find final-release/evidence/security/sarif -type f -name '*.sarif' -size +0c -print -quit | grep -q .
python3 - <<'PY'
import json
import os
from pathlib import Path

summary = json.loads(
    Path("final-release/evidence/security/codeql-summary.json").read_text(encoding="utf-8")
)
expected_commit = os.environ["AQL_RELEASE_COMMIT"]
expected_ref = f"refs/tags/{os.environ['AQL_RELEASE_TAG']}"
if summary.get("approved") is not True:
    raise SystemExit("Publication requires approved CodeQL evidence.")
if summary.get("releaseCommit") != expected_commit:
    raise SystemExit("Publication CodeQL evidence commit mismatch.")
if summary.get("releaseRef") != expected_ref:
    raise SystemExit("Publication CodeQL evidence ref mismatch.")
if summary.get("blockingAlertCount") != 0:
    raise SystemExit("Publication CodeQL evidence contains blocking alerts.")
PY

if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk.sha256"
  test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.apk.spdx.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.provenance.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.sbom.json"
fi
