package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.application.devices.DeviceOperationRuntimeStateDiagnostic
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuntimeCommandOutcomeDiagnosticMapperTest {

    @Test
    fun `timeout keeps command correlation and timeout budget`() {
        val diagnostic = DeviceRuntimeCommandOutcome.Timeout(
            deviceUid = DeviceUid("cooling-1"),
            module = "cooling",
            action = "status.get",
            messageId = "message-7",
            generation = DeviceRuntimeConnectionGeneration(4L),
            timeoutMillis = 3_000L
        ).toOperationDiagnostic(stage = "COOLING_COMMAND")

        assertEquals("TIMEOUT", diagnostic.outcome)
        assertEquals("message-7", diagnostic.command?.messageId)
        assertEquals(4L, diagnostic.command?.connectionGeneration)
        assertEquals(3_000L, diagnostic.command?.timeoutMillis)
    }

    @Test
    fun `firmware error keeps response error fields`() {
        val diagnostic = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DeviceUid("cooling-1"),
            module = "cooling",
            action = "status.get",
            messageId = "message-8",
            generation = DeviceRuntimeConnectionGeneration(5L),
            statusCode = 422,
            code = "INVALID_STATE",
            field = "sensor",
            message = "Sensor state is invalid"
        ).toOperationDiagnostic(stage = "COOLING_COMMAND")

        assertEquals("FIRMWARE_ERROR", diagnostic.outcome)
        assertEquals(422, diagnostic.response?.statusCode)
        assertEquals("INVALID_STATE", diagnostic.response?.firmwareCode)
        assertEquals("sensor", diagnostic.response?.firmwareField)
    }

    @Test
    fun `successful response can report runtime owner rejection`() {
        val diagnostic = DeviceRuntimeCommandOutcome.Success(
            deviceUid = DeviceUid("cooling-1"),
            module = "cooling",
            action = "status.get",
            messageId = "message-9",
            generation = DeviceRuntimeConnectionGeneration(6L),
            statusCode = 200,
            value = Unit
        ).toOperationDiagnostic(
            stage = "COOLING_RUNTIME_OWNER",
            outcomeOverride = "SUCCESS_NOT_ACCEPTED_AS_AUTHORITATIVE",
            runtimeState = DeviceOperationRuntimeStateDiagnostic(
                connectionGeneration = 6L,
                authoritative = false
            )
        )

        assertEquals("SUCCESS_NOT_ACCEPTED_AS_AUTHORITATIVE", diagnostic.outcome)
        assertEquals(200, diagnostic.response?.statusCode)
        assertEquals(false, diagnostic.runtimeState?.authoritative)
    }

    @Test
    fun `protocol reason is retained beside application failure`() {
        val diagnostic = DeviceRuntimeCommandOutcome.ProtocolError(
            deviceUid = DeviceUid("cooling-1"),
            module = "cooling",
            action = "status.get",
            messageId = "message-10",
            generation = DeviceRuntimeConnectionGeneration(7L),
            reason = "Missing data.telemetry"
        ).toOperationDiagnostic(
            stage = "COOLING_COMMAND",
            detailOverride = "failure=InvalidData"
        )

        assertEquals(
            "Missing data.telemetry | failure=InvalidData",
            diagnostic.detail
        )
    }
}
