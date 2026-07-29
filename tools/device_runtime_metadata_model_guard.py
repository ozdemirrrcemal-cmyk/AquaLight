#!/usr/bin/env python3
"""Protect the strict Android runtime identity, capability, limit, and module models."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/model"
IDENTITY_PATH = MODEL_ROOT / "DeviceRuntimeIdentity.kt"
CAPABILITIES_PATH = MODEL_ROOT / "DeviceRuntimeCapabilities.kt"
MODULES_PATH = MODEL_ROOT / "DeviceRuntimeModules.kt"
METADATA_PATH = MODEL_ROOT / "DeviceRuntimeMetadata.kt"
FAMILY_PATH = MODEL_ROOT / "DeviceFamily.kt"

EXPECTED_MODULE_KEYS = {
    "light",
    "cooling",
    "temperature",
    "timerApi",
    "timerEngine",
    "dosing",
    "network",
    "discovery",
    "firmware",
    "system",
}

errors: list[str] = []


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def data_class_parameters(source: str, class_name: str) -> str:
    match = re.search(
        rf"data class {re.escape(class_name)}\((.*?)\n\)",
        source,
        flags=re.DOTALL,
    )
    if match is None:
        errors.append(f"{class_name} declaration is missing")
        return ""
    return match.group(1)


identity = read(IDENTITY_PATH)
capabilities = read(CAPABILITIES_PATH)
modules = read(MODULES_PATH)
metadata = read(METADATA_PATH)
family = read(FAMILY_PATH)

for token in (
    "value class DeviceProductKey",
    "value class DeviceProductId",
    "value class DeviceProductLine",
    "value class DeviceProductModel",
    "value class DeviceSkuId",
    "value class DeviceSkuCode",
    "value class DeviceHardwareRevision",
    "value class DeviceFirmwareVersion",
    "value class DeviceApiVersion",
    "value class DeviceProtocolVersion",
    "data class DeviceRuntimeIdentity",
    "data class DeviceCompatibilityIdentity",
    "val compatibilityIdentity: DeviceCompatibilityIdentity",
):
    require(token in identity, f"strict identity token is missing: {token}")

for forbidden in (".trim()", ".lowercase()", "DeviceFamily.UNKNOWN ->", "?: DeviceFamily"):
    require(forbidden not in identity, f"strict identity must not normalize or fall back: {forbidden}")

identity_parameters = data_class_parameters(identity, "DeviceRuntimeIdentity")
for field in (
    "deviceUid: DeviceUid",
    "productKey: DeviceProductKey",
    "productId: DeviceProductId",
    "family: DeviceFamily",
    "line: DeviceProductLine",
    "model: DeviceProductModel",
    "skuId: DeviceSkuId",
    "skuCode: DeviceSkuCode",
    "hardwareRevision: DeviceHardwareRevision",
    "firmwareVersion: DeviceFirmwareVersion",
    "apiVersion: DeviceApiVersion",
    "protocolVersion: DeviceProtocolVersion",
):
    require(field in identity_parameters, f"DeviceRuntimeIdentity field is missing: {field}")
require("=" not in identity_parameters, "DeviceRuntimeIdentity fields must not have implicit defaults")
require(
    "require(family != DeviceFamily.UNKNOWN)" in identity,
    "strict runtime identity must reject an unknown family",
)
require(
    "fun fromWireExact(value: String): DeviceFamily?" in family,
    "DeviceFamily must expose an exact commercial parser",
)

for class_name in ("DeviceCapabilitySet", "DeviceLimitSet", "DeviceRuntimeCapabilities"):
    parameters = data_class_parameters(capabilities, class_name)
    require("=" not in parameters, f"{class_name} fields must not have implicit defaults")

for field in (
    "light: Boolean",
    "manualLight: Boolean",
    "lightProgram: Boolean",
    "lightPresets: Boolean",
    "lightSimulation: Boolean",
    "fan: Boolean",
    "cooling: Boolean",
    "temperature: Boolean",
    "standaloneTimer: Boolean",
    "dosing: Boolean",
    "timeSync: Boolean",
    "ota: Boolean",
):
    require(field in capabilities, f"DeviceCapabilitySet field is missing: {field}")

for field in (
    "lightChannelCount: Int",
    "fanOutputCount: Int",
    "temperatureSensorCount: Int",
    "timerChannelCount: Int",
    "dosingChannelCount: Int",
):
    require(field in capabilities, f"DeviceLimitSet field is missing: {field}")
require(
    "Set<AqlDeviceFeatureKey>" in capabilities and "Set<AqlDeviceScreenKey>" in capabilities,
    "runtime capability metadata must use exact typed feature and screen sets",
)
require("JSONObject" not in capabilities, "runtime capability models must not parse JSON")

module_values = set(
    re.findall(r'^[ \t]+[A-Z0-9_]+\("([A-Za-z0-9]+)"\)', modules, flags=re.MULTILINE)
)
require(module_values == EXPECTED_MODULE_KEYS, "runtime module wire keys drifted from firmware")
require("fun fromWireExact(value: String)" in modules, "runtime module keys need an exact parser")
require("timerApi: Boolean" in modules, "standalone timer API state is missing")
require("timerEngine: Boolean" in modules, "internal timer engine state is missing")
require(
    "val exposesStandaloneTimerApi" in modules and "get() = timerApi" in modules,
    "standalone Timer exposure must depend only on timerApi",
)
require(
    "val usesInternalTimerEngine" in modules and "get() = timerEngine" in modules,
    "internal timer engine state must remain separately observable",
)
require(".trim()" not in modules and ".lowercase()" not in modules, "module keys must be exact")
require("List<String>" not in modules, "strict runtime modules must not degrade to string lists")

metadata_parameters = data_class_parameters(metadata, "DeviceRuntimeMetadata")
for field in (
    "identity: DeviceRuntimeIdentity",
    "capabilities: DeviceRuntimeCapabilities",
    "modules: DeviceRuntimeModules",
):
    require(field in metadata_parameters, f"DeviceRuntimeMetadata field is missing: {field}")
require("=" not in metadata_parameters, "DeviceRuntimeMetadata fields must not have defaults")

if errors:
    print("Device runtime metadata model guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device runtime metadata model guard passed.")
