#!/usr/bin/env python3
from __future__ import annotations

import base64
import gzip
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGING_ROOT = ROOT / ".android-ota-hardening"
APPLICATION = ROOT / "scripts/aql_apply_android_ota_hardening.py"
EXPECTED_ARCHIVE_SHA256 = "768859ffadf96086719ab7ad34eac517e7a2fd0e8ae3f09ab662090ad1ef0205"
EXPECTED_SOURCE_SHA256 = "ac0550ea1a0b945acd9d82bcc8d9e8c2a26d5c7bbc6757d12a625ff36710e89f"


def main() -> int:
    chunks = sorted(STAGING_ROOT.glob("chunk*.b64"))
    if len(chunks) != 4:
        raise SystemExit(f"Android OTA hardening requires exactly four archive chunks; found {len(chunks)}")
    try:
        encoded = "".join(path.read_text(encoding="utf-8").strip() for path in chunks)
        archive = base64.b64decode(encoded, validate=True)
        if hashlib.sha256(archive).hexdigest() != EXPECTED_ARCHIVE_SHA256:
            raise SystemExit("Android OTA hardening archive SHA-256 mismatch")
        application_source = gzip.decompress(archive)
        if hashlib.sha256(application_source).hexdigest() != EXPECTED_SOURCE_SHA256:
            raise SystemExit("Android OTA hardening source SHA-256 mismatch")
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
