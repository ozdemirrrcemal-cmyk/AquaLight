#!/usr/bin/env bash
set -Eeuo pipefail

release_root="${AQL_RELEASE_ROOT:-candidate-release}"
[[ "$release_root" == "candidate-release" ]]
[[ "${AQL_INCLUDE_APK:-}" == "true" ]]
[[ "${GITHUB_RUN_ID:-}" =~ ^[1-9][0-9]*$ ]]
[[ "${GITHUB_REPOSITORY:-}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]
[[ "${AQL_RELEASE_VERSION_CODE:-}" =~ ^[1-9][0-9]*$ ]]

evidence="$release_root/supply-chain"
validation="$release_root/validation"
mkdir -p "$evidence/attestations" "$validation"

policy_evidence="$evidence/stage14-validation-policy.json"
python3 tools/verify_stage14_policy.py \
  --policy config/commercial/stage14-validation-policy.json \
  --app-gradle app/build.gradle \
  --emulator-workflow .github/workflows/android_emulator_tests.yml \
  --release-workflow .github/workflows/android_release.yml \
  --summary "$policy_evidence"
test -s "$policy_evidence"

quality_policy="$validation/quality/release-quality/stage14-policy-validation.json"
quality_blocker="$validation/quality/release-quality/release-blocker-inventory.json"
test -s "$quality_policy"
test -s "$quality_blocker"
cp "$quality_blocker" "$validation/candidate-release-blocker-inventory.json"

python3 - "$policy_evidence" "$quality_policy" <<'PY'
import json
import sys
from pathlib import Path

current = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
quality = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
for field in ("policyId", "sourceSha256", "canonicalSha256"):
    if current.get(field) != quality.get(field):
        raise SystemExit(f"Quality policy identity mismatch: {field}")
PY

for source in \
  "$AAB_PROVENANCE_BUNDLE" \
  "$AAB_SBOM_BUNDLE" \
  "$APK_PROVENANCE_BUNDLE" \
  "$APK_SBOM_BUNDLE"; do
  test -s "$source"
done
cp \
  "$AAB_PROVENANCE_BUNDLE" \
  "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.provenance.json"
cp \
  "$AAB_SBOM_BUNDLE" \
  "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.sbom.json"
cp \
  "$APK_PROVENANCE_BUNDLE" \
  "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.provenance.json"
cp \
  "$APK_SBOM_BUNDLE" \
  "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.sbom.json"

python3 - "$evidence"/*.spdx.json <<'PY'
import json
import sys
from pathlib import Path

for value in sys.argv[1:]:
    path = Path(value)
    document = json.loads(path.read_text(encoding="utf-8"))
    if not str(document.get("spdxVersion", "")).startswith("SPDX-"):
        raise SystemExit(f"Invalid SPDX document: {path}")
    if document.get("SPDXID") != "SPDXRef-DOCUMENT":
        raise SystemExit(f"Invalid SPDX root: {path}")
PY

instrumentation_validation="$validation/instrumentation"
if [[ -d "$instrumentation_validation" ]]; then
  find "$instrumentation_validation" -type f -name '*.lck' -delete
fi

python3 tools/release_candidate_manifest.py create \
  --root "$release_root" \
  --release-tag "$AQL_RELEASE_TAG" \
  --release-version "$AQL_RELEASE_VERSION" \
  --commit "$AQL_RELEASE_COMMIT" \
  --run-id "$GITHUB_RUN_ID" \
  --repository "$GITHUB_REPOSITORY" \
  --version-name "$AQL_RELEASE_VERSION_NAME" \
  --version-code "$AQL_RELEASE_VERSION_CODE" \
  --signing-cert-sha256 "$AQL_RELEASE_CERT_SHA256" \
  --output "$release_root/CANDIDATE.json"

python3 tools/release_candidate_manifest.py verify \
  --root "$release_root" \
  --manifest "$release_root/CANDIDATE.json" \
  --release-tag "$AQL_RELEASE_TAG" \
  --commit "$AQL_RELEASE_COMMIT" \
  --run-id "$GITHUB_RUN_ID" \
  --repository "$GITHUB_REPOSITORY" \
  --summary "$validation/candidate-verification.json"
