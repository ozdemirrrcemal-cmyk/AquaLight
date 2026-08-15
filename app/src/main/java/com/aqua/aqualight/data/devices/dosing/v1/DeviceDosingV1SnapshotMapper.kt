package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlin.math.abs
import kotlin.math.round

internal data class DeviceDosingV1MappedSnapshots(
    val channel: DeviceDosingChannelSnapshot,
    val calibration: DeviceDosingCalibrationSnapshot
)

/** Exact wire-to-application mapping shared by every Dosing read and mutation path. */
internal object DeviceDosingV1SnapshotMapper {

    fun map(
        deviceUid: DeviceUid,
        slotId: String,
        global: DeviceDosingV1GlobalStatus,
        channelStatus: DeviceDosingV1ChannelStatus,
        progressStatus: DeviceDosingV1ProgressStatus,
        lowLevelAlertEnabled: Boolean
    ): DeviceDosingV1MappedSnapshots {
        val detail = channelStatus.channel
        validateJoinedStatus(slotId, global, channelStatus, progressStatus)
        val scheduling = global.scheduling.toApplicationPolicy()
        val channelNumber = detail.index + 1
        val channelSnapshot = DeviceDosingChannelSnapshot(
            deviceUid = deviceUid.value,
            slotId = slotId,
            pumpCount = global.envelope.channelCount,
            channelNumber = channelNumber,
            channelTitle = detail.effectiveName,
            revision = detail.revision,
            runtimeEnabled = detail.runtimeEnabled,
            runtimeReason = detail.runtimeReason.toApplicationRuntimeReason(),
            deliveryAccountingCertain = detail.deliveryAccountingCertain,
            calibrated = detail.calibration.confirmed,
            lastCalibratedAtEpochSeconds = detail.calibration.lastCalibratedAt,
            scheduling = scheduling,
            program = detail.program?.toApplicationProgram(),
            progress = progressStatus.toApplicationProgress(detail),
            reservoir = detail.reservoir.toApplicationReservoir(lowLevelAlertEnabled),
            activeRun = detail.activeRun.toApplicationActiveRun(),
            controls = detail.toApplicationControls(global),
            usageToday = detail.usageToday.toApplicationUsage()
        )
        return DeviceDosingV1MappedSnapshots(
            channel = channelSnapshot,
            calibration = detail.toApplicationCalibration(
                deviceUid = deviceUid,
                slotId = slotId,
                envelope = channelStatus.envelope
            )
        )
    }

    fun toWireProgram(program: DeviceDosingProgram): DeviceDosingV1Program =
        DeviceDosingV1Program(
            enabled = program.enabled,
            weekdays = DeviceDosingV1Weekdays(program.weekdays),
            config = program.schedule.toWireConfig(),
            missedDoseRecoveryEnabled = program.missedDoseRecoveryEnabled
        )

    private fun validateJoinedStatus(
        slotId: String,
        global: DeviceDosingV1GlobalStatus,
        channelStatus: DeviceDosingV1ChannelStatus,
        progressStatus: DeviceDosingV1ProgressStatus
    ) {
        val detail = channelStatus.channel
        val expectedKey = DeviceDosingV1SlotKeyMapper.channelKey(slotId)
        require(global.envelope.channelCount == global.channels.size) {
            "Dosing global channel count does not match the channel list."
        }
        require(channelStatus.envelope.channelCount == global.envelope.channelCount)
        require(progressStatus.envelope.channelCount == global.envelope.channelCount)
        require(detail.channelKey == expectedKey)
        require(progressStatus.channelKey == expectedKey)
        require(detail.index in 0 until global.envelope.channelCount)
        require(DeviceDosingV1SlotKeyMapper.channelNumber(expectedKey) == detail.index + 1)
        val globalChannel = requireNotNull(
            global.channels.singleOrNull { candidate -> candidate.channelKey == expectedKey }
        ) { "Dosing global status does not contain the requested channel." }
        require(globalChannel.revision == detail.revision)
        require(progressStatus.revision == detail.revision)
        require(globalChannel.effectiveName == detail.effectiveName)
        require(globalChannel.programMode.raw == (detail.program?.mode?.raw ?: PROGRAM_MODE_NONE))
    }

    private fun DeviceDosingV1SchedulingMetadata.toApplicationPolicy():
        DeviceDosingSchedulingPolicy = DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = amountResolutionMilliliters.toMicroliters(),
            maxEventsPerChannel = maxEventsPerChannel,
            maxCustomPeriodsPerChannel = maxCustomPeriodsPerChannel,
            scheduledDispatchGraceMillis = scheduledDispatchGraceMillis,
            minimumPumpRunDurationMillis = minimumPumpRunDurationMillis,
            maximumPumpRunDurationMillis = maximumPumpRunDurationMillis,
            maximumManualDoseMicroliters = maximumManualDoseMilliliters.toMicroliters(),
            supportsWeekdayRecurrence = supportsWeekdayRecurrence,
            supportsMissedDoseRecovery = supportsMissedDoseRecovery,
            supportsChannelReset = supportsChannelReset,
            supportsDailyDeliveredUsage = supportsDailyDeliveredUsage,
            supportedModes = supportedModes.mapTo(linkedSetOf()) { mode ->
                mode.toApplicationProgramMode()
            },
            effectiveScheduledDoseMicroliters = effectiveScheduledDose.toApplicationRange()
        )

    private fun DeviceDosingV1EffectiveScheduledDose.toApplicationRange(): LongRange? {
        if (!available) return null
        val minimum = requireNotNull(minimumMilliliters).toMicroliters()
        val maximum = requireNotNull(maximumMilliliters).toMicroliters()
        require(maximum >= minimum)
        return minimum..maximum
    }

    private fun DeviceDosingV1ProgramSnapshot.toApplicationProgram(): DeviceDosingProgram {
        val schedule = when (val snapshot = config) {
            is DeviceDosingV1ProgramSnapshotConfig.Single -> {
                require(mode.raw == PROGRAM_MODE_SINGLE)
                DeviceDosingProgramSchedule.Single(
                    snapshot.dailyDoseMilliliters.toMicroliters(),
                    snapshot.startTimeMillis
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Hourly24 -> {
                require(mode.raw == PROGRAM_MODE_HOURLY_24)
                DeviceDosingProgramSchedule.Hourly24(
                    snapshot.dailyDoseMilliliters.toMicroliters(),
                    snapshot.startTimeMillis
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.CustomPeriods -> {
                require(mode.raw == PROGRAM_MODE_CUSTOM_PERIODS)
                DeviceDosingProgramSchedule.CustomPeriods(
                    dailyDoseMicroliters = snapshot.dailyDoseMilliliters.toMicroliters(),
                    periods = snapshot.periods.map { period ->
                        DeviceDosingCustomPeriodDraft(
                            startTimeMs = period.startTimeMillis,
                            endTimeMs = period.endTimeMillis,
                            doseCount = period.doseCount
                        )
                    }
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Timer -> {
                require(mode.raw == PROGRAM_MODE_TIMER)
                DeviceDosingProgramSchedule.Timer(
                    doses = snapshot.events.map { event ->
                        DeviceDosingTimerDoseDraft(
                            startTimeMs = event.timeMillis,
                            amountMicroliters = event.amountMilliliters.toMicroliters()
                        )
                    }
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Unknown -> error(
                "Unknown firmware Dosing program mode cannot enter application state."
            )
        }
        return DeviceDosingProgram(
            enabled = enabled,
            weekdays = weekdays,
            schedule = schedule,
            missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
        )
    }

    private fun DeviceDosingV1ProgressStatus.toApplicationProgress(
        detail: DeviceDosingV1ChannelDetail
    ): DeviceDosingChannelProgress = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = progress.totalAmountMilliliters.toMicroliters(
            allowZero = true
        ),
        completedAmountMicroliters = progress.completedAmountMilliliters.toMicroliters(
            allowZero = true
        ),
        occurrences = occurrences.map { occurrence -> occurrence.toApplicationOccurrence() },
        executionCurrent = progress.executionCurrent,
        accountingCertain = detail.deliveryAccountingCertain && progress.uncertain == 0
    )

    private fun DeviceDosingV1Occurrence.toApplicationOccurrence():
        DeviceDosingOccurrenceProgress = DeviceDosingOccurrenceProgress(
            index = index,
            eventId = eventId,
            programDayOffset = programDayOffset,
            timeMillis = timeMillis,
            amountMicroliters = amountMilliliters.toMicroliters(),
            state = when (status.raw) {
                "pending" -> DeviceDosingOccurrenceState.PENDING
                "running" -> DeviceDosingOccurrenceState.RUNNING
                "completed" -> DeviceDosingOccurrenceState.COMPLETED
                "skipped" -> DeviceDosingOccurrenceState.SKIPPED
                "uncertain" -> DeviceDosingOccurrenceState.UNCERTAIN
                else -> error("Unknown firmware Dosing occurrence state.")
            }
        )

    private fun DeviceDosingV1Reservoir.toApplicationReservoir(
        lowLevelAlertEnabled: Boolean
    ): DeviceDosingReservoirSnapshot = DeviceDosingReservoirSnapshot(
        trackingEnabled = trackingEnabled,
        capacityMicroliters = capacityMilliliters.toMicroliters(allowZero = true),
        remainingMicroliters = remainingMilliliters.toMicroliters(allowZero = true),
        accountingCertain = accountingCertain,
        lowLevelActive = lowLevelActive,
        lowLevelAlertEnabled = lowLevelAlertEnabled
    )

    private fun DeviceDosingV1ActiveRun.toApplicationActiveRun(): DeviceDosingActiveRun =
        DeviceDosingActiveRun(
            active = active,
            source = source.toApplicationRunSource(),
            targetAmountMicroliters = targetAmountMilliliters.toMicroliters(allowZero = true),
            remainingMillis = remainingMillis
        )

    private fun DeviceDosingV1DailyUsage.toApplicationUsage():
        DeviceDosingDailyUsageSnapshot = DeviceDosingDailyUsageSnapshot(
            valid = dateValid,
            scheduledDeliveredMicroliters = scheduledDeliveredMilliliters.toMicroliters(
                allowZero = true
            ),
            manualDeliveredMicroliters = manualDeliveredMilliliters.toMicroliters(
                allowZero = true
            ),
            totalDeliveredMicroliters = totalDeliveredMilliliters.toMicroliters(allowZero = true)
        )

    private fun DeviceDosingV1ChannelDetail.toApplicationControls(
        global: DeviceDosingV1GlobalStatus
    ): DeviceDosingChannelControls = DeviceDosingChannelControls(
        programEditable = global.runtime.supportsProgramApply,
        reservoirEditable = editable.reservoir && global.runtime.supportsChannelConfig,
        displayNameEditable = editable.displayName && global.runtime.supportsChannelConfig,
        calibrationEditable = editable.dosingCalibration &&
            global.runtime.supportsCalibrationWorkflow,
        manualDoseSupported = global.runtime.supportsManualDose,
        stopDoseSupported = global.runtime.supportsManualDose,
        resetSupported = global.runtime.supportsChannelReset &&
            global.scheduling.supportsChannelReset,
        refillSupported = editable.reservoir && global.runtime.supportsReservoirRefill
    )

    private fun DeviceDosingV1ChannelDetail.toApplicationCalibration(
        deviceUid: DeviceUid,
        slotId: String,
        envelope: DeviceDosingV1Envelope
    ): DeviceDosingCalibrationSnapshot = DeviceDosingCalibrationSnapshot(
        deviceUid = deviceUid.value,
        slotId = slotId,
        pumpCount = envelope.channelCount,
        channelNumber = index + 1,
        channelTitle = effectiveName,
        deviceUptimeMs = envelope.uptimeMillis,
        calibrated = calibration.confirmed,
        lastCalibratedAt = calibration.lastCalibratedAt,
        sessionPhase = calibration.state.toApplicationCalibrationPhase(),
        startedAtUptimeMs = calibrationStartedAtUptime(),
        durationMs = calibration.durationMillis,
        measuredMl = calibration.measuredMilliliters,
        pendingDoseMsPerMl = calibration.pendingDoseMillisPerMilliliter.toExactLong(),
        verificationDoseStarted = calibration.verificationDoseStarted,
        verificationDoseComplete = calibration.verificationDoseComplete,
        verificationDoseRemainingMs = verificationRemainingMillis(),
        manualActive = activeRun.active
    )

    private fun DeviceDosingV1ChannelDetail.calibrationStartedAtUptime(): Long =
        lastRuntimeEvent.occurredAtMillis.takeIf {
            lastRuntimeEvent.valid &&
                lastRuntimeEvent.kind.raw == "runStarted" &&
                lastRuntimeEvent.source.raw in CALIBRATION_RUN_SOURCES
        } ?: 0L

    private fun DeviceDosingV1ChannelDetail.verificationRemainingMillis(): Long =
        activeRun.remainingMillis.takeIf {
            activeRun.active && activeRun.source.raw == "verification"
        } ?: 0L

    private fun DeviceDosingV1WireValue.toApplicationProgramMode(): DeviceDosingProgramMode =
        when (raw) {
            PROGRAM_MODE_SINGLE -> DeviceDosingProgramMode.SINGLE
            PROGRAM_MODE_HOURLY_24 -> DeviceDosingProgramMode.HOURLY_24
            PROGRAM_MODE_CUSTOM_PERIODS -> DeviceDosingProgramMode.CUSTOM_PERIODS
            PROGRAM_MODE_TIMER -> DeviceDosingProgramMode.TIMER
            else -> error("Unknown firmware Dosing program mode.")
        }

    private fun DeviceDosingV1WireValue.toApplicationRuntimeReason(): DeviceDosingRuntimeReason =
        when (raw) {
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

    private fun DeviceDosingV1WireValue.toApplicationRunSource(): DeviceDosingRunSource =
        when (raw) {
            "none" -> DeviceDosingRunSource.NONE
            "scheduled" -> DeviceDosingRunSource.SCHEDULED
            "manual" -> DeviceDosingRunSource.MANUAL
            "calibration" -> DeviceDosingRunSource.CALIBRATION
            "verification" -> DeviceDosingRunSource.VERIFICATION
            "prime" -> DeviceDosingRunSource.PRIME
            else -> DeviceDosingRunSource.UNKNOWN
        }

    private fun DeviceDosingV1WireValue.toApplicationCalibrationPhase():
        DeviceDosingCalibrationSessionPhase = when (raw) {
            "idle" -> DeviceDosingCalibrationSessionPhase.IDLE
            "running" -> DeviceDosingCalibrationSessionPhase.RUNNING
            "pendingVerification" -> DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION
            else -> error("Unknown firmware Dosing calibration state.")
        }

    private fun DeviceDosingProgramSchedule.toWireConfig(): DeviceDosingV1ProgramConfig =
        when (this) {
            is DeviceDosingProgramSchedule.Single -> DeviceDosingV1ProgramConfig.Single(
                dailyDose = dailyDoseMicroliters.toWireAmount(),
                startTimeMillis = startTimeMillis
            )
            is DeviceDosingProgramSchedule.Hourly24 -> DeviceDosingV1ProgramConfig.Hourly24(
                dailyDose = dailyDoseMicroliters.toWireAmount(),
                startTimeMillis = startTimeMillis
            )
            is DeviceDosingProgramSchedule.CustomPeriods ->
                DeviceDosingV1ProgramConfig.CustomPeriods(
                    dailyDose = dailyDoseMicroliters.toWireAmount(),
                    periods = periods.map { period ->
                        DeviceDosingV1ProgramConfig.CustomPeriod(
                            startTimeMillis = period.startTimeMs,
                            endTimeMillis = period.endTimeMs,
                            doseCount = period.doseCount
                        )
                    }
                )
            is DeviceDosingProgramSchedule.Timer -> DeviceDosingV1ProgramConfig.Timer(
                events = doses.map { dose ->
                    DeviceDosingV1ProgramConfig.TimerEvent(
                        timeMillis = dose.startTimeMs,
                        amount = dose.amountMicroliters.toWireAmount()
                    )
                }
            )
        }

    private fun Long.toWireAmount(): DeviceDosingV1Amount =
        DeviceDosingV1Amount.fromMilliliters(
            toDouble() / DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
        )

    private fun Double.toMicroliters(allowZero: Boolean = false): Long {
        require(isFinite()) { "Firmware Dosing amount must be finite." }
        val scaled = this * DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
        val normalized = round(scaled)
        require(abs(scaled - normalized) <= AMOUNT_NORMALIZATION_TOLERANCE) {
            "Firmware Dosing amount exceeds the application resolution."
        }
        val minimum = if (allowZero) 0L else 1L
        require(normalized in minimum.toDouble()..Long.MAX_VALUE.toDouble())
        return normalized.toLong()
    }

    private fun Double.toExactLong(): Long {
        require(isFinite() && this >= 0.0)
        val normalized = round(this)
        require(abs(this - normalized) <= AMOUNT_NORMALIZATION_TOLERANCE)
        require(normalized <= Long.MAX_VALUE.toDouble())
        return normalized.toLong()
    }

    private const val AMOUNT_NORMALIZATION_TOLERANCE = 0.000_001
    private const val PROGRAM_MODE_NONE = "none"
    private const val PROGRAM_MODE_SINGLE = "single"
    private const val PROGRAM_MODE_HOURLY_24 = "hourly24"
    private const val PROGRAM_MODE_CUSTOM_PERIODS = "customPeriods"
    private const val PROGRAM_MODE_TIMER = "timer"
    private val CALIBRATION_RUN_SOURCES = setOf("calibration", "verification")
}

/** The catalog's stable `dosing:channelN` identity never crosses the wire boundary. */
internal object DeviceDosingV1SlotKeyMapper {
    fun channelKey(slotId: String): DeviceDosingV1ChannelKey {
        require(SLOT_PATTERN.matches(slotId)) { "Invalid stable Dosing slot id." }
        return DeviceDosingV1ChannelKey.from(slotId.removePrefix(SLOT_PREFIX))
    }

    fun slotId(channelKey: DeviceDosingV1ChannelKey): String {
        channelNumber(channelKey)
        return SLOT_PREFIX + channelKey.value
    }

    fun channelNumber(channelKey: DeviceDosingV1ChannelKey): Int {
        val match = requireNotNull(CHANNEL_PATTERN.matchEntire(channelKey.value)) {
            "Firmware Dosing channel key does not match the commercial catalog shape."
        }
        return match.groupValues[1].toInt().also { number -> require(number > 0) }
    }

    private const val SLOT_PREFIX = "dosing:"
    private val SLOT_PATTERN = Regex("^dosing:channel[1-9][0-9]*$")
    private val CHANNEL_PATTERN = Regex("^channel([1-9][0-9]*)$")
}
