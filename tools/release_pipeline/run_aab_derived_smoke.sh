#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

readonly apk="${1:?Derived universal APK path is required}"
readonly source_link="${2:?Source-link JSON path is required}"
readonly evidence_dir="${3:-final-release/evidence/aab-derived}"
readonly package_name="com.aqua.aqualight"
readonly splash_activity="com.aqua.aqualight.ui.splash.SplashActivity"
readonly component="${package_name}/${splash_activity}"
readonly apkanalyzer="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"

[[ -s "$apk" ]]
[[ -s "$source_link" ]]
test -x "$apkanalyzer"
rm -rf "$evidence_dir"
mkdir -p "$evidence_dir"

cleanup() {
  status=$?
  set +e
  adb shell uiautomator dump /sdcard/aab-derived-window.xml >/dev/null 2>&1
  adb pull /sdcard/aab-derived-window.xml "$evidence_dir/window.xml" >/dev/null 2>&1
  adb exec-out screencap -p > "$evidence_dir/screenshot.png" 2>/dev/null
  adb logcat -d > "$evidence_dir/logcat-full.txt" 2>&1
  adb shell dumpsys activity activities > "$evidence_dir/activities.txt" 2>&1
  adb shell dumpsys package "$package_name" > "$evidence_dir/package.txt" 2>&1
  adb shell pm path "$package_name" > "$evidence_dir/package-path.txt" 2>&1
  adb uninstall "$package_name" >/dev/null 2>&1
  exit "$status"
}
trap cleanup EXIT

python3 - "$apk" "$source_link" <<'PY'
import hashlib
import json
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


apk = Path(sys.argv[1])
link = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
if link.get("approved") is not True:
    raise SystemExit("AAB-derived source-link evidence is not approved.")
expected = link.get("derivedUniversalApk", {}).get("sha256")
if expected != sha256(apk):
    raise SystemExit("Derived APK hash does not match source-link evidence.")
PY

readonly actual_package="$("$apkanalyzer" manifest application-id "$apk" | tr -d '\r\n')"
readonly actual_version_name="$("$apkanalyzer" manifest version-name "$apk" | tr -d '\r\n')"
readonly actual_version_code="$("$apkanalyzer" manifest version-code "$apk" | tr -d '\r\n')"
[[ "$actual_package" == "$package_name" ]]

python3 - "$source_link" "$actual_version_name" "$actual_version_code" <<'PY'
import json
import sys
from pathlib import Path

link = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if link.get("versionName") != sys.argv[2]:
    raise SystemExit("Installed APK versionName differs from source-link evidence.")
if link.get("versionCode") != int(sys.argv[3]):
    raise SystemExit("Installed APK versionCode differs from source-link evidence.")
PY

adb wait-for-device
adb uninstall "$package_name" >/dev/null 2>&1 || true
adb logcat -c
adb install --no-streaming "$apk" 2>&1 | tee "$evidence_dir/install.txt"
adb shell pm path "$package_name" 2>&1 | tee "$evidence_dir/package-path-before-launch.txt"

set +e
adb shell am start -W -S -n "$component" 2>&1 | tee "$evidence_dir/launch.txt"
launch_status=${PIPESTATUS[0]}
set -e
if (( launch_status != 0 )); then
  echo "AAB-derived APK launch command failed: ${launch_status}" >&2
  exit "$launch_status"
fi
if grep -Eiq '(^|[[:space:]])(Error|Exception):|Activity class .* does not exist' "$evidence_dir/launch.txt"; then
  echo "AAB-derived APK launch output contains an error." >&2
  exit 1
fi

sleep 8
readonly pid="$(adb shell pidof "$package_name" 2>/dev/null | tr -d '\r\n')"
if [[ -z "$pid" ]]; then
  echo "AAB-derived application process is not alive after launch." >&2
  exit 1
fi

adb logcat -d --pid="$pid" > "$evidence_dir/logcat-process.txt" 2>&1
adb shell dumpsys activity activities > "$evidence_dir/activities-before-summary.txt"
if ! grep -Fq "$package_name" "$evidence_dir/activities-before-summary.txt"; then
  echo "AAB-derived application has no active activity record." >&2
  exit 1
fi
if grep -Fq "FATAL EXCEPTION" "$evidence_dir/logcat-process.txt"; then
  echo "AAB-derived application logged a fatal exception." >&2
  exit 1
fi

adb logcat -d > "$evidence_dir/logcat-launch-window.txt" 2>&1
if grep -Fq "ANR in ${package_name}" "$evidence_dir/logcat-launch-window.txt"; then
  echo "AAB-derived application entered ANR during launch." >&2
  exit 1
fi

cp "$source_link" "$evidence_dir/source-link.json"
python3 - \
  "$apk" \
  "$evidence_dir/source-link.json" \
  "$evidence_dir/smoke-summary.json" \
  "$pid" \
  "$actual_package" \
  "$actual_version_name" \
  "$actual_version_code" <<'PY'
import hashlib
import json
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


apk = Path(sys.argv[1])
source_link = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
summary = {
    "schemaVersion": 1,
    "approved": True,
    "package": sys.argv[5],
    "versionName": sys.argv[6],
    "versionCode": int(sys.argv[7]),
    "pidObserved": int(sys.argv[4]),
    "launchActivity": "com.aqua.aqualight.ui.splash.SplashActivity",
    "derivedApkSha256": sha256(apk),
    "sourceAabSha256": source_link["sourceAab"]["sha256"],
    "sourceLinkApproved": source_link.get("approved") is True,
    "installPassed": True,
    "launchPassed": True,
    "processAliveAfterSettle": True,
    "fatalExceptionDetected": False,
    "anrDetected": False,
}
Path(sys.argv[3]).write_text(
    json.dumps(summary, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print(json.dumps(summary, indent=2, sort_keys=True))
PY

test -s "$evidence_dir/smoke-summary.json"
printf 'AAB-derived install and launch smoke passed.\n'
