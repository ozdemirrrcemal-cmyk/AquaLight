#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"
ANDROID_TESTS = ROOT / "app/src/androidTest/java/com/aqua/aqualight"

contract = APP / "application/devices/provisioning/ProvisioningDiscoveryOperations.kt"
adapter = APP / "data/devices/provisioning/DefaultProvisioningDiscoveryOperations.kt"
secret_port = APP / "data/devices/provisioning/store/ProvisioningQrSecretStorage.kt"
secret_store = APP / "data/devices/provisioning/store/AqlProvisioningQrSecretStore.kt"
nearby_vm = APP / "ui/tabs/devices/add/DeviceAddViewModel.kt"
qr_vm = APP / "ui/tabs/devices/add/DeviceQrScanViewModel.kt"
qr_fragment = APP / "ui/tabs/devices/add/DeviceQrScanFragment.kt"
qr_decoder = APP / "platform/vision/ProvisioningQrFrameDecoder.kt"
nav_graph = ROOT / "app/src/main/res/navigation/nav_devices.xml"
app_container = APP / "composition/AppContainer.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
view_model_test = TESTS / "ui/tabs/devices/add/ProvisioningDiscoveryViewModelBoundaryTest.kt"
mapper_test = TESTS / "data/devices/provisioning/DefaultProvisioningDiscoveryOperationsMapperTest.kt"
secret_test = (
    ANDROID_TESTS
    / "data/devices/provisioning/store/AqlProvisioningQrSecretStoreInstrumentedTest.kt"
)

required = (
    contract,
    adapter,
    secret_port,
    secret_store,
    nearby_vm,
    qr_vm,
    qr_fragment,
    qr_decoder,
    nav_graph,
    app_container,
    production,
    smoke,
    view_model_test,
    mapper_test,
    secret_test,
)
errors: list[str] = []

for path in required:
    if not path.is_file():
        errors.append(f"missing required provisioning discovery file: {path.relative_to(ROOT)}")

if contract.is_file():
    text = contract.read_text(encoding="utf-8")
    if "package com.aqua.aqualight.application.devices.provisioning" not in text:
        errors.append("provisioning discovery contract is outside the application layer")
    for token in (
        "import android.",
        "import com.aqua.aqualight.data.",
        "DeviceUid",
        "val claimCode",
        "val rawPayload",
    ):
        if token in text:
            errors.append(f"application discovery contract leaks implementation/secret: {token}")
    for token in (
        "interface ProvisioningDiscoveryOperations",
        "fun discardQrPayload(payload: ProvisioningQrPayload)",
        "data class ProvisioningCandidateSnapshot",
        "data class ProvisioningQrPayload",
        "val secretReference: String",
        "opaque reference to encrypted claim material",
        "sealed interface ProvisioningScanStartResult",
    ):
        if token not in text:
            errors.append(f"application discovery contract is incomplete: {token}")

if adapter.is_file():
    text = adapter.read_text(encoding="utf-8")
    for token in (
        "DefaultBleProvisioningScanner",
        "AqlProvisioningQrParser",
        "DevicesRepository",
        "ProvisioningQrSecretStorage",
        "AqlProvisioningQrSecretStore",
        "qrSecretStore.create",
        "override fun discardQrPayload",
        "qrSecretStore.remove(payload.secretReference)",
        "payload.toApplicationPayload(secretReference)",
        "toApplicationSnapshot",
    ):
        if token not in text:
            errors.append(f"provisioning discovery adapter is missing: {token}")

if secret_port.is_file():
    text = secret_port.read_text(encoding="utf-8")
    for token in (
        "interface ProvisioningQrSecretStorage",
        "data class ProvisioningQrSecret",
        "fun get(reference: String)",
        "fun clearOwner()",
    ):
        if token not in text:
            errors.append(f"QR secret storage port is incomplete: {token}")

if secret_store.is_file():
    text = secret_store.read_text(encoding="utf-8")
    for token in (
        "EncryptedSharedPreferences.create",
        "MasterKey.KeyScheme.AES256_GCM",
        "SECRET_TTL_MILLIS",
        "decoded.ownerUid != ownerUid -> null",
        "aql_provisioning_qr_secrets",
        "Encrypted provisioning QR secret storage write failed.",
    ):
        if token not in text:
            errors.append(f"encrypted QR secret invariant is missing: {token}")
    for forbidden in (
        "context.getSharedPreferences(",
        "PreferenceManager.getDefaultSharedPreferences",
    ):
        if forbidden in text:
            errors.append(f"QR claim material may not use plaintext storage: {forbidden}")

for path in (nearby_vm, qr_vm):
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8")
    if "ProvisioningDiscoveryOperations" not in text:
        errors.append(f"{path.relative_to(ROOT)} does not use the application discovery boundary")
    for token in (
        "import com.aqua.aqualight.data.devices.provisioning.",
        "import com.aqua.aqualight.data.devices.repository.",
        "BleProvisioningScanner",
        "AqlProvisioningQrParser",
        "DevicesRepository",
        "AqlBleProvisioningCandidate",
        "AqlProvisioningQrPayload",
        "payload.claimCode",
        "payload.rawPayload",
        "val claimCode:",
        "val rawQrPayload:",
    ):
        if token in text:
            errors.append(f"{path.relative_to(ROOT)} contains forbidden discovery/secret dependency: {token}")

if qr_vm.is_file():
    text = qr_vm.read_text(encoding="utf-8")
    for token in (
        "discardPendingPayload()",
        "pendingPayload?.let(discoveryOperations::discardQrPayload)",
        "override fun onCleared()",
    ):
        if token not in text:
            errors.append(f"QR ViewModel secret cleanup is incomplete: {token}")

if qr_decoder.is_file():
    text = qr_decoder.read_text(encoding="utf-8")
    for token in (
        "package com.aqua.aqualight.platform.vision",
        "interface ProvisioningQrFrameDecoder",
        "interface ProvisioningQrFrameDecoderFactory",
        "class MlKitProvisioningQrFrameDecoderFactory",
        "BarcodeScanning.getClient",
        "Barcode.FORMAT_QR_CODE",
        "AtomicBoolean(false)",
        "scanner.process(inputImage)",
        "closed.compareAndSet(false, true)",
        "imageProxy.close()",
    ):
        if token not in text:
            errors.append(f"QR platform decoder is incomplete: {token}")

if qr_fragment.is_file():
    text = qr_fragment.read_text(encoding="utf-8")
    for token in (
        "ProvisioningQrFrameDecoder",
        "provisioningQrFrameDecoderFactory",
        ".decode(imageProxy)",
        "qrFrameDecoder?.close()",
        "qrSecretReference = result.qrSecretReference",
    ):
        if token not in text:
            errors.append(f"QR Fragment is missing platform/reference behavior: {token}")
    for token in (
        "com.google.mlkit",
        "BarcodeScanner",
        "BarcodeScannerOptions",
        "BarcodeScanning",
        "InputImage",
        "ExperimentalGetImage",
        "claimCode =",
        "rawQrPayload =",
    ):
        if token in text:
            errors.append(f"QR Fragment contains forbidden vendor/secret dependency: {token}")

if nav_graph.is_file():
    text = nav_graph.read_text(encoding="utf-8")
    if 'android:name="qrSecretReference"' not in text:
        errors.append("Wi-Fi navigation must carry an opaque QR secret reference")
    for token in ('android:name="claimCode"', 'android:name="rawQrPayload"'):
        if token in text:
            errors.append(f"navigation graph contains secret argument: {token}")

if app_container.is_file():
    text = app_container.read_text(encoding="utf-8")
    for token in (
        "val provisioningQrFrameDecoderFactory: ProvisioningQrFrameDecoderFactory",
        "MlKitProvisioningQrFrameDecoderFactory()",
    ):
        if token not in text:
            errors.append(f"production QR decoder composition is incomplete: {token}")

for path in (production, smoke):
    if path.is_file():
        text = path.read_text(encoding="utf-8")
        if text.count("DefaultProvisioningDiscoveryOperations.create") < 2:
            errors.append(
                f"production/smoke discovery bindings are incomplete in {path.relative_to(ROOT)}"
            )

if smoke.is_file():
    text = smoke.read_text(encoding="utf-8")
    for token in (
        "override val provisioningQrFrameDecoderFactory",
        "MlKitProvisioningQrFrameDecoderFactory()",
    ):
        if token not in text:
            errors.append(f"release-smoke QR decoder parity is incomplete: {token}")

if view_model_test.is_file():
    text = view_model_test.read_text(encoding="utf-8")
    for token in (
        "FakeProvisioningDiscoveryOperations",
        "nearby scan renders application candidates through one discovery boundary",
        "verified QR opens WiFi with encrypted secret reference and candidate",
        "assertFalse(event.result.toString().contains(\"claim-1\"))",
        "registered QR without setup candidate discards secret and remains blocked",
        "scan again discards pending QR secret before resetting",
        "override fun discardQrPayload",
        "discardedReferences",
    ):
        if token not in text:
            errors.append(f"provisioning discovery ViewModel coverage is missing: {token}")

if mapper_test.is_file():
    text = mapper_test.read_text(encoding="utf-8")
    for token in (
        "BLE candidate maps every discovery field into application snapshot",
        "QR payload maps identity and encrypted secret reference without claim data",
        "assertFalse(mapped.toString().contains(source.claimCode))",
    ):
        if token not in text:
            errors.append(f"provisioning discovery mapping coverage is missing: {token}")

if secret_test.is_file():
    text = secret_test.read_text(encoding="utf-8")
    for token in (
        "encryptedSecretSurvivesStoreRecreationWithoutPlaintextClaimData",
        "anotherOwnerCannotReadOrDeleteTheSecret",
        "expiredSecretFailsClosedAfterProcessRecreation",
        "aql_provisioning_qr_secrets.xml",
    ):
        if token not in text:
            errors.append(f"encrypted QR secret instrumentation coverage is missing: {token}")

if errors:
    print("Provisioning discovery application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Provisioning discovery application boundary guard passed.")
