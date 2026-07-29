#!/usr/bin/env python3
"""Protect generation-scoped, atomic Android runtime metadata assembly."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices"
MODEL = SOURCE / "model/DeviceRuntimeMetadataGeneration.kt"
REDUCER = SOURCE / "repository/DeviceRuntimeMetadataReducer.kt"
PARSERS = SOURCE / "repository/DeviceRuntimeMetadataParsers.kt"
MODULE_PARSER = SOURCE / "repository/DeviceRuntimeModulesParser.kt"
PROJECTOR = SOURCE / "repository/DeviceRuntimeMetadataProjector.kt"
WIRE_BOUNDARY = SOURCE / "repository/DeviceRuntimeMetadataWireBoundary.kt"
PROVISIONING = SOURCE / "provisioning/repository/AqlProvisioningRuntimeMetadataResolver.kt"
BOOTSTRAP_CONTRACT = SOURCE / "repository/DeviceRuntimeMetadataBootstrapContract.kt"
BOOTSTRAP_COORDINATOR = SOURCE / "repository/DeviceRuntimeMetadataBootstrapCoordinator.kt"
RUNTIME_REPOSITORY = SOURCE / "repository/DeviceRuntimeRepository.kt"

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
module_parser = read(MODULE_PARSER)
projector = read(PROJECTOR)
wire_boundary = read(WIRE_BOUNDARY)
provisioning = read(PROVISIONING)
bootstrap_contract = read(BOOTSTRAP_CONTRACT)
bootstrap_coordinator = read(BOOTSTRAP_COORDINATOR)
runtime_repository = read(RUNTIME_REPOSITORY)

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
    "BOOTSTRAP_DISPATCH_FAILED",
    "BOOTSTRAP_RESPONSE_FAILED",
    "BOOTSTRAP_RESPONSE_MISMATCH",
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

for token in (
    "data.requireStatusKeys()",
    "product.requireStatusKeys(PRODUCT_KEYS",
    "runtime.requireStatusKeys(RUNTIME_KEYS",
    "modules.requireStatusKeys(MODULE_KEYS",
    "DeviceFamily.fromWireExact",
    "DeviceRuntimeModules(",
    "timerApi = modules.requireStatusBoolean(\"timerApi\")",
    "timerEngine = modules.requireStatusBoolean(\"timerEngine\")",
    "require(data.requireStatusBoolean(\"authenticated\"))",
    "requireExactRuntimeContract()",
    "AqlWsContract.SCHEMA",
    "AqlWsContract.DEFAULT_PATH",
):
    require(token in module_parser, f"exact firmware modules parser token is missing: {token}")

for module_key in (
    '"light"',
    '"cooling"',
    '"temperature"',
    '"timerApi"',
    '"timerEngine"',
    '"dosing"',
    '"network"',
    '"discovery"',
    '"firmware"',
    '"system"',
):
    require(module_key in module_parser, f"firmware module key is missing: {module_key}")

for forbidden in (
    ".trim()",
    ".lowercase()",
    "optString",
    "optBoolean",
    "optInt",
    "ifBlank",
    "getBoolean",
):
    require(
        forbidden not in module_parser,
        f"firmware modules parser must not normalize, coerce or fallback: {forbidden}",
    )

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
    "error(\"Wire metadata must be parsed into generation-tagged typed fragments before reduction.\")"
    in wire_boundary,
    "direct wire metadata must terminate without mutating the snapshot",
)
for forbidden in (
    "snapshot.copy(",
    "optString",
    "ifBlank",
    "previous.",
):
    require(forbidden not in wire_boundary, f"wire boundary must not preserve a legacy metadata path: {forbidden}")

for token in (
    "DeviceRuntimeMetadataBootstrapKind.IDENTITY",
    "DeviceRuntimeMetadataBootstrapKind.CAPABILITIES",
    "DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES",
    "AqlWsCommandFactory.deviceIdentity()",
    "AqlWsCommandFactory.deviceCapabilities()",
    "AqlWsCommandFactory.deviceStatus()",
    "fun accepts(fragment: DeviceRuntimeMetadataFragment)",
):
    require(token in bootstrap_contract, f"bootstrap contract token is missing: {token}")

for token in (
    "fun beginAndDispatch(",
    "ticketsByRequestId.remove(response.id)",
    "response.module != kind.module",
    "response.action != kind.action",
    "!response.ok || response.statusCode !in SUCCESS_MIN_STATUS..SUCCESS_MAX_STATUS",
    "DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED",
    "DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_FAILED",
    "DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH",
    "removeTicketsLocked(deviceUid)",
):
    require(token in bootstrap_coordinator, f"fail-closed bootstrap token is missing: {token}")

for forbidden in (
    ".trim()",
    ".lowercase()",
    "optString",
    "optBoolean",
    "optInt",
    "ifBlank",
    "object DeviceRuntimeMetadataBootstrapCoordinator",
):
    require(
        forbidden not in bootstrap_coordinator,
        f"bootstrap coordinator must be owner-scoped and exact: {forbidden}",
    )

bootstrap_method = re.search(
    r"private fun sendAuthenticatedBootstrap\(session: RuntimeSession\): Boolean \{(.*?)\n    \}",
    runtime_repository,
    flags=re.DOTALL,
)
require(bootstrap_method is not None, "authenticated bootstrap method is missing")
bootstrap_body = bootstrap_method.group(1) if bootstrap_method else ""
require(
    "metadataBootstrapCoordinator.beginAndDispatch" in bootstrap_body,
    "authenticated bootstrap must start one owner-scoped generation",
)
for forbidden in (
    "securityStatus(",
    "networkStatus(",
    "timeStatus(",
    "firmwareStatus(",
    "lightStatus(",
    "coolingStatus(",
    "timerStatus(",
    "dosingStatus(",
):
    require(forbidden not in bootstrap_body, f"pre-metadata runtime dispatch is forbidden: {forbidden}")

require(
    "timeSyncCoordinator.syncPhoneNowIfNeeded" not in runtime_repository,
    "phone time sync must wait for validated metadata capability",
)
require(
    "metadataBootstrapCoordinator.clear(deviceUid)" in runtime_repository,
    "device retirement must clear metadata generations and tickets",
)
require(
    "metadataBootstrapCoordinator.clearAll()" in runtime_repository,
    "owner shutdown must clear all metadata generations and tickets",
)
require(
    "METADATA_BOOTSTRAP_FAILED_REASON" in runtime_repository,
    "bootstrap dispatch failure must close the partial runtime session",
)

if errors:
    print("Runtime metadata generation guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Runtime metadata generation guard passed.")
