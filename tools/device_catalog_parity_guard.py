#!/usr/bin/env python3
"""Fail closed when the Android device catalog drifts from the pinned firmware contract."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
CONTRACT_PATH = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlDeviceCatalogContract.kt"
MAPPING_PATH = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/DeviceApplicationMapping.kt"
RESOLVER_PATH = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/DeviceRootMenuFeatureResolver.kt"

FIXTURE_SHA256 = "f98d0543dd4efabedb562fa8d34fd85aab68991e000bc0d916d173ce8b9f296d"
FIRMWARE_MERGE_COMMIT = "2ac54677f19b270f970894b21880ab99753ac7f4"

CAPABILITY_KEYS = {
    "light",
    "manualLight",
    "lightProgram",
    "lightPresets",
    "lightSimulation",
    "fan",
    "cooling",
    "temperature",
    "standaloneTimer",
    "dosing",
    "timeSync",
    "ota",
}
LIMIT_KEYS = {
    "lightChannelCount",
    "fanOutputCount",
    "temperatureSensorCount",
    "timerChannelCount",
    "dosingChannelCount",
}
FAMILIES = {"light", "timer", "dosing", "cooling"}
FAMILY_MENU_FEATURES = {
    "light": {
        "LIGHT_MANUAL",
        "LIGHT_QUICK_SETUP",
        "LIGHT_PROGRAMS",
        "LIGHT_PRESETS",
        "COOLING_FANS",
        "COOLING_TEMPERATURE",
        "DEVICE_SETTINGS",
    },
    "timer": {"TIMER_CHANNELS", "TIMER_SCHEDULES", "DEVICE_SETTINGS"},
    "dosing": {
        "DOSING_CHANNELS",
        "DOSING_CALIBRATION",
        "DOSING_SCHEDULES",
        "DEVICE_SETTINGS",
    },
    "cooling": {"COOLING_FANS", "COOLING_TEMPERATURE", "DEVICE_SETTINGS"},
}
LEGACY_ALIAS_TOKENS = {
    '"channels"',
    '"settings"',
    '"quick_setup"',
    '"programList"',
    '"program_list"',
    '"singleDose"',
    '"hourly24"',
    '"customPeriods"',
    '"timerMode"',
    ".lowercase()",
}

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        fail(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def enum_wire_values(source: str, enum_name: str) -> set[str]:
    match = re.search(
        rf"enum class {re.escape(enum_name)}\(val wireValue: String\) \{{(.*?)\n\}}",
        source,
        flags=re.DOTALL,
    )
    if match is None:
        fail(f"{enum_name} declaration is missing")
        return set()
    return set(
        re.findall(
            r'^[ \t]+[A-Z0-9_]+\("([A-Z0-9_]+)"\)',
            match.group(1),
            flags=re.MULTILINE,
        )
    )


fixture_text = read_text(FIXTURE_PATH)
contract_text = read_text(CONTRACT_PATH)
mapping_text = read_text(MAPPING_PATH)
resolver_text = read_text(RESOLVER_PATH)

if fixture_text:
    actual_sha = hashlib.sha256(fixture_text.encode("utf-8")).hexdigest()
    require(
        actual_sha == FIXTURE_SHA256,
        "catalog fixture checksum drifted; regenerate from the pinned firmware catalog",
    )

try:
    fixture = json.loads(fixture_text)
except (json.JSONDecodeError, TypeError) as exc:
    fail(f"catalog fixture is not valid JSON: {exc}")
    fixture = {}

require(fixture.get("fixtureVersion") == 1, "catalog fixtureVersion must remain 1")
require(
    fixture.get("schema") == "aql.product.catalog.fixture.v1",
    "catalog fixture schema is incompatible",
)
source = fixture.get("source")
require(isinstance(source, dict), "catalog source metadata is missing")
if isinstance(source, dict):
    require(
        source.get("repository") == "ozdemirrrcemal-cmyk/AquaLight-Firmware",
        "firmware repository pin drifted",
    )
    require(source.get("commit") == FIRMWARE_MERGE_COMMIT, "firmware merge commit pin drifted")
    require(
        source.get("catalogPath") == "src/product/AqlProductCatalog.hpp",
        "firmware catalog path drifted",
    )
    require(
        source.get("exporterPath") == "tools/aql_export_product_catalog.cpp",
        "firmware exporter path drifted",
    )
    require(
        source.get("otaManifestSchema") == "aql.ota.manifest.v1",
        "OTA manifest schema drifted",
    )

feature_values = enum_wire_values(contract_text, "AqlDeviceFeatureKey")
screen_values = enum_wire_values(contract_text, "AqlDeviceScreenKey")
require("value.trim()" not in contract_text, "wire keys must not be trimmed")
require(".lowercase()" not in contract_text, "wire keys must remain case-sensitive")
require(
    "AqlCatalogKeySet.Invalid" in contract_text,
    "unknown catalog keys must have an explicit invalid result",
)

profiles = fixture.get("profiles")
products = fixture.get("products")
require(
    isinstance(profiles, dict) and len(profiles) == 5,
    "catalog must contain exactly five shared capability profiles",
)
require(
    isinstance(products, list) and len(products) == 9,
    "catalog must contain exactly nine commercial products",
)

seen_envs: set[str] = set()
seen_product_keys: set[str] = set()
seen_product_ids: set[str] = set()
seen_models: set[str] = set()
seen_sku_ids: set[str] = set()
seen_sku_codes: set[str] = set()

if isinstance(profiles, dict):
    for profile_name, profile in profiles.items():
        if not isinstance(profile, dict):
            fail(f"profile {profile_name} must be an object")
            continue
        capabilities = profile.get("capabilities")
        features = profile.get("supportedFeatures")
        screens = profile.get("supportedScreens")
        expected_menu = profile.get("expectedMenuFeatures")
        require(
            isinstance(capabilities, dict) and set(capabilities) == CAPABILITY_KEYS,
            f"profile {profile_name} capability keys drifted",
        )
        if isinstance(capabilities, dict):
            require(
                all(type(value) is bool for value in capabilities.values()),
                f"profile {profile_name} capabilities must be booleans",
            )
        require(
            isinstance(features, list) and len(features) == len(set(features)),
            f"profile {profile_name} feature keys must be unique",
        )
        require(
            isinstance(screens, list) and len(screens) == len(set(screens)),
            f"profile {profile_name} screen keys must be unique",
        )
        require(
            isinstance(expected_menu, list) and len(expected_menu) == len(set(expected_menu)),
            f"profile {profile_name} menu keys must be unique",
        )
        if isinstance(features, list):
            require(
                set(features).issubset(feature_values),
                f"profile {profile_name} contains an untyped firmware feature key",
            )
        if isinstance(screens, list):
            require(
                set(screens).issubset(screen_values),
                f"profile {profile_name} contains an untyped firmware screen key",
            )

if isinstance(products, list) and isinstance(profiles, dict):
    for product in products:
        if not isinstance(product, dict):
            fail("every product entry must be an object")
            continue
        profile_name = product.get("profile")
        profile = profiles.get(profile_name)
        env_name = product.get("env")
        product_key = product.get("productKey")
        product_id = product.get("productId")
        family = product.get("family")
        model = product.get("model")
        sku_id = product.get("skuId")
        sku_code = product.get("skuCode")
        limits = product.get("limits")

        require(profile is not None, f"product {product_key} references an unknown profile")
        require(family in FAMILIES, f"product {product_key} has an unsupported family")
        require(
            isinstance(env_name, str) and env_name == str(product_key).lower(),
            f"product {product_key} environment name drifted",
        )
        require(
            isinstance(product_id, str) and product_id.startswith(f"com.aqualight.{family}."),
            f"product {product_key} productId drifted",
        )
        require(isinstance(model, str) and bool(model), f"product {product_key} model is required")
        require(
            product.get("hardwareRevision") == "2.0",
            f"product {product_key} hardwareRevision drifted",
        )
        require(
            isinstance(limits, dict) and set(limits) == LIMIT_KEYS,
            f"product {product_key} limit keys drifted",
        )
        if isinstance(limits, dict):
            require(
                all(type(value) is int and value >= 0 for value in limits.values()),
                f"product {product_key} limits must be non-negative integers",
            )

        for value, seen, label in (
            (env_name, seen_envs, "environment"),
            (product_key, seen_product_keys, "productKey"),
            (product_id, seen_product_ids, "productId"),
            (model, seen_models, "model"),
            (sku_id, seen_sku_ids, "skuId"),
            (sku_code, seen_sku_codes, "skuCode"),
        ):
            require(
                isinstance(value, str) and value not in seen,
                f"duplicate or missing {label}: {value}",
            )
            if isinstance(value, str):
                seen.add(value)

        if not isinstance(limits, dict) or profile is None:
            continue
        capabilities = profile["capabilities"]
        expected_menu = set(profile["expectedMenuFeatures"])
        require(
            expected_menu.issubset(FAMILY_MENU_FEATURES[family]),
            f"product {product_key} exposes cross-family menu features",
        )

        if family == "light":
            require(
                limits["lightChannelCount"] > 0 and capabilities["light"],
                f"light product {product_key} lacks light channels/capability",
            )
            require(
                limits["timerChannelCount"] == 0 and limits["dosingChannelCount"] == 0,
                f"light product {product_key} leaks timer/dosing limits",
            )
        elif family == "timer":
            require(
                limits["timerChannelCount"] > 0 and capabilities["standaloneTimer"],
                f"timer product {product_key} lacks timer channels/capability",
            )
            require(not capabilities["dosing"], f"timer product {product_key} leaks dosing capability")
        elif family == "dosing":
            require(
                limits["dosingChannelCount"] > 0 and capabilities["dosing"],
                f"dosing product {product_key} lacks dosing channels/capability",
            )
            require(
                not capabilities["standaloneTimer"],
                f"dosing product {product_key} must not expose standalone timer",
            )
            require(
                "TIMER_CHANNELS" not in expected_menu and "TIMER_SCHEDULES" not in expected_menu,
                f"dosing product {product_key} exposes timer menu",
            )
        elif family == "cooling":
            require(
                limits["fanOutputCount"] > 0 and limits["temperatureSensorCount"] > 0,
                f"cooling product {product_key} lacks fan/sensor limits",
            )
            require(
                capabilities["cooling"] and capabilities["fan"] and capabilities["temperature"],
                f"cooling product {product_key} lacks cooling capabilities",
            )

for token in LEGACY_ALIAS_TOKENS:
    require(
        token not in mapping_text and token not in resolver_text,
        f"legacy/permissive menu alias is forbidden: {token}",
    )
require("when (product.family)" in resolver_text, "menu resolution must be family-scoped")
require("DeviceFamily.UNKNOWN -> emptySet()" in resolver_text, "unknown families must fail closed")
require(
    "capabilities.standaloneTimer &&" in resolver_text,
    "timer menus must require standalone timer capability",
)
require("capabilities.dosing &&" in resolver_text, "dosing menus must require dosing capability")

if errors:
    print("Device catalog parity guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device catalog parity guard passed.")
