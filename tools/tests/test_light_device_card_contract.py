from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class LightDeviceCardContractTest(unittest.TestCase):
    def test_tank_card_observes_light_application_boundary_only(self) -> None:
        view_model = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
            "TankDetailDevicesViewModel.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("DeviceLightCardOperations", view_model)
        self.assertNotIn("data.devices.light", view_model)
        self.assertNotIn("DeviceLightRuntime", view_model)

    def test_card_adapter_reuses_central_light_dashboard_projection(self) -> None:
        operations = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/data/devices/light/dashboard/"
            "DefaultDeviceLightCardOperations.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("DeviceLightControlOperations", operations)
        self.assertIn("controlOperations.observeControl(deviceUid)", operations)
        self.assertIn("controlOperations.refreshControl(deviceUid.value)", operations)
        for forbidden in (
            "DeviceLightStatusParser",
            "requestGraph(",
            "requestStatus(",
            "DeviceLightRuntimeStateOwner",
            "System.currentTimeMillis",
        ):
            self.assertNotIn(forbidden, operations)

    def test_owner_light_bundle_constructs_card_boundary_once(self) -> None:
        owner_graph = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/composition/OwnerDependencyGraph.kt"
        )).read_text(encoding="utf-8")

        self.assertIn("val cardOperations: DeviceLightCardOperations", owner_graph)
        self.assertEqual(1, owner_graph.count("DefaultDeviceLightCardOperations("))

    def test_card_reuses_shared_connection_icon_and_auto_event_icon(self) -> None:
        card = (ROOT / (
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
            "LightDeviceSpotlightCard.kt"
        )).read_text(encoding="utf-8")

        self.assertNotIn("snapshot?.hero?.outputActive", card)
        self.assertNotIn("outerActiveWindow", card)
        self.assertIn("AutomaticCycleEventIcon", card)
        self.assertIn("AutomaticCycleEventKind.SUNRISE", card)
        self.assertIn("AutomaticCycleEventKind.SUNSET", card)
        self.assertIn("ic_status_wifi", card)
        self.assertIn("statusStyle.tintColorRes", card)
        self.assertNotIn("ic_devices", card)


if __name__ == "__main__":
    unittest.main()
