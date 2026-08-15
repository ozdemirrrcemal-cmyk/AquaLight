package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingCalibrationFailureMapperTest {

    @Test
    fun `transport failures map only to connection failure`() {
        val failures = listOf(
            DeviceRuntimeCommandOutcome.NotConnected(
                DEVICE_UID,
                DeviceDosingV1Contract.MODULE,
                DeviceDosingV1Contract.Action.CALIBRATION_START
            ),
            DeviceRuntimeCommandOutcome.Timeout(
                DEVICE_UID,
                DeviceDosingV1Contract.MODULE,
                DeviceDosingV1Contract.Action.CALIBRATION_START,
                "request-1",
                GENERATION,
                5_000L
            )
        )

        failures.forEach { failure ->
            assertEquals(
                DeviceDosingCalibrationFailure.CONNECTION,
                DeviceDosingCalibrationFailureMapper.map(failure)
            )
        }
    }

    @Test
    fun `storage and physical pump failures remain distinct`() {
        assertEquals(
            DeviceDosingCalibrationFailure.STORAGE,
            mapFirmware(
                code = "STORAGE_ERROR",
                field = "dosing",
                message = "physical run checkpoint could not be saved before pump start"
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.HARDWARE,
            mapFirmware(
                code = "HARDWARE_ERROR",
                field = "pump",
                message = "dosing output hardware could not be energized"
            )
        )
    }

    @Test
    fun `firmware busy guards map to operation in progress`() {
        assertEquals(
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS,
            mapFirmware(
                code = "DEVICE_BUSY",
                field = "calibration",
                message = "calibration is already running"
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS,
            mapFirmware(
                code = "INVALID_VALUE",
                field = "calibration",
                message = "calibration is already running/pending or the pump is busy"
            )
        )
    }

    @Test
    fun `trusted device time rejection is not a duration or connection failure`() {
        assertEquals(
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY,
            mapFirmware(
                code = "INVALID_VALUE",
                field = "deviceTime",
                message = "trusted device time is required before calibration confirmation"
            )
        )
    }

    @Test
    fun `workflow and stale revision rejections share state mismatch recovery`() {
        val stateFailures = listOf(
            firmwareError(
                code = "INVALID_VALUE",
                field = "verification",
                message = "the 4 ml verification dose must finish before confirmation"
            ),
            firmwareError(
                code = "INVALID_VALUE",
                field = "calibration",
                message = "no pending calibration exists for this channel"
            ),
            firmwareError(
                code = "INVALID_VALUE",
                field = "expectedRevision",
                message = "stale dosing channel revision"
            ),
            firmwareError(
                code = "NOT_FOUND",
                field = "channelKey",
                message = "configured dosing channel was not found"
            )
        )

        stateFailures.forEach { failure ->
            assertEquals(
                DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH,
                DeviceDosingCalibrationFailureMapper.map(failure)
            )
        }
    }

    @Test
    fun `firmware measured volume rejection retains invalid measurement semantics`() {
        assertEquals(
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT,
            mapFirmware(
                code = "INVALID_VALUE",
                field = "measuredMl",
                message = "measuredMl is outside the supported calibration range"
            )
        )
    }

    @Test
    fun `unsupported and unrecognized failures stay internal`() {
        assertEquals(
            DeviceDosingCalibrationFailure.INTERNAL,
            DeviceDosingCalibrationFailureMapper.map(
                DeviceRuntimeCommandOutcome.UnsupportedByDevice(
                    DEVICE_UID,
                    DeviceDosingV1Contract.MODULE,
                    DeviceDosingV1Contract.Action.CALIBRATION_START
                )
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.INTERNAL,
            mapFirmware(
                code = "FEATURE_NOT_AVAILABLE",
                field = "calibration",
                message = "calibration is not available"
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.INTERNAL,
            mapFirmware(
                code = "INVALID_VALUE",
                field = "expectedRevision",
                message = "expectedRevision must be an unsigned integer"
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.INTERNAL,
            mapFirmware(
                code = "FUTURE_FIRMWARE_ERROR",
                field = "futureField",
                message = "future firmware detail"
            )
        )
        assertEquals(
            DeviceDosingCalibrationFailure.INTERNAL,
            mapFirmware(
                code = "HARDWARE_ERROR",
                field = "dosing",
                message = "dosing runtime boot state is not ready"
            )
        )
    }

    private fun mapFirmware(
        code: String,
        field: String,
        message: String
    ): DeviceDosingCalibrationFailure =
        DeviceDosingCalibrationFailureMapper.map(firmwareError(code, field, message))

    private fun firmwareError(
        code: String,
        field: String,
        message: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DEVICE_UID,
        module = DeviceDosingV1Contract.MODULE,
        action = DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM,
        messageId = "response-1",
        generation = GENERATION,
        statusCode = 422,
        code = code,
        field = field,
        message = message
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-CALIBRATION-TEST")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
