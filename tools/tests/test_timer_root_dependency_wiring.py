from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ROOT_VIEW_MODEL = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/"
    "DeviceTimerRootViewModel.kt"
)
COMPOSITIONS = {
    "production": ROOT / "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt",
    "debug": ROOT / (
        "app/src/debug/java/com/aqua/aqualight/debug/devices/"
        "DebugDeviceFixtureAppContainer.kt"
    ),
    "releaseSmoke": ROOT / (
        "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
    ),
}


class TimerRootDependencyWiringTest(unittest.TestCase):

    def test_root_dependencies_are_mandatory(self) -> None:
        text = ROOT_VIEW_MODEL.read_text(encoding="utf-8")

        for token in (
            "private val timerControlOperations: DeviceTimerControlOperations,",
            "private val controlSurfacePreparationOperations: "
            "DeviceControlSurfacePreparationOperations",
        ):
            self.assertIn(token, text)

        for forbidden in (
            "DeviceTimerControlOperations?",
            "DeviceControlSurfacePreparationOperations?",
            "timerControlOperations: DeviceTimerControlOperations =",
            "controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations =",
        ):
            self.assertNotIn(forbidden, text)

    def test_every_explicit_composition_wires_the_timer_boundaries(self) -> None:
        for name, path in COMPOSITIONS.items():
            with self.subTest(composition=name):
                text = path.read_text(encoding="utf-8")
                self.assertIn("DeviceTimerRootViewModel(", text)
                self.assertIn("timerControlOperations =", text)
                self.assertIn("controlSurfacePreparationOperations =", text)

    def test_production_and_debug_share_owner_scoped_timer_dependencies(self) -> None:
        production = COMPOSITIONS["production"].read_text(encoding="utf-8")
        debug = COMPOSITIONS["debug"].read_text(encoding="utf-8")

        self.assertIn("timerControlOperations = graph.timerControlOperations", production)
        self.assertIn(
            "controlSurfacePreparationOperations = graph.controlSurfacePreparationOperations",
            production,
        )
        self.assertIn("timerControlOperations = graph.timerControlOperations", debug)
        self.assertIn(
            "controlSurfacePreparationOperations = graph.controlSurfacePreparationOperations",
            debug,
        )

    def test_release_smoke_uses_a_single_stateless_timer_adapter(self) -> None:
        smoke = COMPOSITIONS["releaseSmoke"].read_text(encoding="utf-8")

        self.assertIn(
            "private val timerControlOperations = "
            "DefaultDeviceTimerControlOperations(devicesRepository)",
            smoke,
        )
        self.assertIn("timerControlOperations = timerControlOperations", smoke)


if __name__ == "__main__":
    unittest.main()
