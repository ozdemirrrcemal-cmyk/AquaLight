#!/usr/bin/env bash
set -Eeuo pipefail
(cd final-release/artifacts && sha256sum --check --strict SHA256SUMS)
(cd final-release/supply-chain && sha256sum --check --strict SHA256SUMS)

readonly aab="final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
readonly aab_evidence="final-release/evidence/aab"
readonly smoke_evidence="final-release/evidence/aab-derived"
readonly expected_version_code="${AQL_VERSION_CODE:-${GITHUB_RUN_NUMBER:-}}"

test -s "$aab"
test -s "${aab}.sha256"
test -s "final-release/artifacts/signed-aab-verification.txt"
test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.aab.spdx.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.provenance.json"
test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.sbom.json"

test -s "${aab_evidence}/aab-validation.json"
test -s "${aab_evidence}/base-manifest.xml"
test -s "${aab_evidence}/bundletool-validation.txt"
test -s "${aab_evidence}/r8/mapping.txt"
test -s "${aab_evidence}/r8/mapping.txt.sha256"
(cd "${aab_evidence}/r8" && sha256sum --check --strict mapping.txt.sha256)

test -s "${smoke_evidence}/smoke-summary.json"
test -s "${smoke_evidence}/source-link.json"
test -s "${smoke_evidence}/install.txt"
test -s "${smoke_evidence}/launch.txt"
test -s "${smoke_evidence}/logcat-process.txt"
test -s "${smoke_evidence}/activities-before-summary.txt"

test -s "final-release/evidence/security/codeql-analysis.json"
test -s "final-release/evidence/security/code-scanning-alerts.json"
test -s "final-release/evidence/security/codeql-summary.json"
find final-release/evidence/security/sarif -type f -name '*.sarif' -size +0c -print -quit | grep -q .

python3 - \
  "$aab" \
  "${aab_evidence}/aab-validation.json" \
  "$expected_version_code" \
  "${smoke_evidence}/smoke-summary.json" \
  "${smoke_evidence}/source-link.json" <<'PY'
import hashlib
import json
import os
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


aab = Path(sys.argv[1])
aab_summary = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
expected_version_code = int(sys.argv[3])
smoke = json.loads(Path(sys.argv[4]).read_text(encoding="utf-8"))
source_link = json.loads(Path(sys.argv[5]).read_text(encoding="utf-8"))

if aab_summary.get("approved") is not True:
    raise SystemExit("Publication requires approved AAB validation evidence.")
actual_aab_hash = sha256(aab)
if aab_summary.get("aabSha256") != actual_aab_hash:
    raise SystemExit("Publication AAB hash does not match validation evidence.")
manifest = aab_summary.get("manifest", {})
if manifest.get("package") != "com.aqua.aqualight":
    raise SystemExit("Publication AAB package mismatch.")
if manifest.get("versionName") != os.environ["AQL_RELEASE_VERSION"]:
    raise SystemExit("Publication AAB versionName mismatch.")
if manifest.get("versionCode") != expected_version_code:
    raise SystemExit("Publication AAB versionCode mismatch.")
if manifest.get("minSdk") != 27 or manifest.get("targetSdk") != 36:
    raise SystemExit("Publication AAB SDK contract mismatch.")
if manifest.get("debuggable") is not False or manifest.get("testOnly") is not False:
    raise SystemExit("Publication AAB must not be debuggable or test-only.")

if source_link.get("approved") is not True:
    raise SystemExit("Publication requires approved AAB-derived source-link evidence.")
if source_link.get("sourceAab", {}).get("sha256") != actual_aab_hash:
    raise SystemExit("AAB-derived APK was not generated from the publication AAB.")
if source_link.get("package") != manifest.get("package"):
    raise SystemExit("AAB-derived APK package differs from publication AAB.")
if source_link.get("versionName") != manifest.get("versionName"):
    raise SystemExit("AAB-derived APK versionName differs from publication AAB.")
if source_link.get("versionCode") != manifest.get("versionCode"):
    raise SystemExit("AAB-derived APK versionCode differs from publication AAB.")

if smoke.get("approved") is not True:
    raise SystemExit("Publication requires approved AAB-derived launch smoke evidence.")
if smoke.get("sourceAabSha256") != actual_aab_hash:
    raise SystemExit("AAB-derived smoke references a different source AAB.")
if smoke.get("derivedApkSha256") != source_link.get("derivedUniversalApk", {}).get("sha256"):
    raise SystemExit("AAB-derived smoke APK hash differs from source-link evidence.")
for required_true in (
    "installPassed",
    "launchPassed",
    "processAliveAfterSettle",
    "sourceLinkApproved",
):
    if smoke.get(required_true) is not True:
        raise SystemExit(f"AAB-derived smoke requirement failed: {required_true}")
if smoke.get("fatalExceptionDetected") is not False or smoke.get("anrDetected") is not False:
    raise SystemExit("AAB-derived smoke detected a crash or ANR.")

security = json.loads(
    Path("final-release/evidence/security/codeql-summary.json").read_text(encoding="utf-8")
)
expected_commit = os.environ["AQL_RELEASE_COMMIT"]
expected_ref = f"refs/tags/{os.environ['AQL_RELEASE_TAG']}"
if security.get("approved") is not True:
    raise SystemExit("Publication requires approved CodeQL evidence.")
if security.get("releaseCommit") != expected_commit:
    raise SystemExit("Publication CodeQL evidence commit mismatch.")
if security.get("releaseRef") != expected_ref:
    raise SystemExit("Publication CodeQL evidence ref mismatch.")
if security.get("blockingAlertCount") != 0:
    raise SystemExit("Publication CodeQL evidence contains blocking alerts.")
PY

if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk.sha256"
  test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.apk.spdx.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.provenance.json"
  test -s "final-release/supply-chain/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.sbom.json"
fi
