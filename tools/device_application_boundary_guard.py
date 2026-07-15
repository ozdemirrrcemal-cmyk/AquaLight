#!/usr/bin/env python3
"""Protect the Stage 3 owner-device and device-menu application boundaries."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"

OWNER_CONTRACT = SOURCE / "application/devices/OwnerDevicesOperations.kt"
MENU_CONTRACT = SOURCE / "application/devices/DeviceMenuAccessOperations.kt"
OWNER_ADAPTER = SOURCE / "data/devices/DefaultOwnerDevicesOperations.kt"
MAPPING = SOURCE / "data/devices/DeviceApplicationMapping.kt"
MENU_ADAPTER = SOURCE / "data/devices/menu/DefaultDeviceMenuAccessOperations.kt"
DEVICES_VIEW_MODEL = SOURCE / "ui/tabs/devices/DevicesViewModel.kt"
TANK_DEVICES_VIEW_MODEL = SOURCE / "ui/tabs/aquarium/detail/devices/TankDetailDevicesViewModel.kt"
CARD_MAPPER = SOURCE / "ui/tabs/devices/DeviceCardMapper.kt"
ROUTE_RESOLVER = SOURCE / "ui/tabs/devices/route/DeviceRouteResolver.kt"
OBSOLETE_UI_GATE = SOURCE / "ui/tabs/devices/route/DeviceMenuOpenGate.kt"
FACTORY = SOURCE / "composition/AquaViewModelFactory.kt"
SMOKE_FACTORY = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
DEVICES_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/devices/DevicesViewModelBoundaryTest.kt"
AUTH_POLICY_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/menu/DeviceMenuAuthenticationPolicyTest.kt"
PROOF_POLICY_TEST = ROOT / "app/src/test/java/com/aqua/aqualight/data/devices/menu/DeviceMenuRuntimeProofPolicyTest.kt"
SEQUENCE = ROOT / "docs/stage-3-commercial-closure-sequence.md"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required device-boundary file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


owner_contract = read(OWNER_CONTRACT)
menu_contract = read(MENU_CONTRACT)
owner_adapter = read(OWNER_ADAPTER)
mapping = read(MAPPING)
menu_adapter = read(MENU_ADAPTER)
devices_view_model = read(DEVICES_VIEW_MODEL)
tank_devices_view_model = read(TANK_DEVICES_VIEW_MODEL)
card_mapper = read(CARD_MAPPER)
route_resolver = read(ROUTE_RESOLVER)
factory = read(FACTORY)
smoke_factory = read(SMOKE_FACTORY)
devices_test = read(DEVICES_TEST)
auth_policy_test = read(AUTH_POLICY_TEST)
proof_policy_test = read(PROOF_POLICY_TEST)
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
):
    if token not in menu_contract:
        errors.append(f"{MENU_CONTRACT.relative_to(ROOT)}: {reason}: {token}")

for path, text in ((OWNER_CONTRACT, owner_contract), (MENU_CONTRACT, menu_contract)):
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
    ("DeviceOnlineState.AUTHENTICATED", "runtime state must be mapped explicitly"),
    ("DeviceFamily.LIGHT", "data family must be mapped explicitly"),
    ("fun DeviceSnapshot.toOwnerDeviceListItem", "snapshot mapping must be centralized"),
):
    if token not in mapping:
        errors.append(f"{MAPPING.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("interface DeviceMenuRuntimePort", "menu liveness needs a testable data port"),
    ("class RepositoryDeviceMenuRuntimePort", "repository access needs a concrete adapter"),
    ("class DefaultDeviceMenuAccessOperations", "menu access must implement the application contract"),
    ("DeviceMenuAuthenticationPolicy", "fresh authenticated sessions must be verified"),
    ("DeviceMenuRuntimeProofPolicy", "command responses must prove current liveness"),
    ("expectedRequestId", "runtime proof must correlate request ids"),
    ("CURRENT_LIVENESS_NOT_PROVEN", "failed liveness must be typed"),
):
    if token not in menu_adapter:
        errors.append(f"{MENU_ADAPTER.relative_to(ROOT)}: {reason}: {token}")

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

if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", card_mapper, re.MULTILINE):
    errors.append(
        f"{CARD_MAPPER.relative_to(ROOT)}: card mapping must consume application DTOs, not data models"
    )

if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", route_resolver, re.MULTILINE):
    errors.append(
        f"{ROUTE_RESOLVER.relative_to(ROOT)}: route mapping must consume application values, not data models"
    )

if OBSOLETE_UI_GATE.exists():
    errors.append(
        f"{OBSOLETE_UI_GATE.relative_to(ROOT)}: obsolete UI-owned menu gate must not exist"
    )

for path, text in ((FACTORY, factory), (SMOKE_FACTORY, smoke_factory)):
    for token in (
        "DefaultOwnerDevicesOperations(",
        "DefaultDeviceMenuAccessOperations.create(",
        "menuAccessOperations =",
        "routeResolver = DeviceRouteResolver()",
    ):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: production/smoke wiring is missing device boundary token: {token}"
            )

for token, reason in (
    ("FakeOwnerDevicesOperations", "DevicesViewModel needs a deterministic owner-device fake"),
    ("FakeDeviceMenuAccessOperations", "DevicesViewModel needs a deterministic menu fake"),
    ("device cards are rendered from one application boundary", "card rendering needs boundary coverage"),
    ("available device menu result is mapped to UI route", "menu routing needs boundary coverage"),
    ("partial delete keeps only failed devices selected", "partial deletion needs regression coverage"),
):
    if token not in devices_test:
        errors.append(f"{DEVICES_TEST.relative_to(ROOT)}: {reason}: {token}")

for path, text, token in (
    (AUTH_POLICY_TEST, auth_policy_test, "fresh authentication for requested device opens menu"),
    (PROOF_POLICY_TEST, proof_policy_test, "matching successful network status response proves current liveness"),
):
    if token not in text:
        errors.append(f"{path.relative_to(ROOT)}: migrated menu policy coverage is missing: {token}")

for branch_name in (
    "feature/stage-3-device-application-boundaries",
    "feature/stage-3-aquarium-care-boundaries",
    "feature/stage-3-provisioning-platform-boundaries",
    "feature/stage-3-composition-root-closure",
):
    if branch_name not in sequence:
        errors.append(
            f"{SEQUENCE.relative_to(ROOT)}: locked Stage 3 sequence is missing {branch_name}"
        )

if errors:
    print("Device application boundary guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device application boundary guard passed.")
