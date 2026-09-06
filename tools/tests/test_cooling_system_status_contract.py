import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
STATUS_ALARMS = MAIN / (
    "java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/status/"
    "CoolingSystemAlarmCard.kt"
)
DASHBOARD = MAIN / (
    "java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation/dashboard/"
    "CoolingDashboardScreen.kt"
)
STRINGS = {
    "en": MAIN / "res/values/device_cooling_strings.xml",
    "tr": MAIN / "res/values-tr/device_cooling_strings.xml",
}


class CoolingSystemStatusContractTest(unittest.TestCase):

    def test_dashboard_alarm_card_has_no_source_or_call_site(self) -> None:
        presentation = MAIN / (
            "java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation"
        )
        removed_sources = list((presentation / "dashboard").glob("*Alarm*Messages*"))
        removed_sources += list((presentation / "common").glob("*Alarm*Commercial*"))
        self.assertEqual([], removed_sources)
        self.assertNotIn("alarm-messages", DASHBOARD.read_text(encoding="utf-8"))

    def test_status_screen_keeps_every_firmware_alarm_identity_visible(self) -> None:
        text = STATUS_ALARMS.read_text(encoding="utf-8")

        self.assertIn("telemetry.alarms.forEachIndexed", text)
        self.assertIn("alarm.diagnosticCode", text)
        self.assertIn("alarm.severity.toStatusTextRes()", text)
        self.assertIn("alarm.latched", text)
        self.assertIn("alarm.affectedKey", text)
        self.assertIn("alarm.diagnosticReason", text)

    def test_status_copy_is_bilingual_and_old_alarm_keys_are_gone(self) -> None:
        keys = {
            locale: set(re.findall(r'<(?:string|plurals) name="([^"]+)"', path.read_text()))
            for locale, path in STRINGS.items()
        }
        status_keys = {
            locale: {key for key in locale_keys if key.startswith("device_cooling_system_status_")}
            for locale, locale_keys in keys.items()
        }

        self.assertEqual(status_keys["en"], status_keys["tr"])
        old_prefix = "device_cooling_" + "alarm_"
        for locale_keys in keys.values():
            self.assertFalse(any(key.startswith(old_prefix) for key in locale_keys))

    def test_status_route_and_all_runtime_compositions_are_wired(self) -> None:
        route = (MAIN / "res/navigation/nav_devices.xml").read_text(encoding="utf-8")
        self.assertIn("deviceCoolingSystemStatusFragment", route)
        self.assertIn("cooling/status", route)

        composition_paths = (
            MAIN / "java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt",
            ROOT / (
                "app/src/debug/java/com/aqua/aqualight/debug/devices/"
                "DebugDeviceFixtureAppContainer.kt"
            ),
            ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt",
        )
        for path in composition_paths:
            with self.subTest(path=path):
                self.assertIn(
                    "DeviceCoolingSystemStatusViewModel",
                    path.read_text(encoding="utf-8"),
                )


if __name__ == "__main__":
    unittest.main()
