#!/usr/bin/env bash
set -euo pipefail

raw_version="${1:-}"
version_name="${raw_version#v}"

if [[ ! "$version_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Version must use stable SemVer format vMAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH." >&2
  exit 2
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

major_value=$((10#$major))
minor_value=$((10#$minor))
patch_value=$((10#$patch))

if (( major_value > 2100 || minor_value > 999 || patch_value > 999 )); then
  echo "Version components exceed the supported versionCode ranges." >&2
  exit 3
fi

version_code=$((major_value * 1000000 + minor_value * 1000 + patch_value))
if (( version_code < 1 || version_code > 2100000000 )); then
  echo "Derived versionCode is outside the supported Play Store range." >&2
  exit 4
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version_name=$version_name"
    echo "version_code=$version_code"
  } >> "$GITHUB_OUTPUT"
else
  printf 'version_name=%s\nversion_code=%s\n' "$version_name" "$version_code"
fi
