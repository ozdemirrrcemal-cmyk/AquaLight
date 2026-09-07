from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CoolingDeviceCardContractTest(unittest.TestCase):
    def test_tank_card_observes_application_boundary_not_cooling_data_sources(self) -> None:
        view_model = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
            "TankDetailDevicesViewModel.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("DeviceCoolingCardOperations", view_model)
        self.assertNotIn("data.devices.cooling", view_model)
        self.assertNotIn("DeviceCoolingRuntimeState", view_model)

    def test_card_projection_reads_the_single_central_cooling_owner(self) -> None:
        operations = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/data/devices/cooling/"
            "DefaultDeviceCoolingCardOperations.kt"
        )).read_text(encoding="utf-8")
        mapper = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/data/devices/cooling/"
            "DeviceCoolingCardSnapshotMapper.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("runtime.states", operations)
        self.assertIn("DeviceCoolingCardSnapshotMapper.map(state)", operations)
        self.assertIn("fun map(state: DeviceCoolingRuntimeState)", mapper)
        self.assertIn("targetFanPercent = control.targetFanPercent", mapper)
        self.assertNotIn("targetFanPercent = control.manualFanPercent", mapper)
        for forbidden in ("System.currentTimeMillis", "LocalTime", "Calendar", "Timer("):
            self.assertNotIn(forbidden, mapper)

    def test_program_interval_is_retained_by_revision_in_the_central_owner(self) -> None:
        owner = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/cooling/"
            "DeviceCoolingRuntimeStateOwner.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("val programSnapshot: DeviceCoolingV1ProgramSnapshot?", owner)
        self.assertIn("current.status?.programRevision != program.programRevision", owner)


if __name__ == "__main__":
    unittest.main()
