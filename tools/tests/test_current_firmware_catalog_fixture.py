from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
CURRENT_FIRMWARE_COMMIT = (
    "2e3688f266d7ed34a6773badafcd62af73cf4aac"
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
        (0, 1, 2, 0, 0),
    ),
}

EXPECTED_WRGB_FEATURES = {
    "WIFI_SETUP",
    "LAN_DISCOVERY",
    "LIGHT_CONTROL",
    "LIGHT_QUICK_SETUP",
    "LIGHT_PRESETS",
    "LIGHT_MOONLIGHT",
    "LIGHT_ACCLIMATION",
    "LIGHT_TEMPERATURE_PROTECTION",
    "LIGHT_FAN_CONTROL",
    "TEMPERATURE_READ",
    "OTA_UPDATE",
}
EXPECTED_COOLING_FEATURES = {
    "WIFI_SETUP",
    "LAN_DISCOVERY",
    "COOLING_CONTROL",
    "COOLING_PROGRAM",
    "COOLING_HISTORY",
    "COOLING_SILENT_MODE",
    "COOLING_POWER_ESTIMATE",
    "TEMPERATURE_READ",
    "ROOM_AMBIENT_READ",
    "HUMIDITY_READ",
    "OTA_UPDATE",
}
EXPECTED_COOLING_SCREENS = {
    "OVERVIEW",
    "COOLING_CONTROL",
    "COOLING_PROGRAM",
    "COOLING_HISTORY",
    "ADVANCED",
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

    def test_all_seven_products_match_exact_identity_and_limits(self) -> None:
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
        self.assertNotIn(
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

    def test_wrgb_feature_and_screen_contract_matches_firmware_exactly(self) -> None:
        profile = self.fixture["profiles"]["lightWrgbProElite"]
        self.assertEqual(EXPECTED_WRGB_FEATURES, set(profile["supportedFeatures"]))
        self.assertIn("LIGHT_FAN_CONTROL", profile["supportedScreens"])
        self.assertNotIn("COOLING_CONTROL", profile["supportedFeatures"])
        self.assertNotIn("COOLING_CONTROL", profile["supportedScreens"])

    def test_cooling_feature_and_screen_contract_matches_firmware_exactly(self) -> None:
        profile = self.fixture["profiles"]["coolingCoolPro"]
        self.assertEqual(EXPECTED_COOLING_FEATURES, set(profile["supportedFeatures"]))
        self.assertEqual(EXPECTED_COOLING_SCREENS, set(profile["supportedScreens"]))


if __name__ == "__main__":
    unittest.main()
