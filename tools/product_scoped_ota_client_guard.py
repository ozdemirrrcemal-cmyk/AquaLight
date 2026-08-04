#!/usr/bin/env python3
"""Protect the final authenticated, typed product-scoped OTA client boundary."""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
APP_GRADLE = ROOT / "app/build.gradle"
APPLICATION = SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt"
OWNER_FACTORY = SOURCE / "composition/OwnerViewModelFactory.kt"
UI_UPDATE = SOURCE / "ui/tabs/devices/detail/update/DeviceFirmwareUpdateViewModel.kt"
UI_SETTINGS = SOURCE / "ui/tabs/devices/detail/settings/DeviceFamilySettingsViewModel.kt"
ADAPTER = SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt"
COORDINATOR = SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt"
REPOSITORY = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdateRepository.kt"
RESOLVER = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareChannelManifestResolver.kt"
HTTP_SOURCE = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestHttpSource.kt"
MODELS = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareModels.kt"
STATUS_PARSER = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt"
PLANNER = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt"
LEGACY_CONFIG = SOURCE / "application/devices/DeviceFirmwareManifestConfig.kt"
LEGACY_INSECURE_FIELD = "allow" + "InsecureHttp"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required OTA contract file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


texts = {path: read(path) for path in (
    APP_GRADLE, APPLICATION, OWNER_FACTORY, UI_UPDATE, UI_SETTINGS, ADAPTER,
    COORDINATOR, REPOSITORY, RESOLVER, HTTP_SOURCE, MODELS, STATUS_PARSER, PLANNER,
)}

if LEGACY_CONFIG.exists():
    errors.append(f"{LEGACY_CONFIG.relative_to(ROOT)}: global manifest URL configuration is forbidden")

for path in (APP_GRADLE, APPLICATION, OWNER_FACTORY, UI_UPDATE, UI_SETTINGS):
    text = texts[path]
    for forbidden in (
        "manifestUrl", "ManifestUrl", "DEVICE_FIRMWARE_MANIFEST_URL",
        "AQL_OTA_MANIFEST_URL", "AQL_OTA_STABLE_MANIFEST_URL",
        "AQL_OTA_DEBUG_MANIFEST_URL", "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
        "raw.githubusercontent.com", "releases/latest/download",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: OTA source ownership leaked: {forbidden}")

for path in (ADAPTER, COORDINATOR, REPOSITORY):
    if "manifestUrl" in texts[path]:
        errors.append(f"{path.relative_to(ROOT)}: raw manifest URL API is forbidden")

for token in (
    "enum class DeviceFirmwareChannel", 'STABLE("stable")', 'BETA("beta")', 'DEV("dev")',
    "channel: DeviceFirmwareChannel = DeviceFirmwareChannel.STABLE",
):
    if token not in texts[APPLICATION]:
        errors.append(f"{APPLICATION.relative_to(ROOT)}: typed channel contract missing: {token}")

for token in (
    "coordinator.checkAvailability", "channel = channel",
):
    if token not in texts[ADAPTER]:
        errors.append(f"{ADAPTER.relative_to(ROOT)}: typed coordinator call missing: {token}")
for forbidden in ("DeviceFirmwareChannelManifestResolver", "currentDevice(uid)"):
    if forbidden in texts[ADAPTER]:
        errors.append(f"{ADAPTER.relative_to(ROOT)}: channel/snapshot resolution escaped coordinator: {forbidden}")

for token in (
    "channel: DeviceFirmwareChannel", "connectRuntime(deviceUid).getOrThrow()",
    "snapshot.hasValidatedRuntimeMetadata", "runtimeMetadataGeneration == metadataGeneration",
):
    if token not in texts[COORDINATOR]:
        errors.append(f"{COORDINATOR.relative_to(ROOT)}: authenticated typed state boundary missing: {token}")

for token in (
    "channelManifestResolver.resolve(snapshot, channel)",
    "fetchManifest(snapshot, channel)",
    "channel: DeviceFirmwareChannel",
):
    if token not in texts[REPOSITORY]:
        errors.append(f"{REPOSITORY.relative_to(ROOT)}: data-owned channel resolution missing: {token}")

for token in (
    "snapshot.hasValidatedRuntimeMetadata", "snapshot.product.productKey",
    "lowercase(Locale.ROOT)", "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
):
    if token not in texts[RESOLVER]:
        errors.append(f"{RESOLVER.relative_to(ROOT)}: authenticated resolver token missing: {token}")

for token in (
    "raw.githubusercontent.com", "main/channels/",
    "Signed OTA manifest channel differs from its official channel path.",
    "Signed OTA manifest product differs from its official channel path.",
):
    if token not in texts[HTTP_SOURCE] and token not in read(SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeContract.kt"):
        errors.append(f"Product channel HTTP contract token missing: {token}")

for token in (
    "isExactFirmwareVersion", "exactFirmwareVersionPartsOrNull",
    "Invalid exact X.Y.Z firmware version",
):
    if token not in texts[MODELS] and token not in texts[PLANNER]:
        errors.append(f"Strict firmware version contract missing: {token}")

for source_path in SOURCE.rglob("*.kt"):
    source_text = source_path.read_text(encoding="utf-8", errors="ignore")
    if LEGACY_INSECURE_FIELD in source_text:
        errors.append(f"{source_path.relative_to(ROOT)}: removed insecure transport field remains")
    for forbidden in (
        "OFFICIAL_LATEST_RELEASE_URL_PREFIX", "releases/latest/download/manifest-",
        "manifest-stable.json", "manifest-beta.json", "manifest-dev.json",
    ):
        if forbidden in source_text:
            errors.append(f"{source_path.relative_to(ROOT)}: global/latest OTA source remains: {forbidden}")

for path in (UI_UPDATE, UI_SETTINGS):
    constructor = re.search(r"class\s+\w+ViewModel\s*\((.*?)\)\s*:\s*ViewModel", texts[path], re.DOTALL)
    if constructor and re.search(r"String\s*=.*manifest", constructor.group(1), re.IGNORECASE):
        errors.append(f"{path.relative_to(ROOT)}: ViewModel constructor contains OTA source string")

if errors:
    print("Product-scoped OTA client guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Product-scoped OTA client guard passed.")
