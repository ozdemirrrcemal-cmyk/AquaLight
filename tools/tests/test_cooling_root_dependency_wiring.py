from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ROOT_VIEW_MODEL = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/root/"
    "DeviceCoolingRootViewModel.kt"
)
COMPOSITIONS = {
    "production": ROOT / "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt",
    "debug": ROOT / (
        "app/src/debug/java/com/aqua/aqualight/debug/devices/DebugDeviceFixtureAppContainer.kt"
    ),
    "releaseSmoke": ROOT / (
        "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
    ),
}


class CoolingRootDependencyWiringTest(unittest.TestCase):

    def test_root_dependencies_are_mandatory(self) -> None:
        text = ROOT_VIEW_MODEL.read_text(encoding="utf-8")

        for token in (
            "private val controlOperations: DeviceCoolingControlOperations,",
            "private val historyOperations: DeviceCoolingTemperatureHistoryOperations,",
            "private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations",
        ):
            self.assertIn(token, text)

        for forbidden in (
            "DeviceCoolingTemperatureHistoryOperations?",
            "DeviceCoolingAutomaticSettingsOperations?",
            "historyOperations ?: return",
            "automaticSettingsOperations ?: return",
        ):
            self.assertNotIn(forbidden, text)

    def test_every_composition_wires_all_root_feature_operations(self) -> None:
        for name, path in COMPOSITIONS.items():
            with self.subTest(composition=name):
                text = path.read_text(encoding="utf-8")
                self.assertIn("DeviceCoolingRootViewModel(", text)
                self.assertIn("controlOperations = DefaultDeviceCoolingControlOperations(", text)
                self.assertIn(
                    "historyOperations = DefaultDeviceCoolingTemperatureHistoryOperations(",
                    text,
                )
                self.assertIn(
                    "automaticSettingsOperations =",
                    text,
                )
                self.assertIn("DefaultDeviceCoolingAutomaticSettingsOperations(", text)


if __name__ == "__main__":
    unittest.main()
