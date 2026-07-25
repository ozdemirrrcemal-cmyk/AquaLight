#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

[[ "${AQL_PRODUCTION_RELEASE_ENABLED:-}" == "true" ]]
[[ "${AQL_RELEASE_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]]
[[ "$(git rev-parse HEAD)" == "$AQL_RELEASE_COMMIT" ]]
for name in \
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

umask 077
signing_dir="${RUNNER_TEMP}/aqualight-signing"
keystore_path="${signing_dir}/release-key.jks"
cleanup_signing() {
  rm -f release-key.jks
  rm -rf "$signing_dir"
}
trap cleanup_signing EXIT

rm -rf "$signing_dir" release-validation/aab-derived
mkdir -p \
  "$signing_dir/probe" \
  final-release/artifacts \
  final-release/evidence/aab \
  final-release/supply-chain/attestations \
  release-validation/aab-derived
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

tasks=(bundleRelease)
[[ "${AQL_INCLUDE_APK:-false}" == "true" ]] && tasks+=(assembleRelease)
./gradlew "${tasks[@]}" --no-daemon --stacktrace
./gradlew :app:verifyFirebaseRuntimePolicy --no-daemon --stacktrace
python3 tools/firebase_telemetry_guard.py --scan-build-output

artifacts="final-release/artifacts"
source_aab="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit)"
test -s "$source_aab"
aab="$artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
cp "$source_aab" "$aab"
jarsigner -verify -verbose -certs "$aab" > "$artifacts/signed-aab-verification.txt" 2>&1
aab_cert="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
[[ "$(normalize "$aab_cert")" == "$actual" ]]

bash tools/release_pipeline/validate_release_aab.sh \
  "$aab" \
  final-release/evidence/aab

if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  source_apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit)"
  test -s "$source_apk"
  apk="$artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
  cp "$source_apk" "$apk"
  apksigner="$(find "${ANDROID_HOME}/build-tools" -maxdepth 2 -type f -name apksigner -print | sort -V | tail -n 1)"
  "$apksigner" verify --verbose --print-certs "$apk" > "$artifacts/signed-apk-verification.txt" 2>&1
  apk_cert="$(sed -n 's/^Signer #1 certificate SHA-256 digest:[[:space:]]*//p' "$artifacts/signed-apk-verification.txt" | head -n 1)"
  [[ "$(normalize "$apk_cert")" == "$actual" ]]
  apkanalyzer="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"
  [[ "$("$apkanalyzer" manifest application-id "$apk")" == "com.aqua.aqualight" ]]
  [[ "$("$apkanalyzer" manifest version-name "$apk")" == "$AQL_RELEASE_VERSION" ]]
fi

derived_apk="$(
  bash tools/release_pipeline/build_aab_derived_package.sh \
    "$aab" \
    release-validation/aab-derived \
    "$keystore_path" \
    | tail -n 1
)"
test -s "$derived_apk"
printf '%s\n' "$derived_apk" > release-validation/aab-derived-apk-path.txt

echo "AQL_RELEASE_CERT_SHA256=$actual" >> "$GITHUB_ENV"
