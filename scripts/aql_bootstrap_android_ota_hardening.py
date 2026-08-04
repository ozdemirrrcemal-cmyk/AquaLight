#!/usr/bin/env python3
from __future__ import annotations

import base64
import gzip
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGING_ROOT = ROOT / ".android-ota-hardening"
ARCHIVE = STAGING_ROOT / "script.py.gz.b64"
APPLICATION = ROOT / "scripts/aql_apply_android_ota_hardening.py"


def main() -> int:
    if not ARCHIVE.is_file():
        raise SystemExit("Android OTA hardening archive is missing")
    try:
        encoded = ARCHIVE.read_text(encoding="utf-8").strip()
        application_source = gzip.decompress(base64.b64decode(encoded, validate=True))
    except (ValueError, OSError) as error:
        raise SystemExit(f"Android OTA hardening archive is invalid: {error}") from error

    APPLICATION.write_bytes(application_source)
    result = subprocess.run(
        [sys.executable, str(APPLICATION)],
        cwd=ROOT,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    shutil.rmtree(STAGING_ROOT)
    Path(__file__).unlink()
    print("Applied staged Android OTA commercial hardening.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
