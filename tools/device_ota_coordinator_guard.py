#!/usr/bin/env python3
"""Protect shared OTA state coordination and the exact firmware-owned release contract."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt",
    "adapter": SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "coordinator": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "runtime": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeRepository.kt",
    "validation": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaValidation.kt",
    "planner": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt",
    "models": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareModels.kt",
    "manifest": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestParser.kt",
    "status": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt",
}

errors: list[str] = []


def read(label: str) -> str:
    path = FILES[label]
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        if token not in source:
            errors.append(f"{label} token is missing: {token}")


def forbid_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        if token in source:
            errors.append(f"{label} contains forbidden OTA token: {token}")


sources = {label: read(label) for label in FILES}

require_tokens(
    "contract",
    (
        "sealed interface DeviceOtaState",
        "data class DeviceFirmwareReleaseContent",
        "fun observe(deviceUid: String): StateFlow<DeviceOtaState>",
        "suspend fun checkAvailability(",
        "runtimeMetadataGeneration: Long",
        "releaseContent: DeviceFirmwareReleaseContent",
    ),
)
require_tokens(
    "adapter",
    (
        "DeviceOtaCoordinator(",
        "coordinator.observe(",
        "coordinator.checkAvailability(",
        "coordinator.startUpdate(plan)",
        "coordinator.requestStatus(",
        "coordinator.clearStatus(",
        "recoverRuntime = devicesRepository::replaceRuntimeAfterControlFailure",
        "runtimeLifecycleEvents = devicesRepository.runtimeLifecycleEvents()",
        "runtimeTypedEvents = devicesRepository.typedRuntimeEvents()",
    ),
)
require_tokens(
    "coordinator",
    (
        "events.collect(::processLifecycleEvent)",
        "events.collect(::processTypedEvent)",
        "updates.collect(::processSnapshotUpdates)",
        "An OTA operation is already active for this device.",
        "runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration",
        "parseOtaProgressEventExact",
        "selected.runtimeGeneration != event.generation",
        "Firmware OTA request echo differs from the selected plan.",
        "DeviceOtaState.Recovering",
        "private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()",
        "startLock(deviceUid).withLock",
        "private suspend fun startUpdateLocked(",
        "startLocks.putIfAbsent(deviceUid, candidate)",
        "pendingVersionVerification",
        "runCatching { refreshDiscovery() }",
        "recoverRuntime(deviceUid)",
        "DeviceRuntimeLifecycleEvent.Authenticated",
        "DeviceRuntimeLifecycleEvent.Unavailable",
    ),
)
require_tokens(
    "runtime",
    (
        "private val gateway: DeviceRuntimeCommandGateway",
        "gateway.execute(",
        "suspend fun startOta(",
        "suspend fun readOtaStatus(",
        "suspend fun clearOtaStatus(",
        "DeviceFirmwareStatusParser.parseOtaStartAcceptedExact",
        "DeviceFirmwareStatusParser.parseOtaClearResultExact",
    ),
)
forbid_tokens("runtime", ("AqlWsCommandClient", "sendLegacy", "LegacyOnlyGateway"))
obsolete_raw_mapper = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareOtaEventMapper.kt"
if obsolete_raw_mapper.exists():
    errors.append(f"obsolete raw OTA mapper remains: {obsolete_raw_mapper.relative_to(ROOT)}")
require_tokens(
    "validation",
    (
        "object DeviceOtaValidator",
        "snapshot.targetVersion != plan.targetVersion",
        "snapshot.sha256Expected.equals(plan.firmware.sha256",
        "snapshot.contentLength != plan.firmware.size.toLong()",
        "snapshot.sha256Actual.equals(plan.firmware.sha256",
        "snapshot.bytesWritten != plan.firmware.size.toLong()",
        "snapshot.firmwareVersion != plan.targetVersion",
        "object DeviceOtaStateMapper",
    ),
)
require_tokens(
    "planner",
    (
        "fun evaluateUpdate(",
        "requireValidatedSnapshot(snapshot)",
        'val expectedTag = "${artifact.env}-v${manifest.version}"',
        "require(manifest.tag == expectedTag)",
        "compatible.size == 1",
        "return compatible.single()",
        "artifact.env == environment",
        "artifact.compatibility.family == family",
        "artifact.compatibility.line == line",
        "artifact.product.capabilities == snapshot.capabilities",
        "artifact.product.limits == snapshot.limits",
        "version = artifact.firmware.version",
        "model = snapshot.product.model",
        "runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration",
        "manifest.releaseNotes.resolve",
    ),
)
forbid_tokens(
    "planner",
    (
        "compatibleArtifacts.first()",
        "compatible.first()",
        "AqlCommercialDeviceCatalog",
        "AqlCommercialCatalogValidation",
        "requireValidatedProduct",
    ),
)
require_tokens(
    "models",
    (
        "val model: String",
        ".put(DeviceFirmwareRuntimeContract.Field.MODEL, model)",
        "data class DeviceFirmwareOtaStartRequestEcho",
        "data class DeviceFirmwareManifestPlatform",
        "data class DeviceFirmwareReleaseNoteItem",
        "data class DeviceFirmwareReleaseNotes",
        "val capabilities: DeviceCapabilities",
        "val limits: DeviceLimits",
        "val version: String",
        "data class DeviceFirmwareFactoryAsset",
        "sealed interface DeviceFirmwareAvailability",
    ),
)
require_tokens(
    "manifest",
    (
        "root.requireExactKeys(ROOT_KEYS, \"manifest\")",
        "parsePlatform",
        "parseReleaseNotes",
        "DeviceFirmwareReleaseNoteItem(",
        "parseCapabilities",
        "parseLimits",
        "requiredNullableObject(\"factory\")",
        "json.requireExactKeys(FIRMWARE_KEYS, label)",
        "json.requireExactKeys(FACTORY_KEYS, label)",
        "artifact.product.family == artifact.compatibility.family",
        "artifact.product.line == artifact.compatibility.line",
        "artifact.firmware.version == manifest.version",
        "DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT",
    ),
)
forbid_tokens(
    "manifest",
    (
        "releaseNotes.locales",
        "DeviceFirmwareLocalizedReleaseNotes",
        "mandatory =",
        "parseAsset(",
    ),
)
require_tokens(
    "status",
    (
        "parseOtaStartAcceptedExact",
        "parseOtaStatusResponseExact",
        "parseOtaProgressEventExact",
        "parseOtaClearResultExact",
        "parseOtaSnapshotExact",
        'model = json.requiredExactString("model")',
        "OTA active flag differs from its exact phase.",
    ),
)

if errors:
    print("Device OTA coordinator guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device OTA coordinator guard passed.")
