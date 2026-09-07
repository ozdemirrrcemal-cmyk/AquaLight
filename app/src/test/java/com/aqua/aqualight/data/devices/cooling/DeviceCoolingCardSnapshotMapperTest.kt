package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1FanPolicy
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1OperatingState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramPolicy
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSlot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ResponseParser
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1TemperaturePolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingCardSnapshotMapperTest {
    @Test
    fun `program card projects one coherent central snapshot without schedule evaluation`() {
        val original = DeviceCoolingV1ResponseParser.parseStatus(statusJson())
        val telemetry = original.telemetry.copy(
            controlMode = DeviceCoolingV1ControlMode.PROGRAM,
            operatingState = DeviceCoolingV1OperatingState.PROGRAM,
            controlReason = "PROGRAM_SLOT_COOLING",
            activeProgramSlotIndex = 0,
            fan = original.telemetry.fan.copy(outputPercent = 42.0)
        )
        val status = original.copy(
            config = original.config.copy(
                controlMode = DeviceCoolingV1ControlMode.PROGRAM,
                manualTargetPercent = 75.0
            ),
            program = original.program.copy(activeSlotIndex = 0, slotCount = 2),
            control = original.control.copy(
                controlMode = DeviceCoolingV1ControlMode.PROGRAM,
                operatingState = DeviceCoolingV1OperatingState.PROGRAM,
                controlReason = "PROGRAM_SLOT_COOLING"
            ),
            telemetry = telemetry
        )
        val summary = DeviceCoolingCardSnapshotMapper.map(
            DeviceCoolingRuntimeState(
                authoritative = true,
                status = status,
                config = status.config,
                telemetry = telemetry,
                programSnapshot = programSnapshot(status.programRevision)
            )
        )

        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals(DeviceCoolingControlMode.PROGRAM, summary.mode)
        assertEquals(42.0, summary.actualFanPercent ?: -1.0, 0.0)
        assertEquals(75.0, summary.targetFanPercent ?: -1.0, 0.0)
        assertEquals(1, summary.program?.activeSlotNumber)
        assertEquals(2, summary.program?.slotCount)
        assertEquals(480, summary.program?.activeSlot?.startMinutes)
        assertEquals(600, summary.program?.activeSlot?.endMinutes)
        assertTrue(summary.fanMotionActive)
    }

    @Test
    fun `fan motion requires both applied output and an active firmware state`() {
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())
        val telemetry = status.telemetry.copy(
            operatingState = DeviceCoolingV1OperatingState.IDLE,
            fan = status.telemetry.fan.copy(outputPercent = 42.0)
        )
        val summary = DeviceCoolingCardSnapshotMapper.map(
            DeviceCoolingRuntimeState(
                authoritative = true,
                status = status.copy(telemetry = telemetry),
                config = status.config,
                telemetry = telemetry
            )
        )

        assertFalse(requireNotNull(summary).fanMotionActive)
    }

    private fun statusJson(): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(STATUS_FIXTURE)) {
            "Missing fixture resource: $STATUS_FIXTURE"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )

    private fun programSnapshot(revision: Long): DeviceCoolingV1ProgramSnapshot =
        DeviceCoolingV1ProgramSnapshot(
            programRevision = revision,
            clockReady = true,
            currentMinuteOfDay = 500,
            activeSlotIndex = 0,
            policy = DeviceCoolingV1ProgramPolicy(
                maximumSlotCount = 8,
                timeStepMinutes = 15,
                minimumDurationMinutes = 15,
                crossMidnightSlotsSupported = false,
                requiresTrustedDeviceClock = true,
                scheduleBasis = "device_local_time",
                programActivation = "active_slot",
                timeAuthority = "device",
                currentMinuteOfDaySource = "device",
                activeSlotAuthority = "device",
                startBoundary = "inclusive",
                endBoundary = "exclusive",
                endMinuteMaximum = 1_440,
                fan = DeviceCoolingV1FanPolicy(0.0, 100.0, 1.0),
                fanOnTemperature = DeviceCoolingV1TemperaturePolicy(10.0, 40.0, 0.1, 25.0)
            ),
            slots = listOf(
                DeviceCoolingV1ProgramSlot(480, 600, 25.0, 50.0),
                DeviceCoolingV1ProgramSlot(840, 1_080, 26.0, 70.0)
            )
        )

    private companion object {
        const val STATUS_FIXTURE = "aql_cooling_status_v1.json"
    }
}
