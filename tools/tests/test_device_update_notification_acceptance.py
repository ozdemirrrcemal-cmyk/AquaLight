#!/usr/bin/env python3
"""Regression checks for commercial device-update notification acceptance."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def between(text: str, start: str, end: str) -> str:
    return text.split(start, 1)[1].split(end, 1)[0]


class DeviceUpdateNotificationAcceptanceTest(unittest.TestCase):

    def test_runtime_trigger_is_session_bound_and_centrally_scheduled(self) -> None:
        session = read(
            "app/src/main/java/com/aqua/aqualight/data/auth/"
            "SessionBoundServiceManager.kt"
        )
        owner_session = read(
            "app/src/main/java/com/aqua/aqualight/data/auth/"
            "OwnerSessionCoordinator.kt"
        )
        owner_graph = read(
            "app/src/main/java/com/aqua/aqualight/composition/"
            "OwnerDependencyGraph.kt"
        )
        trigger = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityEventTrigger.kt"
        )
        worker = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityWorker.kt"
        )

        self.assertIn("DeviceFirmwareAvailabilityEventTrigger(", session)
        self.assertIn("registerOwnerScopedResource", session)
        self.assertLess(
            session.index("DevicesRepositoryProvider.clear"),
            session.index("deviceUpdateWorkCoordinator.cancelOwner"),
        )
        self.assertIn("SessionBoundServiceManager.stop(", owner_session)
        self.assertIn("stopPreviousOwnerIfRequired", owner_session)
        self.assertNotIn("DeviceFirmwareAvailabilityEventTrigger", owner_graph)
        self.assertIn("NotificationPlatform.get", trigger)
        self.assertIn("deviceUpdateWorkCoordinator::reconcileOwner", trigger)
        self.assertNotIn("DeviceFirmwareAvailabilityWorker", trigger)
        self.assertNotIn("enqueueValidated", worker)

    def test_fresh_process_trust_survives_temporary_reconnect_state(self) -> None:
        trigger = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityEventTrigger.kt"
        )
        snapshots = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilitySnapshotSource.kt"
        )
        accept_snapshot = between(
            trigger,
            "internal suspend fun acceptSnapshot",
            "internal suspend fun acceptUnavailable",
        )
        load_active = between(
            snapshots,
            "private suspend fun loadActive",
            "private suspend fun isEligible",
        )

        self.assertNotIn("trust.clearDevice", accept_snapshot)
        self.assertNotIn("trust.clearDevice", load_active)
        self.assertIn("trust.recordValidated(ownerUid, snapshot) ||", snapshots)
        self.assertIn("trust.isFresh(ownerUid, snapshot)", snapshots)

    def test_untrusted_alerts_are_cancelled_before_manifest_fetch(self) -> None:
        runner = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityCheckRunner.kt"
        )

        self.assertIn("cancelUntrustedAvailability(", runner)
        self.assertIn(
            "snapshotResult.currentDeviceUids - eligibleDeviceUids",
            runner,
        )
        self.assertLess(
            runner.index("cancelUntrustedAvailability(ownerUid, snapshotResult)"),
            runner.index("manifestLoader(DEVICE_FIRMWARE_MANIFEST_URL)"),
        )

    def test_foreground_and_preference_lifecycle_cannot_leave_orphan_work(self) -> None:
        worker = read(
            "app/src/main/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityWorker.kt"
        )
        contract = read(
            "app/src/main/java/com/aqua/aqualight/application/notifications/"
            "NotificationContracts.kt"
        )

        self.assertNotIn("ProcessLifecycleOwner", worker)
        self.assertNotIn("KEY_ALLOW_FOREGROUND", worker)
        self.assertNotIn("ExistingWorkPolicy.KEEP", worker)
        self.assertIn("ExistingWorkPolicy.REPLACE", worker)
        self.assertIn("deviceUpdateWorkCoordinator.cancelOwner(ownerUid)", contract)

    def test_firmware_route_is_revalidated_before_navigation(self) -> None:
        coordinator = read(
            "app/src/main/java/com/aqua/aqualight/ui/main/"
            "MainNavigationCoordinator.kt"
        )
        gate_test = read(
            "app/src/test/java/com/aqua/aqualight/ui/main/"
            "DeviceFirmwareNotificationRouteGateTest.kt"
        )

        self.assertGreaterEqual(coordinator.count("firmwareRouteGate.evaluate("), 2)
        self.assertIn("ownerChangeBetweenChecksRejectsStaleIntent", gate_test)
        self.assertIn("deviceDeletionBetweenChecksRejectsStaleIntent", gate_test)

    def test_successful_firmware_navigation_dismisses_visible_availability_centrally(self) -> None:
        coordinator = read(
            "app/src/main/java/com/aqua/aqualight/ui/main/"
            "MainNavigationCoordinator.kt"
        )
        route_operations = read(
            "app/src/main/java/com/aqua/aqualight/composition/"
            "ResolvingDeviceFirmwareNotificationRouteOperations.kt"
        )
        publisher = read(
            "app/src/main/java/com/aqua/aqualight/platform/notifications/"
            "AndroidDeviceFirmwareUpdateNotificationPublisher.kt"
        )
        publisher_test = read(
            "app/src/androidTest/java/com/aqua/aqualight/platform/notifications/"
            "AndroidDeviceFirmwareUpdateNotificationPublisherInstrumentedTest.kt"
        )

        self.assertIn("firmwareRouteGate.acknowledgeOpened(deviceUid, ownerUid)", coordinator)
        self.assertIn("dismissOpenedAvailability", route_operations)
        self.assertIn("deviceFirmwareNotifications.dismissAvailability", route_operations)
        self.assertIn("override suspend fun dismissAvailability", publisher)
        self.assertIn(
            "openingAvailabilityDismissesVisibleNotificationButPreservesTargetDedup",
            publisher_test,
        )
        self.assertIn("dismissAvailabilityDoesNotCancelActiveUpdateOperation", publisher_test)

    def test_acceptance_scenarios_have_executable_regressions(self) -> None:
        runner_test = read(
            "app/src/test/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityCheckRunnerTest.kt"
        )
        trust_test = read(
            "app/src/androidTest/java/com/aqua/aqualight/data/devices/update/"
            "DeviceFirmwareAvailabilityTrustStoreInstrumentedTest.kt"
        )
        publisher_test = read(
            "app/src/androidTest/java/com/aqua/aqualight/platform/notifications/"
            "AndroidDeviceFirmwareUpdateNotificationPublisherInstrumentedTest.kt"
        )

        self.assertIn("ownerChangeAfterSnapshotLoadFailsClosed", runner_test)
        self.assertIn("recreatedStoreAcceptsMatchingDurableSnapshotAfterProcessDeath", trust_test)
        self.assertIn("deviceDeletionRemovesPersistedTrust", trust_test)
        self.assertIn("successfulDeviceDeletionCancelsOnlyDeletedDeviceNotification", publisher_test)


if __name__ == "__main__":
    unittest.main()
