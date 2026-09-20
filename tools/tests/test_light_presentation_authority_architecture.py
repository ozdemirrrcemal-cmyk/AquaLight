from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
LIGHT = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/light"


def read(relative: str) -> str:
    return (LIGHT / relative).read_text(encoding="utf-8")


def method_body(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin)
    return source[begin:finish]


class LightPresentationAuthorityArchitectureTest(unittest.TestCase):
    def test_dashboard_observation_uses_only_retained_central_light_frames(self):
        source = read("dashboard/DefaultDeviceLightControlOperations.kt")
        observe = method_body(
            source,
            "override fun observeControl",
            "override fun currentControl",
        )

        self.assertNotIn("rootOperations.observe", observe)
        self.assertIn("projectLightControlPresentationRead", observe)
        self.assertNotIn("DeviceLightDashboardReadAuthority.PRESENTATION", source)

        presentation = read("dashboard/DeviceLightControlPresentationProjection.kt")
        self.assertIn("DeviceLightDashboardReadAuthority.PRESENTATION", presentation)
        self.assertIn("DeviceLightLibraryReadAuthority.PRESENTATION", presentation)
        self.assertIn("DeviceLightSystemReadAuthority.PRESENTATION", presentation)

        authoritative = source[source.index("override fun currentControl"):]
        self.assertIn("rootOperations.current", authoritative)
        self.assertIn("DeviceLightDashboardReadAuthority.AUTHORITATIVE", authoritative)

    def test_system_observation_does_not_depend_on_transient_root_metadata(self):
        source = read("system/DefaultDeviceLightSystemOperations.kt")
        observe = method_body(source, "override fun observe", "override fun current")

        self.assertNotIn("rootOperations.observe", observe)
        self.assertIn("DeviceLightStatusReadAuthority.PRESENTATION", observe)
        self.assertIn("DeviceLightSystemReadAuthority.PRESENTATION", observe)

        authoritative = source[source.index("override fun current"):]
        self.assertIn("rootOperations.current", authoritative)
        self.assertIn("DeviceLightSystemReadAuthority.AUTHORITATIVE", authoritative)

    def test_library_observation_uses_retained_status(self):
        source = read("library/DefaultDeviceLightLibraryOperations.kt")
        observe = method_body(
            source,
            "private fun observeAvailableLibrary",
            "override suspend fun usedNames",
        )
        self.assertIn("DeviceLightStatusReadAuthority.PRESENTATION", observe)

    def test_plan_window_projection_is_separate_from_snapshot_mapping(self):
        mapper = read("dashboard/DeviceLightControlSnapshotMapper.kt")
        projection = read("dashboard/DeviceLightPlanWindowProjection.kt")

        self.assertNotIn("private fun DeviceLightGraph.autoActiveWindow", mapper)
        self.assertNotIn("private fun DeviceLightGraph.customActiveWindow", mapper)
        self.assertIn("toApplicationActiveWindow", projection)
        self.assertIn("autoActiveWindow", projection)
        self.assertIn("customActiveWindow", projection)

    def test_runtime_owner_remains_the_only_light_snapshot_retainer(self):
        source = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/light/"
              "DeviceLightRuntimeStateOwner.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("DeviceLightDashboardReadAuthority.PRESENTATION", source)
        self.assertIn("DeviceLightLibraryReadAuthority.PRESENTATION", source)
        self.assertIn("DeviceLightAutomaticReadAuthority.PRESENTATION", source)
        self.assertIn("DeviceLightSystemReadAuthority.PRESENTATION", source)
        self.assertIn("Presentation retains the last complete frame", source)


if __name__ == "__main__":
    unittest.main()
