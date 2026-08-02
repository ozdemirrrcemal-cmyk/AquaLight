#!/usr/bin/env python3
"""Protect shared OTA state coordination and the exact signed release contract."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
TEST_SOURCE = ROOT / "app/src/test/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt",
    "adapter": SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "coordinator": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "runtime": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeRepository.kt",
    "validation": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaValidation.kt",
    "planner": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt",
    "manifest_contract": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestContractValidator.kt",
    "models": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareModels.kt",
    "manifest": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestParser.kt",
    "status": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt",
    "live_manifest_test": TEST_SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareLiveManifestContractTest.kt",
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
        "val items: List<String>",
        "fun observe(deviceUid: String): StateFlow<DeviceOtaState>",
        "suspend fun checkAvailability(",
        "runtimeMetadataGeneration: Long",
        "releaseContent: DeviceFirmwareReleaseContent",
    ),
)
forbid_tokens(
    "contract",
    (
        "val title: String",
        "val summary: String",
        "val changes: List<String>",
        "val warnings: List<String>",
        "val mandatory: Boolean",
        "val displayName: String",
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
obsolete_raw_mapper = (
    SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareOtaEventMapper.kt"
)
if obsolete_raw_mapper.exists():
    errors.append(
        f"obsolete raw OTA mapper remains: {obsolete_raw_mapper.relative_to(ROOT)}"
    )
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
        "compatible.size == 1",
        "return compatible.single()",
        "artifact.env == environment",
        "DeviceFirmwareProductIdentity.fromSnapshot(snapshot.product)",
        "DeviceFirmwareProductIdentity.fromCompatibility(artifact.compatibility) ==",
        "DeviceFirmwareProductIdentity.fromCatalog(product) == deviceIdentity",
        "model = snapshot.product.model",
        "runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration",
        "manifest.releaseNotes.resolve",
        "DeviceFirmwareManifestContractValidator.requireValid(",
    ),
)
forbid_tokens(
    "planner",
    (
        "compatibleArtifacts.first()",
        "compatible.first()",
        "displayName = snapshot.title",
        "artifact.compatibility.family == family",
        "artifact.compatibility.line == line",
        "artifact.product.displayName",
        "artifact.product.capabilities",
        "artifact.product.limits",
    ),
)
require_tokens(
    "manifest_contract",
    (
        "internal data class DeviceFirmwareProductIdentity(",
        "val productKey: String",
        "val productId: String",
        "val family: String",
        "val line: String",
        "val model: String",
        "val hardwareRevision: String",
        "DeviceFirmwareProductIdentity.fromManifest(artifact.product) == catalogIdentity",
        "DeviceFirmwareProductIdentity.fromCompatibility(artifact.compatibility) ==",
        "requireProductIdentityMatchesCatalog(artifact, product)",
        "requireFirmwareIdentityMatchesManifest(artifact, manifest)",
        "requireReleaseChannelMatchesContract(manifest)",
    ),
)
forbid_tokens(
    "manifest_contract",
    (
        "artifact.product.displayName",
        "artifact.product.brand",
        "artifact.product.skuCode",
        "artifact.product.capabilities",
        "artifact.product.limits",
        "expectedManifestCapabilities",
        "expectedManifestLimits",
        "requireProductContractMatchesCatalog",
        "alias",
        "fallback",
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
        "data class DeviceFirmwareManifestCapabilities",
        "data class DeviceFirmwareManifestLimits",
        "data class DeviceFirmwareFactoryAsset",
        "sealed interface DeviceFirmwareAvailability",
        "items = localizedItems",
    ),
)
forbid_tokens(
    "models",
    (
        "DeviceFirmwareLocalizedReleaseNotes",
        "val mandatory: Boolean",
        "val locales:",
    ),
)
require_tokens(
    "manifest",
    (
        'root.requireExactKeys(ROOT_KEYS, "manifest")',
        "parsePlatform",
        "parseReleaseNotes",
        "parseReleaseNoteItems",
        "DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA",
        "json.requireExactKeys(PLATFORM_KEYS",
        "json.requireExactKeys(RELEASE_NOTES_KEYS",
        "item.requireExactKeys(RELEASE_NOTE_ITEM_KEYS",
        "json.requireExactKeys(SIGNATURE_KEYS",
        "json.requireExactKeys(PRODUCT_KEYS",
        "json.requireExactKeys(CAPABILITY_KEYS",
        "json.requireExactKeys(LIMIT_KEYS",
        "json.requireExactKeys(COMPATIBILITY_KEYS",
        "json.requireExactKeys(FIRMWARE_ASSET_KEYS",
        "json.requireExactKeys(FACTORY_ASSET_KEYS",
        "artifact.product.family == artifact.compatibility.family",
        "artifact.product.line == artifact.compatibility.line",
        "assetVersion == manifestVersion",
        "requiredNullableObject(\"factory\")",
    ),
)
forbid_tokens(
    "manifest",
    (
        "root.requireKnownKeys(",
        "json.requireKnownKeys(",
        "releaseNotes.locales",
        "requiredReleaseNoteArray",
        "DeviceFirmwareLocalizedReleaseNotes",
        "private val ASSET_KEYS =",
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
require_tokens(
    "live_manifest_test",
    (
        "DeviceFirmwareManifestContractValidator.requireValid(",
        "DeviceFirmwareProductIdentity.fromCompatibility(",
        "DeviceFirmwareProductIdentity.fromCatalog(product) == manifestIdentity",
    ),
)
forbid_tokens(
    "live_manifest_test",
    (
        "artifact.product.displayName",
        "assertEquals(product.displayName",
        "assertEquals(product.brand",
        "assertEquals(product.skuCode",
        "assertEquals(product.capabilities",
        "assertEquals(product.limits",
    ),
)

if errors:
    print("Device OTA coordinator guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device OTA coordinator guard passed.")
