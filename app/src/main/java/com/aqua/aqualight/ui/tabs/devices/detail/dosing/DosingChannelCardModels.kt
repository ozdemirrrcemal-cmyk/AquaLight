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
    val calibrationState: DosingCalibrationUiState = DosingCalibrationUiState.REQUIRED,
    val setupState: DosingSetupUiState = DosingSetupUiState.NOT_CONFIGURED,
    val visualState: DosingChannelVisualState = DosingChannelVisualState.SETUP_REQUIRED,
    val doseProgress: DosingDoseProgressUiState = DosingDoseProgressUiState()
) {
    init {
        require(slotId.isNotBlank()) { "Dosing channel card requires a stable catalog slot id." }
        require(channelNumber > 0) { "Dosing channel number must be positive." }
        require(wireKey.isNotBlank()) { "Dosing channel card requires a catalog wire key." }
        require(displayName.isNotBlank()) { "Dosing channel display name must not be blank." }
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

/**
 * Volume-based daily dosing progress.
 *
 * [dailyTargetMl] is the configured total dose for the day. [deliveredMl] is the amount actually
 * delivered so far. Optional checkpoints are cumulative dose boundaries supplied by the future
 * channel configuration mapper; they are deliberately expressed in millilitres, never time.
 */
@Immutable
data class DosingDoseProgressUiState(
    val dailyTargetMl: Double = 0.0,
    val deliveredMl: Double = 0.0,
    val doseCheckpointsMl: List<Double> = emptyList(),
    val visualState: DosingDoseProgressVisualState = DosingDoseProgressVisualState.EMPTY
) {
    init {
        require(dailyTargetMl >= 0.0) { "Daily dosing target must not be negative." }
        require(deliveredMl >= 0.0) { "Delivered dosing amount must not be negative." }
        require(doseCheckpointsMl.all { it >= 0.0 && it <= dailyTargetMl }) {
            "Dose checkpoints must stay inside the configured daily target."
        }
        require(doseCheckpointsMl.zipWithNext().all { (previous, next) -> previous <= next }) {
            "Dose checkpoints must be ordered by cumulative volume."
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
