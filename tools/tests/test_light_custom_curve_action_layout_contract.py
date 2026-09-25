from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CUSTOM = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/custom"
)
STRINGS = ROOT / "app/src/main/res/values/device_light_strings.xml"
MORE_ICON = ROOT / "app/src/main/res/drawable/ic_more_vert_24.xml"


def read(name: str) -> str:
    return (CUSTOM / name).read_text(encoding="utf-8")


class LightCustomCurveActionLayoutContractTest(unittest.TestCase):
    def test_app_bar_has_no_destructive_overflow(self):
        fragment = read("DeviceLightCustomCurveFragment.kt")

        self.assertNotIn("AquaHeaderAction", fragment)
        self.assertNotIn("ic_more_vert_24", fragment)

    def test_removed_preview_and_overflow_resources_stay_deleted(self):
        strings = STRINGS.read_text(encoding="utf-8")

        self.assertNotIn("device_light_custom_virtual_preview_compact", strings)
        self.assertNotIn("device_light_custom_more_actions_description", strings)
        self.assertFalse(MORE_ICON.exists())

    def test_delete_button_reuses_existing_bottom_sheet_entry_point(self):
        fragment = read("DeviceLightCustomCurveFragment.kt")
        header = read("DeviceLightCustomCurveHeaderActions.kt")

        self.assertIn(
            "onDeleteDeviceProgramClick = viewModel.requestDeviceProgramActions",
            fragment,
        )
        self.assertIn("onClick = onDeleteDeviceProgramClick", header)
        self.assertIn("showDeviceProgramActions()", fragment)
        self.assertIn("showDeleteDeviceProgramConfirmation()", fragment)

    def test_preview_and_delete_live_in_curve_header(self):
        chart = read("DeviceLightCustomCurveChart.kt")
        screen = read("DeviceLightCustomCurveScreen.kt")
        header = read("DeviceLightCustomCurveHeaderActions.kt")

        self.assertIn("CurveHeaderActions(", chart)
        self.assertIn(
            "onDeleteDeviceProgramClick = onDeleteDeviceProgramClick",
            chart,
        )
        self.assertNotIn("VirtualTimePreviewCard(state, actions, visuals)", screen)
        self.assertIn("device_light_custom_preview", header)
        self.assertIn("device_light_custom_delete_device_program_confirm", header)
        self.assertIn("R.drawable.ic_delete_24", header)

    def test_header_actions_use_compact_visual_geometry(self):
        header = read("DeviceLightCustomCurveHeaderActions.kt")

        self.assertIn("HEADER_ACTION_HEIGHT_DP = 33", header)
        self.assertIn("PREVIEW_ACTION_WIDTH_DP = 74", header)
        self.assertIn("DELETE_ACTION_WIDTH_DP = 54", header)
        self.assertIn("HEADER_ACTION_ICON_SIZE_DP = 13", header)

    def test_playhead_time_bubble_uses_solid_action_surface(self):
        playhead = read("DeviceLightCustomCurvePlayhead.kt")

        self.assertIn(".background(visuals.colors.action)", playhead)
        self.assertNotIn("PLAYHEAD_LABEL_BORDER_DP", playhead)
        self.assertIn("PLAYHEAD_LABEL_TOUCH_HEIGHT_DP = 48", playhead)


if __name__ == "__main__":
    unittest.main()
