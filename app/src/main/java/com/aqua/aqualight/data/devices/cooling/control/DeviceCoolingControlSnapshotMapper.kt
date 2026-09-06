package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingProgramRuntimeSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1OperatingState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1StatusDocument
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Telemetry
import kotlin.math.roundToInt

/** Projects central runtime authority into the application-owned Cooling control contract. */
internal object DeviceCoolingControlSnapshotMapper {
    fun map(state: DeviceCoolingRuntimeState): DeviceCoolingControlSnapshot? {
        val config = state.config
        val status = state.status
        return if (config == null || status == null) {
            null
        } else {
            val live = state.telemetry
            DeviceCoolingControlSnapshot(
                mode = (live?.controlMode ?: status.control.controlMode).toApplicationMode(),
                manualFanPercent = config.manualTargetPercent.toWritableIntPercentOrNull(),
                actualFanPercent = live?.fan?.outputPercent,
                tankTemperatureC = live.waterTemperatureOrNull(),
                capabilities = status.toApplicationCapabilities(),
                telemetry = live?.let(DeviceCoolingTelemetrySnapshotMapper::map),
                operatingState = (live?.operatingState ?: status.control.operatingState)
                    .toApplicationOperatingState(),
                controlReason = (live?.controlReason ?: status.control.controlReason)
                    .toApplicationControlReason(),
                targetFanPercent = live?.fan?.targetPercent ?: status.control.targetPercent,
                manualActive = live?.manualActive ?: status.control.manualActive,
                programRuntime = status.toApplicationProgramRuntime(live)
            )
        }
    }

    private fun DeviceCoolingV1Telemetry?.waterTemperatureOrNull(): Double? = this
        ?.sensors
        ?.firstOrNull { sensor ->
            sensor.sensorKey == DeviceCoolingV1Contract.WATER_SENSOR_KEY && sensor.readingValid
        }
        ?.temperatureC

    private fun DeviceCoolingV1StatusDocument.toApplicationCapabilities():
        DeviceCoolingControlCapabilities {
        val supportedModes = policy.controlModes.mapTo(linkedSetOf()) { mode ->
            mode.toApplicationMode()
        }
        val fanPolicy = policy.fanPercent
        val manualSupported = DeviceCoolingControlMode.MANUAL in supportedModes &&
            topology.fanOutputs.any { fan -> fan.fanKey == DeviceCoolingV1Contract.FAN_KEY }
        val minimumPercent = fanPolicy.minimumPercent.toWritableIntPercentOrNull()
        val maximumPercent = fanPolicy.maximumPercent.toWritableIntPercentOrNull()
        val stepPercent = fanPolicy.stepPercent.toPositiveWritableIntPercentOrNull()
        return DeviceCoolingControlCapabilities(
            supportedModes = supportedModes,
            modeSelectionWritable = true,
            manualFan = if (
                manualSupported &&
                minimumPercent != null &&
                maximumPercent != null
            ) {
                DeviceCoolingManualFanCapabilities(
                    minimumPercent = minimumPercent,
                    maximumPercent = maximumPercent,
                    stepPercent = stepPercent,
                    writable = stepPercent != null
                )
            } else {
                null
            }
        )
    }

    private fun DeviceCoolingV1StatusDocument.toApplicationProgramRuntime(
        live: DeviceCoolingV1Telemetry?
    ): DeviceCoolingProgramRuntimeSnapshot = DeviceCoolingProgramRuntimeSnapshot(
        persistedRevision = program.programRevision,
        evaluatedRevision = live?.programRevision ?: program.evaluatedProgramRevision,
        slotCount = program.slotCount,
        clockReady = live?.clockReady ?: program.clockReady,
        currentMinuteOfDay = live?.currentMinuteOfDay ?: program.currentMinuteOfDay,
        activeSlotIndex = live?.activeProgramSlotIndex ?: program.activeSlotIndex
    )

    private fun DeviceCoolingV1ControlMode.toApplicationMode(): DeviceCoolingControlMode =
        when (this) {
            DeviceCoolingV1ControlMode.AUTOMATIC -> DeviceCoolingControlMode.AUTOMATIC
            DeviceCoolingV1ControlMode.MANUAL -> DeviceCoolingControlMode.MANUAL
            DeviceCoolingV1ControlMode.PROGRAM -> DeviceCoolingControlMode.PROGRAM
        }

    private fun DeviceCoolingV1OperatingState.toApplicationOperatingState():
        DeviceCoolingOperatingState = when (this) {
        DeviceCoolingV1OperatingState.IDLE -> DeviceCoolingOperatingState.IDLE
        DeviceCoolingV1OperatingState.COOLING -> DeviceCoolingOperatingState.COOLING
        DeviceCoolingV1OperatingState.MANUAL -> DeviceCoolingOperatingState.MANUAL
        DeviceCoolingV1OperatingState.PROGRAM -> DeviceCoolingOperatingState.PROGRAM
        DeviceCoolingV1OperatingState.FAULT -> DeviceCoolingOperatingState.FAULT
    }

    private fun String.toApplicationControlReason(): DeviceCoolingControlReason =
        DeviceCoolingControlReason.values().firstOrNull { reason -> reason.name == this }
            ?: DeviceCoolingControlReason.UNKNOWN

    /** Writable Manual policy is discrete; firmware-computed runtime telemetry is not. */
    private fun Double.toWritableIntPercentOrNull(): Int? {
        val rounded = if (isFinite()) roundToInt() else null
        return rounded?.takeIf { value ->
            value in MIN_PERCENT..MAX_PERCENT &&
                kotlin.math.abs(this - value.toDouble()) <= PERCENT_ROUNDING_EPSILON
        }
    }

    private fun Double.toPositiveWritableIntPercentOrNull(): Int? =
        toWritableIntPercentOrNull()?.takeIf { it > MIN_PERCENT }

    private const val MIN_PERCENT = 0
    private const val MAX_PERCENT = 100
    private const val PERCENT_ROUNDING_EPSILON = 0.0001
}
