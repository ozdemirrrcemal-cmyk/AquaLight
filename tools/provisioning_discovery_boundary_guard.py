#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"

contract = APP / "application/devices/provisioning/ProvisioningDiscoveryOperations.kt"
adapter = APP / "data/devices/provisioning/DefaultProvisioningDiscoveryOperations.kt"
nearby_vm = APP / "ui/tabs/devices/add/DeviceAddViewModel.kt"
qr_vm = APP / "ui/tabs/devices/add/DeviceQrScanViewModel.kt"
qr_fragment = APP / "ui/tabs/devices/add/DeviceQrScanFragment.kt"
qr_decoder = APP / "platform/vision/ProvisioningQrFrameDecoder.kt"
app_container = APP / "composition/AppContainer.kt"
production = APP / "composition/AquaViewModelFactory.kt"
smoke = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/Stage3SmokeAppContainer.kt"
view_model_test = TESTS / "ui/tabs/devices/add/ProvisioningDiscoveryViewModelBoundaryTest.kt"
mapper_test = TESTS / "data/devices/provisioning/DefaultProvisioningDiscoveryOperationsMapperTest.kt"

required = (
    contract,
    adapter,
    nearby_vm,
    qr_vm,
    qr_fragment,
    qr_decoder,
    app_container,
    production,
    smoke,
    view_model_test,
    mapper_test,
)

errors: list[str] = []

for path in required:
    if not path.is_file():
        errors.append(f"missing required provisioning discovery file: {path.relative_to(ROOT)}")

if contract.is_file():
    text = contract.read_text(encoding="utf-8")
    if "package com.aqua.aqualight.application.devices.provisioning" not in text:
        errors.append("provisioning discovery contract is outside the application layer")
    for token in ("import android.", "import com.aqua.aqualight.data.", "DeviceUid"):
        if token in text:
            errors.append(f"application discovery contract leaks implementation type: {token}")
    for token in (
        "interface ProvisioningDiscoveryOperations",
        "data class ProvisioningCandidateSnapshot",
        "data class ProvisioningQrPayload",
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
        "toApplicationSnapshot",
        "toApplicationPayload",
    ):
        if token not in text:
            errors.append(f"provisioning discovery adapter is missing: {token}")

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
    ):
        if token in text:
            errors.append(f"{path.relative_to(ROOT)} contains forbidden discovery dependency: {token}")

if qr_decoder.is_file():
    text = qr_decoder.read_text(encoding="utf-8")
    for token in (
        "package com.aqua.aqualight.platform.vision",
        "interface ProvisioningQrFrameDecoder",
        "interface ProvisioningQrFrameDecoderFactory",
        "class MlKitProvisioningQrFrameDecoderFactory",
        "BarcodeScanning.getClient",
        "Barcode.FORMAT_QR_CODE",
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
    ):
        if token not in text:
            errors.append(f"QR Fragment is missing platform decoder behavior: {token}")
    for token in (
        "com.google.mlkit",
        "BarcodeScanner",
        "BarcodeScannerOptions",
        "BarcodeScanning",
        "InputImage",
        "ExperimentalGetImage",
    ):
        if token in text:
            errors.append(f"QR Fragment contains forbidden ML Kit implementation dependency: {token}")

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
        "verified QR opens WiFi with application payload and candidate",
        "registered QR without setup candidate remains blocked",
    ):
        if token not in text:
            errors.append(f"provisioning discovery ViewModel coverage is missing: {token}")

if mapper_test.is_file():
    text = mapper_test.read_text(encoding="utf-8")
    for token in (
        "BLE candidate maps every discovery field into application snapshot",
        "QR payload maps identity and claim fields without data types",
    ):
        if token not in text:
            errors.append(f"provisioning discovery mapping coverage is missing: {token}")

if errors:
    print("Provisioning discovery application boundary guard failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Provisioning discovery application boundary guard passed.")
