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
    fun `typed snapshot failure codes map without diagnostic wording dependency`() {
        data class Expected(
            val code: String,
            val reason: DeviceOtaFailureReason,
            val recoverable: Boolean
        )

        val cases = listOf(
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.SECURE_TIME_NOT_READY,
                DeviceOtaFailureReason.SECURE_TIME_NOT_READY,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.DEVICE_NETWORK_UNAVAILABLE,
                DeviceOtaFailureReason.DEVICE_NETWORK_UNAVAILABLE,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.SAFE_MODE_ENTER_FAILED,
                DeviceOtaFailureReason.SAFE_MODE_FAILED,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.SAFE_MODE_RESTORE_FAILED,
                DeviceOtaFailureReason.SAFE_MODE_RESTORE_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.TLS_TRUST_UNAVAILABLE,
                DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.INSECURE_TRANSPORT,
                DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_URL_OPEN_FAILED,
                DeviceOtaFailureReason.DOWNLOAD_URL_OPEN_FAILED,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.RELEASE_SIZE_MISMATCH,
                DeviceOtaFailureReason.RELEASE_PACKAGE_MISMATCH,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.INSUFFICIENT_SPACE,
                DeviceOtaFailureReason.INSUFFICIENT_SPACE,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.FLASH_BEGIN_FAILED,
                DeviceOtaFailureReason.FLASH_WRITE_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.FLASH_WRITE_FAILED,
                DeviceOtaFailureReason.FLASH_WRITE_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_STREAM_INTERRUPTED,
                DeviceOtaFailureReason.DOWNLOAD_STREAM_INTERRUPTED,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_SIZE_MISMATCH,
                DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH,
                true
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.INTEGRITY_CHECK_FAILED,
                DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
                false
            ),
            Expected(
                DeviceFirmwareRuntimeContract.FailureCode.FLASH_FINALIZE_FAILED,
                DeviceOtaFailureReason.FLASH_WRITE_FAILED,
                false
            )
        )

        cases.forEach { expected ->
            val failure = DeviceOtaFailureMapper.snapshot(
                failedSnapshot(
                    field = "diagnostic-field",
                    message = "Diagnostic wording may change without changing behavior.",
                    failureCode = expected.code
                )
            )

            assertEquals(expected.code, failure.code)
            assertEquals(expected.reason, failure.reason)
            assertEquals(expected.recoverable, failure.recoverable)
        }

        assertEquals(
            DeviceFirmwareRuntimeContract.FailureCode.ALL -
                DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS,
            cases.mapTo(linkedSetOf()) { expected -> expected.code }
        )
    }

    @Test
    fun `typed HTTP failure code keeps signed status classification`() {
        val unavailable = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "Diagnostic text is not part of classification.",
                httpStatus = 503,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
            )
        )
        val timeout = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "Different diagnostic text.",
                httpStatus = -11,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_SERVER_UNAVAILABLE, unavailable.reason)
        assertTrue(unavailable.recoverable)
        assertEquals(DeviceOtaFailureReason.DOWNLOAD_TIMEOUT, timeout.reason)
        assertTrue(timeout.recoverable)
    }

    @Test
    fun `firmware invalid wifi value maps to device network guidance`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.WIFI,
                message = "Wi-Fi client connection is required before OTA download"
            )
        )

        assertEquals(DeviceOtaFailureReason.DEVICE_NETWORK_UNAVAILABLE, failure.reason)
        assertTrue(failure.recoverable)
        assertEquals(DeviceFirmwareRuntimeContract.ErrorField.WIFI, failure.field)
    }

    @Test
    fun `failed snapshot wifi disconnect maps to device network guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.WIFI,
                message = "Wi-Fi disconnected while preparing secure OTA",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DEVICE_NETWORK_UNAVAILABLE
            )
        )

        assertEquals(DeviceOtaFailureReason.DEVICE_NETWORK_UNAVAILABLE, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `invalid release url maps to terminal security validation`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.URL,
                message = "OTA URL must target the official AquaLight release repository"
            )
        )

        assertEquals(DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `invalid expected size maps to terminal ota contract mismatch`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE,
                message = "expectedSize must be greater than zero"
            )
        )

        assertEquals(DeviceOtaFailureReason.PROTOCOL_MISMATCH, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `ota task allocation failure stays retryable device internal`() {
        val failure = DeviceOtaFailureMapper.command(
            firmwareError(
                code = DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE,
                field = DeviceFirmwareRuntimeContract.ErrorField.TASK,
                message = "failed to create OTA task"
            )
        )

        assertEquals(DeviceOtaFailureReason.DEVICE_INTERNAL, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `secure time readiness failure maps to retryable time guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.TLS,
                message = "secure system time was not synchronized before OTA",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.SECURE_TIME_NOT_READY
            )
        )

        assertEquals(DeviceOtaFailureReason.SECURE_TIME_NOT_READY, failure.reason)
        assertTrue(failure.recoverable)
    }

    @Test
    fun `missing production tls trust remains terminal security validation`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.TLS,
                message = "OTA TLS root CA is required in production firmware",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.TLS_TRUST_UNAVAILABLE
            )
        )

        assertEquals(DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED, failure.reason)
        assertFalse(failure.recoverable)
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
                message = "downloaded firmware SHA256 does not match manifest",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.INTEGRITY_CHECK_FAILED
            )
        )

        assertEquals(DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `failed exact-state restore maps to terminal physical restart guidance`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE_RESTORE,
                message = "exact pre-OTA runtime restore failed",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.SAFE_MODE_RESTORE_FAILED
            )
        )

        assertEquals(DeviceOtaFailureReason.SAFE_MODE_RESTORE_FAILED, failure.reason)
        assertFalse(failure.recoverable)
    }

    @Test
    fun `failed snapshot http 404 maps to unavailable official release`() {
        val failure = DeviceOtaFailureMapper.snapshot(
            failedSnapshot(
                field = DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS,
                message = "OTA download failed with HTTP status 404",
                httpStatus = 404,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                httpStatus = -1,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                httpStatus = -11,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                httpStatus = -7,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                httpStatus = 403,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                httpStatus = 503,
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_HTTP_STATUS
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
                message = "OTA stream timed out or closed before completion",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_STREAM_INTERRUPTED
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
                message = "downloaded byte count does not match manifest size",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.DOWNLOAD_SIZE_MISMATCH
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
                message = "HTTP content length does not match manifest size",
                failureCode = DeviceFirmwareRuntimeContract.FailureCode.RELEASE_SIZE_MISMATCH
            )
        )

        assertEquals(DeviceOtaFailureReason.RELEASE_PACKAGE_MISMATCH, failure.reason)
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
        httpStatus: Int = 0,
        failureCode: String
    ) = DeviceFirmwareOtaSnapshot(
        phase = DeviceFirmwareOtaPhase.FAILED,
        phaseRaw = DeviceFirmwareOtaPhase.FAILED.wireValue,
        completed = true,
        failed = true,
        failureCode = failureCode,
        lastError = message,
        lastErrorField = field,
        httpStatus = httpStatus
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-OTA-FAILURE-MAPPER")
        val RUNTIME_GENERATION = DeviceRuntimeConnectionGeneration(7L)
    }
}
