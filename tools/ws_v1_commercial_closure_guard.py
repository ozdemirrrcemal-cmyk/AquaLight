#!/usr/bin/env python3
"""Fail closed when WS v1 migration residue or a parallel runtime path returns."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TRACKER = ROOT / "docs/AQL_WS_V1_MIGRATION_TRACKER.md"
CLOSURE = ROOT / "docs/AQL_WS_V1_COMMERCIAL_CLOSURE.md"

FILES = {
    "presence": APP / "data/devices/monitor/DevicePresenceRuntimeMonitor.kt",
    "probe": APP / "data/devices/monitor/DeviceAuthenticatedLivenessProbe.kt",
    "runtime": APP / "data/devices/repository/DeviceRuntimeRepository.kt",
    "devices": APP / "data/devices/repository/DevicesRepository.kt",
    "lifecycle": APP / "data/devices/runtime/events/DeviceRuntimeLifecycleEvent.kt",
    "menu": APP / "data/devices/menu/DefaultDeviceMenuAccessOperations.kt",
    "time_models": APP / "data/devices/runtime/modules/time/DeviceTimeModels.kt",
    "time_parser": APP / "data/devices/runtime/modules/time/DeviceTimeStatusParser.kt",
    "ota_adapter": APP / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "ota_coordinator": APP / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "tracker": TRACKER,
    "closure": CLOSURE,
}

errors: list[str] = []


def read(label: str) -> str:
    path = FILES[label]
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require(label: str, *tokens: str) -> None:
    source = sources[label]
    for token in tokens:
        if token not in source:
            errors.append(f"{label} is missing closure token: {token}")


def forbid(label: str, *tokens: str) -> None:
    source = sources[label]
    for token in tokens:
        if token in source:
            errors.append(f"{label} contains obsolete closure token: {token}")


sources = {label: read(label) for label in FILES}

tracked = subprocess.run(
    ["git", "ls-files", "-z"],
    cwd=ROOT,
    check=True,
    stdout=subprocess.PIPE,
).stdout.decode("utf-8", errors="strict").split("\0")

for relative in filter(None, tracked):
    path = Path(relative)
    if relative.startswith(".gradle/"):
        errors.append(f"tracked Gradle state is forbidden: {relative}")
    if path.name == ".gitkeep":
        errors.append(f"tracked placeholder is forbidden after closure: {relative}")
    if path.suffix.lower() in {".tmp", ".bak", ".orig", ".rej", ".patch", ".apk", ".aab"}:
        errors.append(f"tracked temporary/build artifact is forbidden: {relative}")

for obsolete in (
    APP / "data/devices/runtime/ws/AqlWsCommandClient.kt",
    APP / "data/devices/runtime/modules/firmware/DeviceFirmwareOtaEventMapper.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/menu/DeviceMenuRuntimeProofPolicyTest.kt",
):
    if obsolete.exists():
        errors.append(f"obsolete compatibility file remains: {obsolete.relative_to(ROOT)}")

require(
    "probe",
    "DeviceRuntimeCommandOutcome.Success",
    "recordProof(deviceUid)",
)
require(
    "presence",
    "runtime.runtimeModules.network::requestStatus",
    "DeviceAuthenticatedLivenessProbe",
    "lastControlProofAtMillis = nowWallMillis",
    "cancelLivenessProbes()",
)
require(
    "runtime",
    "val lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>",
    "event.toLifecycleEvent()",
    "DeviceRuntimeLifecycleEvent.Authenticated",
    "DeviceRuntimeLifecycleEvent.Unavailable",
)
require(
    "lifecycle",
    "sealed interface DeviceRuntimeLifecycleEvent",
    "data class Authenticated",
    "data class Unavailable",
)
require(
    "menu",
    "suspend fun proveCurrentLiveness",
    "outcome is DeviceRuntimeCommandOutcome.Success",
)
require("time_parser", "fun parseExact(data: JSONObject): DeviceTimeStatus")
require(
    "ota_adapter",
    "runtimeLifecycleEvents = devicesRepository.runtimeLifecycleEvents()",
    "runtimeTypedEvents = devicesRepository.typedRuntimeEvents()",
)
require(
    "ota_coordinator",
    "SharedFlow<DeviceRuntimeLifecycleEvent>",
    "SharedFlow<DeviceRuntimeTypedEvent>",
)
require(
    "tracker",
    "11 — WS v1 commercial cleanup and closure",
    "Canonical physical signed-OTA commercial-device gate passes",
    "chore/ws-v1-commercial-closure",
)
require(
    "closure",
    "Raw `AqlWsEvent` messages remain an internal transport primitive only.",
    "Canonical physical signed-OTA release gate",
    "The release is blocked while any item above is open.",
)

forbid("runtime", "AqlWsCommandClient", "fun commandClient(")
forbid("devices", "AqlWsCommandClient", "fun commandClient(", "fun runtimeEvents(")
forbid("menu", "DeviceMenuRuntimeProofPolicy", "AqlWsEvent", "fun runtimeEvents(")
forbid("time_models", "DeviceTimeCommandResult", "fire-and-forget")
forbid("time_parser", "fun parse(data: JSONObject): DeviceTimeStatus", ".optBoolean(", ".optString(")
forbid("ota_adapter", "runtimeEvents =", "AqlWsEvent")
forbid("ota_coordinator", "AqlWsEvent", "DeviceFirmwareOtaEventMapper")

for source_file in APP.rglob("*.kt"):
    source = source_file.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "DeviceTimeCommandResult",
        "DeviceMenuRuntimeProofPolicy",
        "DeviceFirmwareOtaEventMapper",
        "AqlWsCommandClient",
    ):
        if token in source:
            errors.append(
                f"{source_file.relative_to(ROOT)} contains removed compatibility symbol: {token}"
            )

if errors:
    print("WS v1 commercial closure guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("WS v1 commercial closure guard passed.")
