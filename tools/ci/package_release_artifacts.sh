#!/usr/bin/env bash
set -Eeuo pipefail

: "${AQL_VERSION_NAME:?AQL_VERSION_NAME is required}"
DIST_DIR="${1:-dist}"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
APK_PATH="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | head -n 1)"
MAPPING_PATH="app/build/outputs/mapping/release/mapping.txt"
SBOM_JSON="build/reports/cyclonedx/bom.json"
SBOM_XML="build/reports/cyclonedx/bom.xml"

for artifact in "$AAB_PATH" "$APK_PATH" "$MAPPING_PATH" "$SBOM_JSON" "$SBOM_XML"; do
  if [[ -z "$artifact" || ! -s "$artifact" ]]; then
    echo "Required release artifact is missing or empty: ${artifact:-<none>}" >&2
    exit 1
  fi
done

cp "$AAB_PATH" "$DIST_DIR/AquaLight-${AQL_VERSION_NAME}.aab"
cp "$APK_PATH" "$DIST_DIR/AquaLight-${AQL_VERSION_NAME}.apk"
cp "$MAPPING_PATH" "$DIST_DIR/AquaLight-${AQL_VERSION_NAME}-mapping.txt"
cp "$SBOM_JSON" "$DIST_DIR/AquaLight-${AQL_VERSION_NAME}.cdx.json"
cp "$SBOM_XML" "$DIST_DIR/AquaLight-${AQL_VERSION_NAME}.cdx.xml"

(
  cd "$DIST_DIR"
  sha256sum AquaLight-* > SHA256SUMS
)

test -s "$DIST_DIR/SHA256SUMS"
ls -lh "$DIST_DIR"
