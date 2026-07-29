#!/usr/bin/env python3
"""Protect generation-scoped, atomic Android runtime metadata assembly."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices"
MODEL = SOURCE / "model/DeviceRuntimeMetadataGeneration.kt"
REDUCER = SOURCE / "repository/DeviceRuntimeMetadataReducer.kt"
PARSERS = SOURCE / "repository/DeviceRuntimeMetadataParsers.kt"
PROJECTOR = SOURCE / "repository/DeviceRuntimeMetadataProjector.kt"
WIRE_BOUNDARY = SOURCE / "repository/DeviceRuntimeMetadataWireBoundary.kt"
PROVISIONING = SOURCE / "provisioning/repository/AqlProvisioningRuntimeMetadataResolver.kt"

errors: list[str] = []


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


model = read(MODEL)
reducer = read(REDUCER)
parsers = read(PARSERS)
projector = read(PROJECTOR)
wire_boundary = read(WIRE_BOUNDARY)
provisioning = read(PROVISIONING)

for token in (
    "value class DeviceRuntimeMetadataGeneration",
    "data class Collecting",
    "data class Ready",
    "data class Rejected",
    "data class Identity",
    "data class Capabilities",
    "data class Modules",
    "data class IgnoredStale",
    "val publishedMetadata: DeviceRuntimeMetadata?",
):
    require(token in model, f"generation model token is missing: {token}")

require(
    "override val publishedMetadata: DeviceRuntimeMetadata? = null" in model,
    "collecting/rejected generations must not publish partial metadata",
)
require("internal fun next()" in model, "generation must advance monotonically")
require("Long.MAX_VALUE" in model, "generation exhaustion must fail closed")

for token in (
    "fun begin(",
    "fun reduce(",
    "fragment.generation != current.generation",
    "DeviceRuntimeMetadataReduction.IgnoredStale",
    "DeviceRuntimeMetadataGenerationState.Ready",
    "DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY",
    "DeviceRuntimeMetadataFailureCode.CONFLICTING_CAPABILITIES",
    "DeviceRuntimeMetadataFailureCode.CONFLICTING_MODULES",
    "DeviceRuntimeMetadataFailureCode.DEVICE_UID_MISMATCH",
):
    require(token in reducer, f"atomic reducer token is missing: {token}")

for forbidden in (
    "JSONObject",
    "JSONArray",
    "AqlWsIncomingMessage",
    "DeviceSnapshot",
    ".trim()",
    ".lowercase()",
    "ifBlank",
    "optString",
    "optBoolean",
    "optInt",
):
    require(forbidden not in reducer, f"atomic reducer must not parse, normalize or fallback: {forbidden}")

for token in (
    "data.requireExactKeys(IDENTITY_KEYS",
    "runtime.requireExactKeys(RUNTIME_KEYS",
    "data.requireExactKeys(CAPABILITY_RESPONSE_KEYS",
    "capabilities.requireExactKeys(CAPABILITY_KEYS",
    "limits.requireExactKeys(LIMIT_KEYS",
    "DeviceFamily.fromWireExact",
    "parseAqlDeviceFeatureKeysExact",
    "parseAqlDeviceScreenKeysExact",
    "value is Boolean",
    "value is Number",
):
    require(token in parsers, f"exact metadata parser token is missing: {token}")

for forbidden in (
    ".trim()",
    ".lowercase()",
    "optString",
    "optBoolean",
    "optInt",
    "ifBlank",
):
    require(forbidden not in parsers, f"metadata parsers must not normalize or fallback: {forbidden}")

require(
    "fun applyReady(" in projector and "snapshot.copy(" in projector,
    "ready metadata must be projected in one snapshot copy",
)
require(
    "fun applyProvisioningMetadata(" in projector,
    "provisioning must receive one exact identity/capability projection",
)
require(
    "DeviceRuntimeMetadataProjector.applyProvisioningMetadata" in provisioning,
    "provisioning must use the exact atomic projection",
)
for forbidden in (
    "applyDeviceIdentity",
    "applyDeviceCapabilities",
    "identityData: JSONObject?",
    "capabilitiesData: JSONObject?",
):
    require(forbidden not in provisioning, f"permissive provisioning reducer is forbidden: {forbidden}")

require(
    "Direct wire-to-snapshot reduction is forbidden" in wire_boundary,
    "legacy direct wire reduction must be explicitly blocked",
)
require(
    "): DeviceSnapshot? = null" in wire_boundary,
    "direct wire metadata must fail closed without mutating the snapshot",
)
for forbidden in (
    "snapshot.copy(",
    "optString",
    "ifBlank",
    "previous.",
):
    require(forbidden not in wire_boundary, f"wire boundary must not preserve a legacy metadata path: {forbidden}")

if errors:
    print("Runtime metadata generation guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Runtime metadata generation guard passed.")
