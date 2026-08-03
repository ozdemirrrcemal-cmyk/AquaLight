package com.aqua.aqualight.application.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
class DeviceFirmwareUpdateApplicationContractTest {

    @Test
    fun `shared ota application contract exposes every family screen state`() = runTest {
        val plan = preparedUpdate()
        val operations = object : DeviceFirmwareUpdateOperations {
            override suspend fun prepareUpdate(
                deviceUid: String,
                manifestUrl: String,
                applyNow: Boolean
            ): Result<PreparedDeviceFirmwareUpdate> = Result.success(plan)

            override suspend fun startUpdate(
                plan: PreparedDeviceFirmwareUpdate
            ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(
                sent = true,
                messageId = "start-1"
            )

            override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
                DeviceFirmwareCommandResult(sent = true, messageId = "status-1")

            override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
                DeviceFirmwareCommandResult(sent = true, messageId = "clear-1")
        }

        assertEquals(DeviceOtaState.Idle(DEVICE_UID), operations.observe(DEVICE_UID).value)
        assertEquals(
            DeviceOtaState.UpdateAvailable(plan),
            operations.checkAvailability(DEVICE_UID, MANIFEST_URL, applyNow = false).getOrThrow()
        )
        operations.close()

        val releaseContent = plan.releaseContent
        val failure = failure()
        assertTrue(releaseContent.isPresent)
        assertFalse(DeviceFirmwareReleaseContent.EMPTY.isPresent)

        val states: List<DeviceOtaState> = listOf(
            DeviceOtaState.Idle(DEVICE_UID),
            DeviceOtaState.Checking(DEVICE_UID, plan.currentVersion),
            DeviceOtaState.Unsupported(DEVICE_UID, "unsupported"),
            DeviceOtaState.UpToDate(
                DEVICE_UID,
                plan.currentVersion,
                plan.targetVersion,
                releaseContent
            ),
            DeviceOtaState.UpdateAvailable(plan),
            DeviceOtaState.Starting(plan, "start-1"),
            DeviceOtaState.InProgress(
                DEVICE_UID,
                plan.targetVersion,
                DeviceOtaProgressPhase.DOWNLOADING,
                progressPermille = 500,
                bytesWritten = 512L,
                contentLength = 1_024L,
                releaseContent = releaseContent
            ),
            DeviceOtaState.Recovering(DEVICE_UID, plan.targetVersion, 500),
            DeviceOtaState.RestartRequired(
                DEVICE_UID,
                plan.targetVersion,
                restartScheduled = true,
                releaseContent = releaseContent
            ),
            DeviceOtaState.Succeeded(DEVICE_UID, plan.targetVersion, releaseContent),
            DeviceOtaState.Failed(DEVICE_UID, failure)
        )

        assertEquals(states.size, states.count { state -> state.deviceUid == DEVICE_UID })
        assertEquals(plan.targetVersion, (states[5] as DeviceOtaState.Starting).plan.targetVersion)
        assertEquals(500, (states[6] as DeviceOtaState.InProgress).progressPermille)
        assertTrue((states[8] as DeviceOtaState.RestartRequired).restartScheduled)
        assertEquals(failure, (states[10] as DeviceOtaState.Failed).failure)

        assertTrue(DeviceFirmwareCommandResult(sent = true, messageId = "ok").isSuccess)
        assertFalse(DeviceFirmwareCommandResult(sent = false, failure = failure).isSuccess)
        assertFalse(
            DeviceFirmwareCommandResult(
                sent = true,
                messageId = failure.requestId,
                failure = failure
            ).isSuccess
        )
    }

    private fun failure() = DeviceFirmwareFailure(
        kind = DeviceFirmwareFailureKind.DOWNLOAD,
        source = DeviceFirmwareFailureSource.FIRMWARE_STATUS,
        stage = DeviceFirmwareFailureStage.TRANSFER,
        technicalMessage = "download failed",
        code = "firmware_ota_failed",
        field = "download",
        statusCode = 422,
        httpStatus = 503,
        requestId = "start-1",
        firmwarePhase = "failed",
        recoverable = true
    )

    private fun preparedUpdate(): PreparedDeviceFirmwareUpdate = PreparedDeviceFirmwareUpdate(
        deviceUid = DEVICE_UID,
        currentVersion = "1.0.0",
        targetVersion = "2.0.0",
        channel = "stable",
        environment = "dosing_dose_pro_2",
        productKey = "DOSING_DOSE_PRO_2",
        productId = "com.aqualight.dosing.dose_pro_2",
        model = "dose_pro_2",
        hardwareRevision = "2.0",
        filename = "AquaLight-dosing_dose_pro_2-v2.0.0-ota.bin",
        downloadUrl = "https://example.invalid/ota.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_024,
        applyNow = false,
        runtimeMetadataGeneration = 7L,
        manifestTag = "v2.0.0",
        releaseContent = DeviceFirmwareReleaseContent(
            localeTag = "tr",
            items = listOf("Kalibrasyon kontrolleri geliştirildi.")
        )
    )

    private companion object {
        const val DEVICE_UID = "AQL-DP2-OTA-CONTRACT"
        const val MANIFEST_URL = "https://example.invalid/manifest.json"
    }
}
