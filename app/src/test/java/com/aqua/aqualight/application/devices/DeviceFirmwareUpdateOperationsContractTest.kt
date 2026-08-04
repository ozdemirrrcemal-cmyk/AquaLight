package com.aqua.aqualight.application.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareUpdateOperationsContractTest {

    @Test
    fun `default application boundary exposes idle and prepared update states`() = runTest {
        val plan = preparedPlan()
        val operations = FakeFirmwareOperations(plan)

        assertEquals(DeviceOtaState.Idle("device-1"), operations.observe("device-1").value)
        assertEquals(
            DeviceOtaState.UpdateAvailable(plan),
            operations.checkAvailability("device-1").getOrThrow()
        )
        assertEquals(DeviceFirmwareChannel.STABLE, operations.lastPreparedChannel)
        operations.close()
    }

    @Test
    fun `application boundary exposes typed isolated channels instead of URLs`() = runTest {
        val operations = FakeFirmwareOperations(preparedPlan())

        operations.checkAvailability(
            deviceUid = "device-1",
            channel = DeviceFirmwareChannel.BETA,
            applyNow = false
        ).getOrThrow()

        assertEquals(DeviceFirmwareChannel.BETA, operations.lastPreparedChannel)
        assertFalse(operations.lastApplyNow)
    }

    @Test
    fun `release content and terminal availability states retain exact values`() {
        val content = DeviceFirmwareReleaseContent(
            localeTag = "tr-TR",
            title = "Güvenli güncelleme",
            summary = "Kararlılık iyileştirmeleri.",
            changes = listOf("Kalibrasyon doğrulaması geliştirildi."),
            warnings = listOf("Cihaz yeniden başlatılır."),
            mandatory = true
        )

        assertTrue(content.isPresent)
        assertFalse(DeviceFirmwareReleaseContent.EMPTY.isPresent)

        val unsupported = DeviceOtaState.Unsupported("device-1")
        val upToDate = DeviceOtaState.UpToDate("device-1", "2.0.0", "2.0.0", content)
        val succeeded = DeviceOtaState.Succeeded("device-1", "2.0.0", content)

        assertEquals("device-1", unsupported.deviceUid)
        assertSame(content, upToDate.releaseContent)
        assertEquals("2.0.0", succeeded.targetVersion)
        assertSame(content, succeeded.releaseContent)
    }

    private fun preparedPlan() = PreparedDeviceFirmwareUpdate(
        deviceUid = "device-1",
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
        downloadUrl = "https://example.invalid/firmware.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_048_576,
        applyNow = true,
        manifestTag = "dosing_dose_pro_2-v2.0.0"
    )

    private class FakeFirmwareOperations(
        private val plan: PreparedDeviceFirmwareUpdate
    ) : DeviceFirmwareUpdateOperations {
        var lastPreparedChannel: DeviceFirmwareChannel? = null
        var lastApplyNow: Boolean = true

        override suspend fun prepareUpdate(
            deviceUid: String,
            channel: DeviceFirmwareChannel,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> {
            lastPreparedChannel = channel
            lastApplyNow = applyNow
            return Result.success(plan)
        }

        override suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate) =
            DeviceFirmwareCommandResult(sent = true)

        override suspend fun requestStatus(deviceUid: String) = DeviceFirmwareCommandResult(sent = true)

        override suspend fun clearStatus(deviceUid: String) = DeviceFirmwareCommandResult(sent = true)
    }
}
