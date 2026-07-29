#!/usr/bin/env python3
"""Protect the device-root and firmware-update application boundaries."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"

ROOT_CONTRACT = SOURCE / "application/devices/DeviceRootOperations.kt"
FIRMWARE_CONTRACT = SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt"
ROOT_ADAPTER = SOURCE / "data/devices/DefaultDeviceRootOperations.kt"
FIRMWARE_ADAPTER = SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt"
MAPPING = SOURCE / "data/devices/DeviceRootSnapshotMapping.kt"
CAPABILITY_MAPPING = SOURCE / "data/devices/DeviceRootCapabilityMapping.kt"
MENU_RESOLVER = SOURCE / "data/devices/DeviceRootMenuFeatureResolver.kt"
ROUTE_POLICY = SOURCE / "data/devices/DeviceRootRoutePolicy.kt"
MENU_MAPPER = SOURCE / "ui/tabs/devices/detail/common/DeviceRootMenuMapper.kt"
PRESENTATION_MAPPER = SOURCE / "ui/tabs/devices/detail/common/DeviceRootPresentationMapper.kt"
FACTORY = SOURCE / "composition/OwnerViewModelFactory.kt"
SMOKE_FACTORY = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
TEST = ROOT / "app/src/test/java/com/aqua/aqualight/ui/tabs/devices/detail/DeviceRootViewModelBoundaryTest.kt"
ROOT_VIEW_MODELS = (
    SOURCE / "ui/tabs/devices/detail/common/DeviceRootOverviewViewModel.kt",
    SOURCE / "ui/tabs/devices/detail/light/DeviceLightRootViewModel.kt",
    SOURCE / "ui/tabs/devices/detail/cooling/DeviceCoolingRootViewModel.kt",
    SOURCE / "ui/tabs/devices/detail/timer/DeviceTimerRootViewModel.kt",
    SOURCE / "ui/tabs/devices/detail/dosing/DeviceDosingRootViewModel.kt",
)

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required device-root boundary file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


root_contract = read(ROOT_CONTRACT)
firmware_contract = read(FIRMWARE_CONTRACT)
root_adapter = read(ROOT_ADAPTER)
firmware_adapter = read(FIRMWARE_ADAPTER)
mapping = read(MAPPING)
capability_mapping = read(CAPABILITY_MAPPING)
menu_resolver = read(MENU_RESOLVER)
route_policy = read(ROUTE_POLICY)
menu_mapper = read(MENU_MAPPER)
presentation_mapper = read(PRESENTATION_MAPPER)
factory = read(FACTORY)
smoke_factory = read(SMOKE_FACTORY)
test = read(TEST)
view_models = {path: read(path) for path in ROOT_VIEW_MODELS}

for path, text, tokens in (
    (
        ROOT_CONTRACT,
        root_contract,
        (
            "interface DeviceRootOperations",
            "data class DeviceRootSnapshot",
            "enum class DeviceRootCapability",
            "enum class DeviceRootMenuFeature",
            "fun observe(deviceUid: String): Flow<DeviceRootSnapshot?>",
        ),
    ),
    (
        FIRMWARE_CONTRACT,
        firmware_contract,
        (
            "interface DeviceFirmwareUpdateOperations",
            "data class PreparedDeviceFirmwareUpdate",
            "data class DeviceFirmwareCommandResult",
            "suspend fun prepareUpdate",
            "fun startUpdate",
        ),
    ),
):
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: application boundary token is missing: {token}")
    if re.search(
        r"^import\s+(?:android(?:x)?\.|com\.google\.|com\.aqua\.aqualight\.(?:data|platform|ui|composition)\.)",
        text,
        re.MULTILINE,
    ):
        errors.append(f"{path.relative_to(ROOT)}: application boundary depends on an outer layer")

for path, text, tokens in (
    (
        ROOT_ADAPTER,
        root_adapter,
        (
            "class DefaultDeviceRootOperations",
            "devicesRepository.observeDevice",
            "toDeviceRootSnapshot",
            "devicesRepository.connectRuntime",
        ),
    ),
    (
        FIRMWARE_ADAPTER,
        firmware_adapter,
        (
            "class DefaultDeviceFirmwareUpdateOperations",
            "fetchAndPlanUpdate",
            "toApplicationPlan",
            "toDataPlan",
            "requestOtaStatus",
            "clearOtaStatus",
        ),
    ),
):
    for token in tokens:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: data adapter token is missing: {token}")

for token in (
    "fun DeviceSnapshot.toDeviceRootSnapshot",
    "AqlCommercialDeviceCatalog.validateSnapshot(this)",
    "DeviceRootMenuFeatureResolver.resolve(product)",
    "DeviceRootRoutePolicy.allowedRoutes(product)",
):
    if token not in mapping:
        errors.append(f"{MAPPING.relative_to(ROOT)}: root mapping token is missing: {token}")

for token in (
    "fun DeviceCapabilitySet.toRootCapabilities",
    "DeviceRootCapability.MANUAL_LIGHT",
    "DeviceRootCapability.OTA",
):
    if token not in capability_mapping:
        errors.append(
            f"{CAPABILITY_MAPPING.relative_to(ROOT)}: root capability mapping token is missing: {token}"
        )

for token in (
    "DeviceRootMenuFeature.LIGHT_MANUAL",
    "DeviceRootMenuFeature.DOSING_CHANNELS",
    "DeviceRootMenuFeature.TIMER_CHANNELS",
    "DeviceRootMenuFeature.COOLING_FANS",
    "fun resolve(product: AqlCommercialCatalogProduct)",
    "when (product.family)",
    "DeviceFamily.UNKNOWN -> emptySet()",
):
    if token not in menu_resolver:
        errors.append(
            f"{MENU_RESOLVER.relative_to(ROOT)}: root menu resolution token is missing: {token}"
        )

for token in (
    "fun allowedRoutes(product: AqlCommercialCatalogProduct)",
    "fun authorize(",
    "route in allowedRoutes(product)",
):
    if token not in route_policy:
        errors.append(f"{ROUTE_POLICY.relative_to(ROOT)}: route authorization token is missing: {token}")

for forbidden in (
    '"channels"',
    '"settings"',
    '"quick_setup"',
    ".lowercase()",
    "DeviceSnapshot",
    "product.model",
):
    if forbidden in menu_resolver:
        errors.append(
            f"{MENU_RESOLVER.relative_to(ROOT)}: permissive/model-specific menu resolution is forbidden: {forbidden}"
        )

for path, text in view_models.items():
    for forbidden in (
        "import com.aqua.aqualight.data.",
        "DevicesRepository",
        "DeviceSnapshot",
        "runtimeModules()",
        "connectRuntime(",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: device infrastructure leaked into UI: {forbidden}")
    if "DeviceRootOperations" not in text:
        errors.append(f"{path.relative_to(ROOT)}: ViewModel must receive DeviceRootOperations")

light_view_model = view_models[ROOT_VIEW_MODELS[1]]
for token in (
    "private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations",
    "firmwareUpdateOperations.prepareUpdate",
    "firmwareUpdateOperations.startUpdate",
    "firmwareUpdateOperations.requestStatus",
    "firmwareUpdateOperations.clearStatus",
):
    if token not in light_view_model:
        errors.append(f"{ROOT_VIEW_MODELS[1].relative_to(ROOT)}: firmware boundary wiring is missing: {token}")

for path, text in ((MENU_MAPPER, menu_mapper), (PRESENTATION_MAPPER, presentation_mapper)):
    if re.search(r"^import\s+com\.aqua\.aqualight\.data\.", text, re.MULTILINE):
        errors.append(f"{path.relative_to(ROOT)}: root presentation must consume application values")

for path, text in ((FACTORY, factory), (SMOKE_FACTORY, smoke_factory)):
    for token in (
        "DefaultDeviceRootOperations(",
        "DefaultDeviceFirmwareUpdateOperations(",
        "DeviceLightRootViewModel(",
        "DeviceCoolingRootViewModel(",
        "DeviceTimerRootViewModel(",
        "DeviceDosingRootViewModel(",
        "DeviceRootOverviewViewModel(",
    ):
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: production/smoke root binding is missing: {token}")

for token in (
    "FakeDeviceRootOperations",
    "FakeFirmwareUpdateOperations",
    "overview renders application root snapshot without repository models",
    "light root delegates ota preparation and commands through firmware boundary",
):
    if token not in test:
        errors.append(f"{TEST.relative_to(ROOT)}: fake-backed root coverage is missing: {token}")

if errors:
    print("Device root application boundary guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device root application boundary guard passed.")
