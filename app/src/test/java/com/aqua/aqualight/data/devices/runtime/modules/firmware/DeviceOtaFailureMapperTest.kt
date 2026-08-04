package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
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

    @Test
    fun `connection refused maps to exact recoverable transport guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status -1",
                httpStatus = -1
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_CONNECTION_FAILED, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(-1, failure.httpStatus)
    }

    @Test
    fun `read timeout maps to exact retry guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status -11",
                httpStatus = -11
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_TIMEOUT, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `no HTTP server response maps separately from connection refusal`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status -7",
                httpStatus = -7
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_SERVER_NO_RESPONSE, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `HTTP access denial maps to terminal release access guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status 403",
                httpStatus = 403
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_ACCESS_DENIED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `HTTP service failure maps to retryable release server guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status 503",
                httpStatus = 503
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_SERVER_UNAVAILABLE, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `stream closure maps to interrupted download guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.STREAM,
                message = "OTA stream timed out or closed before completion"
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_STREAM_INTERRUPTED, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `downloaded byte mismatch is not reported as insufficient slot space`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.SIZE,
                message = "downloaded byte count does not match manifest size"
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `server content length mismatch is terminal signed release mismatch`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.SIZE,
                message = "HTTP content length does not match manifest size"
            )
        )

        assertEquals(DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH, failure.reason)
        assertFalse(failure.recoverable)
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
