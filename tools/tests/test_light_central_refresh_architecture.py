from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / (
    "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/light"
)
DATA_LIGHT = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/light"
MODULE_PROVIDER = ROOT / (
    "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/"
    "DeviceRuntimeModuleProvider.kt"
)


class LightCentralRefreshArchitectureTest(unittest.TestCase):
    def test_one_refresh_coordinator_owns_light_revalidation(self):
        coordinator = (
            RUNTIME / "DeviceLightRuntimeRefreshCoordinator.kt"
        ).read_text(encoding="utf-8")

        for token in (
            "class DeviceLightRuntimeRefreshCoordinator",
            "inFlight.putIfAbsent(deviceUid, pending)",
            "suspend fun refreshAll",
            "suspend fun refreshGeneration",
            "dashboardRefresh.refresh(deviceUid)",
            "runtime.requestCustom(deviceUid)",
            "runtime.requestAutoPrograms(deviceUid)",
            "runtime.requestGraph(deviceUid)",
            "thermal.requestStatus(deviceUid)",
            "protection.requestStatus(deviceUid)",
            "verifyAuthoritativeSurface",
        ):
            self.assertIn(token, coordinator)

        self.assertNotIn("MutableStateFlow", coordinator)
        self.assertNotIn("MutableSharedFlow", coordinator)
        self.assertIn(
            "generation: DeviceRuntimeConnectionGeneration",
            coordinator,
        )
        self.assertIn(
            "runtime.isAuthoritative(deviceUid, generation)",
            coordinator,
        )

        dashboard = (
            RUNTIME / "DeviceLightDashboardRefreshCoordinator.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn("inFlight", dashboard)
        self.assertNotIn("reconcileCommitted", dashboard)

    def test_light_bootstrap_uses_the_same_central_refresh_flight(self):
        provider = MODULE_PROVIDER.read_text(encoding="utf-8")

        self.assertIn(
            "private val lightRuntimeRefreshCoordinator = DeviceLightRuntimeRefreshCoordinator(",
            provider,
        )
        self.assertIn(
            "LightRuntimeBootstrapPort(lightRuntimeRefreshCoordinator)",
            provider,
        )
        self.assertIn(
            "refreshCoordinator.refreshGeneration(",
            provider,
        )
        self.assertIn(
            "lightRuntimeRefreshCoordinator.refreshAll(deviceUid)",
            provider,
        )

    def test_feature_refresh_paths_reuse_the_shared_coordinator(self):
        paths = (
            "automatic/DefaultDeviceLightAutomaticOperations.kt",
            "custom/DefaultDeviceLightCustomOperations.kt",
            "system/DefaultDeviceLightSystemOperations.kt",
            "adaptation/DefaultDeviceLightAdaptationOperations.kt",
        )
        for relative in paths:
            source = (DATA_LIGHT / relative).read_text(encoding="utf-8")
            self.assertIn("refreshLightRuntime(", source, relative)

        automatic = (DATA_LIGHT / paths[0]).read_text(encoding="utf-8")
        custom = (DATA_LIGHT / paths[1]).read_text(encoding="utf-8")
        automatic_read = automatic[
            automatic.index("override suspend fun read"):
            automatic.index("override suspend fun create")
        ]
        custom_read = custom[
            custom.index("override suspend fun read"):
            custom.index("override suspend fun applyToDevice")
        ]
        self.assertNotIn("runtime.currentStatus(uid) == null", automatic_read)
        self.assertNotIn("runtime.currentStatus(uid) == null", custom_read)
        self.assertIn("runtime.currentStatus(uid) == null", custom)

    def test_light_fragments_do_not_own_foreground_recovery(self):
        fragments = (
            "adaptation/DeviceLightAdaptationFragment.kt",
            "system/DeviceLightSystemFragment.kt",
            "automatic/programs/DeviceLightAutomaticProgramsFragment.kt",
            "custom/DeviceLightCustomCurveFragment.kt",
            "automatic/editor/DeviceLightAutomaticProgramEditorFragment.kt",
        )
        ui_root = ROOT / (
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/presentation"
        )
        for relative in fragments:
            source = (ui_root / relative).read_text(encoding="utf-8")
            self.assertNotIn("override fun onResume()", source, relative)

    def test_runtime_state_owner_remains_the_only_light_snapshot_owner(self):
        owner = (RUNTIME / "DeviceLightRuntimeStateOwner.kt").read_text(encoding="utf-8")
        coordinator = (
            RUNTIME / "DeviceLightRuntimeRefreshCoordinator.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("class DeviceLightRuntimeStateOwner", owner)
        self.assertIn("DeviceLightRuntimeAuthorityCoordinator", owner)
        self.assertNotIn("DeviceLightRuntimeStateOwner()", coordinator)


if __name__ == "__main__":
    unittest.main()
