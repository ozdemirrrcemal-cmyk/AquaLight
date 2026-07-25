#!/usr/bin/env bash
set -Eeuo pipefail

evidence="final-release/supply-chain"
mkdir -p "$evidence/attestations"
policy_evidence="$evidence/stage14-validation-policy.json"
python3 tools/verify_stage14_policy.py \
  --policy config/commercial/stage14-validation-policy.json \
  --app-gradle app/build.gradle \
  --emulator-workflow .github/workflows/android_emulator_tests.yml \
  --release-workflow .github/workflows/android_release.yml \
  --summary "$policy_evidence"
test -s "$policy_evidence"
quality_policy="final-release/validation/quality/release-quality/stage14-policy-validation.json"
test -s "$quality_policy"

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

test -s "$AAB_PROVENANCE_BUNDLE"
test -s "$AAB_SBOM_BUNDLE"
cp "$AAB_PROVENANCE_BUNDLE" "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.provenance.json"
cp "$AAB_SBOM_BUNDLE" "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.aab.sbom.json"

if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  test -s "$APK_PROVENANCE_BUNDLE"
  test -s "$APK_SBOM_BUNDLE"
  cp "$APK_PROVENANCE_BUNDLE" "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.provenance.json"
  cp "$APK_SBOM_BUNDLE" "$evidence/attestations/AquaLight-${AQL_RELEASE_VERSION}.apk.sbom.json"
fi

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

python3 tools/generate_stage14_final_evidence.py \
  --policy "$quality_policy" \
  --quality-root final-release/validation/quality \
  --instrumentation-root final-release/validation/instrumentation \
  --codeql-root final-release/supply-chain/security/codeql \
  --release-root final-release \
  --blocker final-release/validation/release-blocker-inventory.json \
  --manual final-release/validation/manual-acceptance.json \
  --release-tag "$AQL_RELEASE_TAG" \
  --release-version "$AQL_RELEASE_VERSION" \
  --commit "$AQL_RELEASE_COMMIT" \
  --include-apk "${AQL_INCLUDE_APK:-false}" \
  --json "$evidence/final-evidence.json" \
  --markdown "$evidence/final-evidence.md"

python3 - \
  final-release/RELEASE.json \
  "$AQL_RELEASE_TAG" \
  "$AQL_RELEASE_COMMIT" \
  "${AQL_INCLUDE_APK:-false}" \
  "$policy_evidence" \
  "$evidence/final-evidence.json" \
  "$evidence/final-evidence.md" \
  final-release/validation/release-blocker-inventory.json \
  final-release/validation/manual-acceptance.json <<'PY'
import hashlib
import json
import sys
from pathlib import Path

(
    output,
    tag,
    commit,
    include_apk,
    policy_path,
    final_json_path,
    final_markdown_path,
    blocker_path,
    manual_path,
) = sys.argv[1:]
policy = json.loads(Path(policy_path).read_text(encoding="utf-8"))
if policy.get("passed") is not True:
    raise SystemExit("Stage 14 policy evidence did not pass validation.")
final_evidence = json.loads(Path(final_json_path).read_text(encoding="utf-8"))
blocker = json.loads(Path(blocker_path).read_text(encoding="utf-8"))
manual = json.loads(Path(manual_path).read_text(encoding="utf-8"))
for label, value in (
    ("final evidence", final_evidence),
    ("release blocker inventory", blocker),
    ("manual acceptance", manual),
):
    if value.get("passed") is not True:
        raise SystemExit(f"{label} did not pass.")
    if value.get("releaseCommit") != commit:
        raise SystemExit(f"{label} does not belong to the release commit.")

def sha256(path: str) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

Path(output).write_text(
    json.dumps(
        {
            "schemaVersion": 1,
            "status": "approved-for-publication",
            "releaseTag": tag,
            "releaseCommit": commit,
            "includeApk": include_apk == "true",
            "stage14Policy": {
                "policyId": policy["policyId"],
                "sourceSha256": policy["sourceSha256"],
                "canonicalSha256": policy["canonicalSha256"],
            },
            "pipelineOrder": policy["pipelineOrder"],
            "finalEvidence": {
                "jsonSha256": sha256(final_json_path),
                "markdownSha256": sha256(final_markdown_path),
                "releaseBlockerInventorySha256": sha256(blocker_path),
                "manualAcceptanceSha256": sha256(manual_path),
            },
        },
        indent=2,
        sort_keys=True,
    ) + "\n",
    encoding="utf-8",
)
PY

(
  cd "$evidence"
  find . -type f ! -name SHA256SUMS -printf '%P\0' \
    | sort -z \
    | xargs -0 sha256sum \
    > SHA256SUMS
  sha256sum --check --strict SHA256SUMS
)
