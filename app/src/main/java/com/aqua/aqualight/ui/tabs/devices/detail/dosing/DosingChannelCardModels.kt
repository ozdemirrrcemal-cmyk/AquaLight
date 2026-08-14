package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget

/**
 * Dosing-only presentation state.
 *
 * Catalog identity and structural validity are owned by the single central device catalog before
 * this state is created. Runtime/firmware transport models must be mapped upstream; Compose never
 * reconstructs scheduling, occurrence outcomes, reservoir policy or runtime truth.
 */
@Immutable
data class DosingChannelCardUiState(
    val slotId: String,
    val channelNumber: Int,
    val displayName: String,
    val dailyDoseMl: Double? = null,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.NOT_CONFIGURED,
    val scheduleDays: DosingScheduleDaysUiState = DosingScheduleDaysUiState(),
    val programMode: DosingProgramModeUi? = null,
    val scheduledProgress: DosingScheduledProgressUiState? = null,
    val reservoir: DosingReservoirSummaryUiState? = null,
    val manualUsage: DosingManualUsageUiState? = null
)

enum class DosingChannelVisualState(
    @StringRes val labelRes: Int?
) {
    NOT_CONFIGURED(R.string.device_dosing_channel_not_configured),
    IDLE(null),
    DOSING(R.string.device_dosing_card_status_dosing),
    ERROR(R.string.device_dosing_card_status_error)
}

enum class DosingWeekday(
    @StringRes val shortLabelRes: Int
) {
    MONDAY(R.string.device_dosing_weekday_mon),
    TUESDAY(R.string.device_dosing_weekday_tue),
    WEDNESDAY(R.string.device_dosing_weekday_wed),
    THURSDAY(R.string.device_dosing_weekday_thu),
    FRIDAY(R.string.device_dosing_weekday_fri),
    SATURDAY(R.string.device_dosing_weekday_sat),
    SUNDAY(R.string.device_dosing_weekday_sun)
}

/** Presentation-only schedule selection. Canonical schedule validity belongs upstream. */
@Immutable
data class DosingScheduleDaysUiState(
    val selectedDays: List<DosingWeekday> = emptyList()
) {
    val isEveryDay: Boolean
        get() = selectedDays.size == ALL_DOSING_WEEKDAYS.size &&
            selectedDays.containsAll(ALL_DOSING_WEEKDAYS)
}

enum class DosingProgramModeUi {
    SINGLE,
    HOURLY_24,
    CUSTOM_PERIODS,
    TIMER
}

enum class DosingScheduleProgressStateUi {
    ACTIVE,
    NO_SCHEDULE
}

enum class DosingOccurrenceVisualStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    UNCERTAIN
}

@Immutable
data class DosingOccurrenceProgressUiState(
    val index: Int,
    val eventId: Int?,
    val programDayOffset: Int,
    val timeMs: Long,
    val amountMl: Double,
    val status: DosingOccurrenceVisualStatus
)

/**
 * Firmware-authoritative scheduled progress projection prepared for presentation.
 *
 * Amounts/counts/percent are consumed directly. UI code must not re-sum occurrences or derive a
 * replacement completion value. customPeriodGroups is canonical program metadata, never inferred
 * from occurrence times.
 */
@Immutable
data class DosingScheduledProgressUiState(
    val mode: DosingProgramModeUi,
    val scheduleState: DosingScheduleProgressStateUi,
    val executionCurrent: Boolean,
    val totalAmountMl: Double,
    val completedAmountMl: Double,
    val remainingAmountMl: Double,
    val completionPercent: Double,
    val totalCount: Int,
    val completedCount: Int,
    val resolvedCount: Int,
    val pendingCount: Int,
    val runningCount: Int,
    val skippedCount: Int,
    val uncertainCount: Int,
    val occurrences: List<DosingOccurrenceProgressUiState>,
    val customPeriodGroups: List<Int> = emptyList()
)

enum class DosingReservoirLevelUiState {
    NORMAL,
    LOW,
    UNKNOWN
}

/** estimatedDaysRemaining is calculated upstream from authoritative firmware inputs, never in UI. */
@Immutable
data class DosingReservoirSummaryUiState(
    val remainingMl: Double,
    val estimatedDaysRemaining: Int?,
    val level: DosingReservoirLevelUiState
)

/** Manual delivery is intentionally a sibling of scheduled progress, never part of its numerator. */
@Immutable
data class DosingManualUsageUiState(
    val deliveredMlToday: Double
)

/** Initial presentation comes only from the validated commercial channel-slot catalog. */
internal fun DeviceDosingChannelSlot.toInitialDosingChannelCardUiState(): DosingChannelCardUiState =
    DosingChannelCardUiState(
        slotId = id.value,
        channelNumber = index.position,
        displayName = defaultDisplayName
    )

/**
 * Navigation decides only whether the central channel destination is available. It is not runtime
 * truth and therefore never fabricates dose/schedule/progress values.
 */
internal fun DosingChannelCardUiState.withNavigationTarget(
    target: DeviceDosingChannelNavigationTarget?
): DosingChannelCardUiState = target?.let { navigationTarget ->
    copy(
        displayName = navigationTarget.channelTitle.ifBlank { displayName },
        visualState = when (navigationTarget.destination) {
            DeviceDosingChannelDestination.DETAIL -> DosingChannelVisualState.IDLE
            DeviceDosingChannelDestination.CALIBRATION ->
                DosingChannelVisualState.NOT_CONFIGURED
        }
    )
} ?: this

private val ALL_DOSING_WEEKDAYS = listOf(
    DosingWeekday.MONDAY,
    DosingWeekday.TUESDAY,
    DosingWeekday.WEDNESDAY,
    DosingWeekday.THURSDAY,
    DosingWeekday.FRIDAY,
    DosingWeekday.SATURDAY,
    DosingWeekday.SUNDAY
)
