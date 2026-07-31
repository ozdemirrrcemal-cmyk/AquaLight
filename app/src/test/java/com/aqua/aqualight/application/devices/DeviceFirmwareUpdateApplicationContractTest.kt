package com.aqua.aqualight.application.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
            ): DeviceFirmwareOperationResult = DeviceFirmwareOperationResult(
                successful = true,
                correlationId = "start-1"
            )

            override suspend fun requestStatus(deviceUid: String): DeviceFirmwareOperationResult =
                DeviceFirmwareOperationResult(successful = true, correlationId = "status-1")

            override suspend fun clearStatus(deviceUid: String): DeviceFirmwareOperationResult =
                DeviceFirmwareOperationResult(successful = true, correlationId = "clear-1")
        }

        assertEquals(DeviceOtaState.Idle(DEVICE_UID), operations.observe(DEVICE_UID).value)
        assertEquals(
            DeviceOtaState.UpdateAvailable(plan),
            operations.checkAvailability(DEVICE_UID, MANIFEST_URL, applyNow = false).getOrThrow()
        )
        operations.close()

        val releaseContent = plan.releaseContent
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
            DeviceOtaState.Failed(
                DEVICE_UID,
                message = "failed",
                field = "ota",
                recoverable = true
            )
        )

        assertEquals(states.size, states.count { state -> state.deviceUid == DEVICE_UID })
        assertEquals(plan.targetVersion, (states[5] as DeviceOtaState.Starting).plan.targetVersion)
        assertEquals(500, (states[6] as DeviceOtaState.InProgress).progressPermille)
        assertTrue((states[8] as DeviceOtaState.RestartRequired).restartScheduled)
        assertEquals("ota", (states[10] as DeviceOtaState.Failed).field)

        assertTrue(DeviceFirmwareOperationResult(successful = true, correlationId = "ok").successful)
        assertFalse(
            DeviceFirmwareOperationResult(
                successful = false,
                errorMessage = "rejected"
            ).successful
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceFirmwareOperationResult(successful = false, errorMessage = "")
        }
    }

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
        displayName = "Dose Pro 2",
        filename = "AquaLight-dosing_dose_pro_2-v2.0.0-ota.bin",
        downloadUrl = "https://example.invalid/ota.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_024,
        applyNow = false,
        runtimeMetadataGeneration = 7L,
        manifestTag = "v2.0.0",
        releaseContent = DeviceFirmwareReleaseContent(
            localeTag = "tr-TR",
            title = "Güvenli güncelleme",
            summary = "Dozaj güvenilirliği geliştirildi.",
            changes = listOf("Kalibrasyon kontrolleri geliştirildi."),
            warnings = listOf("Güncelleme sırasında cihazı kapatmayın."),
            mandatory = false
        )
    )

    private companion object {
        const val DEVICE_UID = "AQL-DP2-OTA-CONTRACT"
        const val MANIFEST_URL = "https://example.invalid/manifest.json"
    }
}
