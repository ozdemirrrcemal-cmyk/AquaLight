#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

readonly aab="${1:?Release AAB path is required}"
readonly evidence_dir="${2:-final-release/evidence/aab}"
readonly expected_package="${AQL_EXPECTED_PACKAGE:-com.aqua.aqualight}"
readonly expected_version_name="${AQL_RELEASE_VERSION:?AQL_RELEASE_VERSION is required}"
readonly expected_version_code="${AQL_VERSION_CODE:-${GITHUB_RUN_NUMBER:-}}"
readonly expected_min_sdk="${AQL_EXPECTED_MIN_SDK:-27}"
readonly expected_target_sdk="${AQL_EXPECTED_TARGET_SDK:-36}"
readonly expected_application="${AQL_EXPECTED_APPLICATION:-com.aqua.aqualight.app.AquaApp}"

[[ -s "$aab" ]]
[[ "$expected_version_code" =~ ^[1-9][0-9]*$ ]]

mkdir -p "$evidence_dir/r8"
readonly bundletool_jar="$(bash tools/release_pipeline/install_bundletool.sh)"
readonly bundletool_version="$(java -jar "$bundletool_jar" version | tr -d '\r\n')"
readonly validation_log="$evidence_dir/bundletool-validation.txt"
readonly manifest_xml="$evidence_dir/base-manifest.xml"
readonly manifest_stderr="$evidence_dir/base-manifest-stderr.txt"
readonly mapping_source="app/build/outputs/mapping/release/mapping.txt"
readonly mapping_evidence="$evidence_dir/r8/mapping.txt"
readonly summary="$evidence_dir/aab-validation.json"

{
  echo "bundletoolVersion=${bundletool_version}"
  echo "aab=${aab}"
  java -jar "$bundletool_jar" validate --bundle="$aab"
  echo "BUNDLETOOL_VALIDATE_PASS"
} > "$validation_log" 2>&1

grep -Fq "BUNDLETOOL_VALIDATE_PASS" "$validation_log"

java -jar "$bundletool_jar" dump manifest \
  --bundle="$aab" \
  --module=base \
  > "$manifest_xml" \
  2> "$manifest_stderr"

test -s "$manifest_xml"
grep -q '<manifest' "$manifest_xml"

test -s "$mapping_source"
cp "$mapping_source" "$mapping_evidence"
(
  cd "$(dirname "$mapping_evidence")"
  sha256sum "$(basename "$mapping_evidence")" > "$(basename "$mapping_evidence").sha256"
  sha256sum --check --strict "$(basename "$mapping_evidence").sha256"
)

for optional_report in \
  configuration.txt \
  seeds.txt \
  usage.txt \
  resources.txt; do
  source_report="app/build/outputs/mapping/release/${optional_report}"
  if [[ -s "$source_report" ]]; then
    cp "$source_report" "$evidence_dir/r8/${optional_report}"
  fi
done

python3 tools/release_pipeline/verify_release_aab.py \
  --aab "$aab" \
  --manifest "$manifest_xml" \
  --bundletool-validation "$validation_log" \
  --mapping "$mapping_evidence" \
  --expected-package "$expected_package" \
  --expected-version-name "$expected_version_name" \
  --expected-version-code "$expected_version_code" \
  --expected-min-sdk "$expected_min_sdk" \
  --expected-target-sdk "$expected_target_sdk" \
  --expected-application "$expected_application" \
  --bundletool-version "$bundletool_version" \
  --output "$summary"

test -s "$summary"
python3 - "$summary" <<'PY'
import json
import sys
from pathlib import Path

summary = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if summary.get("approved") is not True:
    raise SystemExit("Release AAB evidence is not approved.")
PY

printf 'Commercial AAB validation passed: %s\n' "$aab"
