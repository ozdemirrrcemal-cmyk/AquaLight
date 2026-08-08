package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.application.devices.DeviceOtaState
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
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeContract
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFirmwareAvailabilityEvaluatorTest {

    @Test
    fun `background no-update outcome clears stale availability centrally`() = runTest {
        val notifications = RecordingNotifications()
        val evaluator = DeviceFirmwareAvailabilityEvaluator(
            ownerProvider = StableOwnerProvider,
            notifications = notifications,
            hintEvaluator = { snapshot, _ ->
                Result.success(
                    DeviceFirmwareAvailabilityHint.UpToDate(
                        deviceUid = snapshot.deviceUid.value,
                        deviceName = "Dose Pro 4",
                        currentVersion = "1.0.0",
                        targetVersion = "1.0.0"
                    )
                )
            }
        )

        val outcome = evaluator.evaluate(
            ownerUid = OWNER_UID,
            snapshots = listOf(snapshot()),
            manifest = manifest()
        )

        assertEquals(DeviceFirmwareAvailabilityCheckOutcome.Completed, outcome)
        assertEquals(listOf("clear:$DEVICE_UID"), notifications.events)
    }

    @Test
    fun `background genuine update still publishes one central hint`() = runTest {
        val notifications = RecordingNotifications()
        val evaluator = DeviceFirmwareAvailabilityEvaluator(
            ownerProvider = StableOwnerProvider,
            notifications = notifications,
            hintEvaluator = { snapshot, _ ->
                Result.success(
                    DeviceFirmwareAvailabilityHint.UpdateAvailable(
                        deviceUid = snapshot.deviceUid.value,
                        deviceName = "WRGB Pro Elite 120",
                        currentVersion = "1.0.0",
                        targetVersion = "1.1.0"
                    )
                )
            }
        )

        val outcome = evaluator.evaluate(
            ownerUid = OWNER_UID,
            snapshots = listOf(snapshot()),
            manifest = manifest()
        )

        assertEquals(DeviceFirmwareAvailabilityCheckOutcome.Completed, outcome)
        assertEquals(listOf("publish:$DEVICE_UID:1.1.0"), notifications.events)
    }

    private class RecordingNotifications : DeviceFirmwareUpdateNotificationOperations {
        val events = mutableListOf<String>()

        override suspend fun publishOtaState(
            ownerUid: String,
            state: DeviceOtaState,
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
        ) = Unit

        override suspend fun clearOwner(ownerUid: String) = Unit
    }

    private object StableOwnerProvider : AuthenticatedOwnerProvider {
        private val owner = AuthenticatedOwner(
            uid = OWNER_UID,
            email = "",
            displayName = "",
            photoUrl = "",
            isEmailVerified = true
        )
        private val ownerState = MutableStateFlow<AuthenticatedOwnerState>(
            AuthenticatedOwnerState.Authenticated(owner)
        )

        override val state: StateFlow<AuthenticatedOwnerState> = ownerState
        override fun currentOwner(): AuthenticatedOwner = owner
        override suspend fun validateCurrentOwner(): OwnerTokenValidationResult =
            OwnerTokenValidationResult.Valid(OWNER_UID)
    }

    private companion object {
        const val OWNER_UID = "owner-evaluator"
        const val DEVICE_UID = "device-evaluator"

        fun snapshot() = DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(DEVICE_UID)),
            product = DeviceProduct(displayName = "Dose Pro 4"),
            firmwareVersion = "1.0.0",
            capabilities = DeviceCapabilities(ota = true)
        )

        fun manifest() = DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = "1.1.0",
            tag = "v1.1.0",
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-08-08T00:00:00Z",
            platform = DeviceFirmwareManifestPlatform("", "", "", "", ""),
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
                defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
                items = emptyList()
            ),
            artifacts = emptyList(),
            signature = DeviceFirmwareManifestSignature("", "", "", "")
        )
    }
}
