package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason

internal object DeviceDosingV1ChannelSnapshotMapper {
    fun runtimeReason(detail: DeviceDosingV1ChannelDetail): DeviceDosingRuntimeReason =
        when (detail.runtimeReason.raw) {
            "none" -> DeviceDosingRuntimeReason.NONE
            "programDisabled" -> DeviceDosingRuntimeReason.PROGRAM_DISABLED
            "missingCalibration" -> DeviceDosingRuntimeReason.MISSING_CALIBRATION
            "invalidTime" -> DeviceDosingRuntimeReason.INVALID_TIME
            "reservoirUnavailable" -> DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE
            "accountingUncertain" -> DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN
            "unsafeAfterCalibration" -> DeviceDosingRuntimeReason.UNSAFE_AFTER_CALIBRATION
            "busy" -> DeviceDosingRuntimeReason.BUSY
            "invalidProgram" -> DeviceDosingRuntimeReason.INVALID_PROGRAM
            else -> DeviceDosingRuntimeReason.UNKNOWN
        }

    fun reservoir(
        detail: DeviceDosingV1ChannelDetail,
        lowLevelAlertEnabled: Boolean
    ): DeviceDosingReservoirSnapshot {
        val reservoir = detail.reservoir
        return DeviceDosingReservoirSnapshot(
            trackingEnabled = reservoir.trackingEnabled,
            capacityMicroliters = reservoirAmountMicroliters(reservoir.capacityMilliliters),
            remainingMicroliters = reservoirAmountMicroliters(reservoir.remainingMilliliters),
            accountingCertain = reservoir.accountingCertain,
            lowLevelActive = reservoir.lowLevelActive,
            lowLevelAlertEnabled = lowLevelAlertEnabled
        )
    }

    fun activeRun(detail: DeviceDosingV1ChannelDetail): DeviceDosingActiveRun {
        val run = detail.activeRun
        return DeviceDosingActiveRun(
            active = run.active,
            source = runSource(run.source),
            targetAmountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                run.targetAmountMilliliters,
                allowZero = true
            ),
            remainingMillis = run.remainingMillis
        )
    }

    fun usage(detail: DeviceDosingV1ChannelDetail): DeviceDosingDailyUsageSnapshot {
        val usage = detail.usageToday
        return DeviceDosingDailyUsageSnapshot(
            valid = usage.dateValid,
            scheduledDeliveredMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                usage.scheduledDeliveredMilliliters,
                allowZero = true
            ),
            manualDeliveredMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                usage.manualDeliveredMilliliters,
                allowZero = true
            ),
            totalDeliveredMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                usage.totalDeliveredMilliliters,
                allowZero = true
            )
        )
    }

    fun controls(
        detail: DeviceDosingV1ChannelDetail,
        global: DeviceDosingV1GlobalStatus
    ): DeviceDosingChannelControls {
        val storageReady = allEnabled(
            global.envelope.bootReady,
            global.envelope.storageHealthy
        )
        val outputIdle = !detail.activeRun.active
        val persistedMutationAllowed = allEnabled(
            storageReady,
            outputIdle,
            detail.calibration.state.raw == CALIBRATION_IDLE
        )
        val runtimeRefillAllowed = allEnabled(storageReady, outputIdle)

        return DeviceDosingChannelControls(
            programEditable = allEnabled(
                persistedMutationAllowed,
                global.runtime.supportsProgramApply
            ),
            reservoirEditable = allEnabled(
                persistedMutationAllowed,
                detail.editable.reservoir,
                global.runtime.supportsChannelConfig
            ),
            displayNameEditable = allEnabled(
                persistedMutationAllowed,
                detail.editable.displayName,
                global.runtime.supportsChannelConfig
            ),
            calibrationEditable = allEnabled(
                detail.editable.dosingCalibration,
                global.runtime.supportsCalibrationWorkflow
            ),
            manualDoseSupported = global.runtime.supportsManualDose,
            stopDoseSupported = global.runtime.supportsManualDose,
            resetSupported = allEnabled(
                storageReady,
                global.runtime.supportsChannelReset,
                global.scheduling.supportsChannelReset
            ),
            refillSupported = allEnabled(
                runtimeRefillAllowed,
                detail.editable.reservoir,
                global.runtime.supportsReservoirRefill
            )
        )
    }

    private fun reservoirAmountMicroliters(value: Double): Long =
        if (value == FIRMWARE_UNAVAILABLE_AMOUNT_ML) {
            0L
        } else {
            DeviceDosingV1AmountMapper.toMicroliters(value, allowZero = true)
        }

    private fun runSource(value: DeviceDosingV1WireValue): DeviceDosingRunSource =
        when (value.raw) {
            "none" -> DeviceDosingRunSource.NONE
            "scheduled" -> DeviceDosingRunSource.SCHEDULED
            "manual" -> DeviceDosingRunSource.MANUAL
            "calibration" -> DeviceDosingRunSource.CALIBRATION
            "verification" -> DeviceDosingRunSource.VERIFICATION
            "prime" -> DeviceDosingRunSource.PRIME
            else -> DeviceDosingRunSource.UNKNOWN
        }

    private fun allEnabled(vararg conditions: Boolean): Boolean = conditions.all { it }

    private const val FIRMWARE_UNAVAILABLE_AMOUNT_ML = -1.0
    private const val CALIBRATION_IDLE = "idle"
}
