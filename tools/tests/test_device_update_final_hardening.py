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

    def test_firmware_navigation_idempotency_regression_is_present(self) -> None:
        navigation_test = read(
            "app/src/test/java/com/aqua/aqualight/ui/navigation/"
            "AppRouteNavigatorFirmwarePolicyTest.kt"
        )
        navigator = read(
            "app/src/main/java/com/aqua/aqualight/ui/navigation/AppRouteNavigator.kt"
        )

        self.assertIn("repeatedRequestsForOpenDeviceRemainSingleDestination", navigation_test)
        self.assertIn("repeat(5)", navigation_test)
        self.assertIn("DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen", navigation_test)
        self.assertIn("launchSingleTop = true", navigator)
        self.assertIn("AppRouteOpenResult.ALREADY_OPEN", navigator)


if __name__ == "__main__":
    unittest.main()
