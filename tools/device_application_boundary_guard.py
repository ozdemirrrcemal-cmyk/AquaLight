#!/usr/bin/env python3
"""Protect completed device application boundaries."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"

OWNER_CONTRACT = SOURCE / "application/devices/OwnerDevicesOperations.kt"
MENU_CONTRACT = SOURCE / "application/devices/DeviceMenuAccessOperations.kt"
STATUS_CONTRACT = SOURCE / "application/devices/DeviceStatusOperations.kt"
OWNER_ADAPTER = SOURCE / "data/devices/DefaultOwnerDevicesOperations.kt"
STATUS_ADAPTER = SOURCE / "data/devices/DefaultDeviceStatusOperations.kt"
MAPPING = SOURCE / "data/devices/DeviceApplicationMapping.kt"
MENU_ADAPTER = SOURCE / "data/devices/menu/DefaultDeviceMenuAccessOperations.kt"
DEVICES_VIEW_MODEL = SOURCE / "ui/tabs/devices/DevicesViewModel.kt"
TANK_DEVICES_VIEW_MODEL = SOURCE / "ui/tabs/aquarium/detail/devices/TankDetailDevicesViewModel.kt"
STATUS_VIEW_MODEL = SOURCE / "ui/tabs/settings/device/DeviceStatusViewModel.kt"
SETTINGS_VIEW_MODEL = SOURCE / "ui/tabs/settings/SettingsViewModel.kt"
STATUS_CLOCK = SOURCE / "ui/tabs/settings/device/DeviceStatusClock.kt"
CARD_MAPPER = SOURCE / "ui/tabs/devices/DeviceCardMapper.kt"
STATUS_MAPPER = SOURCE / "ui/tabs/settings/device/DeviceStatusSnapshotMapper.kt"
ROUTE_RESOLVER = SOURCE / "ui/tabs/devices/route/DeviceRouteResolver.kt"
OBSOLETE_UI_GATE = SOURCE / "ui/tabs/devices/route/DeviceMenuOpenGate.kt"
FACTORY = SOURCE / "composition/OwnerViewModelFactory.kt"
SMOKE_FACTORY = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
DEVICES_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/devices/DevicesViewModelBoundaryTest.kt"
STATUS_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/device/DeviceStatusViewModelBoundaryTest.kt"
SETTINGS_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/settings/SettingsViewModelBoundaryTest.kt"
AUTH_POLICY_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/menu/DeviceMenuAuthenticationPolicyTest.kt"
MENU_ACCESS_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/menu/DefaultDeviceMenuAccessOperationsTest.kt"
DISCOVERY_CONTRACT_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/discovery/udp/AqlDiscoveryParserContractTest.kt"
SEQUENCE = ROOT / "docs/commercial-architecture-closure-record.md"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required device-boundary file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


owner_contract = read(OWNER_CONTRACT)
menu_contract = read(MENU_CONTRACT)
status_contract = read(STATUS_CONTRACT)
owner_adapter = read(OWNER_ADAPTER)
status_adapter = read(STATUS_ADAPTER)
mapping = read(MAPPING)
menu_adapter = read(MENU_ADAPTER)
devices_view_model = read(DEVICES_VIEW_MODEL)
tank_devices_view_model = read(TANK_DEVICES_VIEW_MODEL)
status_view_model = read(STATUS_VIEW_MODEL)
settings_view_model = read(SETTINGS_VIEW_MODEL)
status_clock = read(STATUS_CLOCK)
card_mapper = read(CARD_MAPPER)
status_mapper = read(STATUS_MAPPER)
route_resolver = read(ROUTE_RESOLVER)
factory = read(FACTORY)
smoke_factory = read(SMOKE_FACTORY)
devices_test = read(DEVICES_TEST)
status_test = read(STATUS_TEST)
settings_test = read(SETTINGS_TEST)
auth_policy_test = read(AUTH_POLICY_TEST)
menu_access_test = read(MENU_ACCESS_TEST)
discovery_contract_test = read(DISCOVERY_CONTRACT_TEST)
sequence = read(SEQUENCE)

for token, reason in (
    ("interface OwnerDevicesOperations", "owner device access needs an explicit application contract"),
    ("data class OwnerDeviceListItem", "UI needs a stable application DTO"),
    ("enum class OwnerDeviceFamily", "device family must not leak from data models"),
    ("enum class OwnerDeviceAvailability", "runtime presence must be reduced to an application state"),
    ("data class DeleteOwnerDevicesResult", "device deletion needs a typed application result"),
    ("suspend fun deleteDevices(deviceUids: Set<String>)", "device deletion must cross one boundary"),
):
    if token not in owner_contract:
        errors.append(f"{OWNER_CONTRACT.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("interface DeviceMenuAccessOperations", "menu access needs an application contract"),
    ("sealed interface DeviceMenuAccessResult", "menu access needs a typed result"),
    ("data class Available", "approved routing metadata must be explicit"),
    ("data class Unavailable", "blocked menu decisions must be explicit"),
    ("enum class DeviceMenuUnavailableReason", "blocked decisions need typed reasons"),
    ("LOCAL_NETWORK_UNAVAILABLE", "local network loss needs a dedicated product reason"),
    ("AUTHENTICATION_REQUIRED", "pairing/authentication failure needs a dedicated product reason"),
    ("DEVICE_UNRESPONSIVE", "target-device failure needs a dedicated product reason"),
    ("VERIFICATION_TIMED_OUT", "bounded verification timeout needs a dedicated product reason"),
    ("CURRENT_LIVENESS_NOT_PROVEN", "UDP-only discovery must fail closed"),
):
    if token not in menu_contract:
        errors.append(f"{MENU_CONTRACT.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("interface DeviceStatusOperations", "status access needs a read-only application contract"),
    ("val statuses: Flow<List<OwnerDeviceStatusSnapshot>>", "status observation must expose application DTOs"),
    ("data class OwnerDeviceStatusSnapshot", "status UI needs a stable application DTO"),
    ("val lastSeenAtMillis: Long", "relative status labels need a primitive timestamp"),
):
    if token not in status_contract:
        errors.append(f"{STATUS_CONTRACT.relative_to(ROOT)}: {reason}: {token}")

for path, text in (
    (OWNER_CONTRACT, owner_contract),
    (MENU_CONTRACT, menu_contract),
    (STATUS_CONTRACT, status_contract),
):
    for forbidden in (
        "import android.",
        "import androidx.",
        "import com.google.",
        "import com.aqua.aqualight.data.",
        "import com.aqua.aqualight.platform.",
        "import com.aqua.aqualight.ui.",
    ):
        if forbidden in text:
            errors.append(
                f"{path.relative_to(ROOT)}: application contract contains forbidden dependency: {forbidden}"
            )

for token, reason in (
    ("class DefaultOwnerDevicesOperations", "data adapter must implement the owner-device boundary"),
    ("assignmentRepository.assignedTankNamesByDevice()", "tank names must be joined behind the boundary"),
    ("deviceDataCleaner.deleteDevices", "transactional deletion must remain behind the boundary"),
    ("snapshot.toOwnerDeviceListItem", "owner adapter must use central mapping"),
):
    if token not in owner_adapter:
        errors.append(f"{OWNER_ADAPTER.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("class DefaultDeviceStatusOperations", "data adapter must implement the status boundary"),
    ("devicesRepository.devices.map", "status adapter must observe the repository behind the boundary"),
    ("toOwnerDeviceStatusSnapshot", "status adapter must use central mapping"),
    ("devicesRepository.start(scope)", "runtime start must remain behind the status boundary"),
):
    if token not in status_adapter:
        errors.append(f"{STATUS_ADAPTER.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("DeviceOnlineState.AUTHENTICATED", "runtime state must be mapped explicitly"),
    ("DeviceFamily.LIGHT", "data family must be mapped explicitly"),
    ("fun DeviceSnapshot.toOwnerDeviceListItem", "list mapping must be centralized"),
    ("fun DeviceSnapshot.toOwnerDeviceStatusSnapshot", "status mapping must be centralized"),
    ("lastControlProofAtMillis", "latest-seen mapping must include correlated control proof"),
    ("lastRuntimeMessageAtMillis", "latest-seen mapping must include decoded runtime traffic"),
    ("lastAuthenticatedAtMillis", "latest-seen mapping must include authenticated runtime activity"),
    ("lastWsConnectedAtMillis", "latest-seen mapping must include WebSocket activity"),
    ("lastUdpSeenAtMillis", "latest-seen mapping must include LAN discovery activity"),
):
    if token not in mapping:
        errors.append(f"{MAPPING.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("interface DeviceMenuRuntimePort", "menu liveness needs a testable data port"),
    ("class RepositoryDeviceMenuRuntimePort", "repository access needs a concrete adapter"),
    ("class DefaultDeviceMenuAccessOperations", "menu access must implement the application contract"),
    ("DeviceMenuAuthenticationPolicy", "fresh authenticated sessions must be verified"),
    ("proveCurrentLiveness", "menu liveness must use the correlated command outcome"),
    ("outcome is DeviceRuntimeCommandOutcome.Success", "queued writes must not prove liveness"),
    ("recordControlProof", "successful liveness proof must update the canonical registry"),
    ("MENU_ACCESS_BUDGET_MS", "interactive liveness verification must be bounded"),
    ("AUTHENTICATION_REQUIRED", "authentication failure must remain typed"),
    ("DEVICE_UNRESPONSIVE", "unresponsive target failure must remain typed"),
    ("VERIFICATION_TIMED_OUT", "timeout failure must remain typed"),
    ("CURRENT_LIVENESS_NOT_PROVEN", "UDP-only discovery must not authorize controls"),
):
    if token not in menu_adapter:
        errors.append(f"{MENU_ADAPTER.relative_to(ROOT)}: {reason}: {token}")

for forbidden in ("verifyLanAccess", "hasFreshLanProof"):
    if forbidden in menu_adapter:
        errors.append(
            f"{MENU_ADAPTER.relative_to(ROOT)}: UDP discovery cannot authorize menu access: {forbidden}"
        )

for token, reason in (
    (
        "fresh UDP proof without authenticated runtime endpoint is rejected",
        "UDP-only menu denial regression test is missing",
    ),
    (
        "discovered endpoint still requires authenticated runtime proof",
        "endpoint discovery must not bypass authenticated runtime proof",
    ),
):
    if token not in menu_access_test:
        errors.append(f"{MENU_ACCESS_TEST.relative_to(ROOT)}: {reason}")

for token, reason in (
    (
        "firmware sentAt does not replace Android receive clocks",
        "firmware uptime must not determine Android freshness",
    ),
    (
        "stale v2 documentation shape is not accepted as a runtime contract",
        "the executable UDP v1 baseline needs a regression test",
    ),
):
    if token not in discovery_contract_test:
        errors.append(f"{DISCOVERY_CONTRACT_TEST.relative_to(ROOT)}: {reason}")

for path, text in (
    (DEVICES_VIEW_MODEL, devices_view_model),
    (TANK_DEVICES_VIEW_MODEL, tank_devices_view_model),
):
    for token in (
        "private val menuAccessOperations: DeviceMenuAccessOperations",
        "menuAccessOperations.resolve(deviceUid)",
        "routeResolver.resolve(result)",
    ):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: typed device-menu UI wiring is missing: {token}"
            )
    for forbidden in (
        "DeviceMenuOpenGate",
        "DeviceMenuOpenGateResult",
        "import com.aqua.aqualight.data.devices.menu.",
    ):
        if forbidden in text:
            errors.append(
                f"{path.relative_to(ROOT)}: device-menu implementation leaked into UI: {forbidden}"
            )

for token, reason in (
    ("private val operations: OwnerDevicesOperations", "DevicesViewModel must receive the owner-device boundary"),
    ("operations.start(viewModelScope)", "device runtime start must cross the boundary"),
    ("operations.refreshVisibleDevices()", "refresh must cross the boundary"),
    ("operations.deleteDevices(selected)", "delete must cross the boundary"),
    ("operations.devices", "device observation must cross the boundary"),
):
    if token not in devices_view_model:
        errors.append(f"{DEVICES_VIEW_MODEL.relative_to(ROOT)}: {reason}: {token}")

for path, text, required_tokens in (
    (
        STATUS_VIEW_MODEL,
        status_view_model,
        (
            "private val operations: DeviceStatusOperations",
            "private val clock: DeviceStatusClock",
            "operations.start(viewModelScope)",
            "operations.statuses",
            "clock.ticks",
        ),
    ),
    (
        SETTINGS_VIEW_MODEL,
        settings_view_model,
        (
            "private val deviceStatusOperations: DeviceStatusOperations",
            "deviceStatusOperations.start(viewModelScope)",
            "deviceStatusOperations.statuses",
        ),
    ),
):
    for token in required_tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: status boundary wiring is missing: {token}")
    for forbidden in (
        "import com.aqua.aqualight.data.devices.",
        "DevicesRepository",
        "DeviceSnapshot",
        "DeviceOnlineState",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: status data implementation leaked into UI: {forbidden}")

for token, reason in (
    ("interface DeviceStatusClock", "last-seen refresh needs an injectable clock"),
    ("class SystemDeviceStatusClock", "production needs a concrete UI clock"),
    ("emit(nowMillis())", "clock must emit immediately"),
    ("delay(LAST_SEEN_TICK_MS)", "clock must refresh relative labels periodically"),
):
    if token not in status_clock:
        errors.append(f"{STATUS_CLOCK.relative_to(ROOT)}: {reason}: {token}")

for forbidden in (
    "import com.aqua.aqualight.data.aquarium.devices.",
    "import com.aqua.aqualight.data.devices.remove.",
    "import com.aqua.aqualight.data.devices.repository.",
    "DevicesRepositoryProvider.get(",
    "TankDeviceAssignmentRepositoryProvider.get(",
    "OwnerDeviceDataCleaner.create(",
):
    if forbidden in devices_view_model:
        errors.append(
            f"{DEVICES_VIEW_MODEL.relative_to(ROOT)}: concrete device infrastructure leaked into UI: {forbidden}"
        )

for path, text, label in (
    (CARD_MAPPER, card_mapper, "card"),
    (STATUS_MAPPER, status_mapper, "status"),
    (ROUTE_RESOLVER, route_resolver, "route"),
):
    if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", text, re.MULTILINE):
        errors.append(
            f"{path.relative_to(ROOT)}: {label} mapping must consume application values, not data models"
        )

if OBSOLETE_UI_GATE.exists():
    errors.append(
        f"{OBSOLETE_UI_GATE.relative_to(ROOT)}: obsolete UI-owned menu gate must not exist"
    )

for path, text in ((FACTORY, factory), (SMOKE_FACTORY, smoke_factory)):
    for token in (
        "DefaultOwnerDevicesOperations(",
        "DefaultDeviceStatusOperations(",
        "DefaultDeviceMenuAccessOperations.create(",
        "menuAccessOperations =",
        "routeResolver = DeviceRouteResolver()",
    ):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: production/smoke wiring is missing device boundary token: {token}"
            )

for token in (
    "clock = SystemDeviceStatusClock()",
    "deviceStatusOperations = DefaultDeviceStatusOperations",
):
    if token not in factory:
        errors.append(f"{FACTORY.relative_to(ROOT)}: production status binding is missing: {token}")

for token, reason in (
    ("FakeOwnerDevicesOperations", "DevicesViewModel needs a deterministic owner-device fake"),
    ("FakeDeviceMenuAccessOperations", "DevicesViewModel needs a deterministic menu fake"),
    ("device cards are rendered from one application boundary", "card rendering needs boundary coverage"),
    ("available device menu result is mapped to UI route", "menu routing needs boundary coverage"),
    ("partial delete keeps only failed devices selected", "partial deletion needs regression coverage"),
):
    if token not in devices_test:
        errors.append(f"{DEVICES_TEST.relative_to(ROOT)}: {reason}: {token}")

for path, text, required_tokens in (
    (
        STATUS_TEST,
        status_test,
        (
            "FakeDeviceStatusOperations",
            "FakeDeviceStatusClock",
            "status cards are rendered from application DTOs",
            "relative last seen text refreshes only from injected clock",
        ),
    ),
    (
        SETTINGS_TEST,
        settings_test,
        (
            "FakeDeviceStatusOperations",
            "settings combines profile with application device overview",
        ),
    ),
):
    for token in required_tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: status boundary coverage is missing: {token}")

for path, text, token in (
    (AUTH_POLICY_TEST, auth_policy_test, "fresh authentication for requested device opens menu"),
    (
        MENU_ACCESS_TEST,
        menu_access_test,
        "correlated successful runtime response records canonical proof before opening",
    ),
):
    if token not in text:
        errors.append(f"{path.relative_to(ROOT)}: migrated menu policy coverage is missing: {token}")

for workstream_name in (
    "Device application boundaries",
    "Aquarium and care boundaries",
    "Provisioning platform boundaries",
    "Composition-root closure",
):
    if workstream_name not in sequence:
        errors.append(
            f"{SEQUENCE.relative_to(ROOT)}: commercial closure record is missing {workstream_name}"
        )

if errors:
    print("Device application boundary guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device application boundary guard passed.")
