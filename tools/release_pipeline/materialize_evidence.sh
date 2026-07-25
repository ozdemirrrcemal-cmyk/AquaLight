#!/usr/bin/env bash
set -Eeuo pipefail

evidence="final-release/supply-chain"
mkdir -p "$evidence/attestations"
policy_evidence="$evidence/stage14-validation-policy.json"
python3 tools/verify_stage14_policy.py \
  --policy config/commercial/stage14-validation-policy.json \
  --app-gradle app/build.gradle \
  --summary "$policy_evidence"
test -s "$policy_evidence"

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

python3 - \
  final-release/RELEASE.json \
  "$AQL_RELEASE_TAG" \
  "$AQL_RELEASE_COMMIT" \
  "${AQL_INCLUDE_APK:-false}" \
  "$policy_evidence" <<'PY'
import json
import sys
from pathlib import Path
output, tag, commit, include_apk, policy_path = sys.argv[1:]
policy = json.loads(Path(policy_path).read_text(encoding="utf-8"))
if policy.get("passed") is not True:
    raise SystemExit("Stage 14 policy evidence did not pass validation.")
Path(output).write_text(
    json.dumps(
        {
            "schemaVersion": 1,
            "status": "published",
            "releaseTag": tag,
            "releaseCommit": commit,
            "includeApk": include_apk == "true",
            "stage14Policy": {
                "policyId": policy["policyId"],
                "sourceSha256": policy["sourceSha256"],
                "canonicalSha256": policy["canonicalSha256"],
            },
            "pipelineOrder": policy["pipelineOrder"],
        },
        indent=2,
        sort_keys=True,
    ) + "\n",
    encoding="utf-8",
)
PY

(
  cd "$evidence"
  find . -type f ! -name SHA256SUMS -printf '%P\n' | sort | xargs sha256sum > SHA256SUMS
  sha256sum --check --strict SHA256SUMS
)
