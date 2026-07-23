#!/usr/bin/env bash
set -Eeuo pipefail

required=(
  RELEASE_KEYSTORE_PATH
  RELEASE_KEYSTORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
)
missing=()
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    missing+=("$variable")
  fi
done
if (( ${#missing[@]} > 0 )); then
  printf 'Missing release signing variables: %s\n' "${missing[*]}" >&2
  exit 1
fi

if [[ ! -s "$RELEASE_KEYSTORE_PATH" ]]; then
  echo "Release keystore is missing or empty: $RELEASE_KEYSTORE_PATH" >&2
  exit 1
fi
chmod 600 "$RELEASE_KEYSTORE_PATH"

keytool -list \
  -keystore "$RELEASE_KEYSTORE_PATH" \
  -storepass:env RELEASE_KEYSTORE_PASSWORD \
  -alias "$RELEASE_KEY_ALIAS" >/dev/null

echo "Release signing material passed structural and alias validation."
