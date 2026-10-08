from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODE_SELECTOR = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/root/"
    "DeviceLightModeSelector.kt"
)
STYLE = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/common/"
    "AquaLightComposeStyle.kt"
)
COLORS = ROOT / "app/src/main/res/values/card_colors.xml"


class LightModeSelectorStyleContractTest(unittest.TestCase):
    def test_mode_selector_uses_shared_card_surface(self):
        source = MODE_SELECTOR.read_text(encoding="utf-8")

        self.assertIn("AquaDeviceCardSurface(", source)
        self.assertNotIn("modeSelectorOutlineWidth", source)
        self.assertNotIn("modeSelectorShape", source)
        self.assertNotIn(".background(colors.mediaSurface)", source)

    def test_interaction_authority_does_not_change_visual_alpha(self):
        source = MODE_SELECTOR.read_text(encoding="utf-8")

        self.assertNotIn("AquaLightDashboardAlpha.disabledControl", source)
        self.assertNotIn(".alpha(", source)
        self.assertIn("enabled = enabled && !selected", source)

    def test_light_dashboard_has_its_own_semantic_accent_token(self):
        style = STYLE.read_text(encoding="utf-8")
        colors = COLORS.read_text(encoding="utf-8")

        self.assertIn("R.color.aqua_card_device_light_accent", style)
        self.assertNotIn("R.color.aqua_card_device_cooling_accent", style)
        self.assertIn('name="aqua_card_device_light_accent"', colors)


if __name__ == "__main__":
    unittest.main()
