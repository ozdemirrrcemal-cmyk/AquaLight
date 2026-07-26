#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C
release_root="${AQL_RELEASE_ROOT:-candidate-release}"
[[ "$release_root" == "candidate-release" ]]
cd "$release_root/artifacts"
mapfile -t binaries < <(find . -maxdepth 1 -type f \( -name '*.aab' -o -name '*.apk' \) -printf '%f\n' | sort)
(( ${#binaries[@]} == 2 ))
: > SHA256SUMS
for binary in "${binaries[@]}"; do
  sha256sum "$binary" | tee "${binary}.sha256" >> SHA256SUMS
done
sha256sum --check --strict SHA256SUMS
