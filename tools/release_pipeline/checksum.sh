#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C
cd final-release/artifacts
mapfile -t binaries < <(find . -maxdepth 1 -type f \( -name '*.aab' -o -name '*.apk' \) -printf '%f\n' | sort)
(( ${#binaries[@]} >= 1 && ${#binaries[@]} <= 2 ))
: > SHA256SUMS
for binary in "${binaries[@]}"; do
  sha256sum "$binary" | tee "${binary}.sha256" >> SHA256SUMS
done
sha256sum --check --strict SHA256SUMS
