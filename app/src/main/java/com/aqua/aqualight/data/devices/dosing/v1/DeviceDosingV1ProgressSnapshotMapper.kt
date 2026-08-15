package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState

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
        occurrences = status.occurrences.map(::occurrence),
        executionCurrent = status.progress.executionCurrent,
        accountingCertain = detail.deliveryAccountingCertain && status.progress.uncertain == 0
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
}
