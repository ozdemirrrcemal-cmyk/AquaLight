package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaPreparedAvailabilityPolicyTest {

    @Test
    fun `prepared update ignores an uncorrelated historical failed snapshot`() {
        val available = DeviceOtaState.UpdateAvailable(preparedUpdate())
        val historicalFailure = DeviceFirmwareOtaSnapshot(
            phase = DeviceFirmwareOtaPhase.FAILED,
            phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
            active = false,
            completed = true,
            failed = true,
            targetVersion = "1.0.1",
            lastError = "OTA download failed with HTTP status -1",
            lastErrorField = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
            httpStatus = -1
        )

        assertTrue(available.preservesPreparedUpdateFor(historicalFailure))
    }

    @Test
    fun `active transfer and current failures are never hidden by availability policy`() {
        val available = DeviceOtaState.UpdateAvailable(preparedUpdate())
        val active = DeviceFirmwareOtaSnapshot(
            phase = DeviceFirmwareOtaPhase.DOWNLOADING,
            phaseRaw = DeviceFirmwareOtaPhase.DOWNLOADING.wireValue,
            active = true,
            targetVersion = "1.0.1"
        )
        val currentFailure = DeviceOtaState.Failed(
            deviceUid = DEVICE_UID,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.DOWNLOAD_FAILED,
                recoverable = true
            )
        )
        val failedSnapshot = DeviceFirmwareOtaSnapshot(
            phase = DeviceFirmwareOtaPhase.FAILED,
            phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
            active = false,
            completed = true,
            failed = true,
            targetVersion = "1.0.1"
        )

        assertFalse(available.preservesPreparedUpdateFor(active))
        assertFalse(currentFailure.preservesPreparedUpdateFor(failedSnapshot))
    }

    private fun preparedUpdate() = PreparedDeviceFirmwareUpdate(
        deviceUid = DEVICE_UID,
        currentVersion = "1.0.0",
        targetVersion = "1.0.1",
        channel = "stable",
        environment = "light_wrgb_pro_elite",
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        hardwareRevision = "2.0",
        displayName = "WRGB Pro Elite 120",
        filename = "AquaLight-light_wrgb_pro_elite-v1.0.1-ota.bin",
        downloadUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "light_wrgb_pro_elite-v1.0.1/" +
            "AquaLight-light_wrgb_pro_elite-v1.0.1-ota.bin",
        sha256 = "a".repeat(DeviceFirmwareRuntimeContract.Limit.SHA256_HEX_LENGTH),
        sizeBytes = 1_048_576,
        applyNow = true,
        runtimeMetadataGeneration = 7L,
        manifestTag = "light_wrgb_pro_elite-v1.0.1"
    )

    private companion object {
        const val DEVICE_UID = "AQL-WPE-336172"
    }
}
