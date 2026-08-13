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
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerProgramConfig
import kotlin.math.abs
import kotlin.math.floor

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
            require(current.channel.calibration.doseMsPerMl > 0L)
        }

        val occurrences = compileOccurrences(program, current)
        if (program.enabled) {
            validateOccurrencePumpSafety(occurrences, current)
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

    private fun compileOccurrences(
        program: DeviceDosingProgram,
        current: DeviceDosingChannelStatus
    ): List<CompiledOccurrence> {
        val scheduling = current.scheduling
        return when (program.mode) {
            DeviceDosingProgramMode.SINGLE -> {
                val config = program.config as DeviceDosingDistributedProgramConfig
                validateLocalDayTime(config.startTimeMs)
                listOf(
                    CompiledOccurrence(
                        absoluteTimeMs = config.startTimeMs,
                        amountQuanta = canonicalAmountQuanta(config.dailyDoseMl, current)
                    )
                )
            }
            DeviceDosingProgramMode.HOURLY_24 -> {
                val config = program.config as DeviceDosingDistributedProgramConfig
                validateLocalDayTime(config.startTimeMs)
                require(scheduling.maxEventsPerChannel >= HOURLY_OCCURRENCE_COUNT) {
                    "Firmware does not expose the required hourly24 occurrence capacity."
                }
                val totalQuanta = canonicalAmountQuanta(config.dailyDoseMl, current)
                require(totalQuanta >= HOURLY_OCCURRENCE_COUNT.toLong())
                val amounts = distributeQuanta(totalQuanta, HOURLY_OCCURRENCE_COUNT)
                List(HOURLY_OCCURRENCE_COUNT) { index ->
                    CompiledOccurrence(
                        absoluteTimeMs = config.startTimeMs + index * HOURLY_SPACING_MS,
                        amountQuanta = amounts[index]
                    )
                }
            }
            DeviceDosingProgramMode.CUSTOM_PERIODS -> {
                val config = program.config as DeviceDosingCustomPeriodsProgramConfig
                require(config.periods.isNotEmpty())
                require(config.periods.size <= scheduling.maxCustomPeriodsPerChannel)
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
                val totalCount = ordered.sumOf { it.doseCount }
                require(totalCount in 1..scheduling.maxEventsPerChannel)
                val totalQuanta = canonicalAmountQuanta(config.dailyDoseMl, current)
                require(totalQuanta >= totalCount.toLong())

                val times = buildCustomOccurrenceTimes(ordered)
                require(times.size == totalCount)
                times.zipWithNext().forEach { (first, second) ->
                    require(first < second) { "Compiled custom Dosing occurrence times must be unique." }
                }
                val amounts = distributeQuanta(totalQuanta, totalCount)
                times.indices.map { index ->
                    CompiledOccurrence(times[index], amounts[index])
                }
            }
            DeviceDosingProgramMode.TIMER -> {
                val config = program.config as DeviceDosingTimerProgramConfig
                require(config.events.isNotEmpty())
                require(config.events.size <= scheduling.maxEventsPerChannel)
                val ordered = config.events.sortedBy { it.timeMs }
                ordered.forEach { event -> validateLocalDayTime(event.timeMs) }
                require(ordered.map { it.timeMs }.distinct().size == ordered.size) {
                    "Dosing timer event times must be unique."
                }
                ordered.map { event ->
                    CompiledOccurrence(
                        absoluteTimeMs = event.timeMs,
                        amountQuanta = canonicalAmountQuanta(event.amountMl, current)
                    )
                }
            }
        }
    }

    private fun buildCustomOccurrenceTimes(
        periods: List<com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriod>
    ): List<Long> = buildList {
        periods.forEach { period ->
            val span = period.endTimeMs - period.startTimeMs
            repeat(period.doseCount) { doseIndex ->
                val offset = if (period.doseCount == 1) {
                    0L
                } else {
                    val denominator = (period.doseCount - 1).toLong()
                    (doseIndex.toLong() * span + denominator / 2L) / denominator
                }
                add(period.startTimeMs + offset)
            }
        }
    }

    private fun distributeQuanta(totalQuanta: Long, count: Int): List<Long> {
        require(count > 0)
        val base = totalQuanta / count
        val remainder = totalQuanta % count
        return List(count) { index -> base + if (index.toLong() < remainder) 1L else 0L }
    }

    private fun validateOccurrencePumpSafety(
        occurrences: List<CompiledOccurrence>,
        current: DeviceDosingChannelStatus
    ) {
        require(occurrences.isNotEmpty())
        val scheduling = current.scheduling
        val doseMsPerMl = current.channel.calibration.doseMsPerMl
        val resolution = scheduling.amountResolutionMl
        require(doseMsPerMl > 0L)

        occurrences.forEachIndexed { index, occurrence ->
            val amountMl = occurrence.amountQuanta * resolution
            scheduling.effectiveScheduledDose.takeIf { it.available }?.let { range ->
                val minimum = requireNotNull(range.minDoseMl)
                val maximum = requireNotNull(range.maxDoseMl)
                require(amountMl + DOSING_VALUE_EPSILON >= minimum)
                require(amountMl <= maximum + DOSING_VALUE_EPSILON)
            }

            val durationRaw = amountMl * doseMsPerMl.toDouble()
            require(durationRaw.isFinite())
            val durationMs = floor(durationRaw + 0.5).toLong()
            require(durationMs in scheduling.minPumpRunDurationMs..scheduling.maxPumpRunDurationMs) {
                "Dosing occurrence $index violates firmware pump-duration safety limits."
            }

            val nextStartMs = if (index + 1 < occurrences.size) {
                occurrences[index + 1].absoluteTimeMs
            } else {
                occurrences.first().absoluteTimeMs + DeviceDosingRuntimeContract.Limit.MILLIS_PER_DAY
            }
            require(nextStartMs > occurrence.absoluteTimeMs)
            require(durationMs <= nextStartMs - occurrence.absoluteTimeMs) {
                "Dosing occurrence $index overlaps the next compiled pump run."
            }
        }
    }

    private fun canonicalAmountQuanta(
        amountMl: Double,
        current: DeviceDosingChannelStatus
    ): Long {
        require(amountMl.isFinite() && amountMl > 0.0)
        val resolution = current.scheduling.amountResolutionMl
        require(resolution.isFinite() && resolution > 0.0)
        val rawQuanta = amountMl / resolution
        require(rawQuanta.isFinite())
        val rounded = floor(rawQuanta + 0.5)
        require(rounded >= 1.0 && rounded <= Long.MAX_VALUE.toDouble())
        require(abs(rawQuanta - rounded) <= DOSING_VALUE_EPSILON) {
            "Dosing amount is not canonical for firmware amountResolutionMl=$resolution."
        }
        return rounded.toLong()
    }

    private fun validateLocalDayTime(timeMs: Long) {
        require(timeMs in 0L..DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY)
    }

    private fun validateScheduling(
        metadata: com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingSchedulingMetadata
    ) {
        require(metadata.contract == DeviceDosingRuntimeContract.SCHEMA)
        require(metadata.schemaVersion == DeviceDosingRuntimeContract.SCHEMA_VERSION)
        require(metadata.amountResolutionMl > 0.0)
        require(metadata.maxEventsPerChannel > 0)
        require(metadata.maxCustomPeriodsPerChannel > 0)
        require(metadata.minPumpRunDurationMs > 0L)
        require(metadata.maxPumpRunDurationMs >= metadata.minPumpRunDurationMs)
        require(metadata.supportedModes.toSet() == DeviceDosingProgramMode.entries.toSet())
        require(metadata.supportsWeekdayRecurrence)
    }

    private data class CompiledOccurrence(
        val absoluteTimeMs: Long,
        val amountQuanta: Long
    )

    private const val HOURLY_OCCURRENCE_COUNT = 24
    private const val HOURLY_SPACING_MS = 3_600_000L
}
