package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpVisualState
import java.time.LocalDate

internal fun DeviceDosingChannelSlot.toInitialDosingChannelCardUiState():
    DosingChannelCardUiState = DosingChannelCardUiState(
    slotId = id.value,
    channelNumber = index.position,
    displayName = defaultDisplayName
)

internal fun DosingChannelCardUiState.withNavigationTarget(
    target: DeviceDosingChannelNavigationTarget?
): DosingChannelCardUiState = target?.let { navigationTarget ->
    copy(
        displayName = navigationTarget.channelTitle.ifBlank { displayName },
        visualState = when (navigationTarget.destination) {
            DeviceDosingChannelDestination.DETAIL -> visualState.takeUnless { state ->
                state == DosingChannelVisualState.NOT_CONFIGURED
            } ?: DosingChannelVisualState.PROGRAM_NOT_CONFIGURED
            DeviceDosingChannelDestination.CALIBRATION ->
                DosingChannelVisualState.NOT_CONFIGURED
        }
    )
} ?: this

internal fun DosingChannelCardUiState.withChannelSnapshot(
    snapshot: DeviceDosingChannelSnapshot?,
    today: LocalDate = LocalDate.now()
): DosingChannelCardUiState = snapshot?.let { channel ->
    copy(
        displayName = channel.channelTitle.ifBlank { displayName },
        visualState = channel.toCardVisualState(),
        scheduleDays = channel.toScheduleDaysUiState(),
        programProgress = channel.toProgramProgressUiState(today),
        reservoir = channel.toReservoirUiState(today)
    )
} ?: this

internal fun DeviceDosingChannelSnapshot.toPumpVisualState(): DosingPumpVisualState = when {
    hasAttentionState() -> DosingPumpVisualState.ERROR
    activeRun.active -> DosingPumpVisualState.RUNNING
    else -> DosingPumpVisualState.IDLE
}

private fun DeviceDosingChannelSnapshot.toScheduleDaysUiState() = DosingScheduleDaysUiState(
    selectedDays = ALL_DOSING_WEEKDAYS.zip(program?.weekdays.orEmpty())
        .mapNotNull { (weekday, selected) -> weekday.takeIf { selected } }
)

private fun DeviceDosingChannelSnapshot.toProgramProgressUiState(
    today: LocalDate
): DosingProgramProgressUiState {
    val configuredProgram = program ?: return DosingProgramProgressUiState()
    val mode = configuredProgram.schedule.toUiMode()
    val selectedToday = configuredProgram.weekdays.getOrNull(today.dayOfWeek.value - 1) == true
    val currentOccurrences = if (progress.executionCurrent) {
        progress.occurrences.map(DeviceDosingOccurrenceProgress::toUiState)
    } else {
        emptyList()
    }
    val occurrences = when {
        currentOccurrences.isNotEmpty() -> currentOccurrences
        !configuredProgram.enabled && selectedToday -> configuredProgram.placeholderOccurrences()
        else -> emptyList()
    }
    val scheduledToday = configuredProgram.enabled &&
        progress.executionCurrent &&
        progress.scheduledAmountMicroliters > 0L
    return DosingProgramProgressUiState(
        mode = mode,
        dailyDoseMl = configuredProgram.dailyDoseMicroliters().toMilliliters(),
        scheduledDeliveredTodayMl = progress.completedAmountMicroliters.toMilliliters(),
        manualDeliveredTodayMl = usageToday.manualDeliveredMicroliters
            .takeIf { usageToday.valid }
            ?.toMilliliters()
            ?: 0.0,
        occurrences = occurrences,
        customPeriods = configuredProgram.toCustomPeriodUiStates(occurrences),
        scheduledToday = scheduledToday,
        visualState = toProgressVisualState(configuredProgram)
    )
}

private fun DeviceDosingChannelSnapshot.toReservoirUiState(
    today: LocalDate
): DosingReservoirUiState? {
    if (!reservoir.trackingEnabled) return null
    val configuredProgram = program
    val dailyDoseMicroliters = configuredProgram?.dailyDoseMicroliters() ?: 0L
    val remainingToday = if (progress.executionCurrent) {
        (progress.scheduledAmountMicroliters - progress.completedAmountMicroliters)
            .coerceAtLeast(0L)
    } else {
        0L
    }
    val estimatedDays = if (
        configuredProgram?.enabled == true &&
        progress.executionCurrent &&
        reservoir.accountingCertain &&
        deliveryAccountingCertain
    ) {
        DosingReservoirProjection.estimateRemainingDays(
            remainingMicroliters = reservoir.remainingMicroliters,
            dailyDoseMicroliters = dailyDoseMicroliters,
            remainingScheduledTodayMicroliters = remainingToday,
            selectedWeekdays = configuredProgram.weekdays,
            today = today
        )
    } else {
        null
    }
    val fillFraction = if (reservoir.capacityMicroliters <= 0L) {
        0f
    } else {
        (reservoir.remainingMicroliters.toDouble() / reservoir.capacityMicroliters)
            .coerceIn(0.0, 1.0)
            .toFloat()
    }
    return DosingReservoirUiState(
        remainingMl = reservoir.remainingMicroliters.toMilliliters(),
        fillFraction = fillFraction,
        estimatedRemainingDays = estimatedDays,
        tone = when {
            !reservoir.accountingCertain || !deliveryAccountingCertain ->
                DosingReservoirTone.UNCERTAIN
            estimatedDays != null && estimatedDays < CRITICAL_REMAINING_DAYS ->
                DosingReservoirTone.CRITICAL
            estimatedDays != null && estimatedDays <= WARNING_REMAINING_DAYS ->
                DosingReservoirTone.WARNING
            else -> DosingReservoirTone.NORMAL
        }
    )
}

private fun DeviceDosingChannelSnapshot.toCardVisualState(): DosingChannelVisualState = when {
    !calibrated -> DosingChannelVisualState.NOT_CONFIGURED
    program == null -> DosingChannelVisualState.PROGRAM_NOT_CONFIGURED
    hasAttentionState() -> DosingChannelVisualState.ERROR
    !program.enabled -> DosingChannelVisualState.AUTOMATIC_DOSING_OFF
    activeRun.active -> DosingChannelVisualState.DOSING
    else -> DosingChannelVisualState.CONFIGURED
}

private fun DeviceDosingChannelSnapshot.toProgressVisualState(
    configuredProgram: DeviceDosingProgram
): DosingDoseProgressVisualState = when {
    hasAttentionState() || !progress.accountingCertain -> DosingDoseProgressVisualState.ERROR
    !configuredProgram.enabled -> DosingDoseProgressVisualState.DISABLED
    !progress.executionCurrent || progress.scheduledAmountMicroliters <= 0L ->
        DosingDoseProgressVisualState.EMPTY
    activeRun.active && activeRun.source == DeviceDosingRunSource.SCHEDULED ->
        DosingDoseProgressVisualState.ACTIVE
    progress.completedAmountMicroliters >= progress.scheduledAmountMicroliters ->
        DosingDoseProgressVisualState.COMPLETE
    else -> DosingDoseProgressVisualState.READY
}

private fun DeviceDosingProgramSchedule.toUiMode(): DosingProgramModeUiState = when (this) {
    is DeviceDosingProgramSchedule.Single -> DosingProgramModeUiState.SINGLE
    is DeviceDosingProgramSchedule.Hourly24 -> DosingProgramModeUiState.HOURLY_24
    is DeviceDosingProgramSchedule.CustomPeriods -> DosingProgramModeUiState.CUSTOM_PERIODS
    is DeviceDosingProgramSchedule.Timer -> DosingProgramModeUiState.TIMER
}

private fun DeviceDosingProgram.dailyDoseMicroliters(): Long = when (val value = schedule) {
    is DeviceDosingProgramSchedule.Single -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Hourly24 -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.CustomPeriods -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Timer -> value.doses.sumOf { dose -> dose.amountMicroliters }
}

private fun DeviceDosingOccurrenceProgress.toUiState() = DosingProgressOccurrenceUiState(
    timeFraction = timeMillis.toFloat() / MILLIS_PER_DAY.toFloat(),
    amountMl = amountMicroliters.toMilliliters(),
    visualState = when (state) {
        DeviceDosingOccurrenceState.PENDING -> DosingOccurrenceVisualState.PENDING
        DeviceDosingOccurrenceState.RUNNING -> DosingOccurrenceVisualState.ACTIVE
        DeviceDosingOccurrenceState.COMPLETED -> DosingOccurrenceVisualState.COMPLETED
        DeviceDosingOccurrenceState.SKIPPED -> DosingOccurrenceVisualState.SKIPPED
        DeviceDosingOccurrenceState.UNCERTAIN -> DosingOccurrenceVisualState.UNCERTAIN
    }
)

private fun DeviceDosingProgram.placeholderOccurrences():
    List<DosingProgressOccurrenceUiState> = when (val value = schedule) {
    is DeviceDosingProgramSchedule.Single -> listOf(
        placeholderOccurrence(value.startTimeMillis, value.dailyDoseMicroliters)
    )
    is DeviceDosingProgramSchedule.Hourly24 -> {
        val amounts = splitAmount(value.dailyDoseMicroliters, HOURLY_OCCURRENCE_COUNT)
        List(HOURLY_OCCURRENCE_COUNT) { index ->
            placeholderOccurrence(
                timeMillis = (value.startTimeMillis + index * MILLIS_PER_HOUR) % MILLIS_PER_DAY,
                amountMicroliters = amounts[index]
            )
        }
    }
    is DeviceDosingProgramSchedule.CustomPeriods -> {
        val amounts = splitAmount(
            value.dailyDoseMicroliters,
            value.periods.sumOf(DeviceDosingCustomPeriodDraft::doseCount)
        )
        var amountIndex = 0
        value.periods.flatMap { period ->
            List(period.doseCount) { occurrenceIndex ->
                placeholderOccurrence(
                    timeMillis = period.placeholderTime(occurrenceIndex),
                    amountMicroliters = amounts[amountIndex++]
                )
            }
        }
    }
    is DeviceDosingProgramSchedule.Timer -> value.doses.map { dose ->
        placeholderOccurrence(dose.startTimeMs, dose.amountMicroliters)
    }
}

private fun DeviceDosingProgram.toCustomPeriodUiStates(
    occurrences: List<DosingProgressOccurrenceUiState>
): List<DosingCustomPeriodProgressUiState> {
    val periods = (schedule as? DeviceDosingProgramSchedule.CustomPeriods)?.periods
        ?: return emptyList()
    var cursor = 0
    return periods.map { period ->
        DosingCustomPeriodProgressUiState(
            occurrences = occurrences.drop(cursor).take(period.doseCount)
        ).also { cursor += period.doseCount }
    }
}

private fun DeviceDosingCustomPeriodDraft.placeholderTime(index: Int): Long {
    if (doseCount <= 1) return startTimeMs
    val interval = (endTimeMs - startTimeMs) / (doseCount - 1)
    return startTimeMs + interval * index
}

private fun placeholderOccurrence(
    timeMillis: Long,
    amountMicroliters: Long
) = DosingProgressOccurrenceUiState(
    timeFraction = timeMillis.toFloat() / MILLIS_PER_DAY.toFloat(),
    amountMl = amountMicroliters.toMilliliters(),
    visualState = DosingOccurrenceVisualState.PENDING
)

private fun splitAmount(totalMicroliters: Long, count: Int): List<Long> {
    if (count <= 0) return emptyList()
    val baseAmount = totalMicroliters / count
    val remainder = (totalMicroliters % count).toInt()
    return List(count) { index -> baseAmount + if (index < remainder) 1L else 0L }
}

private fun DeviceDosingChannelSnapshot.hasAttentionState(): Boolean =
    !deliveryAccountingCertain ||
        runtimeReason in ATTENTION_RUNTIME_REASONS ||
        reservoir.trackingEnabled && !reservoir.accountingCertain

private fun Long.toMilliliters(): Double = toDouble() / MICROLITERS_PER_MILLILITER

private val ATTENTION_RUNTIME_REASONS = setOf(
    DeviceDosingRuntimeReason.INVALID_TIME,
    DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE,
    DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN,
    DeviceDosingRuntimeReason.UNSAFE_AFTER_CALIBRATION,
    DeviceDosingRuntimeReason.INVALID_PROGRAM,
    DeviceDosingRuntimeReason.UNKNOWN
)

private const val MICROLITERS_PER_MILLILITER = 1_000.0
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val HOURLY_OCCURRENCE_COUNT = 24
private const val CRITICAL_REMAINING_DAYS = 10
private const val WARNING_REMAINING_DAYS = 20
