#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"

contract = APP / "application/devices/provisioning/ProvisioningProgressOperations.kt"
adapter = APP / "data/devices/provisioning/DefaultProvisioningProgressOperations.kt"
mapping = APP / "data/devices/provisioning/ProvisioningProgressMapping.kt"
view_model = APP / "ui/tabs/devices/add/DeviceProvisioningProgressViewModel.kt"
presentation = APP / "ui/tabs/devices/add/ProvisioningProgressPresentation.kt"
event_contract = APP / "ui/tabs/devices/add/DeviceProvisioningProgressContract.kt"
fragment = APP / "ui/tabs/devices/add/DeviceProvisioningProgressFragment.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
view_model_test = TESTS / "ui/tabs/devices/add/DeviceProvisioningProgressViewModelBoundaryTest.kt"
mapping_test = TESTS / "data/devices/provisioning/ProvisioningProgressMappingTest.kt"
obsolete_contract = APP / "ui/tabs/devices/add/DeviceProvisioningProgressOperations.kt"
obsolete_adapter = APP / "composition/DefaultDeviceProvisioningProgressOperations.kt"

required = (
    contract,
    adapter,
    mapping,
    view_model,
    presentation,
    event_contract,
    fragment,
    production,
    smoke,
    view_model_test,
    mapping_test,
)
errors: list[str] = []

for path in required:
    if not path.is_file():
        errors.append(f"missing required provisioning progress file: {path.relative_to(ROOT)}")

for path in (obsolete_contract, obsolete_adapter):
    if path.exists():
        errors.append(f"obsolete provisioning progress path remains: {path.relative_to(ROOT)}")

if contract.is_file():
    text = contract.read_text(encoding="utf-8")
    if "package com.aqua.aqualight.application.devices.provisioning" not in text:
        errors.append("provisioning progress contract is outside application layer")
    for token in (
        "import android.",
        "import androidx.",
        "import com.aqua.aqualight.data.",
        "import com.aqua.aqualight.platform.",
        "import com.aqua.aqualight.ui.",
        "DeviceSnapshot",
        "DeviceUid",
        "AqlProvisioning",
        "DeviceRoute",
    ):
        if token in text:
            errors.append(f"application progress contract leaks implementation type: {token}")
    for token in (
        "interface ProvisioningProgressOperations",
        "data class ProvisioningSessionSnapshot",
        "data class PreparedProvisioningRegistration",
        "sealed interface ProvisioningTransportEvent",
        "suspend fun prepareRegistration",
        "suspend fun commitPreparedRegistration",
        "suspend fun rollbackProvisioningRegistrationForOwner",
    ):
        if token not in text:
            errors.append(f"application progress contract is incomplete: {token}")

if adapter.is_file():
    text = adapter.read_text(encoding="utf-8")
    for token in (
        "AqlBleProvisioningAddressResolver(appContext)",
        "AqlBleProvisioningGattClient(appContext)",
        "AqlProvisioningHandoffSaver(appContext)",
        "AqlProvisioningDraftStore.get",
        "UserDataScope.requireCurrentUid()",
        "ConcurrentHashMap<String, DeviceSnapshot>",
        "registration.device.deviceUid",
        "removePreparedSnapshot",
    ):
        if token not in text:
            errors.append(f"provisioning progress adapter is missing: {token}")

if mapping.is_file():
    text = mapping.read_text(encoding="utf-8")
    for token in (
        "toApplicationSession",
        "toApplicationEvent",
        "toDataHandoff",
        "ProvisioningStatus.valueOf(status.name)",
    ):
        if token not in text:
            errors.append(f"provisioning progress mapping is missing: {token}")

if view_model.is_file():
    text = view_model.read_text(encoding="utf-8")
    for token in (
        "ProvisioningProgressOperations",
        "ProvisioningTransportEvent",
        "PreparedProvisioningRegistration",
        "rollbackProvisioningRegistrationForOwner",
    ):
        if token not in text:
            errors.append(f"progress ViewModel is missing application behavior: {token}")
    for token in (
        "import com.aqua.aqualight.data.",
        "import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute",
        "AqlBleProvisioningGattEvent",
        "AqlProvisioningDraft",
        "AqlProvisioningRuntimeHandoff",
        "DeviceSnapshot",
        "DeviceUid(",
    ):
        if token in text:
            errors.append(f"progress ViewModel contains forbidden implementation dependency: {token}")

if event_contract.is_file():
    text = event_contract.read_text(encoding="utf-8")
    if "ProvisionedDevice" not in text:
        errors.append("progress completion event must carry an application device")
    if "DeviceRoute" in text:
        errors.append("progress completion event must not carry a UI route")

if fragment.is_file():
    text = fragment.read_text(encoding="utf-8")
    for token in ("ProvisionedDevice", "OwnerDeviceFamily"):
        if token not in text:
            errors.append(f"progress fragment is missing application navigation input: {token}")
    for token in ("DeviceRoute", "DeviceRouteTarget"):
        if token in text:
            errors.append(f"progress fragment contains obsolete route model: {token}")

for path in (production, smoke):
    if path.is_file():
        text = path.read_text(encoding="utf-8")
        if "DefaultProvisioningProgressOperations(appContext)" not in text:
            errors.append(f"missing provisioning progress binding in {path.relative_to(ROOT)}")
        if "DefaultDeviceProvisioningProgressOperations" in text:
            errors.append(f"obsolete provisioning progress binding in {path.relative_to(ROOT)}")

if view_model_test.is_file():
    text = view_model_test.read_text(encoding="utf-8")
    for token in (
        "FakeProvisioningOperations",
        "missing session renders expired state without opening transport",
        "start delegates session id and resolved BLE address",
        "runtime handoff and completion commit prepared registration",
        "transport failure after prepare rolls back pending registration",
    ):
        if token not in text:
            errors.append(f"provisioning progress behavior coverage is missing: {token}")

if mapping_test.is_file():
    text = mapping_test.read_text(encoding="utf-8")
    for token in (
        "draft session mapping hides WiFi password and claim data",
        "all provisioning statuses map with exact enum parity",
        "runtime handoff round trip preserves endpoint and identity",
        "device info event maps verified identity fields",
    ):
        if token not in text:
            errors.append(f"provisioning progress mapping coverage is missing: {token}")

if errors:
    print("Provisioning progress application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Provisioning progress application boundary guard passed.")
