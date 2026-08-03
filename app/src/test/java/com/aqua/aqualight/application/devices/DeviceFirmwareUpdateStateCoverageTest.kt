package com.aqua.aqualight.application.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareUpdateStateCoverageTest {

    @Test
    fun `shared ota states retain exact device and release values`() {
        val content = releaseContent()
        val plan = preparedPlan(content)
        val failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
            recoverable = false,
            field = "sha256",
            diagnosticMessage = "downloaded firmware SHA256 does not match manifest"
        )
        val states = listOf<DeviceOtaState>(
            DeviceOtaState.Idle(DEVICE_UID),
            DeviceOtaState.Checking(DEVICE_UID, "1.0.0"),
            DeviceOtaState.Unsupported(DEVICE_UID),
            DeviceOtaState.UpToDate(DEVICE_UID, "2.0.0", "2.0.0", content),
            DeviceOtaState.UpdateAvailable(plan),
            DeviceOtaState.Starting(plan, "request-1"),
            DeviceOtaState.InProgress(
                deviceUid = DEVICE_UID,
                targetVersion = "2.0.0",
                phase = DeviceOtaProgressPhase.WRITING,
                progressPermille = 500,
                bytesWritten = 512,
                contentLength = 1_024,
                releaseContent = content
            ),
            DeviceOtaState.Recovering(DEVICE_UID, "2.0.0", 500),
            DeviceOtaState.RestartRequired(DEVICE_UID, "2.0.0", true, content),
            DeviceOtaState.Succeeded(DEVICE_UID, "2.0.0", content),
            DeviceOtaState.Failed(DEVICE_UID, failure)
        )

        assertTrue(content.isPresent)
        assertTrue(states.all { state -> state.deviceUid == DEVICE_UID })
        assertEquals(DeviceOtaProgressPhase.WRITING, (states[6] as DeviceOtaState.InProgress).phase)
        assertEquals("Güvenli güncelleme", content.title)
        assertEquals(
            DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
            (states[10] as DeviceOtaState.Failed).failure.reason
        )
        assertFalse(DeviceFirmwareReleaseContent.EMPTY.isPresent)
    }

    @Test
    fun `default application boundary maps prepared plan and exposes idle state`() = runTest {
        val operations = FakeFirmwareUpdateOperations(preparedPlan(releaseContent()))

        val observed = operations.observe(DEVICE_UID).value
        val availability = operations.checkAvailability(
            deviceUid = DEVICE_UID,
            manifestUrl = MANIFEST_URL,
            applyNow = false
        ).getOrThrow()

        assertEquals(DeviceOtaState.Idle(DEVICE_UID), observed)
        assertTrue(availability is DeviceOtaState.UpdateAvailable)
        assertTrue(DeviceFirmwareCommandResult(sent = true, messageId = "request-1").isSuccess)
        assertFalse(
            DeviceFirmwareCommandResult(
                sent = false,
                failure = DeviceOtaFailure(
                    reason = DeviceOtaFailureReason.CONNECTION,
                    recoverable = true
                )
            ).isSuccess
        )
        operations.close()
    }

    private class FakeFirmwareUpdateOperations(
        private val plan: PreparedDeviceFirmwareUpdate
    ) : DeviceFirmwareUpdateOperations {
        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> = Result.success(plan.copy(applyNow = applyNow))

        override suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate) =
            DeviceFirmwareCommandResult(sent = true, messageId = "request-1")

        override suspend fun requestStatus(deviceUid: String) = DeviceFirmwareCommandResult(sent = true)

        override suspend fun clearStatus(deviceUid: String) = DeviceFirmwareCommandResult(sent = true)
    }

    private fun releaseContent() = DeviceFirmwareReleaseContent(
        localeTag = "tr-TR",
        title = "Güvenli güncelleme",
        summary = "Kararlılık iyileştirmeleri.",
        changes = listOf("OTA doğrulaması güçlendirildi."),
        warnings = listOf("Cihaz güncelleme sırasında yeniden başlar."),
        mandatory = false
    )

    private fun preparedPlan(content: DeviceFirmwareReleaseContent) = PreparedDeviceFirmwareUpdate(
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
        applyNow = true,
        runtimeMetadataGeneration = 7,
        manifestTag = "v2.0.0",
        releaseContent = content
    )

    private companion object {
        const val DEVICE_UID = "AQL-DP2-OTA-COVERAGE"
        const val MANIFEST_URL = "https://example.invalid/manifest.json"
    }
}
