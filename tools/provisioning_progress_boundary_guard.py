#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"
ANDROID_TESTS = ROOT / "app/src/androidTest/java/com/aqua/aqualight"

contract = APP / "application/devices/provisioning/ProvisioningProgressOperations.kt"
adapter = APP / "data/devices/provisioning/DefaultProvisioningProgressOperations.kt"
mapping = APP / "data/devices/provisioning/ProvisioningProgressMapping.kt"
storage_port = APP / "data/devices/provisioning/store/ProvisioningDraftStorage.kt"
encrypted_store = APP / "data/devices/provisioning/store/AqlProvisioningDraftStore.kt"
draft_adapter = APP / "data/devices/provisioning/repository/DefaultProvisioningDraftOperations.kt"
view_model = APP / "ui/tabs/devices/add/DeviceProvisioningProgressViewModel.kt"
presentation = APP / "ui/tabs/devices/add/ProvisioningProgressPresentation.kt"
event_contract = APP / "ui/tabs/devices/add/DeviceProvisioningProgressContract.kt"
fragment = APP / "ui/tabs/devices/add/DeviceProvisioningProgressFragment.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
view_model_test = TESTS / "ui/tabs/devices/add/DeviceProvisioningProgressViewModelBoundaryTest.kt"
cancellation_test = TESTS / "ui/tabs/devices/add/DeviceProvisioningCancellationBoundaryTest.kt"
presenter_test = TESTS / "ui/tabs/devices/add/ProvisioningProgressPresenterTest.kt"
mapping_test = TESTS / "data/devices/provisioning/ProvisioningProgressMappingTest.kt"
draft_test = TESTS / "data/devices/provisioning/repository/DefaultProvisioningDraftOperationsTest.kt"
process_test = (
    ANDROID_TESTS
    / "data/devices/provisioning/store/AqlProvisioningDraftStoreProcessRecreationTest.kt"
)
obsolete_contract = APP / "ui/tabs/devices/add/DeviceProvisioningProgressOperations.kt"
obsolete_adapter = APP / "composition/DefaultDeviceProvisioningProgressOperations.kt"

required = (
    contract,
    adapter,
    mapping,
    storage_port,
    encrypted_store,
    draft_adapter,
    view_model,
    presentation,
    event_contract,
    fragment,
    production,
    smoke,
    view_model_test,
    cancellation_test,
    presenter_test,
    mapping_test,
    draft_test,
    process_test,
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
        "webSocketToken: String",
        "val webSocketToken",
    ):
        if token in text:
            errors.append(f"application progress contract leaks implementation/credential: {token}")
    for token in (
        "interface ProvisioningProgressOperations",
        "data class ProvisioningSessionSnapshot",
        "data class ProvisioningRuntimeHandoff",
        "val handoffId: String",
        "data class PreparedProvisioningRegistration",
        "sealed interface ProvisioningTransportEvent",
        "suspend fun prepareRegistration",
        "suspend fun commitPreparedRegistration",
        "suspend fun rollbackProvisioningRegistrationForOwner",
        "no credential crosses into UI",
    ):
        if token not in text:
            errors.append(f"application progress contract is incomplete: {token}")

if adapter.is_file():
    text = adapter.read_text(encoding="utf-8")
    for token in (
        "AqlBleProvisioningAddressResolver(appContext)",
        "AqlBleProvisioningGattClient(appContext)",
        "AqlProvisioningHandoffSaver(appContext)",
        "draftStore.get",
        "OwnerProvisioningScope.create",
        "UserDataScope.withOwnerUid(ownerUid)",
        "ConcurrentHashMap<String, AqlProvisioningRuntimeHandoff>",
        "ConcurrentHashMap<String, DeviceSnapshot>",
        "preparedRuntimeTokens",
        "preparedHandoffIds",
        "registerRuntimeHandoff",
        "requireDataHandoff",
        "dataHandoff.webSocketToken",
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
        "toApplicationReference",
        "ProvisioningStatus.valueOf(status.name)",
    ):
        if token not in text:
            errors.append(f"provisioning progress mapping is missing: {token}")
    for forbidden in (
        "toDataHandoff",
        "webSocketToken = webSocketToken",
    ):
        if forbidden in text:
            errors.append(f"provisioning mapping exposes runtime credential: {forbidden}")

if storage_port.is_file():
    text = storage_port.read_text(encoding="utf-8")
    if "interface ProvisioningDraftStorage" not in text:
        errors.append("provisioning draft storage port is missing")

if encrypted_store.is_file():
    text = encrypted_store.read_text(encoding="utf-8")
    for token in (
        "EncryptedSharedPreferences.create",
        "MasterKey.KeyScheme.AES256_GCM",
        "PrefKeyEncryptionScheme.AES256_SIV",
        "PrefValueEncryptionScheme.AES256_GCM",
        "SESSION_TTL_MILLIS",
        "decoded.ownerUid != ownerUid -> null",
        "Encrypted provisioning session storage write failed.",
    ):
        if token not in text:
            errors.append(f"encrypted provisioning session invariant is missing: {token}")
    for forbidden in (
        "context.getSharedPreferences(",
        "PreferenceManager.getDefaultSharedPreferences",
    ):
        if forbidden in text:
            errors.append(f"plaintext provisioning persistence is forbidden: {forbidden}")

if draft_adapter.is_file():
    text = draft_adapter.read_text(encoding="utf-8")
    for token in (
        "ProvisioningDraftStorage",
        "AqlProvisioningDraftStore(context.applicationContext)",
        "draftStore.create",
    ):
        if token not in text:
            errors.append(f"draft adapter storage binding is missing: {token}")

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
        "webSocketToken",
    ):
        if token in text:
            errors.append(f"progress ViewModel contains forbidden implementation/credential: {token}")

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
    for token in ("DeviceRoute", "DeviceRouteTarget", "webSocketToken"):
        if token in text:
            errors.append(f"progress fragment contains obsolete/credential model: {token}")

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
        "handoffId = \"handoff-1\"",
    ):
        if token not in text:
            errors.append(f"provisioning progress behavior coverage is missing: {token}")

if cancellation_test.is_file():
    text = cancellation_test.read_text(encoding="utf-8")
    for token in (
        "back before runtime handoff closes transport removes session and exits",
        "back after prepared handoff rolls back captured owner before exit",
        "rollbackProvisioningRegistrationForOwner",
        "assertEquals(0, operations.commitCalls)",
        "handoffId = \"handoff-1\"",
    ):
        if token not in text:
            errors.append(f"provisioning cancellation coverage is missing: {token}")

if presenter_test.is_file():
    text = presenter_test.read_text(encoding="utf-8")
    for token in (
        "wrong WiFi password returns password correction without progress",
        "missing WiFi network returns SSID correction",
        "device network save failure remains on progress recovery instead of blaming credentials",
    ):
        if token not in text:
            errors.append(f"provisioning WiFi recovery coverage is missing: {token}")

if mapping_test.is_file():
    text = mapping_test.read_text(encoding="utf-8")
    for token in (
        "draft session mapping hides WiFi password and claim data",
        "all provisioning statuses map with exact enum parity",
        "runtime handoff exposes endpoint and identity without credential",
        "assertFalse(mappedEvent.handoff.toString().contains(original.webSocketToken))",
        "device info event maps verified identity fields",
    ):
        if token not in text:
            errors.append(f"provisioning progress mapping coverage is missing: {token}")

if draft_test.is_file():
    text = draft_test.read_text(encoding="utf-8")
    for token in (
        "FakeProvisioningDraftStorage",
        "creates encrypted-storage draft from primitive application request",
        "uses injected BLE cache when navigation carries no address",
    ):
        if token not in text:
            errors.append(f"provisioning draft adapter coverage is missing: {token}")

if process_test.is_file():
    text = process_test.read_text(encoding="utf-8")
    for token in (
        "encryptedSessionSurvivesStoreRecreationWithoutPlaintextSecrets",
        "anotherOwnerCannotReadOrDeleteTheSession",
        "expiredSessionFailsClosedAfterProcessRecreation",
        "aql_provisioning_sessions.xml",
    ):
        if token not in text:
            errors.append(f"encrypted process recreation coverage is missing: {token}")

if errors:
    print("Provisioning progress application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Provisioning progress application boundary guard passed.")
