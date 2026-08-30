package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleState
import java.time.LocalDate

internal object DeviceDosingV1ProgressSnapshotMapper {
    fun map(
        status: DeviceDosingV1ProgressStatus,
        detail: DeviceDosingV1ChannelDetail
    ): DeviceDosingChannelProgress = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
            status.progress.totalAmountMilliliters,
            allowZero = true
        ),
        completedAmountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
            status.progress.completedAmountMilliliters,
            allowZero = true
        ),
        remainingAmountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
            status.progress.remainingAmountMilliliters,
            allowZero = true
        ),
        occurrences = status.occurrences.map(::occurrence),
        scheduleState = scheduleState(status.progress.scheduleState),
        totalOccurrences = status.progress.total,
        completedOccurrences = status.progress.completed,
        resolvedOccurrences = status.progress.resolved,
        pendingOccurrences = status.progress.pending,
        runningOccurrences = status.progress.running,
        skippedOccurrences = status.progress.skipped,
        uncertainOccurrences = status.progress.uncertain,
        completionPercent = status.progress.completionPercent,
        executionCurrent = status.progress.executionCurrent,
        accountingCertain = detail.deliveryAccountingCertain && status.progress.uncertain == 0,
        programDayDate = parseFirmwareProgramDayDate(status.progress.programDayDate)
    )

    private fun occurrence(value: DeviceDosingV1Occurrence): DeviceDosingOccurrenceProgress =
        DeviceDosingOccurrenceProgress(
            index = value.index,
            eventId = value.eventId,
            programDayOffset = value.programDayOffset,
            timeMillis = value.timeMillis,
            amountMicroliters = DeviceDosingV1AmountMapper.toMicroliters(
                value.amountMilliliters
            ),
            state = when (value.status.raw) {
                "pending" -> DeviceDosingOccurrenceState.PENDING
                "running" -> DeviceDosingOccurrenceState.RUNNING
                "completed" -> DeviceDosingOccurrenceState.COMPLETED
                "skipped" -> DeviceDosingOccurrenceState.SKIPPED
                "uncertain" -> DeviceDosingOccurrenceState.UNCERTAIN
                else -> error("Unknown firmware Dosing occurrence state.")
            }
        )

    private fun scheduleState(value: DeviceDosingV1WireValue): DeviceDosingScheduleState =
        when (value.raw) {
            "active" -> DeviceDosingScheduleState.ACTIVE
            "noSchedule" -> DeviceDosingScheduleState.NO_SCHEDULE
            else -> error("Unknown firmware Dosing schedule state.")
        }
}

/**
 * Converts the firmware-owned program-day anchor into an application date.
 *
 * A missing or malformed firmware value fails closed to null. Consumers must not substitute the
 * handset date because that would create a second program-day authority.
 */
internal fun parseFirmwareProgramDayDate(rawDate: String?): LocalDate? = rawDate
    ?.takeIf { value -> value.length == FIRMWARE_PROGRAM_DAY_DATE_LENGTH }
    ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

private const val FIRMWARE_PROGRAM_DAY_DATE_LENGTH = 10
