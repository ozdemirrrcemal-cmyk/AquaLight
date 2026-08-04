#!/usr/bin/env python3
"""Protect product-scoped OTA channel resolution and the UI/data ownership boundary."""
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
RESOLVER = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareChannelManifestResolver.kt"
HTTP_SOURCE = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestHttpSource.kt"
RUNTIME_CONTRACT = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeContract.kt"
PLANNER = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt"
LEGACY_CONFIG = SOURCE / "application/devices/DeviceFirmwareManifestConfig.kt"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required OTA contract file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


app_gradle = read(APP_GRADLE)
application = read(APPLICATION)
owner_factory = read(OWNER_FACTORY)
ui_update = read(UI_UPDATE)
ui_settings = read(UI_SETTINGS)
adapter = read(ADAPTER)
resolver = read(RESOLVER)
http_source = read(HTTP_SOURCE)
runtime_contract = read(RUNTIME_CONTRACT)
planner = read(PLANNER)

if LEGACY_CONFIG.exists():
    errors.append(
        f"{LEGACY_CONFIG.relative_to(ROOT)}: global manifest URL configuration is forbidden"
    )

for path, text in (
    (APP_GRADLE, app_gradle),
    (APPLICATION, application),
    (OWNER_FACTORY, owner_factory),
    (UI_UPDATE, ui_update),
    (UI_SETTINGS, ui_settings),
):
    for forbidden in (
        "manifestUrl",
        "ManifestUrl",
        "DEVICE_FIRMWARE_MANIFEST_URL",
        "AQL_OTA_MANIFEST_URL",
        "AQL_OTA_STABLE_MANIFEST_URL",
        "AQL_OTA_DEBUG_MANIFEST_URL",
        "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
        "raw.githubusercontent.com",
        "releases/latest/download",
    ):
        if forbidden in text:
            errors.append(
                f"{path.relative_to(ROOT)}: OTA source ownership leaked outside data layer: {forbidden}"
            )

for token in (
    "enum class DeviceFirmwareChannel",
    'STABLE("stable")',
    'BETA("beta")',
    'DEV("dev")',
    "channel: DeviceFirmwareChannel = DeviceFirmwareChannel.STABLE",
):
    if token not in application:
        errors.append(
            f"{APPLICATION.relative_to(ROOT)}: typed OTA channel contract is missing: {token}"
        )

for token in (
    "DeviceFirmwareChannelManifestResolver",
    "channelManifestResolver.resolve(snapshot, channel)",
    "coordinator.checkAvailability",
):
    if token not in adapter:
        errors.append(f"{ADAPTER.relative_to(ROOT)}: data-owned channel resolution is missing: {token}")

for token in (
    "snapshot.hasValidatedRuntimeMetadata",
    "snapshot.product.productKey",
    "lowercase(Locale.ROOT)",
    "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
):
    if token not in resolver:
        errors.append(f"{RESOLVER.relative_to(ROOT)}: authenticated channel resolver token is missing: {token}")

for forbidden in (
    "snapshot.title",
    "customName",
    "snapshot.product.model",
    "snapshot.product.displayName",
):
    if forbidden in resolver:
        errors.append(f"{RESOLVER.relative_to(ROOT)}: mutable/display identity is forbidden: {forbidden}")

for token in (
    "raw.githubusercontent.com",
    "main/channels/",
    "Signed OTA manifest channel differs from its official channel path.",
    "Signed OTA manifest product differs from its official channel path.",
    "manifest.artifacts.size == 1",
):
    if token not in http_source and token not in runtime_contract:
        errors.append(f"Product channel HTTP contract token is missing: {token}")

for token in (
    "A product OTA channel manifest must contain exactly one artifact.",
    'val expectedTag = "${artifact.env}-v${manifest.version}"',
    'val expectedFilename = "AquaLight-${artifact.env}-v${manifest.version}-ota.bin"',
    "No compatible OTA artifact found",
):
    if token not in planner:
        errors.append(f"{PLANNER.relative_to(ROOT)}: fail-closed product planner token is missing: {token}")

main_sources = list(SOURCE.rglob("*.kt"))
for path in main_sources:
    text = path.read_text(encoding="utf-8", errors="ignore")
    for forbidden in (
        "OFFICIAL_LATEST_RELEASE_URL_PREFIX",
        "releases/latest/download/manifest-",
        "manifest-stable.json",
        "manifest-beta.json",
        "manifest-dev.json",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: global/latest OTA source is forbidden: {forbidden}")

for path in (UI_UPDATE, UI_SETTINGS):
    text = path.read_text(encoding="utf-8", errors="ignore")
    constructor = re.search(r"class\s+\w+ViewModel\s*\((.*?)\)\s*:\s*ViewModel", text, re.DOTALL)
    if constructor and re.search(r"String\s*=.*manifest", constructor.group(1), re.IGNORECASE):
        errors.append(f"{path.relative_to(ROOT)}: ViewModel constructor contains an OTA source string")

if errors:
    print("Product-scoped OTA client guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Product-scoped OTA client guard passed.")
