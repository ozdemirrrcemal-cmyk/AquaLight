package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft

internal object DeviceDosingV1ProgramSnapshotMapper {
    fun scheduling(metadata: DeviceDosingV1SchedulingMetadata): DeviceDosingSchedulingPolicy =
        DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                metadata.amountResolutionMilliliters
            ),
            maxEventsPerChannel = metadata.maxEventsPerChannel,
            maxCustomPeriodsPerChannel = metadata.maxCustomPeriodsPerChannel,
            scheduledDispatchGraceMillis = metadata.scheduledDispatchGraceMillis,
            minimumPumpRunDurationMillis = metadata.minimumPumpRunDurationMillis,
            maximumPumpRunDurationMillis = metadata.maximumPumpRunDurationMillis,
            maximumManualDoseMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                metadata.maximumManualDoseMilliliters
            ),
            supportsWeekdayRecurrence = metadata.supportsWeekdayRecurrence,
            supportsMissedDoseRecovery = metadata.supportsMissedDoseRecovery,
            supportsChannelReset = metadata.supportsChannelReset,
            supportsDailyDeliveredUsage = metadata.supportsDailyDeliveredUsage,
            supportedModes = metadata.supportedModes.mapTo(linkedSetOf(), ::programMode),
            effectiveScheduledDoseMicroliters = scheduledDoseRange(
                metadata.effectiveScheduledDose
            )
        )

    fun program(snapshot: DeviceDosingV1ProgramSnapshot): DeviceDosingProgram =
        DeviceDosingProgram(
            enabled = snapshot.enabled,
            weekdays = snapshot.weekdays,
            schedule = schedule(snapshot),
            missedDoseRecoveryEnabled = snapshot.missedDoseRecoveryEnabled
        )

    fun toWireProgram(program: DeviceDosingProgram): DeviceDosingV1Program = DeviceDosingV1Program(
        enabled = program.enabled,
        weekdays = DeviceDosingV1Weekdays(program.weekdays),
        config = wireConfig(program.schedule),
        missedDoseRecoveryEnabled = program.missedDoseRecoveryEnabled
    )

    private fun programMode(value: DeviceDosingV1WireValue): DeviceDosingProgramMode =
        when (value.raw) {
            PROGRAM_MODE_SINGLE -> DeviceDosingProgramMode.SINGLE
            PROGRAM_MODE_HOURLY_24 -> DeviceDosingProgramMode.HOURLY_24
            PROGRAM_MODE_CUSTOM_PERIODS -> DeviceDosingProgramMode.CUSTOM_PERIODS
            PROGRAM_MODE_TIMER -> DeviceDosingProgramMode.TIMER
            else -> error("Unknown firmware Dosing program mode.")
        }

    private fun scheduledDoseRange(value: DeviceDosingV1EffectiveScheduledDose): LongRange? =
        value.takeIf { range -> range.available }?.let { range ->
            val minimum = DeviceDosingV1AmountMapper.toMicroliters(
                requireNotNull(range.minimumMilliliters)
            )
            val maximum = DeviceDosingV1AmountMapper.toMicroliters(
                requireNotNull(range.maximumMilliliters)
            )
            require(maximum >= minimum)
            minimum..maximum
        }

    private fun schedule(snapshot: DeviceDosingV1ProgramSnapshot): DeviceDosingProgramSchedule =
        when (val config = snapshot.config) {
            is DeviceDosingV1ProgramSnapshotConfig.Single -> {
                require(snapshot.mode.raw == PROGRAM_MODE_SINGLE)
                DeviceDosingProgramSchedule.Single(
                    DeviceDosingV1AmountMapper.toMicroliters(config.dailyDoseMilliliters),
                    config.startTimeMillis
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Hourly24 -> {
                require(snapshot.mode.raw == PROGRAM_MODE_HOURLY_24)
                DeviceDosingProgramSchedule.Hourly24(
                    DeviceDosingV1AmountMapper.toMicroliters(config.dailyDoseMilliliters),
                    config.startTimeMillis
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.CustomPeriods -> {
                require(snapshot.mode.raw == PROGRAM_MODE_CUSTOM_PERIODS)
                DeviceDosingProgramSchedule.CustomPeriods(
                    dailyDoseMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                        config.dailyDoseMilliliters
                    ),
                    periods = config.periods.map { period ->
                        DeviceDosingCustomPeriodDraft(
                            startTimeMs = period.startTimeMillis,
                            endTimeMs = period.endTimeMillis,
                            doseCount = period.doseCount
                        )
                    }
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Timer -> {
                require(snapshot.mode.raw == PROGRAM_MODE_TIMER)
                DeviceDosingProgramSchedule.Timer(
                    doses = config.events.map { event ->
                        DeviceDosingTimerDoseDraft(
                            startTimeMs = event.timeMillis,
                            amountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                                event.amountMilliliters
                            )
                        )
                    }
                )
            }
            is DeviceDosingV1ProgramSnapshotConfig.Unknown -> error(
                "Unknown firmware Dosing program mode cannot enter application state."
            )
        }

    private fun wireConfig(schedule: DeviceDosingProgramSchedule): DeviceDosingV1ProgramConfig =
        when (schedule) {
            is DeviceDosingProgramSchedule.Single -> DeviceDosingV1ProgramConfig.Single(
                dailyDose = DeviceDosingV1AmountMapper.toWireAmount(
                    schedule.dailyDoseMicroliters
                ),
                startTimeMillis = schedule.startTimeMillis
            )
            is DeviceDosingProgramSchedule.Hourly24 -> DeviceDosingV1ProgramConfig.Hourly24(
                dailyDose = DeviceDosingV1AmountMapper.toWireAmount(
                    schedule.dailyDoseMicroliters
                ),
                startTimeMillis = schedule.startTimeMillis
            )
            is DeviceDosingProgramSchedule.CustomPeriods ->
                DeviceDosingV1ProgramConfig.CustomPeriods(
                    dailyDose = DeviceDosingV1AmountMapper.toWireAmount(
                        schedule.dailyDoseMicroliters
                    ),
                    periods = schedule.periods.map { period ->
                        DeviceDosingV1ProgramConfig.CustomPeriod(
                            startTimeMillis = period.startTimeMs,
                            endTimeMillis = period.endTimeMs,
                            doseCount = period.doseCount
                        )
                    }
                )
            is DeviceDosingProgramSchedule.Timer -> DeviceDosingV1ProgramConfig.Timer(
                events = schedule.doses.map { dose ->
                    DeviceDosingV1ProgramConfig.TimerEvent(
                        timeMillis = dose.startTimeMs,
                        amount = DeviceDosingV1AmountMapper.toWireAmount(dose.amountMicroliters)
                    )
                }
            )
        }

    private const val PROGRAM_MODE_SINGLE = "single"
    private const val PROGRAM_MODE_HOURLY_24 = "hourly24"
    private const val PROGRAM_MODE_CUSTOM_PERIODS = "customPeriods"
    private const val PROGRAM_MODE_TIMER = "timer"
}
