#!/usr/bin/env python3
"""Protect exact catalog identity and family-scoped Android routing."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
CATALOG_MODELS = SOURCE / "data/devices/catalog/AqlCommercialCatalogModels.kt"
GENERATED_CATALOG = SOURCE / "data/devices/catalog/AqlGeneratedCommercialCatalog.kt"
CATALOG = SOURCE / "data/devices/catalog/AqlCommercialDeviceCatalog.kt"
RESOLVER = SOURCE / "data/devices/DeviceRootMenuFeatureResolver.kt"
ROUTE_POLICY = SOURCE / "data/devices/DeviceRootRoutePolicy.kt"
MAPPING = SOURCE / "data/devices/DeviceRootSnapshotMapping.kt"
ROOT_CONTRACT = SOURCE / "application/devices/DeviceRootOperations.kt"
ROOT_ROUTING = SOURCE / "application/devices/DeviceRootRouting.kt"
UI_MAPPER = SOURCE / "ui/tabs/devices/detail/common/DeviceRootMenuMapper.kt"
MENU_ACCESS = SOURCE / "data/devices/menu/CommercialDeviceMenuAccessOperations.kt"
DEFAULT_MENU_ACCESS = SOURCE / "data/devices/menu/DefaultDeviceMenuAccessOperations.kt"
MENU_BOUNDARY = SOURCE / "application/devices/DeviceMenuAccessOperations.kt"
GENERATOR = ROOT / "tools/generate_android_commercial_catalog.py"

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


catalog_models = read(CATALOG_MODELS)
generated_catalog = read(GENERATED_CATALOG)
catalog = read(CATALOG)
resolver = read(RESOLVER)
route_policy = read(ROUTE_POLICY)
mapping = read(MAPPING)
root_contract = read(ROOT_CONTRACT)
root_routing = read(ROOT_ROUTING)
ui_mapper = read(UI_MAPPER)
menu_access = read(MENU_ACCESS)
default_menu_access = read(DEFAULT_MENU_ACCESS)
menu_boundary = read(MENU_BOUNDARY)

check = subprocess.run(
    [sys.executable, str(GENERATOR), "--check"],
    cwd=ROOT,
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    check=False,
)
require(
    check.returncode == 0,
    f"generated catalog is stale:\nstdout={check.stdout}\nstderr={check.stderr}",
)

require("// GENERATED FILE. DO NOT EDIT." in generated_catalog, "generated catalog header is missing")
require(
    "cf2222e58e6c69a729071a5d1205497b3fceaa70" in generated_catalog,
    "generated catalog must remain pinned to the firmware merge commit",
)
require(
    generated_catalog.count("    AqlCommercialCatalogProduct(") == 9,
    "generated catalog must contain exactly nine product rows",
)
for token in (
    "data class AqlCommercialCatalogProfile",
    "data class AqlCommercialCatalogProduct",
    "val compatibilityIdentity: DeviceCompatibilityIdentity",
):
    require(token in catalog_models, f"commercial catalog model token is missing: {token}")

for token in (
    "private val productsByIdentity",
    "fun validate(metadata: DeviceRuntimeMetadata)",
    "fun validateSnapshot(snapshot: DeviceSnapshot)",
    "UNKNOWN_COMPATIBILITY_IDENTITY",
    "FAMILY_MISMATCH",
    "CAPABILITIES_MISMATCH",
    "LIMITS_MISMATCH",
    "FEATURES_MISMATCH",
    "SCREENS_MISMATCH",
    "reported.compatibilityIdentity",
    "reported.supportedFeatures != product.profile.supportedFeatures",
    "reported.supportedScreens != product.profile.supportedScreens",
):
    require(token in catalog, f"exact catalog validator token is missing: {token}")
for forbidden in (
    ".trim()",
    ".lowercase()",
    "ifBlank",
    "startsWith(product",
    "contains(product",
):
    require(forbidden not in catalog, f"catalog validator must not normalize or approximate: {forbidden}")

require(
    "fun resolve(product: AqlCommercialCatalogProduct)" in resolver,
    "menu resolver must accept only a validated catalog product",
)
require("when (product.family)" in resolver, "menu resolution must be family-scoped")
require("DeviceFamily.UNKNOWN -> emptySet()" in resolver, "unknown family must fail closed")
for token in (
    "limits.lightChannelCount > 0",
    "limits.timerChannelCount > 0",
    "limits.dosingChannelCount > 0",
    "limits.fanOutputCount > 0",
    "limits.temperatureSensorCount > 0",
    "capabilities.standaloneTimer",
    "capabilities.dosing",
):
    require(token in resolver, f"family menu resolver validation token is missing: {token}")
for forbidden in (
    "DeviceSnapshot",
    "product.model",
    "expectedMenuFeatureNames",
    ".trim()",
    ".lowercase()",
    '"channels"',
    '"settings"',
):
    require(forbidden not in resolver, f"menu resolver contains forbidden coupling: {forbidden}")

for token in (
    "fun allowedRoutes(product: AqlCommercialCatalogProduct)",
    "fun authorize(",
    "route in allowedRoutes(product)",
):
    require(token in route_policy, f"second-stage route policy token is missing: {token}")
require("enum class DeviceRootRoute" in root_routing, "typed root routes are missing")
require("DeviceRootCatalogState" in root_contract, "root catalog state is missing")
require("val allowedRoutes: Set<DeviceRootRoute>" in root_contract, "root allowed routes are missing")

for token in (
    "AqlCommercialDeviceCatalog.validateSnapshot(this)",
    "DeviceRootCatalogState.VALID",
    "DeviceRootCatalogState.INVALID",
    "DeviceRootMenuFeatureResolver.resolve(product)",
    "DeviceRootRoutePolicy.allowedRoutes(product)",
):
    require(token in mapping, f"fail-closed root projection token is missing: {token}")

require("listOfNotNull(" in ui_mapper, "unsupported menu items must be absent")
require("val enabled: Boolean" not in ui_mapper, "disabled menu placeholders are forbidden")
require("filter(DeviceRootMenuItemUi::enabled)" not in ui_mapper, "UI must not filter disabled placeholders")

for token in (
    "class CommercialDeviceMenuAccessOperations",
    "AqlCommercialDeviceCatalog.validateSnapshot(snapshot)",
    "COMMERCIAL_PRODUCT_MISMATCH",
    "validation.product.family.toOwnerDeviceFamily()",
):
    require(token in menu_access, f"commercial menu access token is missing: {token}")
require(
    "fun create(devicesRepository: DevicesRepository): DeviceMenuAccessOperations" in default_menu_access,
    "menu factory must return the composed application boundary",
)
require(
    "CommercialDeviceMenuAccessOperations(" in default_menu_access,
    "liveness access must be followed by commercial catalog validation",
)
require(
    "COMMERCIAL_PRODUCT_MISMATCH" in menu_boundary,
    "commercial mismatch reason is missing from the application boundary",
)

if errors:
    print("Commercial catalog runtime guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Commercial catalog runtime guard passed.")
