from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]
PRESENTATION = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation"
)
COMMON = PRESENTATION / "common"
MANUAL = PRESENTATION / "manual"
CUSTOM = PRESENTATION / "custom"
AUTOMATIC = PRESENTATION / "automatic/editor"


class LightChannelSliderConsistencyTest(unittest.TestCase):
    def test_manual_custom_and_automatic_use_one_shared_channel_row(self):
        sources = {
            "manual": (MANUAL / "DeviceLightManualChannelControls.kt").read_text(
                encoding="utf-8"
            ),
            "custom": (CUSTOM / "DeviceLightCustomCurvePoint.kt").read_text(
                encoding="utf-8"
            ),
            "automatic": (
                AUTOMATIC / "DeviceLightAutomaticEditorSceneCards.kt"
            ).read_text(encoding="utf-8"),
        }

        for name, source in sources.items():
            with self.subTest(screen=name):
                self.assertIn("AquaLightChannelPercentRow(", source)
                self.assertIn("AquaLightChannelPercentRowState(", source)
                self.assertIn("AquaLightChannelPercentRowActions(", source)

        self.assertNotIn("AquaLightManualPercentSlider(", sources["custom"])
        self.assertNotIn("AquaLightManualPercentSlider(", sources["automatic"])
        self.assertNotIn("AquaLightChannelStepButton(", sources["custom"])
        self.assertNotIn("AquaLightChannelStepButton(", sources["automatic"])

    def test_shared_row_owns_manual_reference_geometry_and_percent_format(self):
        shared = (COMMON / "AquaLightChannelPercentRow.kt").read_text(
            encoding="utf-8"
        )

        self.assertIn("AquaLightManualGeometry.channelRowHeight", shared)
        self.assertIn("AquaLightManualGeometry.channelLabelWidth", shared)
        self.assertIn("AquaLightManualGeometry.channelValueWidth", shared)
        self.assertIn("AquaLightManualGeometry.channelSliderHorizontalInset", shared)
        self.assertIn("AquaLightChannelStepButton(", shared)
        self.assertIn("AquaLightManualPercentSlider(", shared)
        self.assertIn("R.string.device_light_live_output_percent_format", shared)

    def test_shared_row_owns_canonical_channel_colors(self):
        shared = (COMMON / "AquaLightChannelPercentRow.kt").read_text(
            encoding="utf-8"
        )
        visuals = (COMMON / "DeviceLightChannelVisuals.kt").read_text(
            encoding="utf-8"
        )
        manual = (MANUAL / "DeviceLightManualChannelControls.kt").read_text(
            encoding="utf-8"
        )
        custom = (CUSTOM / "DeviceLightCustomCurvePoint.kt").read_text(
            encoding="utf-8"
        )
        automatic = (
            AUTOMATIC / "DeviceLightAutomaticEditorSceneCards.kt"
        ).read_text(encoding="utf-8")
        drawing = (CUSTOM / "DeviceLightCustomCurveDrawing.kt").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            "channelColor = deviceLightChannelColor(state.channelWireKey)",
            shared,
        )
        self.assertIn("channelWireKey = channel.id.wireKey", manual)
        self.assertIn("channelWireKey = channel.wireKey", custom)
        self.assertIn("channelWireKey = state.channel.wireKey", automatic)
        self.assertNotIn("channelColor =", manual)
        self.assertNotIn("channelColor =", custom)
        self.assertNotIn("channelColor =", automatic)

        self.assertIn("CHANNEL_RED_ARGB = 0xFFFF0000", visuals)
        self.assertIn("CHANNEL_GREEN_ARGB = 0xFF00FF00", visuals)
        self.assertIn("CHANNEL_BLUE_ARGB = 0xFF0000FF", visuals)
        self.assertIn("CHANNEL_WHITE_ARGB = 0xFFFFFFFF", visuals)
        self.assertIn(
            "deviceLightChannelColor(plot.channel.wireKey)",
            drawing,
        )

        fixture = json.loads(
            (ROOT / "protocol/fixtures/aql_light_contract_v1.json").read_text(
                encoding="utf-8"
            )
        )
        descriptor_rgb = {
            item["key"]: item["displayColorRgb"]
            for item in fixture["channelDescriptors"]
        }
        self.assertEqual(
            {
                "red": 0xFF0000,
                "green": 0x00FF00,
                "blue": 0x0000FF,
                "white": 0xFFFFFF,
            },
            descriptor_rgb,
        )

    def test_all_three_slider_surfaces_resolve_channel_names_from_local_resources(self):
        manual = (MANUAL / "DeviceLightManualChannelControls.kt").read_text(
            encoding="utf-8"
        )
        custom = (CUSTOM / "DeviceLightCustomCurvePoint.kt").read_text(
            encoding="utf-8"
        )
        automatic = (
            AUTOMATIC / "DeviceLightAutomaticEditorSceneCards.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("deviceLightChannelNameResource(channel.id.wireKey)", manual)
        self.assertIn("deviceLightChannelNameResource(channel.wireKey)", custom)
        self.assertIn(
            "deviceLightChannelNameResource(state.channel.wireKey)",
            automatic,
        )

        manual_state = (
            MANUAL / "DeviceLightManualControlUiState.kt"
        ).read_text(encoding="utf-8")
        custom_state = (
            CUSTOM / "DeviceLightCustomCurveUiState.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('RED("red")', manual_state)
        self.assertIn('GREEN("green")', manual_state)
        self.assertIn('BLUE("blue")', manual_state)
        self.assertIn('WHITE("white")', manual_state)
        self.assertIn('RED("red")', custom_state)
        self.assertIn('GREEN("green")', custom_state)
        self.assertIn('BLUE("blue")', custom_state)
        self.assertIn('WHITE("white")', custom_state)


if __name__ == "__main__":
    unittest.main()
