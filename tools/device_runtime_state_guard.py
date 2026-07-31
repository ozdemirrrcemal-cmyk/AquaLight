#!/usr/bin/env python3
"""Protect the single per-device runtime state, reducer and refresh architecture."""
from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices"
FILES = {
    "repository": SOURCE / "repository/DeviceRuntimeRepository.kt",
    "model": SOURCE / "runtime/state/DeviceRuntimeState.kt",
    "store": SOURCE / "runtime/state/DeviceRuntimeStateStore.kt",
    "reducer": SOURCE / "runtime/state/DeviceRuntimeStateReducer.kt",
    "router": SOURCE / "runtime/state/DeviceRuntimeMessageRouter.kt",
    "refresh": SOURCE / "runtime/state/DeviceRuntimeRefreshCoordinator.kt",
    "test": ROOT
    / "app/src/test/java/com/aqua/aqualight/data/devices/runtime/state/DeviceRuntimeStatePipelineTest.kt",
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
            errors.append(f"{label} contains forbidden runtime-state token: {token}")


sources = {label: read(label) for label in FILES}

require_tokens(
    "model",
    (
        "enum class DeviceRuntimeFreshness",
        "UNAVAILABLE",
        "LOADING",
        "READY",
        "STALE",
        "ERROR",
        "data class DeviceRuntimeValue<T>",
        "data class DeviceRuntimeState(",
        "val deviceUid: DeviceUid",
        "val generation: DeviceRuntimeConnectionGeneration?",
        "val authenticated: Boolean",
        "val support: DeviceRuntimeSupport",
        "val firmware: DeviceRuntimeValue<DeviceFirmwareStatus>",
        "val ota: DeviceRuntimeValue<DeviceFirmwareOtaSnapshot>",
        "enum class DeviceRuntimeStateTarget",
        "fun isSupported(state: DeviceRuntimeState)",
        "fun markLoading(state: DeviceRuntimeState)",
        "fun markError(",
    ),
)
forbid_tokens(
    "model",
    (
        "DeviceOnlineState",
        "DeviceConnectionState",
        "DeviceRegistryStore",
        "JSONObject",
        "DeviceRuntimeValue<Any?>",
        "UNCHECKED_CAST",
        "reflection",
    ),
)
require_tokens(
    "store",
    (
        "StateFlow<Map<DeviceUid, DeviceRuntimeState>>",
        "fun beginGeneration(",
        "fun markGenerationStale(",
        "if (state.generation != generation)",
        "Runtime state reduction cannot change deviceUid.",
        "Runtime state reduction cannot change connection generation.",
        "DeviceRuntimeFreshness.STALE",
    ),
)
require_tokens(
    "reducer",
    (
        "fun publishMetadata(",
        "fun commandStarted(",
        "fun commandCompleted(",
        "fun reduceOtaEvent(",
        "DeviceRuntimeCommandOutcome.ProtocolError",
        "DeviceFirmwareOtaClearTypedResult",
        "return if (reduced) null else refreshTarget",
        "target::markLoading",
        "target.markError(state, fault)",
    ),
)
forbid_tokens(
    "reducer",
    (
        "JSONObject",
        "DeviceOnlineState",
        "DeviceRegistryStore",
        "DeviceRuntimeValue<Any?>",
        "UNCHECKED_CAST",
        "updateTarget(",
    ),
)
require_tokens(
    "router",
    (
        "AqlWsContract.isActiveEvent",
        "DeviceFirmwareCommandParsers.parseOtaEvent",
        "data.requireExactKeys(STATUS_CHANGED_KEYS)",
        "AqlWsContract.isAuthenticatedCommand(module, action)",
        "DeviceRuntimeEventRoute.ProtocolFault",
    ),
)
forbid_tokens("router", ("data object Ignored", "DeviceRuntimeEventRoute.Ignored"))
require_tokens(
    "refresh",
    (
        "RefreshKey(",
        "ConcurrentHashMap<RefreshKey, Job>",
        "CoroutineStart.LAZY",
        "jobs.putIfAbsent(key, candidate)",
        "fun cancelGeneration(",
        "fun cancelDevice(",
        "generationProvider(deviceUid) != generation",
        "target.isSupported(currentState)",
    ),
)
require_tokens(
    "repository",
    (
        "private val runtimeStateStore = DeviceRuntimeStateStore()",
        "val runtimeStates: StateFlow<Map<DeviceUid, DeviceRuntimeState>>",
        "private val runtimeStateReducer = DeviceRuntimeStateReducer(runtimeStateStore)",
        "private val runtimeMessageRouter = DeviceRuntimeMessageRouter(runtimeStateReducer)",
        "private val runtimeRefreshCoordinator = DeviceRuntimeRefreshCoordinator(",
        "session?.authenticated == true",
        "supportsCommand(deviceUid, command.module, command.action)",
        "runtimeStateReducer.commandCompleted(outcome)",
        "runtimeRefreshCoordinator.refreshBootstrap(",
        "runtimeStateStore.markGenerationStale(",
        "runtimeMessageRouter.route(event)",
        "runtimeStateStore.clear()",
    ),
)
forbid_tokens(
    "repository",
    (
        "\n    val runtimeStateStore = DeviceRuntimeStateStore()",
        "DeviceRuntimeStateTargetSupport",
        "DeviceRuntimeEventRoute.Ignored",
        "runtimeStateStore.updateConnectionState",
    ),
)
require_tokens(
    "test",
    (
        "store isolates devices and preserves old values as stale across generations",
        "reducer records loading error and rejects old generation completion",
        "message router accepts exact active wrapper and rejects unknown fields",
        "refresh coordinator deduplicates targets isolates devices and cancels generations",
        "store.reduce(DEVICE_A, firstGeneration) { state ->",
        "assertFalse(requireNotNull(store.current(DEVICE_A)).authenticated)",
    ),
)

state_store_constructors = sources["repository"].count("DeviceRuntimeStateStore()")
if state_store_constructors != 1:
    errors.append(
        "DeviceRuntimeRepository must own exactly one DeviceRuntimeStateStore constructor; "
        f"found {state_store_constructors}."
    )

if errors:
    print("Device runtime state guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device runtime state guard passed.")
