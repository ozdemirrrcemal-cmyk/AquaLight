@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.timer.v1

import com.aqua.aqualight.application.devices.timer.DeviceTimerCommandFailure
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
                status = 400,
                code = "BAD_REQUEST",
                field = "data",
                message = "timer.channel.set data must be a JSON object"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.INVALID_CONFIGURATION,
                status = 422,
                code = "INVALID_VALUE",
                field = "schedules",
                message = "at most 8 schedules are supported per timer channel"
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps state and topology rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.CONFLICT,
                status = 409,
                code = "CONFLICT",
                field = "expectedRevision",
                message = "timer config revision is stale"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE,
                status = 404,
                code = "NOT_FOUND",
                field = "channelKey",
                message = "configured timer channel not found for channelKey: channel9"
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps resource and output rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE,
                status = 503,
                code = "HARDWARE_ERROR",
                field = "timer",
                message = "timer transaction snapshot allocation failed"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE,
                status = 503,
                code = "HARDWARE_ERROR",
                field = "schedules",
                message = "timer schedule transaction workspace allocation failed"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.HARDWARE_FAILURE,
                status = 503,
                code = "HARDWARE_ERROR",
                field = "channelKey",
                message = "timer output write failed; channel config was rolled back"
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps persistence lock and unknown rejection families`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.STORAGE_FAILURE,
                status = 500,
                code = "STORAGE_ERROR",
                field = "timer",
                message = "timer channel state was not applied because persistence failed; " +
                    "previous state restored"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.RUNTIME_LOCKED,
                status = 500,
                code = "STORAGE_ERROR",
                field = "timer",
                message = "timer persistence and runtime rollback failed; " +
                    "timer is locked until restart"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.UNKNOWN_REJECTION,
                status = 418,
                code = "FUTURE_TIMER_REJECTION",
                field = "timer",
                message = "future rejection"
            )
        )

        assertCases(cases)
    }

    @Test
    fun `maps hardware rollback and preexisting locks to restart required`() {
        val cases = listOf(
            failureCase(
                expected = DeviceTimerCommandFailure.RUNTIME_LOCKED,
                status = 503,
                code = "HARDWARE_ERROR",
                field = "timer",
                message = "timer runtime transaction is locked until restart"
            ),
            failureCase(
                expected = DeviceTimerCommandFailure.RUNTIME_LOCKED,
                status = 503,
                code = "HARDWARE_ERROR",
                field = "channelKey",
                message = "timer output write and rollback failed; " +
                    "timer runtime remains locked until restart"
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
                    status = 422,
                    code = "CONFLICT",
                    field = "expectedRevision",
                    message = "timer config revision is stale"
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
                    status = 404,
                    code = "NOT_FOUND",
                    field = "timer",
                    message = "unknown resource"
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
}
