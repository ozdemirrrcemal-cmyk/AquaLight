package com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.mapper

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState

internal data class DeviceFirmwareUpdateActionPresentation(
    @StringRes val textRes: Int,
    val enabled: Boolean,
    val loading: Boolean = false
)

/** Pure state-to-resource mapping for progress and the primary action. */
internal object DeviceFirmwareUpdateProgressPresentationMapper {

    @StringRes
    fun phaseTextRes(state: DeviceFirmwareUpdateUiState): Int = when (state.mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_phase_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> R.string.device_settings_update_phase_ready
        DeviceFirmwareUpdateMode.STARTING -> R.string.device_settings_update_phase_starting
        DeviceFirmwareUpdateMode.IN_PROGRESS -> progressPhaseTextRes(state.phase)
        DeviceFirmwareUpdateMode.RECOVERING -> R.string.device_settings_update_phase_recovering
        DeviceFirmwareUpdateMode.RESTARTING -> R.string.device_settings_update_phase_restarting
        DeviceFirmwareUpdateMode.SUCCEEDED -> R.string.device_settings_update_phase_succeeded
        DeviceFirmwareUpdateMode.ROLLED_BACK -> R.string.device_settings_update_phase_rolled_back
        DeviceFirmwareUpdateMode.POST_RESTART_TIMEOUT ->
            R.string.device_settings_update_phase_post_restart_timeout
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.string.device_settings_update_phase_up_to_date
        DeviceFirmwareUpdateMode.FAILED -> when {
            state.failure?.stage == DeviceOtaFailureStage.AVAILABILITY_CHECK ->
                R.string.device_settings_update_phase_check_failed
            state.failure?.recoverable == true ->
                R.string.device_settings_update_phase_failed_recoverable
            else -> R.string.device_settings_update_phase_failed_terminal
        }
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.string.device_settings_update_phase_unsupported
    }

    fun stateIcon(mode: DeviceFirmwareUpdateMode): DeviceFirmwareUpdateIconPresentation? =
        when (mode) {
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.UP_TO_DATE -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_check_24,
                R.color.aqua_status_success
            )
            DeviceFirmwareUpdateMode.ROLLED_BACK -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_warning,
                R.color.aqua_content_warning
            )
            DeviceFirmwareUpdateMode.FAILED,
            DeviceFirmwareUpdateMode.POST_RESTART_TIMEOUT -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_error,
                R.color.aqua_status_danger
            )
            DeviceFirmwareUpdateMode.UNSUPPORTED -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_warning,
                R.color.aqua_content_warning
            )
            else -> null
        }

    fun action(state: DeviceFirmwareUpdateUiState): DeviceFirmwareUpdateActionPresentation =
        when (state.mode) {
            DeviceFirmwareUpdateMode.LOADING,
            DeviceFirmwareUpdateMode.CHECKING -> DeviceFirmwareUpdateActionPresentation(
                textRes = R.string.device_settings_update_action_loading,
                enabled = false,
                loading = true
            )
            DeviceFirmwareUpdateMode.AVAILABLE -> DeviceFirmwareUpdateActionPresentation(
                R.string.device_settings_update_now_action,
                enabled = true
            )
            DeviceFirmwareUpdateMode.STARTING,
            DeviceFirmwareUpdateMode.IN_PROGRESS,
            DeviceFirmwareUpdateMode.RECOVERING,
            DeviceFirmwareUpdateMode.RESTARTING -> DeviceFirmwareUpdateActionPresentation(
                R.string.device_settings_update_active_action,
                enabled = false
            )
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.ROLLED_BACK,
            DeviceFirmwareUpdateMode.UP_TO_DATE -> DeviceFirmwareUpdateActionPresentation(
                R.string.device_settings_update_done_action,
                enabled = true
            )
            DeviceFirmwareUpdateMode.POST_RESTART_TIMEOUT -> DeviceFirmwareUpdateActionPresentation(
                R.string.device_settings_update_action_retry_connection,
                enabled = true
            )
            DeviceFirmwareUpdateMode.FAILED -> DeviceFirmwareUpdateActionPresentation(
                textRes = if (state.failure?.recoverable == true) {
                    R.string.device_settings_retry_update_action
                } else {
                    R.string.device_settings_update_close_action
                },
                enabled = true
            )
            DeviceFirmwareUpdateMode.UNSUPPORTED -> DeviceFirmwareUpdateActionPresentation(
                R.string.device_settings_update_close_action,
                enabled = true
            )
        }

    @StringRes
    fun actionHintRes(mode: DeviceFirmwareUpdateMode): Int? = when {
        mode == DeviceFirmwareUpdateMode.AVAILABLE ->
            R.string.device_settings_update_action_hint_available
        mode.isActive -> R.string.device_settings_update_action_hint_active
        else -> null
    }

    fun shouldPulse(mode: DeviceFirmwareUpdateMode): Boolean = mode in PULSE_MODES

    @StringRes
    private fun progressPhaseTextRes(phase: DeviceOtaProgressPhase?): Int = when (phase) {
        DeviceOtaProgressPhase.STARTING -> R.string.device_settings_update_phase_starting
        DeviceOtaProgressPhase.SAFE_MODE -> R.string.device_settings_update_phase_safe_mode
        DeviceOtaProgressPhase.DOWNLOADING -> R.string.device_settings_update_phase_downloading
        DeviceOtaProgressPhase.WRITING -> R.string.device_settings_update_phase_writing
        DeviceOtaProgressPhase.VERIFYING -> R.string.device_settings_update_phase_verifying
        null -> R.string.device_settings_update_phase_starting
    }

    private val PULSE_MODES = setOf(
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING,
        DeviceFirmwareUpdateMode.STARTING,
        DeviceFirmwareUpdateMode.RECOVERING,
        DeviceFirmwareUpdateMode.RESTARTING
    )
}
