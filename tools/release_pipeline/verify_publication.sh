#!/usr/bin/env bash
set -Eeuo pipefail
(cd final-release/artifacts && sha256sum --check --strict SHA256SUMS)
(cd final-release/supply-chain && sha256sum --check --strict SHA256SUMS)
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab"
test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.aab.sha256"
test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.aab.spdx.json"
if [[ "${AQL_INCLUDE_APK:-false}" == "true" ]]; then
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk"
  test -s "final-release/artifacts/AquaLight-${AQL_RELEASE_VERSION}.apk.sha256"
  test -s "final-release/supply-chain/AquaLight-${AQL_RELEASE_VERSION}.apk.spdx.json"
fi
