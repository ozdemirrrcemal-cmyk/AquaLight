from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ROOT_UI = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/root"
)
CUSTOM_UI = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/custom"
)
STYLE = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/common/"
    "AquaLightComposeStyle.kt"
)
TR_STRINGS = ROOT / "app/src/main/res/values-tr/device_light_strings.xml"


class LightDashboardHeroTimeContractTest(unittest.TestCase):
    def test_auto_and_custom_time_markers_render_device_time(self):
        plan = (ROOT_UI / "DeviceLightPlanCard.kt").read_text(encoding="utf-8")
        custom = (CUSTOM_UI / "DeviceLightCustomCurvePlayhead.kt").read_text(encoding="utf-8")
        strings = TR_STRINGS.read_text(encoding="utf-8")

        self.assertIn("currentTime = checkNotNull(state.currentTime)", plan)
        self.assertIn("device_light_plan_current_time_format, currentTime", plan)
        self.assertIn("text = formatTime(state.previewTimeMs)", custom)
        self.assertIn(
            '<string name="device_light_plan_current_time_format">Şimdi %1$s</string>',
            strings,
        )
        self.assertNotIn("device_light_chart_time_marker_label", plan)
        self.assertNotIn("device_light_chart_time_marker_label", custom)
        self.assertNotIn("device_light_chart_time_marker_label", strings)



if __name__ == "__main__":
    unittest.main()
