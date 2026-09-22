from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/light_quick_setup_production_guard.py"
SPEC = importlib.util.spec_from_file_location("light_quick_setup_production_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)

MEASUREMENT_SHA = "a" * 64


def profile(product: str, channels: tuple[str, ...]) -> str:
    channel_source = ", ".join(f'"{channel}"' for channel in channels)
    return f"""
        DeviceLightMeasuredCalibrationProfile(
            productKey = "{product}",
            calibrationRevision = 3,
            channelKeys = setOf({channel_source}),
            waterHeightCmRange = 20..60,
            fixtureHeightAboveWaterCmRange = 0..50,
            tankWidthCmRange = 30..70,
            tankLengthCmRange = 45..140,
            measurementSetId = "{product.lower()}-par-grid-v3",
            measurementDataSha256 = "{MEASUREMENT_SHA}",
            horizontalMeasurementPointCount = 9,
            measuredPpfdSampleCount = 108,
            coverageModelRevision = 2,
            measuredCalibration = solver
        )
    """


class LightQuickSetupProductionGuardTest(unittest.TestCase):
    def test_complete_measured_registry_shape_passes_parser(self) -> None:
        required = {
            "LIGHT_WRGB_PRO_ELITE": 4,
            "LIGHT_RGB_PRO_SLIM": 3,
        }
        source = "val profiles = listOf(\n" + profile(
            "LIGHT_WRGB_PRO_ELITE", ("white", "red", "green", "blue")
        ) + profile(
            "LIGHT_RGB_PRO_SLIM", ("red", "green", "blue")
        ) + "\n)"

        GUARD.validate(required, source)

    def test_missing_product_fails_closed(self) -> None:
        required = {
            "LIGHT_WRGB_PRO_ELITE": 4,
            "LIGHT_RGB_PRO_SLIM": 3,
        }
        source = "val profiles = listOf(\n" + profile(
            "LIGHT_WRGB_PRO_ELITE", ("white", "red", "green", "blue")
        ) + "\n)"

        with self.assertRaises(SystemExit):
            GUARD.validate(required, source)

    def test_wrong_channel_count_fails_closed(self) -> None:
        required = {"LIGHT_WRGB_PRO_ELITE": 4}
        source = "val profiles = listOf(\n" + profile(
            "LIGHT_WRGB_PRO_ELITE", ("red", "green", "blue")
        ) + "\n)"

        with self.assertRaises(SystemExit):
            GUARD.validate(required, source)

    def test_missing_geometry_domain_fails_closed(self) -> None:
        required = {"LIGHT_RGB_PRO_SLIM": 3}
        source = profile("LIGHT_RGB_PRO_SLIM", ("red", "green", "blue")).replace(
            "fixtureHeightAboveWaterCmRange = 0..50,",
            ""
        )

        with self.assertRaises(SystemExit):
            GUARD.validate(required, source)

    def test_center_only_measurement_set_fails_closed(self) -> None:
        required = {"LIGHT_RGB_PRO_SLIM": 3}
        source = profile("LIGHT_RGB_PRO_SLIM", ("red", "green", "blue")).replace(
            "horizontalMeasurementPointCount = 9",
            "horizontalMeasurementPointCount = 1"
        )

        with self.assertRaises(SystemExit):
            GUARD.validate(required, source)


if __name__ == "__main__":
    unittest.main()
