#!/usr/bin/env python3
"""Fail-closed validation for AquaLight Gradle lock and checksum metadata."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
MODULE_RE = re.compile(r"^[^:\s]+:[^:\s]+:[^=\s]+$")
REQUIRED_CONFIGURATIONS = {
    "debugCompileClasspath",
    "debugRuntimeClasspath",
    "debugUnitTestCompileClasspath",
    "debugUnitTestRuntimeClasspath",
    "debugAndroidTestCompileClasspath",
    "debugAndroidTestRuntimeClasspath",
    "stagingCompileClasspath",
    "stagingRuntimeClasspath",
    "releaseCompileClasspath",
    "releaseRuntimeClasspath",
    "releaseSmokeCompileClasspath",
    "releaseSmokeRuntimeClasspath",
    "androidJacocoAnt",
    "jacocoAgent",
    "jacocoAnt",
    "detektCli",
}
REQUIRED_COMPONENTS = {
    ("com.android.tools.build", "gradle", "8.10.0"),
    ("com.android.tools.build", "aapt2", "8.10.0-12782657"),
    ("com.android.tools.lint", "lint-gradle", "31.10.0"),
    ("org.jetbrains.kotlin", "kotlin-gradle-plugin", "2.1.0"),
    ("com.google.gms", "google-services", "4.4.4"),
    ("com.google.protobuf", "protobuf-gradle-plugin", "0.9.5"),
    ("androidx.navigation", "navigation-safe-args-gradle-plugin", "2.9.5"),
    ("org.jacoco", "org.jacoco.agent", "0.8.15"),
    ("org.jacoco", "org.jacoco.ant", "0.8.15"),
    ("org.jacoco", "org.jacoco.core", "0.8.15"),
    ("org.jacoco", "org.jacoco.report", "0.8.15"),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lockfile", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def parse_lockfile(path: Path) -> tuple[int, set[str]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ValueError(f"cannot read lockfile {path}: {error}") from error

    entries = 0
    configurations: set[str] = set()
    has_empty_state = False
    for line_number, raw in enumerate(lines, start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"invalid lockfile line {line_number}: missing '='")
        module, configuration_csv = line.split("=", 1)
        if module == "empty":
            has_empty_state = True
        else:
            if not MODULE_RE.fullmatch(module):
                raise ValueError(f"invalid locked module at line {line_number}: {module!r}")
            version = module.rsplit(":", 1)[1]
            if "+" in version or version.upper().endswith("SNAPSHOT"):
                raise ValueError(f"dynamic or changing version in lockfile: {module}")
            entries += 1
        for name in configuration_csv.split(","):
            normalized = name.strip()
            if normalized:
                configurations.add(normalized)

    if entries == 0:
        raise ValueError("lockfile contains no locked modules")
    if not has_empty_state:
        raise ValueError("lockfile does not record empty configurations")
    missing = sorted(REQUIRED_CONFIGURATIONS - configurations)
    if missing:
        raise ValueError("lockfile is missing required configurations: " + ", ".join(missing))
    return entries, configurations


def parse_metadata(path: Path) -> tuple[int, int, set[tuple[str, str, str]]]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError(f"cannot read verification metadata {path}: {error}") from error

    namespace_uri = "https://schema.gradle.org/dependency-verification"
    if root.tag != f"{{{namespace_uri}}}verification-metadata":
        raise ValueError(f"unexpected verification metadata root: {root.tag!r}")
    ns = {"v": namespace_uri}

    configuration = root.find("v:configuration", ns)
    if configuration is None:
        raise ValueError("verification metadata configuration is missing")
    if configuration.findtext("v:verify-metadata", namespaces=ns) != "true":
        raise ValueError("verification metadata must verify repository metadata")
    if configuration.findtext("v:verify-signatures", namespaces=ns) != "false":
        raise ValueError("stage 14 checksum policy expects verify-signatures=false")
    if root.find("v:configuration/v:trusted-artifacts", ns) is not None:
        raise ValueError("trusted-artifacts exemptions are not allowed")

    components: set[tuple[str, str, str]] = set()
    artifact_count = 0
    for component in root.findall("v:components/v:component", ns):
        identity = (
            component.get("group", ""),
            component.get("name", ""),
            component.get("version", ""),
        )
        if not all(identity):
            raise ValueError(f"component identity is incomplete: {identity!r}")
        components.add(identity)
        artifacts = component.findall("v:artifact", ns)
        if not artifacts:
            raise ValueError(f"component has no verified artifacts: {identity}")
        for artifact in artifacts:
            artifact_count += 1
            sha256_nodes = artifact.findall("v:sha256", ns)
            if not sha256_nodes:
                raise ValueError(
                    f"artifact has no SHA-256 checksum: {identity} {artifact.get('name')}"
                )
            for node in sha256_nodes:
                value = (node.get("value") or "").lower()
                if not SHA256_RE.fullmatch(value):
                    raise ValueError(
                        f"invalid SHA-256 checksum: {identity} {artifact.get('name')}"
                    )
            if artifact.find("v:md5", ns) is not None or artifact.find("v:sha1", ns) is not None:
                raise ValueError(f"weak checksum found: {identity} {artifact.get('name')}")

    missing_components = sorted(REQUIRED_COMPONENTS - components)
    if missing_components:
        formatted = [":".join(item) for item in missing_components]
        raise ValueError(
            "verification metadata is missing build tools/plugins: " + ", ".join(formatted)
        )
    if not components or not artifact_count:
        raise ValueError("verification metadata contains no components or artifacts")
    return len(components), artifact_count, components


def main() -> int:
    args = parse_args()
    try:
        lock_entries, configurations = parse_lockfile(args.lockfile)
        component_count, artifact_count, _ = parse_metadata(args.metadata)
    except ValueError as error:
        print(f"Dependency integrity policy failed: {error}", file=sys.stderr)
        return 1

    summary = {
        "schemaVersion": 1,
        "passed": True,
        "lockfile": {
            "moduleCount": lock_entries,
            "configurationCount": len(configurations),
        },
        "verificationMetadata": {
            "componentCount": component_count,
            "artifactCount": artifact_count,
            "algorithm": "SHA-256",
            "verifyMetadata": True,
            "verifySignatures": False,
        },
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        "Dependency integrity policy passed: "
        f"{lock_entries} locked modules across {len(configurations)} configurations; "
        f"{component_count} components and {artifact_count} artifacts checksum-verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
