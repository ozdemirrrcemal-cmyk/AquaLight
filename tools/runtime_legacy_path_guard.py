#!/usr/bin/env python3
"""Fail closed if removed runtime compatibility or send-only paths return."""
from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
RUNTIME = APP / "data/devices/runtime"
FIRMWARE = RUNTIME / "modules/firmware"
STATE = RUNTIME / "state"
REPOSITORY = APP / "data/devices/repository/DeviceRuntimeRepository.kt"
DEVICES_REPOSITORY = APP / "data/devices/repository/DevicesRepository.kt"
MODULE_PROVIDER = RUNTIME / "modules/DeviceRuntimeModuleProvider.kt"
APPLICATION_OTA = APP / "application/devices/DeviceFirmwareUpdateOperations.kt"

errors: list[str] = []


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def forbid(path: Path, source: str, tokens: tuple[str, ...]) -> None:
    for token in tokens:
        if token in source:
            errors.append(f"{path.relative_to(ROOT)} contains forbidden legacy token: {token}")


removed_files = (
    RUNTIME / "ws/AqlWsCommandClient.kt",
    FIRMWARE / "DeviceFirmwareStatusParser.kt",
)
for path in removed_files:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)} must remain deleted")

runtime_sources = {
    path: read(path)
    for path in sorted(RUNTIME.rglob("*.kt"))
}
for path, source in runtime_sources.items():
    forbid(
        path,
        source,
        (
            "AqlWsCommandClient",
            "commandClientProvider",
            "DeviceFirmwareStatusParser",
            "DeviceFirmwareCommandResult",
            "DeviceFirmwareOtaClearResult",
            "DeviceRuntimeValue<Any?>",
            "DeviceRuntimeStateTargetSupport",
            "DeviceRuntimeEventRoute.Ignored",
        ),
    )

firmware_sources = "\n".join(
    source for path, source in runtime_sources.items() if FIRMWARE in path.parents
)
for token in (
    "DeviceFirmwareOtaPhase.UNKNOWN",
    "fun fromWire(value:",
    "parseOtaStartAcceptedExact",
    "parseOtaStatusResponseExact",
    "parseOtaProgressEventExact",
    "optString(",
    "optBoolean(",
    "optInt(",
    "optLong(",
    "optJSONObject(",
):
    if token in firmware_sources:
        errors.append(f"runtime/modules/firmware contains forbidden compatibility token: {token}")

repository = read(REPOSITORY)
forbid(
    REPOSITORY,
    repository,
    (
        "fun commandClient(",
        "lastActiveDeviceUid",
        "commandClient =",
        "val commandClient:",
        "DeviceRuntimeStateTargetSupport",
        "DeviceRuntimeEventRoute.Ignored",
    ),
)
if repository.count("DeviceRuntimeStateStore()") != 1:
    errors.append("DeviceRuntimeRepository must own exactly one DeviceRuntimeStateStore")
if repository.count("class DeviceRuntimeRepository(") != 1:
    errors.append("Exactly one production DeviceRuntimeRepository declaration is required")

module_provider = read(MODULE_PROVIDER)
forbid(
    MODULE_PROVIDER,
    module_provider,
    (
        "AqlWsCommandClient",
        "commandClientProvider",
        "UNUSED_PARAMETER",
    ),
)

devices_repository = read(DEVICES_REPOSITORY)
forbid(
    DEVICES_REPOSITORY,
    devices_repository,
    (
        "AqlWsCommandClient",
        "fun commandClient(",
    ),
)
for token in (
    "fun observeRuntimeState(deviceUid: DeviceUid)",
    "fun currentRuntimeState(deviceUid: DeviceUid)",
):
    if token not in devices_repository:
        errors.append(f"DevicesRepository read-only runtime state token is missing: {token}")

application_ota = read(APPLICATION_OTA)
forbid(
    APPLICATION_OTA,
    application_ota,
    (
        "DeviceFirmwareCommandResult",
        "val sent: Boolean",
        "val messageId: String",
        "typealias DeviceFirmwareCommandResult",
    ),
)
for token in (
    "data class DeviceFirmwareOperationResult",
    "val successful: Boolean",
    "val correlationId: String",
):
    if token not in application_ota:
        errors.append(f"Application OTA result token is missing: {token}")

state_sources = "\n".join(read(path) for path in sorted(STATE.glob("*.kt")))
for token in (
    "UNCHECKED_CAST",
    "DeviceRuntimeValue<Any?>",
    "fun markUnavailable(",
    "class DeviceRuntimeStateStore",
):
    if token == "class DeviceRuntimeStateStore":
        if state_sources.count("internal class DeviceRuntimeStateStore") != 1:
            errors.append("Runtime state store must be one internal class")
    elif token in state_sources:
        errors.append(f"runtime/state contains forbidden quality-debt token: {token}")

if errors:
    print("Runtime legacy path guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Runtime legacy path guard passed.")
