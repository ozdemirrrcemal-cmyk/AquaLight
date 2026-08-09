package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot

@Immutable
data class DosingChannelCardUiState(
    val slotId: String,
    val channelNumber: Int,
    val wireKey: String,
    val displayName: String,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.NOT_CONFIGURED,
    val scheduleDays: DosingScheduleDaysUiState = DosingScheduleDaysUiState(),
    val doseProgress: DosingDoseProgressUiState = DosingDoseProgressUiState()
) {
    init {
        require(slotId.isNotBlank()) { "Dosing channel card requires a stable catalog slot id." }
        require(channelNumber > 0) { "Dosing channel number must be positive." }
        require(wireKey.isNotBlank()) { "Dosing channel card requires a catalog wire key." }
        require(displayName.isNotBlank()) { "Dosing channel display name must not be blank." }
    }
}

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
    val selectedDays: List<DosingWeekday> = ALL_DOSING_WEEKDAYS
) {
    init {
        require(selectedDays.distinct().size == selectedDays.size) {
            "Dosing schedule days must not contain duplicates."
        }
    }

    val isEveryDay: Boolean
        get() = selectedDays.size == ALL_DOSING_WEEKDAYS.size &&
            selectedDays.containsAll(ALL_DOSING_WEEKDAYS)
}

/**
 * Volume-based daily dosing progress.
 *
 * [dailyDoseMl] is the total daily dose configured by the user. [deliveredTodayMl] is the amount
 * actually delivered today. Optional [doseMilestonesMl] are cumulative volume boundaries supplied
 * by the future channel-configuration mapper. The progress contract contains no wall-clock axis.
 */
@Immutable
data class DosingDoseProgressUiState(
    val dailyDoseMl: Double = 0.0,
    val deliveredTodayMl: Double = 0.0,
    val doseMilestonesMl: List<Double> = emptyList(),
    val visualState: DosingDoseProgressVisualState = DosingDoseProgressVisualState.EMPTY
) {
    init {
        require(dailyDoseMl >= 0.0) { "Daily dosing amount must not be negative." }
        require(deliveredTodayMl >= 0.0) { "Delivered dosing amount must not be negative." }
        require(doseMilestonesMl.all { it >= 0.0 && it <= dailyDoseMl }) {
            "Dose milestones must stay inside the configured daily dose."
        }
        require(doseMilestonesMl.zipWithNext().all { (previous, next) -> previous <= next }) {
            "Dose milestones must be ordered by cumulative volume."
        }
    }
}

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
        wireKey = wireKey.value,
        displayName = defaultDisplayName
    )

private val ALL_DOSING_WEEKDAYS = listOf(
    DosingWeekday.MONDAY,
    DosingWeekday.TUESDAY,
    DosingWeekday.WEDNESDAY,
    DosingWeekday.THURSDAY,
    DosingWeekday.FRIDAY,
    DosingWeekday.SATURDAY,
    DosingWeekday.SUNDAY
)
