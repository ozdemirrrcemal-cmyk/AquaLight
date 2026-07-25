#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

readonly aab="${1:?Release AAB path is required}"
readonly output_dir="${2:?Output directory is required}"
readonly keystore="${3:?Keystore path is required}"
readonly expected_package="${AQL_EXPECTED_PACKAGE:-com.aqua.aqualight}"
readonly expected_version_name="${AQL_RELEASE_VERSION:?AQL_RELEASE_VERSION is required}"
readonly expected_version_code="${AQL_VERSION_CODE:-${GITHUB_RUN_NUMBER:-}}"

for name in RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
  value="${!name:-}"
  [[ -n "${value//[[:space:]]/}" ]]
done
[[ -s "$aab" ]]
[[ -s "$keystore" ]]
[[ "$expected_version_code" =~ ^[1-9][0-9]*$ ]]

normalize() {
  local value="$1"
  value="${value#SHA256:}"
  value="${value#sha256:}"
  printf '%s' "$value" | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

apk_certificate_sha256() {
  local verification_file="$1"
  sed -n -E \
    's/^(Signer #1|V[0-9.]+ Signer): certificate SHA-256 digest:[[:space:]]*//p' \
    "$verification_file" \
    | head -n 1
}

rm -rf "$output_dir"
mkdir -p "$output_dir"
chmod 700 "$output_dir"
readonly bundletool_jar="$(bash tools/release_pipeline/install_bundletool.sh)"
readonly bundletool_version="$(java -jar "$bundletool_jar" version | tr -d '\r\n')"
readonly apks="$output_dir/AquaLight.apks"
readonly universal_apk="$output_dir/AquaLight-universal.apk"
readonly source_link="$output_dir/source-link.json"

java -jar "$bundletool_jar" build-apks \
  --bundle="$aab" \
  --output="$apks" \
  --mode=universal \
  --ks="$keystore" \
  --ks-pass="pass:${RELEASE_KEYSTORE_PASSWORD}" \
  --ks-key-alias="$RELEASE_KEY_ALIAS" \
  --key-pass="pass:${RELEASE_KEY_PASSWORD}" \
  --overwrite

test -s "$apks"
unzip -p "$apks" universal.apk > "$universal_apk"
test -s "$universal_apk"
chmod 600 "$universal_apk"
rm -f "$apks"

readonly apksigner="$(find "${ANDROID_HOME}/build-tools" -maxdepth 2 -type f -name apksigner -print | sort -V | tail -n 1)"
readonly apkanalyzer="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"
test -x "$apksigner"
test -x "$apkanalyzer"

"$apksigner" verify --verbose --print-certs "$universal_apk" \
  > "$output_dir/apk-signing-verification.txt" 2>&1

readonly aab_cert="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
readonly apk_cert="$(apk_certificate_sha256 "$output_dir/apk-signing-verification.txt")"
readonly normalized_aab_cert="$(normalize "$aab_cert")"
readonly normalized_apk_cert="$(normalize "$apk_cert")"
[[ "$normalized_aab_cert" =~ ^[0-9A-F]{64}$ ]]
[[ "$normalized_apk_cert" =~ ^[0-9A-F]{64}$ ]]
[[ "$normalized_aab_cert" == "$normalized_apk_cert" ]]

readonly actual_package="$("$apkanalyzer" manifest application-id "$universal_apk" | tr -d '\r\n')"
readonly actual_version_name="$("$apkanalyzer" manifest version-name "$universal_apk" | tr -d '\r\n')"
readonly actual_version_code="$("$apkanalyzer" manifest version-code "$universal_apk" | tr -d '\r\n')"
[[ "$actual_package" == "$expected_package" ]]
[[ "$actual_version_name" == "$expected_version_name" ]]
[[ "$actual_version_code" == "$expected_version_code" ]]

python3 - \
  "$aab" \
  "$universal_apk" \
  "$source_link" \
  "$bundletool_version" \
  "$actual_package" \
  "$actual_version_name" \
  "$actual_version_code" \
  "$normalized_aab_cert" <<'PY'
import hashlib
import json
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


aab = Path(sys.argv[1])
apk = Path(sys.argv[2])
output = Path(sys.argv[3])
summary = {
    "schemaVersion": 1,
    "approved": True,
    "derivation": "bundletool-build-apks-universal",
    "bundletoolVersion": sys.argv[4],
    "package": sys.argv[5],
    "versionName": sys.argv[6],
    "versionCode": int(sys.argv[7]),
    "signingCertificateSha256": sys.argv[8],
    "sourceAab": {
        "path": str(aab),
        "sha256": sha256(aab),
        "sizeBytes": aab.stat().st_size,
    },
    "derivedUniversalApk": {
        "path": str(apk),
        "sha256": sha256(apk),
        "sizeBytes": apk.stat().st_size,
    },
}
output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2, sort_keys=True))
PY

test -s "$source_link"
printf '%s\n' "$universal_apk"
