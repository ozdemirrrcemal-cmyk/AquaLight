from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ROOT_VIEW_MODEL = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/root/"
    "DeviceCoolingRootViewModel.kt"
)
COMPOSITIONS = {
    "production": ROOT / "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt",
    "releaseSmoke": ROOT / (
        "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
    ),
}
CONTROL_OPERATION_WIRING = {
    "production": "controlOperations = DefaultDeviceCoolingControlOperations(",
    "releaseSmoke": "controlOperations = DefaultDeviceCoolingControlOperations(",
}
DEBUG_COMPOSITION = ROOT / (
    "app/src/debug/java/com/aqua/aqualight/debug/devices/DebugDeviceFixtureAppContainer.kt"
)


class CoolingRootDependencyWiringTest(unittest.TestCase):

    def test_root_dependencies_are_mandatory(self) -> None:
        text = ROOT_VIEW_MODEL.read_text(encoding="utf-8")

        for token in (
            "private val controlOperations: DeviceCoolingControlOperations,",
            "private val historyOperations: DeviceCoolingTemperatureHistoryOperations,",
            "private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations",
            "private val controlSurfacePreparationOperations:",
        ):
            self.assertIn(token, text)

        for forbidden in (
            "DeviceCoolingTemperatureHistoryOperations?",
            "DeviceCoolingAutomaticSettingsOperations?",
            "historyOperations ?: return",
            "automaticSettingsOperations ?: return",
        ):
            self.assertNotIn(forbidden, text)

    def test_explicit_runtime_compositions_wire_all_root_feature_operations(self) -> None:
        for name, path in COMPOSITIONS.items():
            with self.subTest(composition=name):
                text = path.read_text(encoding="utf-8")
                self.assertIn("DeviceCoolingRootViewModel(", text)
                self.assertIn(CONTROL_OPERATION_WIRING[name], text)
                self.assertIn(
                    "historyOperations = DefaultDeviceCoolingTemperatureHistoryOperations(",
                    text,
                )
                self.assertIn(
                    "automaticSettingsOperations =",
                    text,
                )
                self.assertIn("DefaultDeviceCoolingAutomaticSettingsOperations(", text)
                self.assertIn("controlSurfacePreparationOperations =", text)

    def test_debug_composition_does_not_override_cooling_production_wiring(self) -> None:
        debug_text = DEBUG_COMPOSITION.read_text(encoding="utf-8")

        self.assertIn("else -> return delegate.create(modelClass)", debug_text)
        for forbidden in (
            "DeviceCooling",
            "DebugFixtureCooling",
            "DefaultDeviceCooling",
            "coolingControlOperations",
            "createCooling",
        ):
            self.assertNotIn(forbidden, debug_text)


if __name__ == "__main__":
    unittest.main()
