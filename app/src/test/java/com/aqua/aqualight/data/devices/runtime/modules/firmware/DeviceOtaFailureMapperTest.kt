package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaFailureMapperTest {

    @Test
    fun `firmware invalid wifi value maps to recoverable connection guidance`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.WIFI,
                message = "Wi-Fi client connection is required before OTA download"
            )
        )

        assertEquals(DeviceOtaFailureReason.CONNECTION, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(DeviceFirmwareRuntimeContract.ErrorField.WIFI, failure.field)
    }

    @Test
    fun `firmware product mismatch maps to terminal compatibility guidance`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.PRODUCT_KEY,
                message = "firmware productKey does not match this device"
            )
        )

        assertEquals(DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `firmware storage error maps to terminal flash write guidance`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.STORAGE_ERROR,
                field = DeviceFirmwareRuntimeContract.ErrorField.FLASH,
                message = "Update.begin failed"
            )
        )

        assertEquals(DeviceOtaFailureReason.FLASH_WRITE_FAILED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `failed snapshot sha mismatch maps to integrity guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.SHA256,
                message = "downloaded firmware SHA256 does not match manifest"
            )
        )

        assertEquals(DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `failed snapshot http 404 maps to unavailable official release`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status 404",
                httpStatus = 404
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_UNAVAILABLE, failure.reason)
        assertFalse(failure.recoverable)
        assertEquals(404, failure.httpStatus)
    }

    private fun firmwareError(
        code: String,
        field: String,
        message: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DEVICE_UID,
        module = DeviceFirmwareRuntimeContract.MODULE,
        action = DeviceFirmwareRuntimeContract.Action.OTA_START,
        messageId = "response-1",
        generation = RUNTIME_GENERATION,
        statusCode = 422,
        code = code,
        field = field,
        message = message
    )

    private fun failedSnapshot(
        field: String,
        message: String,
        httpStatus: Int = 0
    ) = DeviceFirmwareOtaSnapshot(
        phase = DeviceFirmwareOtaPhase.FAILED,
        phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
        completed = true,
        failed = true,
        lastError = message,
        lastErrorField = field,
        httpStatus = httpStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-OTA-FAILURE-MAPPER")
        val RUNTIME_GENERATION = DeviceRuntimeConnectionGeneration(7L)
    }
}
