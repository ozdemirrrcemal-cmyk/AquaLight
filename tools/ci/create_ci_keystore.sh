#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT_DIR="${RUNNER_TEMP:-${PWD}/.ci-secrets}"
KEYSTORE_PATH="${OUTPUT_DIR}/aqualight-ci-release.jks"
mkdir -p "$OUTPUT_DIR"

if [[ ! -s "$KEYSTORE_PATH" ]]; then
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE_PATH" \
    -storepass aqualight-ci \
    -alias aqualight-ci \
    -keypass aqualight-ci \
    -keyalg RSA \
    -keysize 2048 \
    -validity 1 \
    -storetype JKS \
    -dname "CN=AquaLight CI,O=AquaLight,C=TR" >/dev/null
fi
chmod 600 "$KEYSTORE_PATH"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    printf 'RELEASE_KEYSTORE_PATH=%s\n' "$KEYSTORE_PATH"
    printf 'RELEASE_KEYSTORE_PASSWORD=%s\n' 'aqualight-ci'
    printf 'RELEASE_KEY_ALIAS=%s\n' 'aqualight-ci'
    printf 'RELEASE_KEY_PASSWORD=%s\n' 'aqualight-ci'
  } >> "$GITHUB_ENV"
else
  printf 'export RELEASE_KEYSTORE_PATH=%q\n' "$KEYSTORE_PATH"
  printf 'export RELEASE_KEYSTORE_PASSWORD=%q\n' 'aqualight-ci'
  printf 'export RELEASE_KEY_ALIAS=%q\n' 'aqualight-ci'
  printf 'export RELEASE_KEY_PASSWORD=%q\n' 'aqualight-ci'
fi
