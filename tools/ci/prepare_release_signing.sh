#!/usr/bin/env bash
set -Eeuo pipefail

required=(
  RELEASE_KEYSTORE_BASE64
  RELEASE_KEYSTORE_SHA256
  RELEASE_CERT_SHA256
  RELEASE_KEYSTORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
)
missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done
if (( ${#missing[@]} > 0 )); then
  printf 'Missing release signing secret(s): %s\n' "${missing[*]}" >&2
  exit 2
fi

umask 077
keystore_path="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/aqualight-release.jks"
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$keystore_path"
test -s "$keystore_path"
chmod 600 "$keystore_path"

normalize_digest() {
  tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

actual_keystore_sha="$(sha256sum "$keystore_path" | awk '{print $1}' | normalize_digest)"
expected_keystore_sha="$(printf '%s' "$RELEASE_KEYSTORE_SHA256" | normalize_digest)"
if [[ "$actual_keystore_sha" != "$expected_keystore_sha" ]]; then
  echo 'Release keystore SHA-256 mismatch.' >&2
  rm -f "$keystore_path"
  exit 3
fi

if ! keytool -list \
  -keystore "$keystore_path" \
  -storepass "$RELEASE_KEYSTORE_PASSWORD" \
  -alias "$RELEASE_KEY_ALIAS" >/dev/null 2>&1; then
  echo 'Release alias/password validation failed.' >&2
  rm -f "$keystore_path"
  exit 4
fi

actual_cert_sha="$(
  keytool -list -v \
    -keystore "$keystore_path" \
    -storepass "$RELEASE_KEYSTORE_PASSWORD" \
    -alias "$RELEASE_KEY_ALIAS" \
  | awk -F': ' '/SHA256:/{print $2; exit}' \
  | normalize_digest
)"
expected_cert_sha="$(printf '%s' "$RELEASE_CERT_SHA256" | normalize_digest)"
if [[ -z "$actual_cert_sha" || "$actual_cert_sha" != "$expected_cert_sha" ]]; then
  echo 'Release signing certificate SHA-256 mismatch.' >&2
  rm -f "$keystore_path"
  exit 5
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'AQL_RELEASE_KEYSTORE_PATH=%s\n' "$keystore_path" >> "$GITHUB_ENV"
else
  printf '%s\n' "$keystore_path"
fi

echo 'Release keystore integrity, alias and certificate checks passed.'
