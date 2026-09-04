#!/usr/bin/env python3
"""Protect generation-scoped owning-domain bootstrap and RuntimeReady semantics."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices"
FILES = {
    "metadata_contract": SOURCE / "repository/DeviceRuntimeMetadataBootstrapContract.kt",
    "metadata_bootstrap": SOURCE / "repository/DeviceRuntimeMetadataBootstrapCoordinator.kt",
    "domain_bootstrap": SOURCE / "repository/DeviceRuntimeDomainBootstrapCoordinator.kt",
    "runtime": SOURCE / "repository/DeviceRuntimeRepository.kt",
    "provider": SOURCE / "runtime/modules/DeviceRuntimeModuleProvider.kt",
    "light_thermal_repo": SOURCE / "runtime/modules/light/DeviceLightThermalRuntimeRepository.kt",
    "light_thermal_parser": SOURCE / "runtime/modules/light/DeviceLightThermalStatusParser.kt",
    "dosing_repo": SOURCE / "runtime/modules/dosing/DeviceDosingRuntimeRepository.kt",
    "dosing_production": SOURCE / "dosing/v1/DeviceDosingV1ProductionRuntime.kt",
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


def require_tokens(label: str, *tokens: str) -> None:
    source = sources[label]
    for token in tokens:
        require(token in source, f"{label} token is missing: {token}")


sources = {label: read(label) for label in FILES}

# Shared Device Core bootstrap remains exactly the existing three-response metadata pipeline.
require_tokens(
    "metadata_contract",
    "DeviceRuntimeMetadataBootstrapKind.IDENTITY",
    "DeviceRuntimeMetadataBootstrapKind.CAPABILITIES",
    "DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES",
    "deviceRuntimeMetadataBootstrapOrder",
)
for forbidden in (
    "LIGHT_STATUS",
    "LIGHT_THERMAL_STATUS",
    "COOLING_STATUS",
    "TIMER_STATUS",
    "DOSING_STATUS",
):
    require(
        forbidden not in sources["metadata_contract"] and
        forbidden not in sources["metadata_bootstrap"],
        f"shared metadata bootstrap must not own domain step {forbidden}",
    )

# Domain planning is catalog/family/feature driven, never product-name inferred.
require_tokens(
    "domain_bootstrap",
    "DeviceRuntimeDomainBootstrapPlanResolver",
    "DeviceFamily.LIGHT",
    "AqlDeviceFeatureKey.LIGHT_FAN_CONTROL in product.profile.supportedFeatures",
    "DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS",
    "DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS",
    "DeviceRuntimeDomainBootstrapStep.COOLING_STATUS",
    "DeviceRuntimeDomainBootstrapStep.TIMER_STATUS",
    "DeviceRuntimeDomainBootstrapStep.DOSING_STATUS",
    "outcome.generation != plan.connectionGeneration",
    "DeviceRuntimeDomainBootstrapResult.Stale",
    "DeviceRuntimeDomainBootstrapResult.AlreadyStarted",
)
for forbidden in (
    "LIGHT_WRGB_PRO_ELITE",
    "LIGHT_RGB_PRO_SLIM",
    "wsClient",
    "AqlWsOutgoingMessage",
):
    require(forbidden not in sources["domain_bootstrap"], f"domain bootstrap contains forbidden coupling: {forbidden}")

# RuntimeRepository owns the phase transition and uses typed module repositories only.
require_tokens(
    "runtime",
    "AqlCommercialDeviceCatalog.validate(state.metadata)",
    "DeviceRuntimeDomainBootstrapPlanResolver.resolve(",
    "DeviceRuntimeSessionReadiness.MetadataReady",
    "DeviceRuntimeSessionReadiness.DomainBootstrapping",
    "DeviceRuntimeSessionReadiness.RuntimeReady",
    "runtimeModules.light.requestStatus(plan.deviceUid)",
    "runtimeModules.lightThermal.requestStatus(plan.deviceUid)",
    "runtimeModules.cooling.requestStatus(plan.deviceUid)",
    "runtimeModules.timer.requestStatus(plan.deviceUid)",
    "runtimeModules.dosing.requestStatus(plan.deviceUid)",
    "timeSyncCoordinator.syncPhoneNowIfNeeded(",
    "COMMAND_CANCELLED_DOMAIN_BOOTSTRAP_FAILURE",
)
ready_index = sources["runtime"].find("DeviceRuntimeSessionReadiness.RuntimeReady(plan)")
time_sync_index = sources["runtime"].find("timeSyncCoordinator.syncPhoneNowIfNeeded(", ready_index)
require(ready_index >= 0 and time_sync_index > ready_index, "time sync must follow RuntimeReady publication")

shared_method_start = sources["runtime"].find("private fun sendAuthenticatedBootstrap(session: RuntimeSession): Boolean")
shared_method_end = sources["runtime"].find("\n    companion object", shared_method_start)
shared_method = sources["runtime"][shared_method_start:shared_method_end]
require("metadataBootstrapCoordinator.beginAndDispatch" in shared_method, "shared bootstrap dispatcher changed")
require("runtimeModules." not in shared_method, "shared bootstrap method must not dispatch domain commands")
require("domainBootstrapCoordinator" not in shared_method, "shared bootstrap method must not own domain continuation")

# Provider owns typed module lifecycle; thermal state is cleared on reconnect and Dosing readiness is reset.
require_tokens(
    "provider",
    "val lightThermal = DeviceLightThermalRuntimeRepository(commandGateway, lightThermalStateStore)",
    "val dosing = DeviceDosingRuntimeRepository(commandGateway)",
    "lightThermalStateStore.clear(deviceUid)",
    "dosing.clearRuntimeState(deviceUid)",
)

# Newly added commands are typed/parsing boundaries, not raw websocket shortcuts.
require_tokens(
    "light_thermal_repo",
    "AqlWsContract.ACTION_LIGHT_THERMAL_STATUS_GET",
    "successParser = DeviceLightThermalStatusParser::parse",
    "stateStore.recordStatus(deviceUid, status)",
)
require_tokens(
    "light_thermal_parser",
    'private const val SCHEMA = "aql.light-thermal.v1"',
    "requireExactKeys(STATUS_KEYS",
    "fanOutputCount == fans.size",
)
require_tokens(
    "dosing_repo",
    "DeviceDosingV1Repository(gateway)",
    "delegate.requestGlobalStatus(deviceUid)",
    "runtimeReadyGenerations",
)
require("MutableStateFlow<Map<DeviceUid, DeviceDosingV1GlobalStatus>>" not in sources["dosing_repo"],
        "Dosing runtime facade must not create a parallel canonical Dosing status store")

# Production Dosing detailed hydration may happen only after RuntimeReady generation proof.
require_tokens(
    "dosing_production",
    "runtimeModules.dosing.runtimeReadyGenerations.collect",
    "refreshedRuntimeGenerations.put(deviceUid, generation) != generation",
)
require(
    "event is DeviceRuntimeLifecycleEvent.Authenticated" not in sources["dosing_production"],
    "Dosing must not issue refreshAll directly from Authenticated before metadata/domain bootstrap",
)

if errors:
    print("Runtime domain bootstrap guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Runtime domain bootstrap guard passed (catalog-validated RuntimeReady pipeline enforced).")
