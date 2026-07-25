#!/usr/bin/env python3
"""Validate the identity, manifest policy, structure, and R8 evidence of a release AAB."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")


class AabValidationFailure(RuntimeError):
    """Raised when a release bundle does not satisfy the commercial identity contract."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--bundletool-validation", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--expected-package", required=True)
    parser.add_argument("--expected-version-name", required=True)
    parser.add_argument("--expected-version-code", type=int, required=True)
    parser.add_argument("--expected-min-sdk", type=int, required=True)
    parser.add_argument("--expected-target-sdk", type=int, required=True)
    parser.add_argument("--expected-application", required=True)
    parser.add_argument("--bundletool-version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def require_file(path: Path, label: str) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise AabValidationFailure(f"{label} is missing or empty: {path}")


def parse_int(value: str | None, label: str) -> int:
    if value is None:
        raise AabValidationFailure(f"Manifest attribute is missing: {label}")
    try:
        return int(value)
    except ValueError as error:
        raise AabValidationFailure(f"Manifest {label} is not an integer: {value}") from error


def parse_bool(value: str | None, default: bool = False) -> bool:
    if value is None:
        return default
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise AabValidationFailure(f"Manifest boolean is invalid: {value}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def component_name(node: ET.Element) -> str:
    return node.get(f"{ANDROID}name", "")


def validate_manifest(args: argparse.Namespace) -> dict[str, object]:
    try:
        root = ET.parse(args.manifest).getroot()
    except (ET.ParseError, OSError) as error:
        raise AabValidationFailure(f"Bundle manifest could not be parsed: {error}") from error

    if root.tag != "manifest":
        raise AabValidationFailure(f"Expected <manifest>, found <{root.tag}>.")

    package_name = root.get("package", "")
    version_name = root.get(f"{ANDROID}versionName", "")
    version_code = parse_int(root.get(f"{ANDROID}versionCode"), "versionCode")
    uses_sdk = root.find("uses-sdk")
    if uses_sdk is None:
        raise AabValidationFailure("Bundle manifest has no <uses-sdk> declaration.")
    min_sdk = parse_int(uses_sdk.get(f"{ANDROID}minSdkVersion"), "minSdkVersion")
    target_sdk = parse_int(uses_sdk.get(f"{ANDROID}targetSdkVersion"), "targetSdkVersion")

    application = root.find("application")
    if application is None:
        raise AabValidationFailure("Bundle manifest has no <application> element.")

    application_name = application.get(f"{ANDROID}name", "")
    debuggable = parse_bool(application.get(f"{ANDROID}debuggable"), default=False)
    test_only = parse_bool(application.get(f"{ANDROID}testOnly"), default=False)
    allow_backup = parse_bool(application.get(f"{ANDROID}allowBackup"), default=True)
    supports_rtl = parse_bool(application.get(f"{ANDROID}supportsRtl"), default=False)
    uses_cleartext = parse_bool(application.get(f"{ANDROID}usesCleartextTraffic"), default=False)

    checks = {
        "package": package_name == args.expected_package,
        "versionName": version_name == args.expected_version_name,
        "versionCode": version_code == args.expected_version_code,
        "minSdk": min_sdk == args.expected_min_sdk,
        "targetSdk": target_sdk == args.expected_target_sdk,
        "applicationName": application_name == args.expected_application,
        "debuggableFalse": not debuggable,
        "testOnlyFalse": not test_only,
        "backupDisabled": not allow_backup,
        "supportsRtl": supports_rtl,
        "cleartextNotGloballyEnabled": not uses_cleartext,
    }

    exported_components: list[dict[str, object]] = []
    implicit_export_errors: list[str] = []
    for tag in COMPONENT_TAGS:
        for node in application.findall(tag):
            has_intent_filter = node.find("intent-filter") is not None
            exported_raw = node.get(f"{ANDROID}exported")
            if has_intent_filter and exported_raw not in {"true", "false"}:
                implicit_export_errors.append(f"{tag}:{component_name(node)}")
            exported_components.append(
                {
                    "type": tag,
                    "name": component_name(node),
                    "hasIntentFilter": has_intent_filter,
                    "exported": exported_raw,
                }
            )
    checks["intentFilterComponentsDeclareExported"] = not implicit_export_errors

    failed = sorted(name for name, passed in checks.items() if not passed)
    if failed:
        raise AabValidationFailure(
            "Bundle manifest identity/policy checks failed: " + ", ".join(failed)
        )

    return {
        "package": package_name,
        "versionName": version_name,
        "versionCode": version_code,
        "minSdk": min_sdk,
        "targetSdk": target_sdk,
        "applicationName": application_name,
        "debuggable": debuggable,
        "testOnly": test_only,
        "allowBackup": allow_backup,
        "supportsRtl": supports_rtl,
        "usesCleartextTraffic": uses_cleartext,
        "checks": checks,
        "components": exported_components,
        "implicitExportErrors": implicit_export_errors,
    }


def validate_archive(aab: Path) -> dict[str, object]:
    try:
        with zipfile.ZipFile(aab) as archive:
            names = sorted(archive.namelist())
            bad_entry = archive.testzip()
    except (OSError, zipfile.BadZipFile) as error:
        raise AabValidationFailure(f"AAB is not a valid ZIP archive: {error}") from error

    if bad_entry is not None:
        raise AabValidationFailure(f"AAB ZIP integrity failed at entry: {bad_entry}")

    modules = sorted(
        name.split("/", 1)[0]
        for name in names
        if name.endswith("/manifest/AndroidManifest.xml") and "/" in name
    )
    modules = sorted(set(modules))
    required_entries = {
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
        "base/resources.pb",
    }
    missing_entries = sorted(required_entries - set(names))
    dex_entries = [name for name in names if name.startswith("base/dex/") and name.endswith(".dex")]
    forbidden_entries = [
        name for name in names if name.startswith("/") or "../" in name or name.endswith(".DS_Store")
    ]

    if missing_entries:
        raise AabValidationFailure("AAB required entries are missing: " + ", ".join(missing_entries))
    if not dex_entries:
        raise AabValidationFailure("AAB base module contains no DEX payload.")
    if modules != ["base"]:
        raise AabValidationFailure(f"Unexpected AAB module set: {modules}")
    if forbidden_entries:
        raise AabValidationFailure(f"AAB contains unsafe/unwanted entries: {forbidden_entries}")

    return {
        "entryCount": len(names),
        "modules": modules,
        "dexEntries": dex_entries,
        "requiredEntries": sorted(required_entries),
        "missingEntries": missing_entries,
        "forbiddenEntries": forbidden_entries,
        "zipIntegrity": "passed",
    }


def main() -> int:
    args = parse_args()
    try:
        require_file(args.aab, "Release AAB")
        require_file(args.manifest, "bundletool manifest dump")
        require_file(args.bundletool_validation, "bundletool validation evidence")
        require_file(args.mapping, "R8 mapping")

        validation_text = args.bundletool_validation.read_text(
            encoding="utf-8", errors="replace"
        )
        if "BUNDLETOOL_VALIDATE_PASS" not in validation_text:
            raise AabValidationFailure("bundletool validate success marker is missing.")

        manifest = validate_manifest(args)
        archive = validate_archive(args.aab)
        mapping_line_count = sum(
            1 for _ in args.mapping.open("r", encoding="utf-8", errors="replace")
        )
        if mapping_line_count < 10:
            raise AabValidationFailure(
                f"R8 mapping is unexpectedly small: {mapping_line_count} lines."
            )

        summary = {
            "schemaVersion": 1,
            "approved": True,
            "bundletoolVersion": args.bundletool_version,
            "aab": str(args.aab),
            "aabSha256": sha256(args.aab),
            "aabSizeBytes": args.aab.stat().st_size,
            "manifest": manifest,
            "archive": archive,
            "mapping": {
                "path": str(args.mapping),
                "sha256": sha256(args.mapping),
                "sizeBytes": args.mapping.stat().st_size,
                "lineCount": mapping_line_count,
            },
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 0
    except AabValidationFailure as error:
        print(f"Release AAB validation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
