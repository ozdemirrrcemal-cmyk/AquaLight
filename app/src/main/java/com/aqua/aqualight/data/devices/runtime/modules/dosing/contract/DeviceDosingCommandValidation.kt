package com.aqua.aqualight.data.devices.runtime.modules.dosing.contract

import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriodsProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingMutationResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerProgramConfig

/** Cross-validates Android requests against the final firmware-owned Dosing v1 contract. */
internal object DeviceDosingCommandValidation {
    fun validateGlobalStatus(status: DeviceDosingGlobalStatus, access: DeviceDosingRuntimeAccess) {
        require(access.supportsApi)
        require(status.envelope.channelCount == access.channelCount)
        require(status.channels.size == access.channelCount)
        require(status.runtime.module == DeviceDosingRuntimeContract.MODULE)
        require(status.runtime.supportsProgramApply == access.supportsProgramEditing)
        require(status.runtime.supportsChannelConfig)
        require(status.runtime.supportsChannelReset == access.supportsChannelReset)
        require(status.runtime.supportsPrime == access.supportsPrime)
        require(status.runtime.supportsManualDose == access.supportsManualDose)
        require(status.runtime.supportsCalibrationWorkflow == access.supportsCalibrationWorkflow)
        require(status.runtime.supportsReservoirRefill == access.supportsReservoirRefill)
        require(status.runtime.supportsChannelScopedStatus)
        require(status.runtime.displayNameEditable == access.supportsChannelDisplayName)
        validateScheduling(status.scheduling)
    }

    fun validateChannelStatus(
        status: DeviceDosingChannelStatus,
        requestedChannelKey: String,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi)
        require(status.envelope.channelCount == access.channelCount)
        require(status.channel.channelKey == normalizeDosingChannelKey(requestedChannelKey))
        require(status.channel.index in 0 until access.channelCount)
        require(!status.channel.editable.hardware)
        require(status.channel.editable.displayName == access.supportsChannelDisplayName)
        require(status.channel.editable.dosingCalibration == access.supportsCalibrationWorkflow)
        require(status.channel.editable.reservoir == access.supportsReservoirRefill)
        validateScheduling(status.scheduling)
    }

    fun validateChannelConfigRequest(
        payload: DeviceDosingChannelConfigPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(payload.expectedRevision == current.channel.revision) {
            "Dosing channel config expectedRevision is stale before dispatch."
        }
        payload.displayName?.let {
            require(access.supportsChannelDisplayName && current.channel.editable.displayName)
        }
        payload.reservoir?.let {
            require(access.supportsReservoirRefill && current.channel.editable.reservoir)
        }
    }

    fun validateProgramRequest(
        payload: DeviceDosingProgramApplyPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsProgramEditing)
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(payload.expectedRevision == current.channel.revision) {
            "Dosing program expectedRevision is stale before dispatch."
        }
        val program = payload.program
        val scheduling = current.scheduling
        require(program.mode in scheduling.supportedModes)
        if (program.enabled) {
            require(program.weekdays.any { it })
            require(current.channel.calibration.confirmed) {
                "An enabled Dosing program requires confirmed calibration."
            }
        }
        when (val config = program.config) {
            is DeviceDosingDistributedProgramConfig -> {
                validateCanonicalAmount(config.dailyDoseMl, current)
                validateLocalDayTime(config.startTimeMs)
            }
            is DeviceDosingCustomPeriodsProgramConfig -> {
                require(config.periods.size <= scheduling.maxCustomPeriodsPerChannel)
                require(config.periods.sumOf { it.doseCount } <= scheduling.maxEventsPerChannel)
                validateCanonicalAmount(config.dailyDoseMl, current)
                val ordered = config.periods.sortedBy { it.startTimeMs }
                ordered.forEach { period ->
                    validateLocalDayTime(period.startTimeMs)
                    validateLocalDayTime(period.endTimeMs)
                    require(period.startTimeMs < period.endTimeMs)
                    require(period.doseCount > 0)
                }
                ordered.zipWithNext().forEach { (first, second) ->
                    require(first.endTimeMs < second.startTimeMs) {
                        "Dosing custom periods must not overlap or touch."
                    }
                }
            }
            is DeviceDosingTimerProgramConfig -> {
                require(config.events.size <= scheduling.maxEventsPerChannel)
                config.events.forEach { event ->
                    validateLocalDayTime(event.timeMs)
                    validateCanonicalAmount(event.amountMl, current)
                }
                require(config.events.map { it.timeMs }.distinct().size == config.events.size) {
                    "Dosing timer event times must be unique."
                }
            }
        }
        if (program.mode == DeviceDosingProgramMode.HOURLY_24) {
            require(scheduling.maxEventsPerChannel >= 24) {
                "Firmware does not expose the required hourly24 occurrence capacity."
            }
        }
    }

    fun validateChannelResetRequest(
        payload: DeviceDosingChannelResetPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsChannelReset && current.scheduling.supportsChannelReset)
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(payload.expectedRevision == current.channel.revision) {
            "Dosing reset expectedRevision is stale before dispatch."
        }
    }

    fun validatePrimeRequest(
        channelKey: String,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsPrime)
        validateChannelStatus(current, channelKey, access)
    }

    fun validateDoseRequest(
        payload: DeviceDosingDoseNowPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsManualDose)
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(payload.amountMl <= current.scheduling.maxManualDoseMl + DOSING_VALUE_EPSILON)
        if (payload.usePendingCalibration) {
            val calibration = current.channel.calibration
            require(calibration.state == DeviceDosingCalibrationState.PENDING_VERIFICATION)
            require(calibration.pendingDoseMsPerMl > 0L)
            require(!calibration.verificationDoseStarted)
        } else {
            require(current.channel.calibration.confirmed)
        }
    }

    fun validateCalibrationStartRequest(
        payload: DeviceDosingCalibrationStartPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsCalibrationWorkflow)
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(current.channel.calibration.state == DeviceDosingCalibrationState.IDLE)
    }

    fun validateCalibrationFinishRequest(
        payload: DeviceDosingCalibrationFinishPayload,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsCalibrationWorkflow)
        validateChannelStatus(current, payload.normalizedChannelKey, access)
        require(current.channel.calibration.state == DeviceDosingCalibrationState.RUNNING)
    }

    fun validateCalibrationChannelRequest(
        channelKey: String,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess,
        requireVerificationComplete: Boolean = false
    ) {
        require(access.supportsCalibrationWorkflow)
        validateChannelStatus(current, channelKey, access)
        if (requireVerificationComplete) {
            require(current.channel.calibration.state == DeviceDosingCalibrationState.PENDING_VERIFICATION)
            require(current.channel.calibration.verificationDoseComplete)
        }
    }

    fun validateReservoirRequest(
        channelKey: String,
        current: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsReservoirRefill)
        validateChannelStatus(current, channelKey, access)
        require(current.channel.reservoir.trackingEnabled)
        require(current.channel.reservoir.capacityMl > 0.0)
    }

    fun validateMutation(
        expectedChannelKey: String,
        result: DeviceDosingMutationResult,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi)
        val expected = normalizeDosingChannelKey(expectedChannelKey)
        require(result.channelKey == expected)
        require(result.channel.channelKey == expected)
        require(result.channel.index in 0 until access.channelCount)
        require(result.event == DeviceDosingRuntimeContract.STATUS_EVENT)
    }

    private fun validateCanonicalAmount(amountMl: Double, current: DeviceDosingChannelStatus) {
        require(amountMl.isFinite() && amountMl > 0.0)
        val resolution = current.scheduling.amountResolutionMl
        require(resolution.isFinite() && resolution > 0.0)
        val quanta = amountMl / resolution
        require(kotlin.math.abs(quanta - kotlin.math.round(quanta)) <= 0.000_001) {
            "Dosing amount is not canonical for firmware amountResolutionMl=$resolution."
        }
        current.scheduling.effectiveScheduledDose.takeIf { it.available }?.let { range ->
            requireNotNull(range.minDoseMl)
            requireNotNull(range.maxDoseMl)
            require(amountMl + DOSING_VALUE_EPSILON >= range.minDoseMl)
            require(amountMl <= range.maxDoseMl + DOSING_VALUE_EPSILON)
        }
    }

    private fun validateLocalDayTime(timeMs: Long) {
        require(timeMs in 0L..DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY)
    }

    private fun validateScheduling(metadata: com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingSchedulingMetadata) {
        require(metadata.contract == DeviceDosingRuntimeContract.SCHEMA)
        require(metadata.schemaVersion == DeviceDosingRuntimeContract.SCHEMA_VERSION)
        require(metadata.amountResolutionMl > 0.0)
        require(metadata.maxEventsPerChannel > 0)
        require(metadata.maxCustomPeriodsPerChannel > 0)
        require(metadata.supportedModes.toSet() == DeviceDosingProgramMode.entries.toSet())
        require(metadata.supportsWeekdayRecurrence)
    }
}
