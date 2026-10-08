#!/usr/bin/env python3
"""Fail production release unless every Quick Setup light has measured calibration metadata."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
REGISTRY = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/light/quicksetup/"
    / "DeviceLightProductionCalibrationRegistry.kt"
)

PROFILE_START = re.compile(
    r'DeviceLightMeasuredCalibrationProfile\(\s*'
    r'productKey\s*=\s*"(?P<product>[^"]+)"',
    re.MULTILINE,
)


def fail(message: str) -> None:
    raise SystemExit(f"Light Quick Setup production guard failed: {message}")


def quick_setup_products(catalog: dict) -> dict[str, int]:
    profiles = catalog["profiles"]
    result: dict[str, int] = {}
    for product in catalog["products"]:
        profile = profiles[product["profile"]]
        if "LIGHT_QUICK_SETUP" in profile.get("supportedFeatures", []):
            result[product["productKey"]] = int(product["limits"]["lightChannelCount"])
    return result


def profile_blocks(source: str) -> dict[str, str]:
    matches = list(PROFILE_START.finditer(source))
    blocks: dict[str, str] = {}
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
        product = match.group("product")
        if product in blocks:
            fail(f"duplicate measured calibration profile for {product}")
        blocks[product] = source[start:end]
    return blocks


def require_positive_int(product: str, field: str, block: str, minimum: int = 1) -> int:
    match = re.search(rf"{field}\s*=\s*(\d+)", block)
    if match is None or int(match.group(1)) < minimum:
        fail(f"{product} must declare {field} >= {minimum}")
    return int(match.group(1))


def require_channel_count(product: str, expected: int, block: str) -> None:
    match = re.search(r"channelKeys\s*=\s*setOf\((?P<body>[^)]*)\)", block, re.S)
    if match is None:
        fail(f"{product} must declare exact channelKeys")
    channels = re.findall(r'"([a-z][a-z0-9]*)"', match.group("body"))
    if len(channels) != expected or len(set(channels)) != expected:
        fail(f"{product} channelKeys do not match catalog channel count {expected}")


def require_geometry_domains(product: str, block: str) -> None:
    for field in (
        "waterHeightCmRange",
        "fixtureHeightAboveWaterCmRange",
        "tankWidthCmRange",
        "tankLengthCmRange",
    ):
        if re.search(rf"{field}\s*=\s*\d+\.\.\d+", block) is None:
            fail(f"{product} must declare a finite measured {field}")


def require_measurement_provenance(product: str, block: str) -> None:
    if re.search(r'measurementSetId\s*=\s*"[^"]+"', block) is None:
        fail(f"{product} must declare measurementSetId")
    if re.search(r'measurementDataSha256\s*=\s*"[0-9a-f]{64}"', block) is None:
        fail(f"{product} must declare a 64-hex measurementDataSha256")
    horizontal = require_positive_int(
        product, "horizontalMeasurementPointCount", block, minimum=3
    )
    samples = require_positive_int(product, "measuredPpfdSampleCount", block)
    if samples < horizontal:
        fail(f"{product} measuredPpfdSampleCount must cover every horizontal measurement point")
    require_positive_int(product, "coverageModelRevision", block)


def validate(required: dict[str, int], source: str) -> None:
    blocks = profile_blocks(source)
    missing = sorted(set(required) - set(blocks))
    extra = sorted(set(blocks) - set(required))
    if missing:
        fail("missing measured calibration profiles: " + ", ".join(missing))
    if extra:
        fail("registry contains non-Quick-Setup products: " + ", ".join(extra))

    for product, channel_count in required.items():
        block = blocks[product]
        require_positive_int(product, "calibrationRevision", block)
        require_channel_count(product, channel_count, block)
        require_geometry_domains(product, block)
        require_measurement_provenance(product, block)
        if "PLACEHOLDER" in block or "DEBUG" in block:
            fail(f"{product} contains forbidden placeholder/debug calibration behavior")

    if "profiles: List<DeviceLightMeasuredCalibrationProfile> = emptyList()" in source:
        fail("production calibration registry must not be empty")


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    source = REGISTRY.read_text(encoding="utf-8")
    required = quick_setup_products(catalog)
    validate(required, source)
    print(
        "Light Quick Setup production calibration guard passed for "
        f"{len(required)} product(s)."
    )


if __name__ == "__main__":
    main()
