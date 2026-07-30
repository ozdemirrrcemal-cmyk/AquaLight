from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
CURRENT_FIRMWARE_COMMIT = (
    "cf2222e58e6c69a729071a5d1205497b3fceaa70"
)

EXPECTED_PRODUCTS = {
    "LIGHT_WRGB_PRO_ELITE": (
        "light",
        "wrgb_pro_elite_120",
        (4, 2, 1, 0, 0),
    ),
    "LIGHT_RGB_PRO_SLIM": (
        "light",
        "rgb_pro_slim",
        (3, 0, 0, 0, 0),
    ),
    "TIMER_RELAY_PRO_2": (
        "timer",
        "relay_pro_2",
        (0, 0, 0, 2, 0),
    ),
    "TIMER_RELAY_PRO_4": (
        "timer",
        "relay_pro_4",
        (0, 0, 0, 4, 0),
    ),
    "DOSING_DOSE_PRO_2": (
        "dosing",
        "dose_pro_2",
        (0, 0, 0, 0, 2),
    ),
    "DOSING_DOSE_PRO_4": (
        "dosing",
        "dose_pro_4",
        (0, 0, 0, 0, 4),
    ),
    "COOLING_COOL_PRO_1F": (
        "cooling",
        "cool_pro_1f",
        (0, 1, 1, 0, 0),
    ),
    "COOLING_COOL_PRO_2F": (
        "cooling",
        "cool_pro_2f",
        (0, 2, 1, 0, 0),
    ),
    "COOLING_COOL_PRO_3F": (
        "cooling",
        "cool_pro_3f",
        (0, 3, 1, 0, 0),
    ),
}


class CurrentFirmwareCatalogFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.fixture = json.loads(
            FIXTURE_PATH.read_text(encoding="utf-8")
        )

    def test_fixture_is_pinned_to_current_firmware_catalog(self) -> None:
        self.assertEqual(
            CURRENT_FIRMWARE_COMMIT,
            self.fixture["source"]["commit"],
        )

    def test_all_nine_products_match_exact_identity_and_limits(self) -> None:
        products = {
            product["productKey"]: product
            for product in self.fixture["products"]
        }
        self.assertEqual(set(EXPECTED_PRODUCTS), set(products))

        for product_key, expected in EXPECTED_PRODUCTS.items():
            family, model, limits = expected
            product = products[product_key]
            actual_limits = product["limits"]
            self.assertEqual(family, product["family"])
            self.assertEqual(model, product["model"])
            self.assertEqual(product_key.lower(), product["env"])
            self.assertEqual("2.0", product["hardwareRevision"])
            self.assertEqual(
                limits,
                (
                    actual_limits["lightChannelCount"],
                    actual_limits["fanOutputCount"],
                    actual_limits["temperatureSensorCount"],
                    actual_limits["timerChannelCount"],
                    actual_limits["dosingChannelCount"],
                ),
            )

    def test_display_name_tokens_are_exactly_scoped(self) -> None:
        profiles = self.fixture["profiles"]
        self.assertIn(
            "TIMER_CHANNEL_DISPLAY_NAME",
            profiles["timerRelayPro"]["supportedFeatures"],
        )
        self.assertIn(
            "DOSING_CHANNEL_DISPLAY_NAME",
            profiles["dosingDosePro"]["supportedFeatures"],
        )
        self.assertIn(
            "COOLING_FAN_DISPLAY_NAME",
            profiles["coolingCoolPro"]["supportedFeatures"],
        )
        self.assertNotIn(
            "TIMER_CHANNEL_DISPLAY_NAME",
            profiles["dosingDosePro"]["supportedFeatures"],
        )
        self.assertNotIn(
            "DOSING_CHANNEL_DISPLAY_NAME",
            profiles["timerRelayPro"]["supportedFeatures"],
        )

    def test_wrgb_has_one_family_specific_fan_destination(self) -> None:
        screens = set(
            self.fixture["profiles"]["lightWrgbProElite"][
                "supportedScreens"
            ]
        )
        self.assertIn("LIGHT_FAN_CONTROL", screens)
        self.assertNotIn("COOLING_CONTROL", screens)


if __name__ == "__main__":
    unittest.main()
