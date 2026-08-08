package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareNotificationActionabilityPolicyTest {

    @Test
    fun newerAvailabilityTargetIsActionable() {
        assertTrue(
            DeviceFirmwareNotificationActionabilityPolicy.availability(
                currentVersion = "1.0.0",
                targetVersion = "1.1.0"
            )
        )
    }

    @Test
    fun staleOrMalformedAvailabilityTargetIsRejected() {
        assertFalse(
            DeviceFirmwareNotificationActionabilityPolicy.availability(
                currentVersion = "1.1.0",
                targetVersion = "1.1.0"
            )
        )
        assertFalse(
            DeviceFirmwareNotificationActionabilityPolicy.availability(
                currentVersion = "1.1.0",
                targetVersion = "invalid"
            )
        )
    }

    @Test
    fun availabilityFailureCannotAuthorizeOperationRoute() {
        val state = DeviceOtaState.Failed(
            deviceUid = DEVICE_UID,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.CONNECTION,
                recoverable = true,
                stage = DeviceOtaFailureStage.AVAILABILITY_CHECK
            )
        )

        assertFalse(
            DeviceFirmwareNotificationActionabilityPolicy.operation(
                state,
                expectedTargetVersion = ""
            )
        )
    }

    @Test
    fun executionFailureCanAuthorizeOperationRoute() {
        val state = DeviceOtaState.Failed(
            deviceUid = DEVICE_UID,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.DOWNLOAD_TIMEOUT,
                recoverable = true,
                stage = DeviceOtaFailureStage.UPDATE_EXECUTION
            )
        )

        assertTrue(
            DeviceFirmwareNotificationActionabilityPolicy.operation(
                state,
                expectedTargetVersion = ""
            )
        )
    }

    @Test
    fun targetMismatchRejectsActiveOperationIntent() {
        val state = DeviceOtaState.Starting(
            plan = preparedPlan(targetVersion = "1.2.0"),
            requestId = "request-1"
        )

        assertFalse(
            DeviceFirmwareNotificationActionabilityPolicy.operation(
                state,
                expectedTargetVersion = "1.1.0"
            )
        )
        assertTrue(
            DeviceFirmwareNotificationActionabilityPolicy.operation(
                state,
                expectedTargetVersion = "1.2.0"
            )
        )
    }

    private fun preparedPlan(targetVersion: String) = PreparedDeviceFirmwareUpdate(
        deviceUid = DEVICE_UID,
        currentVersion = "1.0.0",
        targetVersion = targetVersion,
        channel = "stable",
        environment = "dosing_dose_pro_4",
        productKey = "DOSING_DOSE_PRO_4",
        productId = "com.aqualight.dosing.dose_pro_4",
        model = "dose_pro_4",
        hardwareRevision = "2.0",
        displayName = "Dose Pro 4",
        filename = "firmware.bin",
        downloadUrl = "https://example.invalid/firmware.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1,
        applyNow = true
    )

    private companion object {
        const val DEVICE_UID = "dose-pro-4"
    }
}
