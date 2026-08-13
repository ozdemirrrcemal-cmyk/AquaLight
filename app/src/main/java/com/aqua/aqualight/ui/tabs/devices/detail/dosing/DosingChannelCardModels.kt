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
 * Catalog identity and structural validity are owned by the application catalog before this state
 * is created. Runtime values are projected from the same canonical channel status used for routing.
 */
@Immutable
data class DosingChannelCardUiState(
    val slotId: String,
    val channelNumber: Int,
    val displayName: String,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.NOT_CONFIGURED,
    val scheduleDays: DosingScheduleDaysUiState = DosingScheduleDaysUiState(),
    val doseProgress: DosingDoseProgressUiState = DosingDoseProgressUiState()
)

enum class DosingChannelVisualState(
    @StringRes val labelRes: Int
) {
    NOT_CONFIGURED(R.string.device_dosing_channel_not_configured),
    READY(R.string.device_dosing_channel_status_ready),
    SCHEDULED(R.string.device_dosing_channel_status_scheduled),
    DOSING(R.string.device_dosing_channel_status_dosing),
    ERROR(R.string.device_dosing_channel_status_attention)
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

@Immutable
data class DosingScheduleDaysUiState(
    val selectedDays: List<DosingWeekday> = emptyList()
) {
    val isEveryDay: Boolean
        get() = selectedDays.size == ALL_DOSING_WEEKDAYS.size &&
            selectedDays.containsAll(ALL_DOSING_WEEKDAYS)
}

@Immutable
data class DosingDoseProgressUiState(
    val dailyDoseMl: Double = 0.0,
    val deliveredTodayMl: Double = 0.0,
    val doseMilestonesMl: List<Double> = emptyList(),
    val visualState: DosingDoseProgressVisualState = DosingDoseProgressVisualState.EMPTY
)

enum class DosingDoseProgressVisualState {
    EMPTY,
    READY,
    ACTIVE,
    COMPLETE,
    ERROR
}

/** Initial presentation comes only from the validated commercial channel-slot catalog. */
internal fun DeviceDosingChannelSlot.toInitialDosingChannelCardUiState(): DosingChannelCardUiState =
    DosingChannelCardUiState(
        slotId = id.value,
        channelNumber = index.position,
        displayName = defaultDisplayName
    )

/** Applies the central runtime projection without leaking firmware addressing into presentation. */
internal fun DosingChannelCardUiState.withNavigationTarget(
    target: DeviceDosingChannelNavigationTarget?
): DosingChannelCardUiState = target?.let { runtime ->
    val selectedDays = runtime.programWeekdays
        .takeIf { it.size == ALL_DOSING_WEEKDAYS.size }
        ?.mapIndexedNotNull { index, selected -> ALL_DOSING_WEEKDAYS[index].takeIf { selected } }
        .orEmpty()
    val dailyDose = runtime.dailyDoseMl.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val delivered = runtime.scheduledDeliveredTodayMl
        .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0
    val channelVisualState = when {
        runtime.destination == DeviceDosingChannelDestination.CALIBRATION ->
            DosingChannelVisualState.NOT_CONFIGURED
        !runtime.programConfigured -> DosingChannelVisualState.NOT_CONFIGURED
        runtime.active -> DosingChannelVisualState.DOSING
        !runtime.deliveryAccountingCertain -> DosingChannelVisualState.ERROR
        runtime.programEnabled && !runtime.runtimeEnabled && runtime.runtimeReason !in NON_ERROR_RUNTIME_REASONS ->
            DosingChannelVisualState.ERROR
        runtime.programEnabled && runtime.runtimeEnabled -> DosingChannelVisualState.SCHEDULED
        else -> DosingChannelVisualState.READY
    }
    val progressVisualState = when {
        channelVisualState == DosingChannelVisualState.ERROR -> DosingDoseProgressVisualState.ERROR
        dailyDose <= 0.0 -> DosingDoseProgressVisualState.EMPTY
        runtime.active -> DosingDoseProgressVisualState.ACTIVE
        delivered + PROGRESS_EPSILON >= dailyDose -> DosingDoseProgressVisualState.COMPLETE
        else -> DosingDoseProgressVisualState.READY
    }
    copy(
        displayName = runtime.channelTitle.ifBlank { displayName },
        visualState = channelVisualState,
        scheduleDays = DosingScheduleDaysUiState(selectedDays),
        doseProgress = DosingDoseProgressUiState(
            dailyDoseMl = dailyDose,
            deliveredTodayMl = delivered,
            doseMilestonesMl = runtime.doseMilestonesMl
                .filter { it.isFinite() && it > 0.0 && it <= dailyDose + PROGRESS_EPSILON },
            visualState = progressVisualState
        )
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

private val NON_ERROR_RUNTIME_REASONS = setOf("none", "programDisabled", "busy")
private const val PROGRESS_EPSILON = 0.000_001
