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
    val dailyDoseMl: Double = 0.0,
    val calibrationState: DosingCalibrationUiState = DosingCalibrationUiState.REQUIRED,
    val setupState: DosingSetupUiState = DosingSetupUiState.NOT_CONFIGURED,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.SETUP_REQUIRED,
    val timeline: DosingTimelineUiState = DosingTimelineUiState()
) {
    init {
        require(slotId.isNotBlank()) { "Dosing channel card requires a stable catalog slot id." }
        require(channelNumber > 0) { "Dosing channel number must be positive." }
        require(wireKey.isNotBlank()) { "Dosing channel card requires a catalog wire key." }
        require(displayName.isNotBlank()) { "Dosing channel display name must not be blank." }
        require(dailyDoseMl >= 0.0) { "Dosing channel daily dose must not be negative." }
    }
}

enum class DosingCalibrationUiState(
    @StringRes val labelRes: Int
) {
    REQUIRED(R.string.device_dosing_channel_calibration_required),
    CALIBRATED(R.string.device_dosing_channel_calibrated)
}

enum class DosingSetupUiState(
    @StringRes val labelRes: Int
) {
    NOT_CONFIGURED(R.string.device_dosing_channel_not_configured),
    CONFIGURED(R.string.device_dosing_channel_configured)
}

enum class DosingChannelVisualState(
    @StringRes val labelRes: Int
) {
    SETUP_REQUIRED(R.string.device_dosing_channel_status_setup_required),
    READY(R.string.device_dosing_channel_status_ready),
    SCHEDULED(R.string.device_dosing_channel_status_scheduled),
    DOSING(R.string.device_dosing_channel_status_dosing),
    ERROR(R.string.device_dosing_channel_status_attention)
}

@Immutable
data class DosingTimelineUiState(
    val events: List<DosingTimelineEventUiState> = emptyList(),
    val visualState: DosingTimelineVisualState = DosingTimelineVisualState.EMPTY
)

enum class DosingTimelineVisualState {
    EMPTY,
    READY,
    ACTIVE,
    ERROR
}

@Immutable
data class DosingTimelineEventUiState(
    val fractionOfDay: Float,
    val amountMl: Double,
    val active: Boolean = false,
    val error: Boolean = false
) {
    init {
        require(fractionOfDay in 0f..1f) { "Timeline event must be inside one 24-hour day." }
        require(amountMl >= 0.0) { "Timeline dose amount must not be negative." }
    }
}

/** Initial presentation comes only from the validated commercial channel-slot catalog. */
internal fun DeviceDosingChannelSlot.toInitialDosingChannelCardUiState(): DosingChannelCardUiState =
    DosingChannelCardUiState(
        slotId = id.value,
        channelNumber = index.position,
        wireKey = wireKey.value,
        displayName = defaultDisplayName
    )
