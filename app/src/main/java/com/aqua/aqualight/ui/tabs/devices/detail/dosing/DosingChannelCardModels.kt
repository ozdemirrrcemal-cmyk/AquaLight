package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot

/**
 * Dosing-only presentation state.
 *
 * Catalog identity and structural validity are owned by the application catalog before this state
 * is created. The UI keeps only the values required to render the channel card.
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

/** Presentation-only schedule selection. Canonical schedule validity belongs upstream. */
@Immutable
data class DosingScheduleDaysUiState(
    val selectedDays: List<DosingWeekday> = emptyList()
) {
    val isEveryDay: Boolean
        get() = selectedDays.size == ALL_DOSING_WEEKDAYS.size &&
            selectedDays.containsAll(ALL_DOSING_WEEKDAYS)
}

/**
 * Volume-based daily dosing progress used only for presentation.
 *
 * [dailyDoseMl] is the total daily dose configured by the user. [deliveredTodayMl] is the amount
 * actually delivered today. Optional [doseMilestonesMl] are cumulative volume boundaries supplied
 * by the channel-configuration mapper. Validation and canonicalization of dosing values belongs to
 * the application/domain boundary before values are mapped into this UI state. The progress
 * contract contains no wall-clock axis.
 */
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

/** Applies the central runtime target without leaking firmware addressing into presentation state. */
internal fun DosingChannelCardUiState.withNavigationTarget(
    target: DeviceDosingChannelNavigationTarget?
): DosingChannelCardUiState = target?.let { navigationTarget ->
    copy(
        displayName = navigationTarget.channelTitle.ifBlank { displayName },
        visualState = when (navigationTarget.destination) {
            DeviceDosingChannelDestination.DETAIL -> DosingChannelVisualState.READY
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
