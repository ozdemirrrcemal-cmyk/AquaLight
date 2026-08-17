package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingChannelFailureMapperTest {

    @Test
    fun `firmware runtime guards retain channel semantics`() {
        assertRejected(
            DeviceDosingChannelRejection.BUSY,
            firmwareError(
                field = "dosing",
                message = "dosing channel is busy with an active run or calibration session"
            )
        )
        assertRejected(
            DeviceDosingChannelRejection.BUSY,
            firmwareError(
                field = "calibration",
                message = "normal manual dosing is unavailable while calibration is open"
            )
        )
        assertRejected(
            DeviceDosingChannelRejection.NOT_CALIBRATED,
            firmwareError(
                field = "calibration",
                message = "pump must be calibrated before manual dosing"
            )
        )
        assertRejected(
            DeviceDosingChannelRejection.UNSAFE,
            firmwareError(
                field = "reservoir",
                message = "not enough trustworthy reservoir remaining for requested dose"
            )
        )
    }

    @Test
    fun `stale revision retains state changed semantics`() {
        assertRejected(
            DeviceDosingChannelRejection.CONFLICT,
            firmwareError(
                field = "expectedRevision",
                message = "stale dosing channel revision"
            )
        )
    }

    @Test
    fun `typed user amount rejection remains invalid draft`() {
        assertRejected(
            DeviceDosingChannelRejection.INVALID_DRAFT,
            firmwareError(
                field = "amountMl",
                message = "manual dose must not exceed 1000 ml"
            )
        )
    }

    @Test
    fun `unrecognized invalid value is not blamed on user input`() {
        assertRejected(
            DeviceDosingChannelRejection.UNKNOWN,
            firmwareError(
                field = "futureRuntimeGuard",
                message = "future firmware semantic rejection"
            )
        )
    }

    @Test
    fun `storage hardware and unsupported failures stay fail closed`() {
        assertRejected(
            DeviceDosingChannelRejection.UNSAFE,
            firmwareError(
                code = "STORAGE_ERROR",
                field = "dosing",
                message = "physical run checkpoint could not be saved before pump start"
            )
        )
        assertRejected(
            DeviceDosingChannelRejection.UNSAFE,
            firmwareError(
                code = "HARDWARE_ERROR",
                field = "pump",
                message = "dosing output hardware could not be energized"
            )
        )
        assertEquals(
            DeviceDosingChannelOperationResult.Unavailable,
            DeviceDosingChannelFailureMapper.map(
                DeviceRuntimeCommandOutcome.UnsupportedByDevice(
                    DEVICE_UID,
                    DeviceDosingV1Contract.MODULE,
                    DeviceDosingV1Contract.Action.DOSE_NOW
                )
            )
        )
    }

    private fun assertRejected(
        expected: DeviceDosingChannelRejection,
        failure: DeviceRuntimeCommandOutcome.FirmwareError
    ) {
        assertEquals(
            DeviceDosingChannelOperationResult.Rejected(expected),
            DeviceDosingChannelFailureMapper.map(failure)
        )
    }

    private fun firmwareError(
        code: String = "INVALID_VALUE",
        field: String,
        message: String
    ) = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = DEVICE_UID,
        module = DeviceDosingV1Contract.MODULE,
        action = DeviceDosingV1Contract.Action.DOSE_NOW,
        messageId = "response-1",
        generation = GENERATION,
        statusCode = 422,
        code = code,
        field = field,
        message = message
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-CHANNEL-FAILURE-TEST")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
