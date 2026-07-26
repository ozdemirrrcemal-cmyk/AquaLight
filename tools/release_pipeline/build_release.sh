#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

[[ "${AQL_PRODUCTION_RELEASE_ENABLED:-}" == "true" ]]
[[ "${AQL_RELEASE_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]]
[[ "$(git rev-parse HEAD)" == "$AQL_RELEASE_COMMIT" ]]
[[ "${AQL_INCLUDE_APK:-}" == "true" ]]
release_root="${AQL_RELEASE_ROOT:-candidate-release}"
[[ "$release_root" == "candidate-release" ]]
for name in \
  AQL_FIREBASE_DEBUG_CONFIG_BASE64 \
  AQL_FIREBASE_STAGING_CONFIG_BASE64 \
  AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64 \
  AQL_FIREBASE_PRODUCTION_CONFIG_BASE64 \
  RELEASE_KEYSTORE_BASE64 \
  RELEASE_KEYSTORE_PASSWORD \
  RELEASE_KEY_ALIAS \
  RELEASE_KEY_PASSWORD \
  RELEASE_SIGNING_CERT_SHA256; do
  value="${!name:-}"
  [[ -n "${value//[[:space:]]/}" ]]
done

normalize() {
  local value="$1"
  value="${value#SHA256:}"
  value="${value#sha256:}"
  printf '%s' "$value" | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

certificate_sha256s_from_pem_file() {
  local pem_file="$1"
  python3 - "$pem_file" <<'PY'
import base64
import hashlib
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="strict")
blocks = re.findall(
    r"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----",
    text,
    flags=re.DOTALL,
)
if not blocks:
    raise SystemExit(f"No PEM signing certificate found in {sys.argv[1]}")
for block in blocks:
    encoded = "".join(block.split())
    certificate = base64.b64decode(encoded, validate=True)
    print(hashlib.sha256(certificate).hexdigest().upper())
PY
}

umask 077
signing_dir="${RUNNER_TEMP}/aqualight-signing"
keystore_path="${signing_dir}/release-key.jks"
rm -rf "$signing_dir"
rm -rf "$release_root/artifacts" "$release_root/supply-chain/attestations"
mkdir -p \
  "$signing_dir/probe" \
  "$release_root/artifacts" \
  "$release_root/supply-chain/attestations"
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$keystore_path"
test -s "$keystore_path"
chmod 600 "$keystore_path"

keytool_output="$(keytool -list -v \
  -keystore "$keystore_path" \
  -storepass "$RELEASE_KEYSTORE_PASSWORD" \
  -alias "$RELEASE_KEY_ALIAS")"
grep -Fq "Entry type: PrivateKeyEntry" <<< "$keytool_output"
actual="$(sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' <<< "$keytool_output" | head -n 1)"
actual="$(normalize "$actual")"
expected="$(normalize "$RELEASE_SIGNING_CERT_SHA256")"
[[ "$expected" =~ ^[0-9A-F]{64}$ ]]
[[ "$actual" == "$expected" ]]

printf 'AquaLight production signing probe\n' > "$signing_dir/probe/payload.txt"
jar --create --file "$signing_dir/probe.jar" -C "$signing_dir/probe" payload.txt
jarsigner \
  -keystore "$keystore_path" \
  -storepass "$RELEASE_KEYSTORE_PASSWORD" \
  -keypass "$RELEASE_KEY_PASSWORD" \
  -signedjar "$signing_dir/probe-signed.jar" \
  "$signing_dir/probe.jar" \
  "$RELEASE_KEY_ALIAS" >/dev/null
jarsigner -verify "$signing_dir/probe-signed.jar" >/dev/null
ln -s "$keystore_path" release-key.jks

tasks=(bundleRelease assembleRelease)
./gradlew "${tasks[@]}" --no-daemon --stacktrace
./gradlew :app:verifyFirebaseRuntimePolicy --no-daemon --stacktrace
python3 tools/firebase_telemetry_guard.py --scan-build-output

artifacts="$release_root/artifacts"
source_aab="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit)"
test -s "$source_aab"
aab="$artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
cp "$source_aab" "$aab"
jarsigner -verify -verbose -certs "$aab" > "$artifacts/signed-aab-verification.txt" 2>&1
aab_cert="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
[[ "$(normalize "$aab_cert")" == "$actual" ]]
source_mapping="app/build/outputs/mapping/release/mapping.txt"
test -s "$source_mapping"
cp "$source_mapping" "$artifacts/AquaLight-${AQL_RELEASE_VERSION}-mapping.txt"

source_apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -s "$source_apk"
apk="$artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
cp "$source_apk" "$apk"
apksigner="$(find "${ANDROID_HOME}/build-tools" -maxdepth 2 -type f -name apksigner -print | sort -V | tail -n 1)"
[[ -x "$apksigner" ]]
"$apksigner" verify \
  --verbose \
  --print-certs \
  --print-certs-pem \
  "$apk" \
  > "$artifacts/signed-apk-verification.txt" 2>&1
mapfile -t apk_certificates < <(
  certificate_sha256s_from_pem_file "$artifacts/signed-apk-verification.txt"
)
[[ "${#apk_certificates[@]}" -eq 1 ]]
[[ "${apk_certificates[0]}" == "$actual" ]]
apkanalyzer="$(command -v apkanalyzer)"
[[ -x "$apkanalyzer" ]]
[[ "$("$apkanalyzer" manifest application-id "$apk")" == "com.aqua.aqualight" ]]
version_name="$("$apkanalyzer" manifest version-name "$apk")"
version_code="$("$apkanalyzer" manifest version-code "$apk")"
[[ "$version_name" == "$AQL_RELEASE_VERSION" ]]
[[ "$version_code" =~ ^[1-9][0-9]*$ ]]
(( version_code <= 2100000000 ))

echo "AQL_RELEASE_CERT_SHA256=$actual" >> "$GITHUB_ENV"
echo "AQL_RELEASE_VERSION_NAME=$version_name" >> "$GITHUB_ENV"
echo "AQL_RELEASE_VERSION_CODE=$version_code" >> "$GITHUB_ENV"
