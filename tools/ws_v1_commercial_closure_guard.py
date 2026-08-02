#!/usr/bin/env python3
"""Fail closed when WS v1 migration residue or a parallel runtime path returns."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TRACKER = ROOT / "docs/AQL_WS_V1_MIGRATION_TRACKER.md"
CLOSURE = ROOT / "docs/AQL_WS_V1_COMMERCIAL_CLOSURE.md"
PRESENCE_CONTRACT = ROOT / "docs/device-presence-commercial-contract.md"
BASELINE = ROOT / "config/detekt/advisory-debt-baseline.json"

FILES = {
    "presence": APP / "data/devices/monitor/DevicePresenceRuntimeMonitor.kt",
    "probe": APP / "data/devices/monitor/DeviceAuthenticatedLivenessProbe.kt",
    "runtime": APP / "data/devices/repository/DeviceRuntimeRepository.kt",
    "devices": APP / "data/devices/repository/DevicesRepository.kt",
    "lifecycle": APP / "data/devices/runtime/events/DeviceRuntimeLifecycleEvent.kt",
    "menu": APP / "data/devices/menu/DefaultDeviceMenuAccessOperations.kt",
    "discovery": APP / "data/devices/discovery/udp/AqlDiscoveryParser.kt",
    "firmware_read": (
        APP / "data/devices/runtime/modules/firmware/DeviceFirmwareReadParser.kt"
    ),
    "firmware_status": (
        APP / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt"
    ),
    "time_models": APP / "data/devices/runtime/modules/time/DeviceTimeModels.kt",
    "time_parser": APP / "data/devices/runtime/modules/time/DeviceTimeStatusParser.kt",
    "ota_adapter": APP / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "ota_coordinator": APP / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "tracker": TRACKER,
    "closure": CLOSURE,
    "presence_contract": PRESENCE_CONTRACT,
    "baseline": BASELINE,
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
    "success.generation",
    "recordProof(deviceUid, success.generation)",
)
require(
    "presence",
    "runtime.runtimeModules.network::requestStatus",
    "DeviceAuthenticatedLivenessProbe",
    "runIfCurrentAuthenticatedGeneration(deviceUid, generation)",
    "lastControlProofAtMillis = nowWallMillis",
    "livenessProbeScheduler.cancelAll()",
)
require(
    "runtime",
    "val lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>",
    "event.toLifecycleEvent()",
    "DeviceRuntimeLifecycleEvent.Authenticated",
    "DeviceRuntimeLifecycleEvent.Unavailable",
    "runIfCurrentAuthenticatedGeneration",
    "session.generation == generation",
)
require(
    "devices",
    "recordControlProofIfCurrentGeneration",
    "runIfCurrentAuthenticatedGeneration",
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
    "DeviceRuntimeCommandOutcome.Success",
    "recordControlProofIfCurrentGeneration",
    "generation = success.generation",
)
require(
    "discovery",
    "hasExactKeys(ROOT_KEYS)",
    "exactIntOrNull",
    "exactBooleanOrNull",
    "DeviceFamily.fromWireExact",
    "INVALID_CONTRACT_SHAPE",
)
require(
    "firmware_read",
    "parseValidatedStatus",
    "parseValidatedPartition",
    "parseOtaSnapshotExact",
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
    "originating connection generation",
)
require(
    "presence_contract",
    "exact module, action, device generation and request id",
    "originating WebSocket generation is still current and authenticated",
)
forbid(
    "baseline",
    "AqlWsCommandClient.kt",
    "DeviceFirmwareOtaEventMapper.kt",
    "Function parameter `fallbackSnapshot` is unused.",
)

forbid("runtime", "AqlWsCommandClient", "fun commandClient(")
forbid("devices", "AqlWsCommandClient", "fun commandClient(", "fun runtimeEvents(")
forbid("menu", "DeviceMenuRuntimeProofPolicy", "AqlWsEvent", "fun runtimeEvents(")
forbid(
    "discovery",
    ".stringOrBlank(",
    ".intOrNull(",
    ".booleanOrNull(",
    "private fun JSONObject.stringOrBlank",
    "private fun JSONObject.intOrNull",
    "private fun JSONObject.booleanOrNull",
    "DeviceFamily.fromWire(familyRaw)",
)
forbid(
    "firmware_status",
    "fun parseFirmwareStatus",
    ".optString(",
    ".optBoolean(",
    ".optJSONObject(",
)
forbid("time_models", "DeviceTimeCommandResult", "fire-and-forget")
forbid("time_parser", "fun parse(data: JSONObject): DeviceTimeStatus", ".optBoolean(", ".optString(")
forbid("ota_adapter", "runtimeEvents =", "AqlWsEvent")
forbid("ota_coordinator", "AqlWsEvent", "DeviceFirmwareOtaEventMapper")

raw_event_allowlist = {
    Path("data/devices/repository/DeviceRuntimeRepository.kt"),
    Path("data/devices/repository/DevicesRepository.kt"),
    Path("data/devices/runtime/events/DeviceRuntimeEventPipeline.kt"),
}
transport_allowlist = {
    Path("data/devices/repository/DeviceRuntimeRepository.kt"),
    Path("data/devices/repository/DeviceRuntimeLocalNetworkFactory.kt"),
}

for source_file in APP.rglob("*.kt"):
    source = source_file.read_text(encoding="utf-8", errors="ignore")
    relative_to_app = source_file.relative_to(APP)
    inside_transport = relative_to_app.as_posix().startswith("data/devices/runtime/ws/")

    if (
        re.search(r"\bAqlWsEvent\b", source)
        and not inside_transport
        and relative_to_app not in raw_event_allowlist
    ):
        errors.append(
            f"{source_file.relative_to(ROOT)} violates raw AqlWsEvent boundary allowlist"
        )

    transport_reference = re.search(
        r"import .*\.AqlWs(?:Transport|Client)\b|"
        r"\bAqlWsClient\s*\(|:\s*AqlWsTransport\b",
        source,
    )
    if (
        transport_reference
        and not inside_transport
        and relative_to_app not in transport_allowlist
    ):
        errors.append(
            f"{source_file.relative_to(ROOT)} violates WebSocket transport boundary allowlist"
        )
    if (
        relative_to_app
        == Path("data/devices/repository/DeviceRuntimeLocalNetworkFactory.kt")
        and re.search(r"\.send\s*\(", source)
    ):
        errors.append(
            f"{source_file.relative_to(ROOT)} may construct but never send on the transport"
        )

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
