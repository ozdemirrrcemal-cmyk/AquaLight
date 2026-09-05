package com.aqua.aqualight.data.devices.cooling.v1

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCoolingV1FailureMapperTest {

    @Test
    fun mapsRevisionConflict() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.CONFLICT,
            status = 409,
            code = "CONFLICT",
            field = "expectedConfigRevision",
            message = "cooling config revision is stale"
        )
    }

    @Test
    fun mapsManualModeRequirement() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.MANUAL_MODE_REQUIRED,
            status = 422,
            code = "INVALID_VALUE",
            field = "controlMode",
            message = "manual output requires MANUAL control mode"
        )
    }

    @Test
    fun mapsCatalogFanNotFoundToHardwareUnavailable() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.HARDWARE_UNAVAILABLE,
            status = 404,
            code = "NOT_FOUND",
            field = "fanKey",
            message = "only the catalog fan1 output exists"
        )
    }

    @Test
    fun mapsUnavailableFanOutput() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.HARDWARE_UNAVAILABLE,
            status = 503,
            code = "HARDWARE_ERROR",
            field = "fan1",
            message = "catalog fan1 output is unavailable"
        )
    }

    @Test
    fun mapsPwmWriteFailure() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.HARDWARE_FAILURE,
            status = 503,
            code = "HARDWARE_ERROR",
            field = "fan1",
            message = "fan1 PWM duty write failed; config was not persisted"
        )
    }

    @Test
    fun mapsStorageFailure() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.STORAGE_FAILURE,
            status = 500,
            code = "STORAGE_ERROR",
            field = "cooling",
            message = "cooling config transaction could not be persisted"
        )
    }

    @Test
    fun mapsClockUnsynced() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.CLOCK_UNSYNCED,
            status = 503,
            code = "CLOCK_UNSYNCED",
            field = "time",
            message = "cooling history requires the trusted device clock"
        )
    }

    @Test
    fun mapsInvalidProgramToInvalidConfiguration() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.INVALID_CONFIGURATION,
            status = 422,
            code = "INVALID_VALUE",
            field = "slots",
            message = "INVALID_SLOT_DURATION at slot index 0"
        )
    }

    @Test
    fun rejectsStatusCodeDriftAsProtocolError() {
        assertMapped(
            expected = DeviceCoolingCommandFailure.PROTOCOL_ERROR,
            status = 422,
            code = "CONFLICT",
            field = "expectedProgramRevision",
            message = "cooling program revision is stale"
        )
    }

    private fun assertMapped(
        expected: DeviceCoolingCommandFailure,
        status: Int,
        code: String,
        field: String,
        message: String
    ) {
        assertEquals(
            expected,
            DeviceCoolingV1FailureMapper.map(
                firmwareError(status, code, field, message)
            )
        )
    }

    private fun firmwareError(
        status: Int,
        code: String,
        field: String,
        message: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DeviceUid("cooling-test"),
        module = "cooling",
        action = "test",
        messageId = "message-1",
        generation = DeviceRuntimeConnectionGeneration(1),
        statusCode = status,
        code = code,
        field = field,
        message = message
    )
}
