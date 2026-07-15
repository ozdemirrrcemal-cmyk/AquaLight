#!/usr/bin/env python3
"""Protect the Stage 3 owner-device application boundary.

The Devices screen must consume a stable application contract. Concrete device,
assignment, persistence and deletion infrastructure is constructed only by the
composition root and adapted in the data layer.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"

CONTRACT = SOURCE / "application/devices/OwnerDevicesOperations.kt"
ADAPTER = SOURCE / "data/devices/DefaultOwnerDevicesOperations.kt"
VIEW_MODEL = SOURCE / "ui/tabs/devices/DevicesViewModel.kt"
CARD_MAPPER = SOURCE / "ui/tabs/devices/DeviceCardMapper.kt"
FACTORY = SOURCE / "composition/AquaViewModelFactory.kt"
SMOKE_FACTORY = (
    ROOT
    / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
)
TEST = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/devices/DevicesViewModelBoundaryTest.kt"
)
SEQUENCE = ROOT / "docs/stage-3-commercial-closure-sequence.md"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required device-boundary file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


contract = read(CONTRACT)
adapter = read(ADAPTER)
view_model = read(VIEW_MODEL)
card_mapper = read(CARD_MAPPER)
factory = read(FACTORY)
smoke_factory = read(SMOKE_FACTORY)
test = read(TEST)
sequence = read(SEQUENCE)

for token, reason in (
    ("interface OwnerDevicesOperations", "owner device access needs an explicit application contract"),
    ("data class OwnerDeviceListItem", "UI needs a stable application DTO"),
    ("enum class OwnerDeviceFamily", "device family must not leak from data models"),
    ("enum class OwnerDeviceAvailability", "runtime presence must be reduced to an application state"),
    ("data class DeleteOwnerDevicesResult", "device deletion needs a typed application result"),
    ("suspend fun deleteDevices(deviceUids: Set<String>)", "device deletion must cross one boundary"),
):
    if token not in contract:
        errors.append(f"{CONTRACT.relative_to(ROOT)}: {reason}: {token}")

for forbidden in (
    "import android.",
    "import androidx.",
    "import com.google.",
    "import com.aqua.aqualight.data.",
    "import com.aqua.aqualight.platform.",
    "import com.aqua.aqualight.ui.",
):
    if forbidden in contract:
        errors.append(
            f"{CONTRACT.relative_to(ROOT)}: application contract contains forbidden dependency: {forbidden}"
        )

for token, reason in (
    ("class DefaultOwnerDevicesOperations", "data adapter must implement the boundary"),
    ("assignmentRepository.assignedTankNamesByDevice()", "tank names must be joined behind the boundary"),
    ("deviceDataCleaner.deleteDevices", "transactional deletion must remain behind the boundary"),
    ("DeviceOnlineState.AUTHENTICATED", "runtime state must be mapped explicitly"),
    ("DeviceFamily.LIGHT", "data family must be mapped explicitly"),
):
    if token not in adapter:
        errors.append(f"{ADAPTER.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("private val operations: OwnerDevicesOperations", "DevicesViewModel must receive the application boundary"),
    ("operations.start(viewModelScope)", "device runtime start must cross the boundary"),
    ("operations.refreshVisibleDevices()", "refresh must cross the boundary"),
    ("operations.deleteDevices(selected)", "delete must cross the boundary"),
    ("operations.devices", "device observation must cross the boundary"),
):
    if token not in view_model:
        errors.append(f"{VIEW_MODEL.relative_to(ROOT)}: {reason}: {token}")

for forbidden in (
    "import com.aqua.aqualight.data.aquarium.devices.",
    "import com.aqua.aqualight.data.devices.remove.",
    "import com.aqua.aqualight.data.devices.repository.",
    "DevicesRepositoryProvider.get(",
    "TankDeviceAssignmentRepositoryProvider.get(",
    "OwnerDeviceDataCleaner.create(",
):
    if forbidden in view_model:
        errors.append(
            f"{VIEW_MODEL.relative_to(ROOT)}: concrete device infrastructure leaked into UI: {forbidden}"
        )

if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", card_mapper, re.MULTILINE):
    errors.append(
        f"{CARD_MAPPER.relative_to(ROOT)}: card mapping must consume application DTOs, not data models"
    )

for path, text in ((FACTORY, factory), (SMOKE_FACTORY, smoke_factory)):
    for token in (
        "DefaultOwnerDevicesOperations(",
        "operations = ownerDevicesOperations",
    ):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: production/smoke wiring is missing device boundary token: {token}"
            )

for token, reason in (
    ("FakeOwnerDevicesOperations", "DevicesViewModel needs a deterministic fake"),
    ("device cards are rendered from one application boundary", "card rendering needs boundary coverage"),
    ("partial delete keeps only failed devices selected", "partial deletion needs regression coverage"),
):
    if token not in test:
        errors.append(f"{TEST.relative_to(ROOT)}: {reason}: {token}")

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
