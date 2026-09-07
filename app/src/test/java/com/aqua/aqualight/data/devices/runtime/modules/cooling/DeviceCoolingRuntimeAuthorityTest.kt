package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ChartSource
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1HistoryRange
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingRuntimeAuthorityTest {
    @Test
    fun `status parser preserves every firmware authority section as typed state`() {
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())

        assertEquals(listOf("fan1"), status.topology.fanOutputs.map { it.fanKey })
        assertEquals(listOf("water", "ambient"), status.topology.sensorSlots.map { it.sensorKey })
        assertEquals(50.0, status.policy.silentMode.maximumPercent, 0.0)
        assertEquals(0.5, status.policy.temperature.minimumGapC, 0.0)
        assertEquals(0.5, status.policy.temperature.hysteresisC, 0.0)
        assertEquals(37.77778, status.control.targetPercent, 0.0)
        assertEquals(DeviceCoolingV1OperatingState.COOLING, status.control.operatingState)
        assertEquals("AUTOMATIC_CURVE", status.control.controlReason)
        assertEquals(2, status.program.slotCount)
        assertEquals(8L, status.program.evaluatedProgramRevision)
        assertEquals(8L, status.telemetry.programRevision)
        assertEquals(0, status.telemetry.healthSummary.activeAlarmCount)
        assertEquals(
            DeviceCoolingV1ChartSource.DAILY_AVERAGE,
            status.history.chartSources.getValue(DeviceCoolingV1HistoryRange.DAYS_30)
        )
    }

    @Test
    fun `central owner compares telemetry with evaluated not merely persisted program revision`() {
        val owner = DeviceCoolingRuntimeStateOwner()
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())
        owner.beginGeneration(DEVICE_UID, GENERATION)

        assertTrue(owner.recordStatus(DEVICE_UID, GENERATION, status))
        assertEquals(8L, owner.states.value.getValue(DEVICE_UID).telemetry?.programRevision)

        val evaluatedEvent = status.telemetry.copy(decisionSequence = 43L, uptimeMs = 120100L)
        assertTrue(owner.recordTelemetry(DEVICE_UID, GENERATION, evaluatedEvent))

        val falsePersistedEvent = evaluatedEvent.copy(
            programRevision = status.programRevision,
            decisionSequence = 44L,
            uptimeMs = 120200L
        )
        assertFalse(owner.recordTelemetry(DEVICE_UID, GENERATION, falsePersistedEvent))
    }

    @Test
    fun `central owner retains only program details matching the authoritative revision`() {
        val owner = DeviceCoolingRuntimeStateOwner()
        val status = DeviceCoolingV1ResponseParser.parseStatus(statusJson())
        val program = programSnapshot(status.programRevision)
        owner.beginGeneration(DEVICE_UID, GENERATION)
        assertTrue(owner.recordStatus(DEVICE_UID, GENERATION, status))

        assertTrue(owner.recordProgram(DEVICE_UID, GENERATION, program))
        assertEquals(program, owner.states.value.getValue(DEVICE_UID).programSnapshot)

        assertTrue(owner.recordStatus(DEVICE_UID, GENERATION, status))
        assertEquals(program, owner.states.value.getValue(DEVICE_UID).programSnapshot)

        val nextStatus = status.copy(
            programRevision = status.programRevision + 1,
            program = status.program.copy(programRevision = status.programRevision + 1)
        )
        assertTrue(owner.recordStatus(DEVICE_UID, GENERATION, nextStatus))
        assertNull(owner.states.value.getValue(DEVICE_UID).programSnapshot)
    }

    @Test
    fun `nested status contract additions fail closed`() {
        val invalid = statusJson().apply {
            getJSONObject("policy").getJSONObject("silentMode").put("androidFallback", 50)
        }

        assertTrue(runCatching { DeviceCoolingV1ResponseParser.parseStatus(invalid) }.isFailure)
    }

    @Test
    fun `status accepts continuous firmware computed automatic target`() {
        val runtimeTargetPercent = 35.95
        val status = statusJson().apply {
            getJSONObject("control").put("targetPercent", runtimeTargetPercent)
            getJSONObject("telemetry")
                .getJSONObject("fan")
                .put("targetPercent", runtimeTargetPercent)
                .put("outputPercent", runtimeTargetPercent)
        }

        val parsed = DeviceCoolingV1ResponseParser.parseStatus(status)

        assertEquals(runtimeTargetPercent, parsed.control.targetPercent, 0.0)
        assertEquals(runtimeTargetPercent, parsed.telemetry.fan.targetPercent, 0.0)
    }

    @Test
    fun `status still rejects firmware runtime target outside physical range`() {
        val status = statusJson().apply {
            getJSONObject("control").put("targetPercent", 100.01)
            getJSONObject("telemetry").getJSONObject("fan").put("targetPercent", 100.01)
        }

        assertTrue(runCatching { DeviceCoolingV1ResponseParser.parseStatus(status) }.isFailure)
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
                fanOnTemperature = DeviceCoolingV1TemperaturePolicy(
                    minimumC = 10.0,
                    maximumC = 40.0,
                    stepC = 0.1,
                    defaultC = 25.0
                )
            ),
            slots = listOf(
                DeviceCoolingV1ProgramSlot(480, 600, 25.0, 50.0),
                DeviceCoolingV1ProgramSlot(840, 1_080, 26.0, 70.0)
            )
        )

    private companion object {
        const val STATUS_FIXTURE = "aql_cooling_status_v1.json"
        val DEVICE_UID = DeviceUid("AQL-COOLING-AUTHORITY")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
