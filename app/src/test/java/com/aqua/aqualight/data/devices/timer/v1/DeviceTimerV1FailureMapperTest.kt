package com.aqua.aqualight.data.devices.timer.v1

import com.aqua.aqualight.application.devices.timer.control.DeviceTimerCommandFailure
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTimerV1FailureMapperTest {

    @Test
    fun `maps request and configuration rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.INVALID_REQUEST,
                status = HTTP_BAD_REQUEST,
                code = "BAD_REQUEST",
                field = "data",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.INVALID_CONFIGURATION,
                status = HTTP_UNPROCESSABLE_ENTITY,
                code = "INVALID_VALUE",
                field = "schedules",
                message = FIRMWARE_WIRE_MESSAGE
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps state and topology rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.CONFLICT,
                status = HTTP_CONFLICT,
                code = "CONFLICT",
                field = "expectedRevision",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE,
                status = HTTP_NOT_FOUND,
                code = "NOT_FOUND",
                field = "channelKey",
                message = FIRMWARE_WIRE_MESSAGE
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps resource and output rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.HARDWARE_FAILURE,
                status = HTTP_SERVICE_UNAVAILABLE,
                code = "HARDWARE_ERROR",
                field = "timer",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE,
                status = HTTP_SERVICE_UNAVAILABLE,
                code = "HARDWARE_ERROR",
                field = "schedules",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.HARDWARE_FAILURE,
                status = HTTP_SERVICE_UNAVAILABLE,
                code = "HARDWARE_ERROR",
                field = "channelKey",
                message = FIRMWARE_WIRE_MESSAGE
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps persistence lock and unknown rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.STORAGE_FAILURE,
                status = HTTP_INTERNAL_ERROR,
                code = "STORAGE_ERROR",
                field = "timer",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.UNKNOWN_REJECTION,
                status = HTTP_TEAPOT,
                code = "FUTURE_TIMER_REJECTION",
                field = "timer",
                message = "future rejection"
            )
        )

        assertCases(cases)
    }

    @Test
    fun `masked hardware causes remain one honest wire level category`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.HARDWARE_FAILURE,
                status = HTTP_SERVICE_UNAVAILABLE,
                code = "HARDWARE_ERROR",
                field = "timer",
                message = FIRMWARE_WIRE_MESSAGE
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.HARDWARE_FAILURE,
                status = HTTP_SERVICE_UNAVAILABLE,
                code = "HARDWARE_ERROR",
                field = "channelKey",
                message = FIRMWARE_WIRE_MESSAGE
            )
        )

        assertCases(cases)
    }

    @Test
    fun `status drift in a known firmware code is a protocol error`() {
        assertEquals(
            DeviceTimerCommandFailure.PROTOCOL_ERROR,
            DeviceTimerV1FailureMapper.map(
                firmwareError(
                    status = HTTP_UNPROCESSABLE_ENTITY,
                    code = "CONFLICT",
                    field = "expectedRevision",
                    message = FIRMWARE_WIRE_MESSAGE
                )
            )
        )
    }

    @Test
    fun `unknown identity for known not found code remains a safe unknown rejection`() {
        assertEquals(
            DeviceTimerCommandFailure.UNKNOWN_REJECTION,
            DeviceTimerV1FailureMapper.map(
                firmwareError(
                    status = HTTP_NOT_FOUND,
                    code = "NOT_FOUND",
                    field = "timer",
                    message = FIRMWARE_WIRE_MESSAGE
                )
            )
        )
    }

    private fun failureCase(
        expected: DeviceTimerCommandFailure,
        status: Int,
        code: String,
        field: String,
        message: String
    ) = FailureCase(expected, firmwareError(status, code, field, message))

    private fun assertCases(cases: List<FailureCase>) {
        cases.forEach { case ->
            assertEquals(case.expected, DeviceTimerV1FailureMapper.map(case.error))
        }
    }

    private fun firmwareError(
        status: Int,
        code: String,
        field: String,
        message: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DeviceUid("timer-test"),
        module = "timer",
        action = "test",
        messageId = "message-1",
        generation = DeviceRuntimeConnectionGeneration(1),
        statusCode = status,
        code = code,
        field = field,
        message = message
    )

    private data class FailureCase(
        val expected: DeviceTimerCommandFailure,
        val error: DeviceRuntimeCommandOutcome.FirmwareError
    )

    private companion object {
        const val FIRMWARE_WIRE_MESSAGE = "Command rejected."
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_TEAPOT = 418
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_INTERNAL_ERROR = 500
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
