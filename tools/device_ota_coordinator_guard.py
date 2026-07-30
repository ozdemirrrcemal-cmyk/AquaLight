#!/usr/bin/env python3
"""Protect shared OTA state coordination, exact artifact selection and signed release content."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt",
    "adapter": SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "coordinator": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
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
    ),
)
require_tokens(
    "coordinator",
    (
        "runtimeEvents.collect(::processEvent)",
        "An OTA operation is already active for this device.",
        "runtimeMetadataGeneration != selected.dataPlan.runtimeMetadataGeneration",
        "parseOtaStartAcceptedExact",
        "parseOtaStatusResponseExact",
        "parseOtaProgressEventExact",
        "Firmware OTA request echo differs from the selected plan.",
        "DeviceOtaState.Recovering",
        "private val startLocks = ConcurrentHashMap<DeviceUid, Any>()",
        "synchronized(startLock(deviceUid))",
        "private fun startUpdateLocked(",
        "startLocks.putIfAbsent(deviceUid, candidate)",
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
require_tokens(
    "status",
    (
        "parseOtaStartAcceptedExact",
        "parseOtaStatusResponseExact",
        "parseOtaProgressEventExact",
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
