#!/usr/bin/env python3
"""Final static acceptance checks for device-update hardening."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


class DeviceUpdateFinalHardeningTest(unittest.TestCase):

    def test_settings_retries_only_availability_failures(self) -> None:
        view_model = read(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/settings/"
            "DeviceFamilySettingsViewModel.kt"
        )
        fragment = read(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/settings/"
            "DeviceFamilySettingsFragment.kt"
        )

        self.assertIn("DeviceOtaFailureStage.AVAILABILITY_CHECK", view_model)
        self.assertGreaterEqual(view_model.count("failure.canRetryAvailabilityCheck"), 2)
        self.assertGreaterEqual(fragment.count("failure.canRetryAvailabilityCheck"), 2)
        self.assertNotIn("if (state.failure.recoverable)", view_model)

    def test_manifest_http_failures_keep_structured_release_reasons(self) -> None:
        mapper = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
            "DeviceOtaFailureMapper.kt"
        )
        mapper_test = read(
            "app/src/test/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
            "DeviceOtaAvailabilityFailureMapperTest.kt"
        )

        self.assertIn("DeviceManifestHttpFailureClassifier", mapper)
        self.assertIn("RELEASE_RATE_LIMITED", mapper)
        self.assertIn("RELEASE_SERVER_UNAVAILABLE", mapper)
        self.assertIn("RELEASE_ACCESS_DENIED", mapper)
        self.assertIn("manifest service failure preserves release server reason", mapper_test)
        self.assertIn("wrapped manifest rate limit preserves retryable release reason", mapper_test)

    def test_real_fragment_navigation_back_stack_regression_is_present(self) -> None:
        navigation_test = read(
            "app/src/androidTest/java/com/aqua/aqualight/ui/navigation/"
            "DeviceFirmwareNavigationBackStackInstrumentedTest.kt"
        )
        test_manifest = read("app/src/androidTest/AndroidManifest.xml")
        build_gradle = read("app/build.gradle")

        self.assertIn("ActivityScenario.launch", navigation_test)
        self.assertIn("NavHostFragment", navigation_test)
        self.assertIn("FragmentNavigator", navigation_test)
        self.assertIn("repeat(REPEATED_TAPS)", navigation_test)
        self.assertIn("AppRouteOpenResult.ALREADY_OPEN", navigation_test)
        self.assertIn("navController.popBackStack()", navigation_test)
        self.assertIn("REPEATED_TAPS = 5", navigation_test)
        self.assertIn("FirmwareNavigationTestActivity", test_manifest)
        self.assertNotIn("navigation-testing", build_gradle)


if __name__ == "__main__":
    unittest.main()
