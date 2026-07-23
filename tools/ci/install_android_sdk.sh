#!/usr/bin/env bash
set -Eeuo pipefail

SDK_MANAGER="$(command -v sdkmanager || true)"
if [[ -z "$SDK_MANAGER" && -n "${ANDROID_HOME:-}" ]]; then
  SDK_MANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
fi
if [[ ! -x "$SDK_MANAGER" ]]; then
  echo "sdkmanager was not found in PATH or ANDROID_HOME." >&2
  exit 1
fi

set +o pipefail
yes | "$SDK_MANAGER" --licenses >/dev/null
set -o pipefail

"$SDK_MANAGER" \
  "platform-tools" \
  "platforms;android-36" \
  "cmdline-tools;latest"

if ! "$SDK_MANAGER" "build-tools;36.0.0"; then
  "$SDK_MANAGER" "build-tools;36.0.1"
fi
