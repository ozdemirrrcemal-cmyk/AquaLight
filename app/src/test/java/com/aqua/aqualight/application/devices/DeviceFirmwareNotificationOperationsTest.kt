package com.aqua.aqualight.application.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceFirmwareNotificationOperationsTest {

    @Test
    fun `background no-op is empty successful and reconcilable`() = runTest {
        val result = DeviceFirmwareBackgroundOperations.NoOp
            .refreshRegisteredDevices(
                manifestUrl = "https://example.invalid/manifest.json",
                applyNow = true
            )
            .getOrThrow()

        assertEquals(0, result.inspectedDeviceCount)
        assertEquals(0, result.eligibleDeviceCount)
        assertEquals(0, result.updateAvailableCount)
        assertEquals(0, result.upToDateCount)
        assertEquals(0, result.skippedDeviceCount)
        assertEquals(0, result.failedDeviceCount)
        DeviceFirmwareBackgroundOperations.NoOp.reconcileNotificationState()
    }

    @Test
    fun `default notification route policy fails closed`() {
        assertFalse(
            DeviceFirmwareNotificationRouteOperations.DenyAll.canOpen(
                notificationOwnerUid = "owner-a",
                deviceUid = "device-a"
            )
        )
    }

    @Test
    fun `firmware update operation default release remains source compatible`() = runTest {
        val operations = object : DeviceFirmwareUpdateOperations {
            override suspend fun prepareUpdate(
                deviceUid: String,
                manifestUrl: String,
                applyNow: Boolean
            ): Result<PreparedDeviceFirmwareUpdate> = Result.failure(
                UnsupportedOperationException("not used")
            )

            override suspend fun startUpdate(
                plan: PreparedDeviceFirmwareUpdate
            ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = false)

            override suspend fun requestStatus(
                deviceUid: String
            ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = false)

            override suspend fun clearStatus(
                deviceUid: String
            ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = false)
        }

        operations.releaseDevice("device-a")
    }
}
