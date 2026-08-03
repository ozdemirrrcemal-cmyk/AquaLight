package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceOtaStaleTerminalStatusCharacterizationTest {

    @Test
    fun `matching terminal failure remains a firmware download failure`() {
        val plan = selectedPlan()
        val snapshot = failedSnapshot(
            targetVersion = plan.targetVersion,
            sha256Expected = plan.firmware.sha256
        )

        assertNull(DeviceOtaValidator.snapshotAgainstPlan(snapshot, plan))
        val state = DeviceOtaStateMapper.map(
            snapshot = snapshot,
            deviceUid = DEVICE_UID,
            targetVersion = plan.targetVersion,
            releaseContent = DeviceFirmwareReleaseContent.EMPTY
        ) as DeviceOtaState.Failed

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_FAILED, state.failure.reason)
        assertEquals(FIRMWARE_DOWNLOAD_ERROR, state.failure.diagnosticMessage)
    }

    @Test
    fun `previous target terminal snapshot becomes a protocol mismatch`() {
        val plan = selectedPlan()
        val snapshot = failedSnapshot(
            targetVersion = PREVIOUS_TARGET_VERSION,
            sha256Expected = plan.firmware.sha256
        )

        val validationError = requireNotNull(
            DeviceOtaValidator.snapshotAgainstPlan(snapshot, plan)
        )
        val failure = DeviceOtaFailureMapper.protocol(validationError)

        assertEquals(TARGET_VERSION_MISMATCH, validationError)
        assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, failure.reason)
        assertEquals(TARGET_VERSION_MISMATCH, failure.diagnosticMessage)
    }

    @Test
    fun `same target terminal snapshot with stale digest becomes a protocol mismatch`() {
        val plan = selectedPlan()
        val snapshot = failedSnapshot(
            targetVersion = plan.targetVersion,
            sha256Expected = STALE_SHA256
        )

        val validationError = requireNotNull(
            DeviceOtaValidator.snapshotAgainstPlan(snapshot, plan)
        )
        val failure = DeviceOtaFailureMapper.protocol(validationError)

        assertEquals(SHA256_MISMATCH, validationError)
        assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, failure.reason)
        assertEquals(SHA256_MISMATCH, failure.diagnosticMessage)
    }

    private fun selectedPlan(): DeviceFirmwareUpdatePlan {
        val firmware = DeviceFirmwareAsset(
            version = TARGET_VERSION,
            filename = FIRMWARE_FILENAME,
            url = FIRMWARE_URL,
            sha256 = EXPECTED_SHA256,
            size = FIRMWARE_SIZE,
            format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
            otaSlotCompatible = true
        )
        return DeviceFirmwareUpdatePlan(
            deviceUid = DEVICE_UID,
            currentVersion = CURRENT_VERSION,
            targetVersion = TARGET_VERSION,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            env = ENVIRONMENT,
            productKey = PRODUCT_KEY,
            productId = PRODUCT_ID,
            model = MODEL,
            hardwareRevision = HARDWARE_REVISION,
            displayName = DISPLAY_NAME,
            firmware = firmware,
            payload = DeviceFirmwareOtaStartPayload(
                url = firmware.url,
                version = firmware.version,
                sha256 = firmware.sha256,
                expectedSize = firmware.size,
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                model = MODEL,
                hardwareRevision = HARDWARE_REVISION
            ),
            runtimeMetadataGeneration = RUNTIME_GENERATION,
            manifestTag = TARGET_TAG
        )
    }

    private fun failedSnapshot(
        targetVersion: String,
        sha256Expected: String
    ): DeviceFirmwareOtaSnapshot = DeviceFirmwareOtaSnapshot(
        phase = DeviceFirmwareOtaPhase.FAILED,
        phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
        completed = true,
        failed = true,
        startedAtMs = STARTED_AT_MILLIS,
        finishedAtMs = FINISHED_AT_MILLIS,
        contentLength = FIRMWARE_SIZE.toLong(),
        targetVersion = targetVersion,
        sha256Expected = sha256Expected,
        lastError = FIRMWARE_DOWNLOAD_ERROR,
        lastErrorField = DeviceFirmwareRuntimeContract.ErrorField.STREAM,
        urlScheme = HTTPS_SCHEME,
        httpStatus = HTTP_SERVICE_UNAVAILABLE
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-STALE-OTA-STATUS")

        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "1.0.1"
        const val PREVIOUS_TARGET_VERSION = "0.9.9"
        const val TARGET_TAG = "v1.0.1"
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val MODEL = "dose_pro_2"
        const val HARDWARE_REVISION = "2.0"
        const val DISPLAY_NAME = "Dose Pro 2"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val FIRMWARE_FILENAME = "AquaLight-dosing_dose_pro_2-v1.0.1-ota.bin"
        const val EXPECTED_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val STALE_SHA256 =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val FIRMWARE_SIZE = 1_048_576
        const val RUNTIME_GENERATION = 7L
        const val STARTED_AT_MILLIS = 1L
        const val FINISHED_AT_MILLIS = 2L
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val HTTPS_SCHEME = "https"
        const val FIRMWARE_DOWNLOAD_ERROR = "previous OTA download failed"
        const val TARGET_VERSION_MISMATCH =
            "Firmware OTA targetVersion differs from the selected artifact."
        const val SHA256_MISMATCH =
            "Firmware OTA expected SHA256 differs from the selected artifact."
        const val FIRMWARE_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/v1.0.1/AquaLight-dosing_dose_pro_2-v1.0.1-ota.bin"
    }
}
