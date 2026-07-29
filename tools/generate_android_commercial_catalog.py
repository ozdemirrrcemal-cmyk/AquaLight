#!/usr/bin/env python3
"""Generate the Android commercial catalog from the pinned firmware fixture."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
OUTPUT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/catalog/"
    / "AqlGeneratedCommercialCatalog.kt"
)

CAPABILITY_ORDER = (
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
)
LIMIT_ORDER = (
    "lightChannelCount",
    "fanOutputCount",
    "temperatureSensorCount",
    "timerChannelCount",
    "dosingChannelCount",
)
FAMILY_ENUM = {
    "light": "LIGHT",
    "timer": "TIMER",
    "dosing": "DOSING",
    "cooling": "COOLING",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when the committed generated file differs from the fixture.",
    )
    return parser.parse_args()


def load_fixture() -> dict[str, Any]:
    return json.loads(FIXTURE_PATH.read_text(encoding="utf-8", errors="strict"))


def constant_name(profile_name: str) -> str:
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", profile_name).upper()
    return f"PROFILE_{words}"


def bool_literal(value: Any) -> str:
    if type(value) is not bool:
        raise ValueError("Catalog capabilities must be booleans.")
    return "true" if value else "false"


def enum_set(enum_name: str, values: list[str], indentation: str) -> list[str]:
    if not values:
        return [f"{indentation}emptySet()"]
    lines = [f"{indentation}setOf("]
    lines.extend(f"{indentation}    {enum_name}.{value}," for value in values)
    lines.append(f"{indentation})")
    return lines


def string_set(values: list[str], indentation: str) -> list[str]:
    if not values:
        return [f"{indentation}emptySet()"]
    lines = [f"{indentation}setOf("]
    lines.extend(f'{indentation}    "{value}",' for value in values)
    lines.append(f"{indentation})")
    return lines


def append_named_expression(
    lines: list[str],
    name: str,
    expression_lines: list[str],
    indentation: str = "    ",
) -> None:
    first, *remaining = expression_lines
    lines.append(f"{indentation}{name} = {first.lstrip()}")
    lines.extend(remaining)
    lines[-1] = f"{lines[-1]},"


def render_profile(lines: list[str], name: str, profile: dict[str, Any]) -> None:
    lines.append(f"private val {constant_name(name)} = AqlCommercialCatalogProfile(")
    lines.append("    capabilities = DeviceCapabilitySet(")
    capabilities = profile["capabilities"]
    for key in CAPABILITY_ORDER:
        lines.append(f"        {key} = {bool_literal(capabilities[key])},")
    lines.append("    ),")
    append_named_expression(
        lines,
        "supportedFeatures",
        enum_set("AqlDeviceFeatureKey", profile["supportedFeatures"], "    "),
    )
    append_named_expression(
        lines,
        "supportedScreens",
        enum_set("AqlDeviceScreenKey", profile["supportedScreens"], "    "),
    )
    append_named_expression(
        lines,
        "expectedMenuFeatureNames",
        string_set(profile["expectedMenuFeatures"], "    "),
    )
    lines.append(")")
    lines.append("")


def render_product(lines: list[str], product: dict[str, Any]) -> None:
    lines.append("    AqlCommercialCatalogProduct(")
    lines.append(f'        productKey = DeviceProductKey("{product["productKey"]}"),')
    lines.append(f'        productId = DeviceProductId("{product["productId"]}"),')
    lines.append(f'        family = DeviceFamily.{FAMILY_ENUM[product["family"]]},')
    lines.append(f'        line = DeviceProductLine("{product["line"]}"),')
    lines.append(f'        model = DeviceProductModel("{product["model"]}"),')
    lines.append(f'        displayName = "{product["displayName"]}",')
    lines.append(f'        skuId = DeviceSkuId("{product["skuId"]}"),')
    lines.append(f'        skuCode = DeviceSkuCode("{product["skuCode"]}"),')
    lines.append(
        f'        hardwareRevision = DeviceHardwareRevision("{product["hardwareRevision"]}"),'
    )
    lines.append("        limits = DeviceLimitSet(")
    limits = product["limits"]
    for key in LIMIT_ORDER:
        value = limits[key]
        if type(value) is not int or value < 0:
            raise ValueError("Catalog limits must be non-negative integers.")
        lines.append(f"            {key} = {value},")
    lines.append("        ),")
    lines.append(f"        profile = {constant_name(product['profile'])},")
    lines.append("    ),")


def render(fixture: dict[str, Any]) -> str:
    source = fixture["source"]
    lines = [
        "// GENERATED FILE. DO NOT EDIT.",
        "// Source: protocol/fixtures/aql_product_catalog_v1.json",
        f'// Source firmware commit: {source["commit"]}',
        "",
        "package com.aqua.aqualight.data.devices.catalog",
        "",
        "import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey",
        "import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey",
        "import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet",
        "import com.aqua.aqualight.data.devices.model.DeviceFamily",
        "import com.aqua.aqualight.data.devices.model.DeviceHardwareRevision",
        "import com.aqua.aqualight.data.devices.model.DeviceLimitSet",
        "import com.aqua.aqualight.data.devices.model.DeviceProductId",
        "import com.aqua.aqualight.data.devices.model.DeviceProductKey",
        "import com.aqua.aqualight.data.devices.model.DeviceProductLine",
        "import com.aqua.aqualight.data.devices.model.DeviceProductModel",
        "import com.aqua.aqualight.data.devices.model.DeviceSkuCode",
        "import com.aqua.aqualight.data.devices.model.DeviceSkuId",
        "",
    ]
    for name, profile in fixture["profiles"].items():
        render_profile(lines, name, profile)
    lines.append(
        "internal val AQL_GENERATED_COMMERCIAL_PRODUCTS: "
        "List<AqlCommercialCatalogProduct> = listOf("
    )
    for product in fixture["products"]:
        render_product(lines, product)
    lines.append(")")
    return "\n".join(lines) + "\n"


def main() -> int:
    args = parse_args()
    generated = render(load_fixture())
    if args.check:
        current = OUTPUT_PATH.read_text(encoding="utf-8", errors="strict")
        if current != generated:
            print(
                "Generated Android catalog differs from the firmware fixture. "
                "Run tools/generate_android_commercial_catalog.py.",
                file=sys.stderr,
            )
            return 1
        print("Generated Android commercial catalog is current.")
        return 0
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(generated, encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
