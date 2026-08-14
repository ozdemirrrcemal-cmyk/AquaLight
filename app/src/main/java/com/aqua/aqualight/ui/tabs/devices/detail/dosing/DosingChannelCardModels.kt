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
 * is created. The UI keeps only the values required to render the channel card. Runtime and
 * firmware transport models must be mapped before reaching this presentation model.
 */
@Immutable
data class DosingChannelCardUiState(
    val slotId: String,
    val channelNumber: Int,
    val displayName: String,
    val dailyDoseMl: Double = 0.0,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.NOT_CONFIGURED,
    val scheduleDays: DosingScheduleDaysUiState = DosingScheduleDaysUiState()
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
