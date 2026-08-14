#!/usr/bin/env python3
"""Protect the complete generation-scoped commercial runtime metadata pipeline."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices"
FILES = {
    "identity": SOURCE / "model/DeviceRuntimeIdentity.kt",
    "modules": SOURCE / "model/DeviceRuntimeModules.kt",
    "generation": SOURCE / "model/DeviceRuntimeMetadataGeneration.kt",
    "reducer": SOURCE / "repository/DeviceRuntimeMetadataReducer.kt",
    "parsers": SOURCE / "repository/DeviceRuntimeMetadataParsers.kt",
    "module_parser": SOURCE / "repository/DeviceRuntimeModulesParser.kt",
    "projector": SOURCE / "repository/DeviceRuntimeMetadataProjector.kt",
    "wire_boundary": SOURCE / "repository/DeviceRuntimeMetadataWireBoundary.kt",
    "bootstrap_contract": SOURCE / "repository/DeviceRuntimeMetadataBootstrapContract.kt",
    "bootstrap": SOURCE / "repository/DeviceRuntimeMetadataBootstrapCoordinator.kt",
    "runtime": SOURCE / "repository/DeviceRuntimeRepository.kt",
    "devices": SOURCE / "repository/DevicesRepository.kt",
    "provisioning": SOURCE / "provisioning/repository/AqlProvisioningRuntimeMetadataResolver.kt",
    "catalog": SOURCE / "catalog/AqlCommercialDeviceCatalog.kt",
    "module_contract": SOURCE / "catalog/AqlCommercialRuntimeModuleContract.kt",
    "snapshot": SOURCE / "model/DeviceSnapshot.kt",
    "root_mapping": SOURCE / "DeviceRootSnapshotMapping.kt",
}

errors: list[str] = []


def read(label: str) -> str:
    path = FILES[label]
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def require_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        require(token in source, f"{label} token is missing: {token}")


def forbid_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        require(token not in source, f"{label} contains forbidden legacy/permissive token: {token}")


sources = {label: read(label) for label in FILES}

require_tokens(
    "identity",
    (
        "data class DeviceRuntimeIdentityEnvelope",
        "data class DeviceRuntimeTransportMetadata",
        "transport == RUNTIME_TRANSPORT",
        "wsSchema == AqlWsContract.SCHEMA",
        "wsPath == AqlWsContract.DEFAULT_PATH",
        "wsPort == RUNTIME_WS_PORT",
        "wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION",
        "value == SUPPORTED_DEVICE_API_VERSION",
    ),
)

require_tokens(
    "modules",
    (
        "data class DeviceRuntimeModuleStatus",
        "fun mismatchField(identity: DeviceRuntimeIdentity)",
        'TIMER_API("timerApi")',
        'TIMER_ENGINE("timerEngine")',
    ),
)

require_tokens(
    "generation",
    (
        "value class DeviceRuntimeMetadataGeneration",
        "data class Collecting",
        "data class Ready",
        "data class Rejected",
        "val identity: DeviceRuntimeIdentityEnvelope?",
        "val moduleStatus: DeviceRuntimeModuleStatus?",
        "STATUS_IDENTITY_MISMATCH",
        "CATALOG_VALIDATION_FAILED",
        "val publishedMetadata: DeviceRuntimeMetadata?",
    ),
)
require(
    sources["generation"].count("override val publishedMetadata: DeviceRuntimeMetadata? = null") >= 2,
    "collecting and rejected generations must withdraw publication",
)

require_tokens(
    "reducer",
    (
        "fragment.generation != current.generation",
        "DeviceRuntimeMetadataReduction.IgnoredStale",
        "mergeIdentity",
        "mergeModuleStatus",
        "STATUS_IDENTITY_MISMATCH",
        "DeviceRuntimeMetadataGenerationState.Ready",
        "identityEnvelope = readyIdentity",
        "moduleStatus = readyModuleStatus",
    ),
)
forbid_tokens(
    "reducer",
    (
        "JSONObject",
        "JSONArray",
        "AqlWsIncomingMessage",
        "DeviceSnapshot",
        ".trim()",
        ".lowercase()",
        "optString",
        "optBoolean",
        "ifBlank",
    ),
)

require_tokens(
    "parsers",
    (
        "data.requireExactKeys(IDENTITY_KEYS",
        "runtime.requireExactKeys(RUNTIME_KEYS",
        "DeviceRuntimeTransportMetadata(",
        "data.requireExactKeys(CAPABILITY_RESPONSE_KEYS",
        "capabilities.requireExactKeys(CAPABILITY_KEYS",
        "limits.requireExactKeys(LIMIT_KEYS",
        "parseAqlDeviceFeatureKeysExact",
        "parseAqlDeviceScreenKeysExact",
        "value is Boolean",
        "value is Number",
    ),
)
forbid_tokens("parsers", (".trim()", ".lowercase()", "optString", "optBoolean", "ifBlank"))

require_tokens(
    "module_parser",
    (
        "DeviceRuntimeModuleStatus(",
        "data.requireStatusKeys()",
        "product.requireStatusKeys(PRODUCT_KEYS",
        "runtime.requireStatusKeys(RUNTIME_KEYS",
        "modules.requireStatusKeys(MODULE_KEYS",
        "require(data.requireStatusBoolean(\"authenticated\"))",
        "requireExactRuntimeContract()",
        'timerApi = modules.requireStatusBoolean("timerApi")',
        'timerEngine = modules.requireStatusBoolean("timerEngine")',
    ),
)
forbid_tokens(
    "module_parser",
    (".trim()", ".lowercase()", "optString", "optBoolean", "optInt", "ifBlank", "getBoolean"),
)

require_tokens(
    "bootstrap_contract",
    (
        "DeviceRuntimeMetadataBootstrapProcessing",
        "DeviceRuntimeMetadataBootstrapKind.IDENTITY",
        "DeviceRuntimeMetadataBootstrapKind.CAPABILITIES",
        "DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES",
    ),
)
require_tokens(
    "bootstrap",
    (
        "fun beginAndDispatch(",
        "fun process(",
        "private fun parseFragment(",
        "DeviceRuntimeIdentityParser.parse(",
        "DeviceRuntimeCapabilitiesParser.parse(",
        "DeviceRuntimeModulesParser.parseDeviceStatus(",
        "ticketsByRequestId.remove(response.id)",
        "response.module != kind.module",
        "response.action != kind.action",
        "!response.ok || response.statusCode !in SUCCESS_MIN_STATUS..SUCCESS_MAX_STATUS",
        "removeTicketsLocked(deviceUid)",
    ),
)
forbid_tokens("bootstrap", (".trim()", ".lowercase()", "optString", "optBoolean", "ifBlank"))

require_tokens(
    "catalog",
    (
        "MODULES_MISMATCH",
        "reported.modules != null",
        "product.expectedRuntimeModules()",
    ),
)
require_tokens(
    "module_contract",
    (
        "fun AqlCommercialCatalogProduct.expectedRuntimeModules()",
        "timerApi = standaloneTimer",
        "timerEngine = standaloneTimer",
        "dosing = dosingProduct",
    ),
)
forbid_tokens(
    "module_contract",
    ("timerEngine = standaloneTimer || dosingProduct",),
)

require_tokens(
    "runtime",
    (
        "internal fun processMetadataResponse(",
        "metadataBootstrapCoordinator.process(deviceUid, response)",
        "AqlCommercialDeviceCatalog.validate(state.metadata)",
        "CATALOG_VALIDATION_FAILED",
        "disconnectMetadataFailure",
        "METADATA_BOOTSTRAP_FAILED_REASON",
        "metadataBootstrapCoordinator.beginAndDispatch",
    ),
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
    require(forbidden not in sources["runtime"], f"pre-metadata runtime dispatch is forbidden: {forbidden}")

require_tokens(
    "projector",
    (
        "fun applyReady(",
        "DeviceRuntimeMetadataGenerationState.Ready",
        "runtimeMetadataGeneration = ready.generation.value",
        "fun invalidate(",
        "runtimeMetadataGeneration = 0L",
    ),
)
require_tokens(
    "devices",
    (
        "processMetadataResponse(event.deviceUid, message)",
        "DeviceRuntimeMetadataProjector.applyReady",
        "DeviceRuntimeMetadataProjector::invalidate",
        "invalidateRuntimeMetadata(state.deviceUid)",
        "private fun registerUntrustedSnapshot(",
        "registryStore.updateSnapshot(snapshot.deviceUid) { untrusted }",
        "knownStore?.saveSnapshot(snapshot)",
    ),
)
require(
    "runtimeMetadataReducer.reduce(" not in sources["devices"],
    "legacy direct wire reducer must not be called by DevicesRepository",
)

require_tokens(
    "snapshot",
    (
        "val runtimeMetadataGeneration: Long = 0L",
        "val hasValidatedRuntimeMetadata: Boolean",
    ),
)
require_tokens(
    "provisioning",
    (
        "snapshot.hasValidatedRuntimeMetadata",
        "AqlCommercialDeviceCatalog.validateSnapshot(snapshot)",
        "repository.observeDevice(provisionalSnapshot.deviceUid)",
    ),
)
forbid_tokens(
    "provisioning",
    (
        "DeviceRuntimeIdentityParser",
        "DeviceRuntimeCapabilitiesParser",
        "ProvisioningMetadataAccumulator",
        "AqlWsIncomingMessage",
    ),
)
require(
    "!hasValidatedRuntimeMetadata" in sources["root_mapping"],
    "root projection must fail closed without a current validated generation",
)

require_tokens(
    "wire_boundary",
    (
        "Direct wire-to-snapshot reduction is forbidden",
        "Wire metadata must be parsed into generation-tagged typed fragments before reduction.",
    ),
)

if errors:
    print("Runtime metadata generation guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Runtime metadata generation guard passed (live three-response pipeline enforced).")
