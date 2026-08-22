#!/usr/bin/env python3
"""Protect the tank-device assignment application boundary."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"

CONTRACT = SOURCE / "application/devices/TankDeviceAssignmentOperations.kt"
ADAPTER = SOURCE / "data/aquarium/devices/DefaultTankDeviceAssignmentOperations.kt"
MAPPING = SOURCE / "data/devices/DeviceApplicationMapping.kt"
DETAIL_VM = SOURCE / "ui/tabs/aquarium/detail/devices/TankDetailDevicesViewModel.kt"
SELECT_VM = SOURCE / "ui/tabs/aquarium/detail/devices/select/TankDeviceSelectViewModel.kt"
CARD_MAPPER = SOURCE / "ui/common/devicecard/DeviceCompactSnapshotMapper.kt"
FACTORY = SOURCE / "composition/OwnerViewModelFactory.kt"
SMOKE_FACTORY = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/TankDeviceAssignmentViewModelBoundaryTest.kt"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required assignment-boundary file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


contract = read(CONTRACT)
adapter = read(ADAPTER)
mapping = read(MAPPING)
detail_vm = read(DETAIL_VM)
select_vm = read(SELECT_VM)
card_mapper = read(CARD_MAPPER)
factory = read(FACTORY)
smoke_factory = read(SMOKE_FACTORY)
test = read(TEST)

for token in (
    "interface TankDeviceAssignmentOperations",
    "data class TankDeviceListItem",
    "data class AvailableTankDevicesSnapshot",
    "sealed interface AssignDeviceToTankResult",
    "enum class RemoveDeviceFromTankResult",
    "fun assignedDevices(tankId: Long)",
    "fun availableDevices(tankId: Long)",
    "suspend fun assignDevice",
    "suspend fun removeDevice",
):
    if token not in contract:
        errors.append(f"{CONTRACT.relative_to(ROOT)}: application contract token is missing: {token}")

if re.search(
    r"^import\s+(?:android(?:x)?\.|com\.google\.|com\.aqua\.aqualight\.(?:data|platform|ui|composition)\.)",
    contract,
    re.MULTILINE,
):
    errors.append(f"{CONTRACT.relative_to(ROOT)}: application contract depends on an outer layer")

for token in (
    "class DefaultTankDeviceAssignmentOperations",
    "assignmentRepository.assignedDevicesForTank",
    "assignmentRepository.availableDevicesForTank",
    "devicesRepository.devices",
    "toTankDeviceListItem",
    "AssignDeviceToTankResult.Conflict",
    "RemoveDeviceFromTankResult.FAILURE",
):
    if token not in adapter:
        errors.append(f"{ADAPTER.relative_to(ROOT)}: data adapter token is missing: {token}")

if "fun DeviceSnapshot.toTankDeviceListItem" not in mapping:
    errors.append(f"{MAPPING.relative_to(ROOT)}: tank device DTO mapping is missing")

for path, text in ((DETAIL_VM, detail_vm), (SELECT_VM, select_vm)):
    if "TankDeviceAssignmentOperations" not in text:
        errors.append(f"{path.relative_to(ROOT)}: ViewModel must receive the application boundary")
    for forbidden in (
        "import com.aqua.aqualight.data.",
        "DevicesRepository",
        "TankDeviceAssignmentRepository",
        "TankDeviceAssignmentResult",
        "TankDeviceRemovalResult",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: assignment infrastructure leaked into UI: {forbidden}")
    if re.search(r"\bDeviceUid\b", text):
        errors.append(
            f"{path.relative_to(ROOT)}: assignment infrastructure leaked into UI: DeviceUid"
        )

for token in (
    "assignmentOperations.assignedDevices(tankId)",
    "assignmentOperations.removeDevice(tankId, deviceUid)",
):
    if token not in detail_vm:
        errors.append(f"{DETAIL_VM.relative_to(ROOT)}: detail boundary wiring is missing: {token}")

for token in (
    "assignmentOperations.availableDevices(tankId)",
    "assignmentOperations.assignDevice(tankId, item.deviceUid)",
    "snapshot.hasRegisteredDevices",
):
    if token not in select_vm:
        errors.append(f"{SELECT_VM.relative_to(ROOT)}: selection boundary wiring is missing: {token}")

if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", card_mapper, re.MULTILINE):
    errors.append(f"{CARD_MAPPER.relative_to(ROOT)}: compact card mapper must consume application values")
for token in ("TankDeviceListItem", "OwnerDeviceAvailability.REACHABLE"):
    if token not in card_mapper:
        errors.append(f"{CARD_MAPPER.relative_to(ROOT)}: application card mapping token is missing: {token}")

for path, text in ((FACTORY, factory), (SMOKE_FACTORY, smoke_factory)):
    for token in (
        "DefaultTankDeviceAssignmentOperations(",
        "assignmentOperations =",
        "TankDetailDevicesViewModel(",
        "TankDeviceSelectViewModel(",
    ):
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: production/smoke assignment binding is missing: {token}")

for token in (
    "FakeTankDeviceAssignmentOperations",
    "tank detail renders assigned devices from application boundary",
    "tank detail emits remove failure from typed application result",
    "selection distinguishes all registered devices assigned",
    "selection exposes assignment conflict tank id",
):
    if token not in test:
        errors.append(f"{TEST.relative_to(ROOT)}: fake-backed assignment coverage is missing: {token}")

if errors:
    print("Tank device assignment boundary guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Tank device assignment boundary guard passed.")
