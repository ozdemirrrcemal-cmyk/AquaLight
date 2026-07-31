#!/usr/bin/env python3
"""Protect typed OTA commands, exact parsing and reboot version verification."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt",
    "adapter": SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "provider": SOURCE / "data/devices/runtime/modules/DeviceRuntimeModuleProvider.kt",
    "coordinator": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "runtime": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeRepository.kt",
    "commands": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareCommandParsers.kt",
    "planner": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt",
    "models": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareModels.kt",
    "manifest": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestParser.kt",
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
        "data class DeviceFirmwareOperationResult",
        "val successful: Boolean",
        "val correlationId: String",
        "fun observe(deviceUid: String): StateFlow<DeviceOtaState>",
        "suspend fun checkAvailability(",
        "suspend fun startUpdate(",
        "suspend fun requestStatus(",
        "suspend fun clearStatus(",
        "runtimeMetadataGeneration: Long",
        "releaseContent: DeviceFirmwareReleaseContent",
    ),
)
forbid_tokens(
    "contract",
    (
        "DeviceFirmwareCommandResult",
        "val sent: Boolean",
        "val messageId: String",
        "typealias DeviceFirmwareCommandResult",
    ),
)
require_tokens(
    "adapter",
    (
        "DeviceOtaCoordinator(",
        "coordinator.observe(",
        "coordinator.checkAvailability(",
        "DeviceFirmwareOperationResult",
        "override suspend fun startUpdate(",
        "override suspend fun requestStatus(",
        "override suspend fun clearStatus(",
    ),
)
require_tokens(
    "provider",
    (
        "DeviceFirmwareRuntimeRepository(commandGateway)",
        "DeviceFirmwareUpdateRepository(firmware)",
    ),
)
forbid_tokens(
    "provider",
    (
        "AqlWsCommandClient",
        "commandClientProvider",
        "UNUSED_PARAMETER",
    ),
)
require_tokens(
    "runtime",
    (
        "private val commandGateway: DeviceRuntimeCommandGateway",
        "DeviceFirmwareStatusGetCommand",
        "DeviceFirmwareOtaStatusCommand",
        "DeviceFirmwareOtaStartCommand",
        "DeviceFirmwareOtaClearCommand",
        "DeviceFirmwareCommandParsers.parseFirmwareStatus",
        "DeviceFirmwareCommandParsers.parseOtaStatus",
        "DeviceFirmwareCommandParsers.parseOtaStart",
        "DeviceFirmwareCommandParsers.parseOtaClear",
        "require(response.statusCode == HTTP_ACCEPTED)",
    ),
)
forbid_tokens(
    "runtime",
    (
        "AqlWsCommandClient",
        "commandClientProvider",
        "DeviceFirmwareCommandResult",
        ".command(",
    ),
)
require_tokens(
    "commands",
    (
        "fun parseFirmwareStatus(",
        "fun parseOtaStatus(",
        "fun parseOtaStart(",
        "fun parseOtaClear(",
        "fun parseOtaEvent(",
        "OTA_CLEAR_PREVIOUS_KEYS",
        "OTA_STAGED_EVENT_KEYS",
        "OTA_TICK_EVENT_KEYS",
        "keys differ from the firmware contract",
    ),
)
forbid_tokens(
    "commands",
    (
        "optString(",
        "optBoolean(",
        "optInt(",
        "optLong(",
        "optJSONObject(",
    ),
)
require_tokens(
    "coordinator",
    (
        "runtimeEvents.collect { event -> processEvent(event) }",
        "An OTA operation is already active for this device.",
        "runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration",
        "DeviceFirmwareCommandParsers.parseOtaEvent",
        "updater.requestFirmwareStatus(deviceUid)",
        "Firmware rebooted with version",
        "Firmware identity changed while verifying the installed OTA version.",
        "DeviceOtaState.RestartRequired -> verifyInstalledVersion(",
        "private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()",
        "startLock(deviceUid).withLock",
        "private suspend fun verifyInstalledVersion(",
        "successful = true",
        "correlationId = messageId",
    ),
)
forbid_tokens(
    "coordinator",
    (
        "PendingKind",
        "PendingRequest",
        "pendingRequests",
        "processResponse(",
        "processError(",
        "parseOtaStartAcceptedExact",
        "parseOtaStatusResponseExact",
        "parseOtaProgressEventExact",
        "AqlWsCommandClient",
        "DeviceFirmwareCommandResult",
        "sent = true",
        "sent = false",
        "DeviceFirmwareOtaPhase.UNKNOWN",
    ),
)
require_tokens(
    "planner",
    (
        "fun evaluateUpdate(",
        "compatible.size == 1",
        "return compatible.single()",
        "artifact.env == environment",
        "artifact.compatibility.family == family",
        "artifact.compatibility.line == line",
        "model = snapshot.product.model",
        "runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration",
        "manifest.releaseNotes.resolve",
    ),
)
forbid_tokens("planner", ("compatibleArtifacts.first()", "compatible.first()"))
require_tokens(
    "models",
    (
        "val model: String",
        ".put(DeviceFirmwareRuntimeContract.Field.MODEL, model)",
        "data class DeviceFirmwareOtaStartRequestEcho",
        "data class DeviceFirmwareReleaseNotes",
        "sealed interface DeviceFirmwareAvailability",
        "fun fromWireExact(value: String)",
    ),
)
forbid_tokens(
    "models",
    (
        "UNKNOWN(\"unknown\")",
        "fun fromWire(value:",
        "DeviceFirmwareOtaClearResult",
        "DeviceFirmwareCommandResult",
        "return trim().length",
    ),
)
require_tokens(
    "manifest",
    (
        "root.requireKnownKeys(",
        "parseReleaseNotes",
        "releaseNotes.locales",
        "requiredReleaseNoteArray",
        "json.requireExactKeys(SIGNATURE_KEYS",
        "json.requireKnownKeys(",
        "json.requireExactKeys(PRODUCT_KEYS",
        "json.requireExactKeys(COMPATIBILITY_KEYS",
        "json.requireExactKeys(ASSET_KEYS",
        "artifact.product.family == artifact.compatibility.family",
        "artifact.product.line == artifact.compatibility.line",
    ),
)

legacy_files = (
    SOURCE / "data/devices/runtime/ws/AqlWsCommandClient.kt",
    SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt",
)
for path in legacy_files:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: deleted legacy file must not return")

if errors:
    print("Device OTA coordinator guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device OTA coordinator guard passed.")
