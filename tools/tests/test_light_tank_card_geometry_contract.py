from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STYLE = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/common/"
    "AquaLightComposeStyle.kt"
)
TRACK = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
    "LightDeviceChannelTrack.kt"
)
SPOTLIGHT = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
    "LightDeviceSpotlightCard.kt"
)


class LightTankCardGeometryContractTest(unittest.TestCase):
    def test_schedule_metrics_are_symmetric_and_labels_center_under_content(self):
        source = SPOTLIGHT.read_text(encoding="utf-8")
        schedule = source[
            source.index("private fun LightScheduleRow"):
            source.index("private fun LightChannelSection")
        ]

        self.assertIn("contentAlignment = Alignment.CenterEnd", schedule)
        self.assertIn("contentAlignment = Alignment.CenterStart", schedule)
        self.assertIn("horizontalAlignment = Alignment.CenterHorizontally", schedule)
        self.assertEqual(
            2,
            schedule.count(
                "Spacer(Modifier.width(AquaLightTankCardGeometry.scheduleGap))"
            ),
        )

    def test_tank_card_uses_compact_geometry_without_affecting_root_dashboard(self):
        style = STYLE.read_text(encoding="utf-8")
        track = TRACK.read_text(encoding="utf-8")

        expected = (
            "val verticalPadding = 8.dp",
            "val mediaSize = 58.dp",
            "val mediaImageSize = 48.dp",
            "val headerRowGap = 4.dp",
            "val statusScheduleGap = 6.dp",
            "val scheduleDividerHeight = 32.dp",
            "val dividerTopGap = 5.dp",
            "val dividerBottomGap = 5.dp",
            "val sectionTitleBottomGap = 4.dp",
            "val channelRowHeight = 15.dp",
            "val channelRowGap = 3.dp",
            "val channelTrackHeight = 7.dp",
        )
        for token in expected:
            self.assertIn(token, style)

        self.assertIn("AquaLightTankCardGeometry.channelTrackHeight", track)
        self.assertNotIn(
            ".height(AquaLightDashboardGeometry.liveOutputTrackHeight)",
            track,
        )


if __name__ == "__main__":
    unittest.main()
