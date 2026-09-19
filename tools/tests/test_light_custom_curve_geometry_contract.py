from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CUSTOM = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation/custom"
)


class LightCustomCurveGeometryContractTest(unittest.TestCase):
    def test_curve_card_is_compact_without_shrinking_playhead_touch_target(self):
        chart = (CUSTOM / "DeviceLightCustomCurveChart.kt").read_text(encoding="utf-8")
        playhead = (CUSTOM / "DeviceLightCustomCurvePlayhead.kt").read_text(encoding="utf-8")

        self.assertIn("private const val CURVE_CONTENT_SPACING_DP = 3", chart)
        self.assertIn("internal const val PLAYHEAD_LABEL_SPACE_DP = 40", playhead)
        self.assertIn("internal const val CHART_HEIGHT_DP = 172", playhead)

        # Accessibility/drag target stays commercial-touch-size even though visual spacing shrinks.
        self.assertIn("private const val PLAYHEAD_LABEL_TOUCH_HEIGHT_DP = 48", playhead)
        self.assertIn("private const val PLAYHEAD_LABEL_VISUAL_HEIGHT_DP = 30", playhead)


if __name__ == "__main__":
    unittest.main()
