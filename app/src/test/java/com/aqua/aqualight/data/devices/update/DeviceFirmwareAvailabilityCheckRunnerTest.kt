package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.application.notifications.DeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import com.aqua.aqualight.application.notifications.NotificationPermissionPolicy
import com.aqua.aqualight.application.notifications.NotificationPreferenceRepository
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.notifications.NotificationRenderer
import com.aqua.aqualight.application.notifications.NotificationScheduler
import com.aqua.aqualight.data.auth.AuthenticatedOwner
import com.aqua.aqualight.data.auth.AuthenticatedOwnerProvider
import com.aqua.aqualight.data.auth.AuthenticatedOwnerState
import com.aqua.aqualight.data.auth.OwnerTokenValidationResult
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifest
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifestPlatform
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifestSignature
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareReleaseNotes
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareAvailabilityCheckRunnerTest {

    @Test
    fun ownerChangeAfterSnapshotLoadFailsClosedBeforeManifestOrDispatch() = runTest {
        val ownerProvider = FakeOwnerProvider()
        val notifications = FakeNotifications()
        var manifestLoads = 0
        val runner = runner(
            ownerProvider = ownerProvider,
            notifications = notifications,
            snapshotReader = DeviceFirmwareAvailabilitySnapshotReader {
                ownerProvider.currentUid = "owner-b"
                readyResult()
            },
            manifestLoader = {
                manifestLoads += 1
                Result.success(manifest())
            }
        )

        val outcome = runner.execute(OWNER_UID)

        assertEquals(DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged, outcome)
        assertEquals(0, manifestLoads)
        assertTrue(notifications.events.isEmpty())
    }

    @Test
    fun transientOwnerValidationFailureRequestsBoundedWorkerRetry() = runTest {
        val ownerProvider = FakeOwnerProvider(
            validation = OwnerTokenValidationResult.TransientFailure(
                ownerUid = OWNER_UID,
                error = IllegalStateException("network")
            )
        )
        var snapshotLoads = 0
        val runner = runner(
            ownerProvider = ownerProvider,
            snapshotReader = DeviceFirmwareAvailabilitySnapshotReader {
                snapshotLoads += 1
                readyResult()
            }
        )

        val outcome = runner.execute(OWNER_UID)

        assertEquals(
            DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure(
                DeviceFirmwareAvailabilityFailureStage.OWNER_VALIDATION
            ),
            outcome
        )
        assertEquals(0, snapshotLoads)
    }

    @Test
    fun trustedSnapshotPublishesOnlyAfterOwnerAndPreferenceValidation() = runTest {
        val notifications = FakeNotifications()
        val runner = runner(notifications = notifications)

        val outcome = runner.execute(OWNER_UID)

        assertEquals(DeviceFirmwareAvailabilityCheckOutcome.Completed, outcome)
        assertEquals(
            listOf("reconcile:device-a", "publish:device-a:2.0.0"),
            notifications.events
        )
    }

    private fun runner(
        ownerProvider: FakeOwnerProvider = FakeOwnerProvider(),
        notifications: FakeNotifications = FakeNotifications(),
        snapshotReader: DeviceFirmwareAvailabilitySnapshotReader =
            DeviceFirmwareAvailabilitySnapshotReader { readyResult() },
        manifestLoader: suspend (String) -> Result<DeviceFirmwareManifest> = {
            Result.success(manifest())
        }
    ): DeviceFirmwareAvailabilityCheckRunner {
        return DeviceFirmwareAvailabilityCheckRunner(
            ownerProvider = ownerProvider,
            preferenceUseCase = preferenceUseCase(),
            notifications = notifications,
            snapshotReader = snapshotReader,
            manifestLoader = manifestLoader,
            hintEvaluator = { snapshot, _ ->
                Result.success(
                    DeviceFirmwareAvailabilityHint.UpdateAvailable(
                        deviceUid = snapshot.deviceUid.value,
                        deviceName = "Aqua Light",
                        currentVersion = "1.0.0",
                        targetVersion = "2.0.0"
                    )
                )
            }
        )
    }

    private class FakeOwnerProvider(
        var currentUid: String? = OWNER_UID,
        var validation: OwnerTokenValidationResult =
            OwnerTokenValidationResult.Valid(OWNER_UID)
    ) : AuthenticatedOwnerProvider {
        private val ownerState = MutableStateFlow<AuthenticatedOwnerState>(
            AuthenticatedOwnerState.Authenticated(owner())
        )

        override val state: StateFlow<AuthenticatedOwnerState> = ownerState

        override fun currentOwner(): AuthenticatedOwner? {
            return currentUid?.let { uid -> owner(uid) }
        }

        override suspend fun validateCurrentOwner(): OwnerTokenValidationResult {
            return validation
        }

        private fun owner(uid: String = OWNER_UID): AuthenticatedOwner {
            return AuthenticatedOwner(
                uid = uid,
                email = "",
                displayName = "",
                photoUrl = "",
                isEmailVerified = true
            )
        }
    }

    private class FakeNotifications : DeviceFirmwareUpdateNotificationOperations {
        val events = mutableListOf<String>()

        override suspend fun publishOtaState(
            ownerUid: String,
            state: com.aqua.aqualight.application.devices.DeviceOtaState,
            deviceName: String
        ) = Unit

        override suspend fun publishAvailabilityHint(
            ownerUid: String,
            hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
        ): Boolean {
            events += "publish:${hint.deviceUid}:${hint.targetVersion}"
            return true
        }

        override suspend fun clearAvailability(ownerUid: String, deviceUid: String) {
            events += "clear:$deviceUid"
        }

        override suspend fun clearDeletedDevices(
            ownerUid: String,
            deviceUids: Set<String>
        ): Set<String> = emptySet()

        override suspend fun reconcileDevices(
            ownerUid: String,
            currentDeviceUids: Set<String>
        ) {
            events += "reconcile:${currentDeviceUids.sorted().joinToString()}"
        }

        override suspend fun clearOwner(ownerUid: String) = Unit
    }

    private companion object {
        const val OWNER_UID = "owner-a"

        fun readyResult(): DeviceFirmwareAvailabilitySnapshotResult.Ready {
            val snapshot = DeviceSnapshot(
                identity = DeviceIdentity(uid = DeviceUid("device-a")),
                product = DeviceProduct(),
                firmwareVersion = "1.0.0",
                capabilities = DeviceCapabilities(ota = true)
            )
            return DeviceFirmwareAvailabilitySnapshotResult.Ready(
                currentDeviceUids = setOf(snapshot.deviceUid.value),
                eligibleSnapshots = listOf(snapshot)
            )
        }

        fun manifest(): DeviceFirmwareManifest {
            return DeviceFirmwareManifest(
                schema = "test",
                brand = "AquaLight",
                channel = "stable",
                version = "2.0.0",
                tag = "v2.0.0",
                releaseRepo = "test",
                generatedAt = "2026-08-07T00:00:00Z",
                platform = DeviceFirmwareManifestPlatform("", "", "", "", ""),
                releaseNotes = DeviceFirmwareReleaseNotes("", "", emptyList()),
                artifacts = emptyList(),
                signature = DeviceFirmwareManifestSignature("", "", "", "")
            )
        }

        fun preferenceUseCase(): NotificationPreferenceUseCase {
            val repository = EnabledPreferenceRepository()
            return NotificationPreferenceUseCase(
                repository = repository,
                permissionPolicy = DeliverablePermissionPolicy(),
                scheduler = NoOpNotificationScheduler,
                deviceUpdateWorkCoordinator = NoOpDeviceWorkCoordinator,
                renderer = NoOpNotificationRenderer
            )
        }
    }
}

private class EnabledPreferenceRepository : NotificationPreferenceRepository {
    private val enabled = MutableStateFlow(true)

    override fun enabledFlow(ownerUid: String): Flow<Boolean> = enabled
    override suspend fun isEnabled(ownerUid: String): Boolean = enabled.value
    override suspend fun setEnabled(ownerUid: String, enabled: Boolean) {
        this.enabled.value = enabled
    }
}

private class DeliverablePermissionPolicy : NotificationPermissionPolicy {
    override fun ensureChannels() = Unit

    override fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness {
        return NotificationDeliveryReadiness(
            runtimePermissionGranted = true,
            appNotificationsEnabled = true,
            channelState = NotificationChannelState.ENABLED
        )
    }

    override fun channelId(category: NotificationCategory): String = category.name
}

private object NoOpNotificationScheduler : NotificationScheduler {
    override suspend fun scheduleCareTask(ownerUid: String, taskId: Long) = Unit
    override suspend fun cancelCareTask(ownerUid: String, taskId: Long) = Unit
    override suspend fun reconcileOwner(ownerUid: String) = Unit
    override suspend fun cancelOwner(ownerUid: String) = Unit
}

private object NoOpDeviceWorkCoordinator : DeviceUpdateNotificationWorkCoordinator {
    override suspend fun reconcileOwner(ownerUid: String) = Unit
    override fun cancelOwner(ownerUid: String) = Unit
}

private object NoOpNotificationRenderer : NotificationRenderer {
    override fun renderCareReminder(
        notification: com.aqua.aqualight.application.notifications.CareReminderNotification
    ) = Unit

    override fun renderDeviceAlert(
        notification: com.aqua.aqualight.application.notifications.DeviceAlertNotification
    ) = Unit

    override fun renderDeviceUpdate(
        notification: com.aqua.aqualight.application.notifications.DeviceUpdateNotification
    ) = Unit

    override fun cancelCareReminder(ownerUid: String, taskId: Long) = Unit
    override fun cancelOwner(ownerUid: String) = Unit
}
