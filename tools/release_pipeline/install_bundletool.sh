#!/usr/bin/env bash
set -Eeuo pipefail

readonly BUNDLETOOL_VERSION="1.18.3"
readonly BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
readonly BUNDLETOOL_URL="https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar"

target="${1:-${RUNNER_TEMP:-/tmp}/aqualight-tools/bundletool-all-${BUNDLETOOL_VERSION}.jar}"
target_dir="$(dirname "$target")"
mkdir -p "$target_dir"

if [[ -s "$target" ]]; then
  actual="$(sha256sum "$target" | awk '{print $1}')"
  if [[ "$actual" != "$BUNDLETOOL_SHA256" ]]; then
    rm -f "$target"
  fi
fi

if [[ ! -s "$target" ]]; then
  temporary="${target}.download"
  rm -f "$temporary"
  curl \
    --fail \
    --location \
    --proto '=https' \
    --tlsv1.2 \
    --retry 3 \
    --retry-all-errors \
    --connect-timeout 30 \
    --output "$temporary" \
    "$BUNDLETOOL_URL"
  test -s "$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$target"
fi

actual="$(sha256sum "$target" | awk '{print $1}')"
if [[ "$actual" != "$BUNDLETOOL_SHA256" ]]; then
  echo "bundletool SHA-256 mismatch: expected ${BUNDLETOOL_SHA256}, got ${actual}" >&2
  exit 1
fi

reported_version="$(java -jar "$target" version | tr -d '\r\n')"
if [[ "$reported_version" != "$BUNDLETOOL_VERSION" ]]; then
  echo "bundletool version mismatch: expected ${BUNDLETOOL_VERSION}, got ${reported_version}" >&2
  exit 1
fi

printf 'bundletool %s verified (%s).\n' "$reported_version" "$actual" >&2
printf '%s\n' "$target"
