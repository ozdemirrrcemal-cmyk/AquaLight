#!/usr/bin/env bash
set -Eeuo pipefail

readonly CONTRACT_PATH="${AQL_DEBUG_SIGNING_CONTRACT:-config/signing/debug-certificate.properties}"
readonly KEYSTORE_PATH="${AQL_DEBUG_KEYSTORE_PATH:-${HOME}/.android/debug.keystore}"
readonly STORE_PASSWORD="android"

fail() {
  echo "Persistent debug signing error: $*" >&2
  exit 1
}

read_contract_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$CONTRACT_PATH" | head -n 1
}

normalize_fingerprint() {
  tr '[:lower:]' '[:upper:]' | tr -d ':' | tr -d '[:space:]'
}

[[ -f "$CONTRACT_PATH" ]] || fail "certificate contract is missing: ${CONTRACT_PATH}"

secret="${AQL_DEBUG_KEYSTORE_BASE64:-}"
[[ -n "${secret//[[:space:]]/}" ]] || fail \
  "AQL_DEBUG_KEYSTORE_BASE64 is missing. Do not generate a temporary replacement key."

alias_name="$(read_contract_value alias)"
expected_sha1="$(read_contract_value sha1)"
expected_sha256="$(read_contract_value sha256)"

[[ -n "$alias_name" ]] || fail "alias is missing from ${CONTRACT_PATH}"
[[ -n "$expected_sha1" ]] || fail "sha1 is missing from ${CONTRACT_PATH}"
[[ -n "$expected_sha256" ]] || fail "sha256 is missing from ${CONTRACT_PATH}"

mkdir -p "$(dirname "$KEYSTORE_PATH")"
umask 077
temporary_path="${KEYSTORE_PATH}.tmp"
trap 'rm -f "$temporary_path"' EXIT

printf '%s' "$secret" \
  | tr -d '[:space:]' \
  | base64 --decode > "$temporary_path" \
  || fail "AQL_DEBUG_KEYSTORE_BASE64 is not valid Base64"

[[ -s "$temporary_path" ]] || fail "decoded keystore is empty"

certificate_details="$(
  keytool -list -v \
    -keystore "$temporary_path" \
    -storepass "$STORE_PASSWORD" \
    -alias "$alias_name" 2>/dev/null
)" || fail "decoded keystore or alias is invalid"

actual_sha1="$(
  printf '%s\n' "$certificate_details" \
    | sed -n 's/^[[:space:]]*SHA1: //p' \
    | head -n 1
)"
actual_sha256="$(
  printf '%s\n' "$certificate_details" \
    | sed -n 's/^[[:space:]]*SHA256: //p' \
    | head -n 1
)"

[[ "$(printf '%s' "$actual_sha1" | normalize_fingerprint)" == \
   "$(printf '%s' "$expected_sha1" | normalize_fingerprint)" ]] || \
  fail "SHA-1 certificate fingerprint does not match the committed contract"

[[ "$(printf '%s' "$actual_sha256" | normalize_fingerprint)" == \
   "$(printf '%s' "$expected_sha256" | normalize_fingerprint)" ]] || \
  fail "SHA-256 certificate fingerprint does not match the committed contract"

mv "$temporary_path" "$KEYSTORE_PATH"
chmod 600 "$KEYSTORE_PATH"
trap - EXIT

echo "Persistent debug keystore provisioned."
echo "Alias: ${alias_name}"
echo "SHA-1: ${expected_sha1}"
echo "SHA-256: ${expected_sha256}"
