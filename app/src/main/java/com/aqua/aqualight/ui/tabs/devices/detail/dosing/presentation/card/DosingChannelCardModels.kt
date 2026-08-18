package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.model.DosingWeekday

@Immutable
data class DosingChannelCardUiState(
    val slotId: String,
    val channelNumber: Int,
    val displayName: String,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.NOT_CONFIGURED,
    val scheduleDays: DosingScheduleDaysUiState = DosingScheduleDaysUiState(),
    val programProgress: DosingProgramProgressUiState = DosingProgramProgressUiState(),
    val reservoir: DosingReservoirUiState? = null
)

enum class DosingChannelVisualState(
    @StringRes val labelRes: Int,
    val showsStatusPill: Boolean
) {
    NOT_CONFIGURED(R.string.device_dosing_channel_not_configured, true),
    PROGRAM_NOT_CONFIGURED(R.string.device_dosing_channel_program_not_configured, false),
    AUTOMATIC_DOSING_OFF(R.string.device_dosing_channel_automatic_off, false),
    CONFIGURED(R.string.device_dosing_channel_status_configured, false),
    DOSING(R.string.device_dosing_channel_status_dosing, false),
    ERROR(R.string.device_dosing_channel_status_attention, true)
}

@Immutable
data class DosingScheduleDaysUiState(
    val selectedDays: List<DosingWeekday> = emptyList()
) {
    val isEveryDay: Boolean
        get() = selectedDays.size == ALL_DOSING_WEEKDAYS.size &&
            selectedDays.containsAll(ALL_DOSING_WEEKDAYS)

    val isWeekdays: Boolean
        get() = selectedDays == ALL_DOSING_WEEKDAYS.take(WEEKDAY_COUNT)

    val isWeekend: Boolean
        get() = selectedDays == ALL_DOSING_WEEKDAYS.takeLast(WEEKEND_DAY_COUNT)
}

enum class DosingProgramModeUiState(
    @StringRes val labelRes: Int
) {
    SINGLE(R.string.device_dosing_detail_schedule_single),
    HOURLY_24(R.string.device_dosing_detail_schedule_hourly),
    CUSTOM_PERIODS(R.string.device_dosing_detail_schedule_custom),
    TIMER(R.string.device_dosing_detail_schedule_timer)
}

enum class DosingOccurrenceVisualState {
    PENDING,
    ACTIVE,
    COMPLETED,
    SKIPPED,
    UNCERTAIN
}

@Immutable
data class DosingProgressOccurrenceUiState(
    val amountMl: Double,
    val startFraction: Float = 0f,
    val endFraction: Float = 1f,
    val visualState: DosingOccurrenceVisualState
)

@Immutable
data class DosingProgressMarkerUiState(
    val positionFraction: Float,
    val cumulativeAmountMl: Double
)

@Immutable
data class DosingCustomPeriodProgressUiState(
    val occurrences: List<DosingProgressOccurrenceUiState>
)

enum class DosingDoseProgressVisualState {
    EMPTY,
    READY,
    ACTIVE,
    COMPLETE,
    DISABLED,
    ERROR
}

@Immutable
data class DosingProgramProgressUiState(
    val mode: DosingProgramModeUiState? = null,
    val dailyDoseMl: Double = 0.0,
    val scheduledDeliveredTodayMl: Double = 0.0,
    val manualDeliveredTodayMl: Double = 0.0,
    val occurrences: List<DosingProgressOccurrenceUiState> = emptyList(),
    val customPeriods: List<DosingCustomPeriodProgressUiState> = emptyList(),
    val markers: List<DosingProgressMarkerUiState> = emptyList(),
    val scheduledToday: Boolean = false,
    val visualState: DosingDoseProgressVisualState = DosingDoseProgressVisualState.EMPTY
) {
    val totalOccurrences: Int
        get() = occurrences.size

    val completedOccurrences: Int
        get() = occurrences.count { occurrence ->
            occurrence.visualState == DosingOccurrenceVisualState.COMPLETED
        }
}

enum class DosingReservoirTone {
    NORMAL,
    WARNING,
    CRITICAL,
    UNCERTAIN
}

@Immutable
data class DosingReservoirUiState(
    val remainingMl: Double,
    val fillFraction: Float,
    val estimatedRemainingDays: Int?,
    val tone: DosingReservoirTone
)

internal val ALL_DOSING_WEEKDAYS = DosingWeekday.entries.toList()

private const val WEEKDAY_COUNT = 5
private const val WEEKEND_DAY_COUNT = 2
