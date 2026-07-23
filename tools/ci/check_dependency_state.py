#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
root_lock_file = root / "gradle.lockfile"
app_lock_file = root / "app" / "gradle.lockfile"
metadata_file = root / "gradle" / "verification-metadata.xml"

for required in (root_lock_file, app_lock_file, metadata_file):
    if not required.is_file() or required.stat().st_size == 0:
        print(f"Missing supply-chain file: {required.relative_to(root)}", file=sys.stderr)
        raise SystemExit(1)

try:
    metadata = ET.parse(metadata_file).getroot()
except ET.ParseError as error:
    print(f"Invalid verification metadata: {error}", file=sys.stderr)
    raise SystemExit(1)

checksums = sum(1 for element in metadata.iter() if element.tag.endswith("sha256"))
if checksums == 0:
    print("Verification metadata has no SHA-256 checksums.", file=sys.stderr)
    raise SystemExit(1)

if app_lock_file.read_text(encoding="utf-8").count("\n") < 3:
    print("Application dependency lock state is unexpectedly empty.", file=sys.stderr)
    raise SystemExit(1)

if "cyclonedxBom" not in root_lock_file.read_text(encoding="utf-8"):
    print("Root dependency lock state does not cover the SBOM task.", file=sys.stderr)
    raise SystemExit(1)

print(f"Dependency state contains {checksums} verified checksum entries.")
