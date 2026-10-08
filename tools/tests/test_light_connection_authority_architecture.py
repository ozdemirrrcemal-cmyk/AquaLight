from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
LIGHT_UI = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation"
)
PRESENCE = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/common/devicepresence/"
    "DeviceConnectionVisualState.kt"
)
DEVICE_CARD = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/DeviceCardMapper.kt"
)
COMPACT_DEVICE_CARD = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/common/devicecard/"
    "DeviceCompactSnapshotMapper.kt"
)

CENTRAL_CONNECTION_VIEW_MODELS = (
    "automatic/programs/DeviceLightAutomaticProgramsViewModel.kt",
    "automatic/editor/DeviceLightAutomaticProgramEditorViewModel.kt",
    "custom/DeviceLightCustomCurveViewModel.kt",
    "adaptation/DeviceLightAdaptationViewModel.kt",
    "system/DeviceLightSystemViewModel.kt",
)


class LightConnectionAuthorityArchitectureTest(unittest.TestCase):
    def test_cards_and_headers_share_root_availability_projection(self):
        presence = PRESENCE.read_text(encoding="utf-8")
        cards = DEVICE_CARD.read_text(encoding="utf-8")
        compact_cards = COMPACT_DEVICE_CARD.read_text(encoding="utf-8")
        root = (LIGHT_UI / "root/DeviceLightRootViewModel.kt").read_text(encoding="utf-8")

        self.assertIn(
            "OwnerDeviceAvailability?.toDeviceConnectionVisualState()",
            presence,
        )
        self.assertIn("DeviceRootOperations.observeConnectionVisualState", presence)
        self.assertIn(
            "device.availability.toDeviceConnectionVisualState()",
            cards,
        )
        self.assertIn(
            "device.availability.toDeviceConnectionVisualState()",
            compact_cards,
        )
        self.assertNotIn("OwnerDeviceAvailability.REACHABLE", compact_cards)
        self.assertNotIn("DeviceConnectionVisualState.ONLINE", compact_cards)
        self.assertNotIn("DeviceConnectionVisualState.OFFLINE", compact_cards)
        self.assertIn("root.toDeviceConnectionVisualState()", root)

    def test_feature_authority_never_drives_connection_visual_state(self):
        forbidden = (
            "connectionVisualState = if (snapshot.firmwareWriteAuthoritative)",
            "connectionVisualState = failure.connectionState()",
            "connectionVisualState = DeviceConnectionVisualState.WARNING",
        )
        for relative in CENTRAL_CONNECTION_VIEW_MODELS:
            source = (LIGHT_UI / relative).read_text(encoding="utf-8")
            self.assertIn("private val rootOperations: DeviceRootOperations", source)
            self.assertIn("observeConnectionVisualState(deviceUid)", source)
            for token in forbidden:
                self.assertNotIn(token, source, relative)

    def test_manual_and_library_use_the_same_shared_projection(self):
        for relative in (
            "manual/DeviceLightManualControlViewModel.kt",
            "library/DeviceLightLibraryViewModel.kt",
        ):
            source = (LIGHT_UI / relative).read_text(encoding="utf-8")
            self.assertIn("toDeviceConnectionVisualState()", source)


if __name__ == "__main__":
    unittest.main()
