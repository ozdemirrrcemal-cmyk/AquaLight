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
PINNED_VERSION_RE = re.compile(r"^[0-9][0-9A-Za-z._-]*$")
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
PLUGIN_COMPONENTS = {
    "com.android.application": (
        ("com.android.tools.build", "gradle"),
        ("com.android.application", "com.android.application.gradle.plugin"),
    ),
    "org.jetbrains.kotlin.android": (
        ("org.jetbrains.kotlin", "kotlin-gradle-plugin"),
        (
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.android.gradle.plugin",
        ),
    ),
    "com.google.gms.google-services": (
        ("com.google.gms", "google-services"),
        (
            "com.google.gms.google-services",
            "com.google.gms.google-services.gradle.plugin",
        ),
    ),
    "com.google.protobuf": (
        ("com.google.protobuf", "protobuf-gradle-plugin"),
        ("com.google.protobuf", "com.google.protobuf.gradle.plugin"),
    ),
    "androidx.navigation.safeargs.kotlin": (
        ("androidx.navigation", "navigation-safe-args-gradle-plugin"),
        (
            "androidx.navigation.safeargs.kotlin",
            "androidx.navigation.safeargs.kotlin.gradle.plugin",
        ),
    ),
}
STATIC_REQUIRED_COMPONENTS = {
    ("org.jacoco", "org.jacoco.agent", "0.8.15"),
    ("org.jacoco", "org.jacoco.ant", "0.8.15"),
    ("org.jacoco", "org.jacoco.core", "0.8.15"),
    ("org.jacoco", "org.jacoco.report", "0.8.15"),
}
PLUGIN_DECLARATION_RE = re.compile(
    r"""
    ^\s*id\s*(?:\(\s*)?
    (?P<id_quote>['"])(?P<plugin_id>[^'"]+)(?P=id_quote)
    \s*\)?\s*version\s*(?:\(\s*)?
    (?P<version_quote>['"])(?P<version>[^'"]+)(?P=version_quote)
    \s*\)?
    (?:\s+apply\s*(?:\(\s*)?false\s*\)?)?
    \s*$
    """,
    re.MULTILINE | re.VERBOSE,
)
PLUGIN_ID_RE = re.compile(
    r"""\bid\s*(?:\(\s*)?(?P<quote>['"])(?P<plugin_id>[^'"]+)(?P=quote)"""
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--settings", required=True, type=Path)
    parser.add_argument("--build-tools-manifest", required=True, type=Path)
    parser.add_argument("--lockfile", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def strip_groovy_comments(source: str) -> str:
    """Remove Groovy comments while preserving quoted strings and line positions."""

    output: list[str] = []
    index = 0
    quote: str | None = None
    escaped = False
    while index < len(source):
        char = source[index]
        next_char = source[index + 1] if index + 1 < len(source) else ""
        if quote is not None:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            index += 1
            continue
        if char in {"'", '"'}:
            quote = char
            output.append(char)
            index += 1
            continue
        if char == "/" and next_char == "/":
            output.extend((" ", " "))
            index += 2
            while index < len(source) and source[index] not in "\r\n":
                output.append(" ")
                index += 1
            continue
        if char == "/" and next_char == "*":
            output.extend((" ", " "))
            index += 2
            while index < len(source):
                if source[index] == "*" and index + 1 < len(source) and source[index + 1] == "/":
                    output.extend((" ", " "))
                    index += 2
                    break
                output.append(source[index] if source[index] in "\r\n" else " ")
                index += 1
            else:
                raise ValueError("settings.gradle contains an unterminated block comment")
            continue
        output.append(char)
        index += 1
    if quote is not None:
        raise ValueError("settings.gradle contains an unterminated quoted string")
    return "".join(output)


def mask_quoted_strings(source: str) -> str:
    """Mask strings so structural block parsing ignores braces inside literals."""

    output: list[str] = []
    quote: str | None = None
    escaped = False
    for char in source:
        if quote is not None:
            output.append(char if char in "\r\n" else " ")
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in {"'", '"'}:
            quote = char
            output.append(" ")
        else:
            output.append(char)
    return "".join(output)


def extract_single_named_block(
    source: str,
    block_name: str,
    *,
    start: int = 0,
    end: int | None = None,
) -> tuple[int, int]:
    """Return the body bounds for exactly one named Groovy block."""

    masked = mask_quoted_strings(source)
    boundary = len(source) if end is None else end
    matches: list[tuple[int, int]] = []
    for match in re.finditer(rf"\b{re.escape(block_name)}\b", masked[start:boundary]):
        name_end = start + match.end()
        brace = name_end
        while brace < boundary and masked[brace].isspace():
            brace += 1
        if brace >= boundary or masked[brace] != "{":
            continue
        depth = 1
        cursor = brace + 1
        while cursor < boundary and depth:
            if masked[cursor] == "{":
                depth += 1
            elif masked[cursor] == "}":
                depth -= 1
            cursor += 1
        if depth:
            raise ValueError(f"settings.gradle contains an unterminated {block_name} block")
        matches.append((brace + 1, cursor - 1))
    if len(matches) != 1:
        raise ValueError(
            f"settings.gradle must contain exactly one {block_name} block; "
            f"found {len(matches)}"
        )
    return matches[0]


def parse_plugin_versions(path: Path) -> dict[str, str]:
    try:
        source = path.read_text(encoding="utf-8")
    except OSError as error:
        raise ValueError(f"cannot read settings manifest {path}: {error}") from error

    uncommented = strip_groovy_comments(source)
    management_start, management_end = extract_single_named_block(
        uncommented,
        "pluginManagement",
    )
    plugins_start, plugins_end = extract_single_named_block(
        uncommented,
        "plugins",
        start=management_start,
        end=management_end,
    )
    plugins_body = uncommented[plugins_start:plugins_end]

    occurrences: dict[str, int] = {}
    for match in PLUGIN_ID_RE.finditer(plugins_body):
        plugin_id = match.group("plugin_id")
        if plugin_id in PLUGIN_COMPONENTS:
            occurrences[plugin_id] = occurrences.get(plugin_id, 0) + 1

    versions: dict[str, str] = {}
    declarations: dict[str, int] = {}
    for match in PLUGIN_DECLARATION_RE.finditer(plugins_body):
        plugin_id = match.group("plugin_id")
        if plugin_id not in PLUGIN_COMPONENTS:
            continue
        declarations[plugin_id] = declarations.get(plugin_id, 0) + 1
        version = match.group("version").strip()
        if (
            not PINNED_VERSION_RE.fullmatch(version)
            or "+" in version
            or version.upper().endswith("SNAPSHOT")
            or version.lower().startswith("latest.")
        ):
            raise ValueError(
                f"settings.gradle plugin {plugin_id} must use a pinned literal version"
            )
        versions[plugin_id] = version

    for plugin_id in PLUGIN_COMPONENTS:
        if occurrences.get(plugin_id, 0) != 1:
            raise ValueError(
                f"settings.gradle plugin {plugin_id} must be declared exactly once"
            )
        if declarations.get(plugin_id, 0) != 1 or plugin_id not in versions:
            raise ValueError(
                f"settings.gradle plugin {plugin_id} must use one literal version"
            )
    return versions


def required_components_for_plugins(
    plugin_versions: dict[str, str],
) -> set[tuple[str, str, str]]:
    required = set(STATIC_REQUIRED_COMPONENTS)
    for plugin_id, coordinates in PLUGIN_COMPONENTS.items():
        version = plugin_versions[plugin_id]
        required.update((group, name, version) for group, name in coordinates)
    return required


def parse_build_tools_manifest(
    path: Path,
    plugin_versions: dict[str, str],
) -> set[tuple[str, str, str]]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read resolved build-tools manifest {path}: {error}") from error
    if not isinstance(value, dict) or set(value) != {
        "schemaVersion",
        "androidGradlePluginVersion",
        "components",
    }:
        raise ValueError("resolved build-tools manifest fields are invalid")
    if value["schemaVersion"] != 1:
        raise ValueError("resolved build-tools manifest schemaVersion must be 1")
    agp_version = value["androidGradlePluginVersion"]
    if agp_version != plugin_versions["com.android.application"]:
        raise ValueError(
            "resolved build-tools manifest AGP version does not match settings.gradle"
        )
    raw_components = value["components"]
    if not isinstance(raw_components, list) or not raw_components:
        raise ValueError("resolved build-tools manifest components must be non-empty")

    required_coordinates = {
        ("com.android.tools.build", "aapt2"),
        ("com.android.tools.lint", "lint-gradle"),
    }
    components: set[tuple[str, str, str]] = set()
    for index, raw_component in enumerate(raw_components):
        if not isinstance(raw_component, dict) or set(raw_component) != {
            "group",
            "name",
            "version",
        }:
            raise ValueError(
                f"resolved build-tools manifest component {index} fields are invalid"
            )
        group = raw_component["group"]
        name = raw_component["name"]
        version = raw_component["version"]
        if any(
            not isinstance(item, str) or not item.strip()
            for item in (group, name, version)
        ):
            raise ValueError(
                f"resolved build-tools manifest component {index} is invalid"
            )
        if not PINNED_VERSION_RE.fullmatch(version):
            raise ValueError(
                f"resolved build-tools manifest component {group}:{name} "
                "must use a pinned version"
            )
        identity = (group, name, version)
        if identity in components:
            raise ValueError(
                "resolved build-tools manifest contains duplicate component "
                + ":".join(identity)
            )
        components.add(identity)

    coordinates = {(group, name) for group, name, _ in components}
    if coordinates != required_coordinates:
        missing = sorted(required_coordinates - coordinates)
        unexpected = sorted(coordinates - required_coordinates)
        details: list[str] = []
        if missing:
            details.append(
                "missing " + ", ".join(":".join(item) for item in missing)
            )
        if unexpected:
            details.append(
                "unexpected " + ", ".join(":".join(item) for item in unexpected)
            )
        raise ValueError(
            "resolved build-tools manifest coordinates are invalid: "
            + "; ".join(details)
        )
    return components


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


def parse_metadata(
    path: Path,
    required_components: set[tuple[str, str, str]],
) -> tuple[int, int, set[tuple[str, str, str]]]:
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

    missing_components = sorted(required_components - components)
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
        plugin_versions = parse_plugin_versions(args.settings)
        required_components = required_components_for_plugins(plugin_versions)
        required_components.update(
            parse_build_tools_manifest(
                args.build_tools_manifest,
                plugin_versions,
            )
        )
        lock_entries, configurations = parse_lockfile(args.lockfile)
        component_count, artifact_count, _ = parse_metadata(
            args.metadata,
            required_components,
        )
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
        "declaredPlugins": [
            {
                "id": plugin_id,
                "version": plugin_versions[plugin_id],
                "source": args.settings.as_posix(),
            }
            for plugin_id in PLUGIN_COMPONENTS
        ],
        "resolvedBuildToolsManifest": args.build_tools_manifest.as_posix(),
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
