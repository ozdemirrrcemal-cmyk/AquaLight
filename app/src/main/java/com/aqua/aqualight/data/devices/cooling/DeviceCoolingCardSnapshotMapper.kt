package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardProgramSlot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardProgramSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardTemperatureRange
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.data.devices.cooling.control.DeviceCoolingControlSnapshotMapper
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState

/** Atomic projection from one authoritative Cooling runtime snapshot into the card contract. */
internal object DeviceCoolingCardSnapshotMapper {
    fun map(state: DeviceCoolingRuntimeState): DeviceCoolingCardSummary? {
        val control = DeviceCoolingControlSnapshotMapper.map(state)
        val config = state.config
        val programStatus = state.status?.program
        val programRuntime = control?.programRuntime
        return if (
            control == null ||
            config == null ||
            programStatus == null ||
            programRuntime == null
        ) {
            null
        } else {
            val programSnapshot = state.programSnapshot
                ?.takeIf { snapshot -> snapshot.programRevision == programStatus.programRevision }
            val activeSlotIndex = programRuntime.activeSlotIndex
            val activeSlot = activeSlotIndex
                ?.let { index -> programSnapshot?.slots?.getOrNull(index) }
                ?.let { slot ->
                    DeviceCoolingCardProgramSlot(
                        startMinutes = slot.startMinute,
                        endMinutes = slot.endMinute
                    )
                }
            val appliedOutput = control.actualFanPercent
            DeviceCoolingCardSummary(
                mode = control.mode,
                operatingState = requireNotNull(control.operatingState),
                controlReason = control.controlReason,
                waterTemperatureC = control.tankTemperatureC,
                actualFanPercent = appliedOutput,
                targetFanPercent = control.manualFanPercent?.toDouble(),
                automaticRange = DeviceCoolingCardTemperatureRange(
                    startC = config.startTemperatureC,
                    fullSpeedC = config.fullSpeedTemperatureC
                ),
                program = DeviceCoolingCardProgramSummary(
                    activeSlotNumber = activeSlotIndex?.plus(1),
                    slotCount = programRuntime.slotCount,
                    activeSlot = activeSlot
                ),
                fanMotionActive = appliedOutput != null &&
                    appliedOutput > NO_FAN_OUTPUT &&
                    control.operatingState in ACTIVE_OPERATING_STATES
            )
        }
    }

    private val ACTIVE_OPERATING_STATES = setOf(
        DeviceCoolingOperatingState.COOLING,
        DeviceCoolingOperatingState.MANUAL,
        DeviceCoolingOperatingState.PROGRAM
    )

    private const val NO_FAN_OUTPUT = 0.0
}
