#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C
export CI=true

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

evidence_dir="build/reports/firebase-environment-contract"
mkdir -p "$evidence_dir"

cleanup_generated_configs() {
  find app/src -type f -name google-services.json -delete 2>/dev/null || true
}
trap cleanup_generated_configs EXIT
cleanup_generated_configs

tracked_configs="$(
  git ls-files -- \
    app/google-services.json \
    ':(glob)app/src/**/google-services.json'
)"
if [[ -n "$tracked_configs" ]]; then
  echo "Firebase configuration files must not be tracked:" >&2
  printf '%s\n' "$tracked_configs" >&2
  exit 1
fi

./gradlew \
  :app:verifyNoLegacyFirebaseConfig \
  :app:verifyFirebaseNonProductionEnvironmentIsolation \
  --no-daemon \
  --stacktrace \
  2>&1 | tee "$evidence_dir/non-production-isolation.log"

set +e
env \
  -u AQL_FIREBASE_DEBUG_CONFIG_BASE64 \
  -u AQL_FIREBASE_STAGING_CONFIG_BASE64 \
  -u AQL_FIREBASE_PRODUCTION_CONFIG_BASE64 \
  ./gradlew \
    :app:verifyFirebaseProductionEnvironmentIsolation \
    --no-daemon \
    --stacktrace \
    >"$evidence_dir/missing-production-config.log" 2>&1
missing_config_status=$?
set -e

if [[ "$missing_config_status" -eq 0 ]]; then
  echo "Production Firebase isolation unexpectedly accepted missing protected configs." >&2
  exit 1
fi

if ! grep -Eq \
  'Firebase config for production is missing|Production Firebase isolation requires protected configs' \
  "$evidence_dir/missing-production-config.log"; then
  cat "$evidence_dir/missing-production-config.log" >&2
  echo "Production isolation failed for an unexpected reason." >&2
  exit 1
fi

encode_contract_config() {
  local project_id="$1"
  local package_name="$2"
  python3 - "$project_id" "$package_name" <<'PY'
import base64
import json
import sys

project_id, package_name = sys.argv[1:]
config = {
    "project_info": {
        "project_number": "000000000000",
        "project_id": project_id,
        "storage_bucket": f"{project_id}.appspot.com",
    },
    "client": [
        {
            "client_info": {
                "mobilesdk_app_id": "1:000000000000:android:00000000000000000000000000000099",
                "android_client_info": {"package_name": package_name},
            },
            "oauth_client": [],
            "api_key": [{"current_key": "AIzaSyAQLContract000000000000000000000000"}],
            "services": {},
        }
    ],
    "configuration_version": "1",
}
raw = (json.dumps(config, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
print(base64.b64encode(raw).decode("ascii"))
PY
}

export AQL_FIREBASE_DEBUG_CONFIG_BASE64
export AQL_FIREBASE_STAGING_CONFIG_BASE64
export AQL_FIREBASE_PRODUCTION_CONFIG_BASE64
AQL_FIREBASE_DEBUG_CONFIG_BASE64="$(
  encode_contract_config aqualight-contract-debug com.aqua.aqualight.debug
)"
AQL_FIREBASE_STAGING_CONFIG_BASE64="$(
  encode_contract_config aqualight-contract-staging com.aqua.aqualight.staging
)"
AQL_FIREBASE_PRODUCTION_CONFIG_BASE64="$(
  encode_contract_config aqualight-contract-production com.aqua.aqualight
)"

./gradlew \
  :app:verifyFirebaseProductionEnvironmentIsolation \
  --rerun-tasks \
  --no-daemon \
  --stacktrace \
  2>&1 | tee "$evidence_dir/production-isolation.log"

python3 - "$evidence_dir/contract-evidence.json" "$missing_config_status" <<'PY'
import json
import os
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
missing_config_status = int(sys.argv[2])
expected = {
    "debug": (Path("app/src/debug/google-services.json"), "aqualight-contract-debug"),
    "staging": (Path("app/src/staging/google-services.json"), "aqualight-contract-staging"),
    "production": (Path("app/src/release/google-services.json"), "aqualight-contract-production"),
}
project_ids = {}
for environment, (path, expected_project_id) in expected.items():
    if not path.is_file():
        raise SystemExit(f"Generated Firebase config is missing: {path}")
    project_id = json.loads(path.read_text(encoding="utf-8"))["project_info"]["project_id"]
    if project_id != expected_project_id:
        raise SystemExit(
            f"Unexpected {environment} project ID: {project_id} != {expected_project_id}"
        )
    project_ids[environment] = project_id

if len(set(project_ids.values())) != len(project_ids):
    raise SystemExit(f"Contract project IDs are not distinct: {project_ids}")

output_path.write_text(
    json.dumps(
        {
            "schema_version": 1,
            "commit": os.environ.get("GITHUB_SHA", "local"),
            "tracked_firebase_configs": [],
            "missing_protected_config_rejected": missing_config_status != 0,
            "non_production_isolation_verified": True,
            "production_isolation_verified_with_synthetic_contract_configs": True,
            "project_ids": project_ids,
        },
        indent=2,
        sort_keys=True,
    )
    + "\n",
    encoding="utf-8",
)
PY

test -s "$evidence_dir/contract-evidence.json"
echo "Firebase environment contract verification passed."
