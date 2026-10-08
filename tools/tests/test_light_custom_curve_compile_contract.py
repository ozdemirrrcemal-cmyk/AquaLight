from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CUSTOM = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/custom"
)
ROOT_UI = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/root"
)
TANK_UI = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices"
)


class LightCustomCurveCompileContractTest(unittest.TestCase):
    def test_chart_imports_spacer_when_header_uses_it(self):
        chart = (CUSTOM / "DeviceLightCustomCurveChart.kt").read_text(encoding="utf-8")

        self.assertIn("import androidx.compose.foundation.layout.Spacer", chart)
        self.assertIn("Spacer(", chart)

    def test_delete_callback_property_is_passed_directly(self):
        fragment = (CUSTOM / "DeviceLightCustomCurveFragment.kt").read_text(encoding="utf-8")

        self.assertIn(
            "onDeleteDeviceProgramClick = viewModel.requestDeviceProgramActions",
            fragment,
        )
        self.assertNotIn(
            "onDeleteDeviceProgramClick = viewModel::requestDeviceProgramActions",
            fragment,
        )

    def test_tank_mode_fallback_is_not_coupled_to_removed_hero_copy(self):
        models = (TANK_UI / "LightDeviceCardModels.kt").read_text(encoding="utf-8")

        self.assertIn("R.string.device_light_mode_unavailable", models)
        self.assertNotIn("R.string.device_light_hero_mode_unavailable", models)

    def test_hero_uses_modifier_absolute_offset_extension(self):
        hero = (ROOT_UI / "DeviceLightHero.kt").read_text(encoding="utf-8")

        self.assertIn("import androidx.compose.foundation.layout.absoluteOffset", hero)
        self.assertIn("): Modifier = absoluteOffset(", hero)
        self.assertNotIn("androidx.compose.foundation.layout.absoluteOffset(", hero)


if __name__ == "__main__":
    unittest.main()
