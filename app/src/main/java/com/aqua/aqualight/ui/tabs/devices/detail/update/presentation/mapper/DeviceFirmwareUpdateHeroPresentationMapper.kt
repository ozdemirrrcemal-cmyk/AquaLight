package com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.mapper

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState

internal data class DeviceFirmwareUpdateText(
    @StringRes val stringRes: Int,
    val formatArg: Any? = null
)

internal data class DeviceFirmwareUpdateIconPresentation(
    @DrawableRes val drawableRes: Int,
    @ColorRes val colorRes: Int
)

internal data class DeviceFirmwareUpdateHeroPresentation(
    @StringRes val titleRes: Int,
    val summary: DeviceFirmwareUpdateText,
    @StringRes val statusTextRes: Int,
    @ColorRes val statusColorRes: Int,
    @ColorRes val statusBackgroundColorRes: Int,
    val icon: DeviceFirmwareUpdateIconPresentation
)

/** Pure state-to-resource mapping for the update hero. */
internal object DeviceFirmwareUpdateHeroPresentationMapper {

    fun map(state: DeviceFirmwareUpdateUiState): DeviceFirmwareUpdateHeroPresentation =
        DeviceFirmwareUpdateHeroPresentation(
            titleRes = titleRes(state),
            summary = summary(state),
            statusTextRes = statusTextRes(state),
            statusColorRes = statusColorRes(state),
            statusBackgroundColorRes = statusBackgroundColorRes(state),
            icon = icon(state.mode)
        )

    @StringRes
    fun statusTextRes(state: DeviceFirmwareUpdateUiState): Int = when {
        state.isAvailabilityCheckFailure ->
            R.string.device_settings_update_status_check_failed
        state.mode == DeviceFirmwareUpdateMode.AVAILABLE -> {
            if (state.releaseContent.mandatory) {
                R.string.device_settings_update_status_required
            } else {
                R.string.device_settings_update_status_available
            }
        }
        else -> STATUS_TEXT_RES.getValue(state.mode)
    }

    @StringRes
    private fun titleRes(state: DeviceFirmwareUpdateUiState): Int = when {
        state.isAvailabilityCheckFailure ->
            R.string.device_settings_update_hero_title_check_failed
        state.mode == DeviceFirmwareUpdateMode.AVAILABLE -> {
            if (state.releaseContent.mandatory) {
                R.string.device_settings_update_hero_title_required
            } else {
                R.string.device_settings_update_hero_title_available
            }
        }
        else -> HERO_TITLE_RES.getValue(state.mode)
    }

    private fun summary(state: DeviceFirmwareUpdateUiState): DeviceFirmwareUpdateText =
        when (state.mode) {
            DeviceFirmwareUpdateMode.AVAILABLE -> availableSummary(state)
            DeviceFirmwareUpdateMode.SUCCEEDED -> succeededSummary(state.targetVersion)
            DeviceFirmwareUpdateMode.UP_TO_DATE -> upToDateSummary(state.currentVersion)
            DeviceFirmwareUpdateMode.FAILED -> DeviceFirmwareUpdateText(
                state.failure?.let { failure ->
                    DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
                } ?: R.string.device_settings_update_phase_failed_terminal
            )
            else -> DeviceFirmwareUpdateText(STATIC_SUMMARY_RES.getValue(state.mode))
        }

    private fun availableSummary(state: DeviceFirmwareUpdateUiState): DeviceFirmwareUpdateText =
        when {
            state.releaseContent.mandatory && state.targetVersion.isNotBlank() ->
                DeviceFirmwareUpdateText(
                    R.string.device_settings_update_hero_summary_required_version,
                    state.targetVersion
                )
            state.targetVersion.isNotBlank() -> DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_available_version,
                state.targetVersion
            )
            else -> DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_available
            )
        }

    private fun succeededSummary(targetVersion: String): DeviceFirmwareUpdateText =
        if (targetVersion.isBlank()) {
            DeviceFirmwareUpdateText(R.string.device_settings_update_hero_summary_succeeded)
        } else {
            DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_succeeded_version,
                targetVersion
            )
        }

    private fun upToDateSummary(currentVersion: String): DeviceFirmwareUpdateText =
        if (currentVersion.isBlank()) {
            DeviceFirmwareUpdateText(R.string.device_settings_update_hero_summary_up_to_date)
        } else {
            DeviceFirmwareUpdateText(
                R.string.device_settings_update_hero_summary_up_to_date_version,
                currentVersion
            )
        }

    @ColorRes
    private fun statusColorRes(state: DeviceFirmwareUpdateUiState): Int = when (state.mode) {
        DeviceFirmwareUpdateMode.FAILED -> R.color.aqua_status_danger
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.color.aqua_content_warning
        DeviceFirmwareUpdateMode.AVAILABLE -> if (state.releaseContent.mandatory) {
            R.color.aqua_content_warning
        } else {
            R.color.aqua_accent_positive
        }
        DeviceFirmwareUpdateMode.SUCCEEDED,
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.color.aqua_status_success
        else -> R.color.aqua_accent_primary
    }

    @ColorRes
    private fun statusBackgroundColorRes(state: DeviceFirmwareUpdateUiState): Int =
        when (state.mode) {
            DeviceFirmwareUpdateMode.FAILED -> R.color.aqua_aquarium_fragment_button_outline
            DeviceFirmwareUpdateMode.UNSUPPORTED ->
                R.color.aqua_bg_maintenance_profile_percent_warning_fill
            DeviceFirmwareUpdateMode.AVAILABLE -> if (state.releaseContent.mandatory) {
                R.color.aqua_bg_maintenance_profile_percent_warning_fill
            } else {
                R.color.aqua_surface_positive
            }
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.UP_TO_DATE -> R.color.aqua_surface_positive
            else -> R.color.aqua_surface_action
        }

    private fun icon(mode: DeviceFirmwareUpdateMode): DeviceFirmwareUpdateIconPresentation =
        when (mode) {
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.UP_TO_DATE -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_check_24,
                R.color.aqua_status_success
            )
            DeviceFirmwareUpdateMode.FAILED -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_error,
                R.color.aqua_status_danger
            )
            DeviceFirmwareUpdateMode.UNSUPPORTED -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_warning,
                R.color.aqua_content_warning
            )
            else -> DeviceFirmwareUpdateIconPresentation(
                R.drawable.ic_firmware_update,
                R.color.aqua_accent_primary
            )
        }

    private val HERO_TITLE_RES = mapOf(
        DeviceFirmwareUpdateMode.LOADING to R.string.device_settings_update_hero_title_checking,
        DeviceFirmwareUpdateMode.CHECKING to R.string.device_settings_update_hero_title_checking,
        DeviceFirmwareUpdateMode.STARTING to R.string.device_settings_update_hero_title_starting,
        DeviceFirmwareUpdateMode.IN_PROGRESS to R.string.device_settings_update_hero_title_installing,
        DeviceFirmwareUpdateMode.RECOVERING to R.string.device_settings_update_hero_title_recovering,
        DeviceFirmwareUpdateMode.RESTARTING to R.string.device_settings_update_hero_title_restarting,
        DeviceFirmwareUpdateMode.SUCCEEDED to R.string.device_settings_update_hero_title_succeeded,
        DeviceFirmwareUpdateMode.UP_TO_DATE to R.string.device_settings_update_hero_title_up_to_date,
        DeviceFirmwareUpdateMode.FAILED to R.string.device_settings_update_hero_title_failed,
        DeviceFirmwareUpdateMode.UNSUPPORTED to R.string.device_settings_update_hero_title_unsupported
    )

    private val STATIC_SUMMARY_RES = mapOf(
        DeviceFirmwareUpdateMode.LOADING to R.string.device_settings_update_hero_summary_checking,
        DeviceFirmwareUpdateMode.CHECKING to R.string.device_settings_update_hero_summary_checking,
        DeviceFirmwareUpdateMode.STARTING to R.string.device_settings_update_hero_summary_starting,
        DeviceFirmwareUpdateMode.IN_PROGRESS to R.string.device_settings_update_hero_summary_installing,
        DeviceFirmwareUpdateMode.RECOVERING to R.string.device_settings_update_hero_summary_recovering,
        DeviceFirmwareUpdateMode.RESTARTING to R.string.device_settings_update_hero_summary_restarting,
        DeviceFirmwareUpdateMode.UNSUPPORTED to R.string.device_settings_update_hero_summary_unsupported
    )

    private val STATUS_TEXT_RES = mapOf(
        DeviceFirmwareUpdateMode.LOADING to R.string.device_settings_update_status_checking,
        DeviceFirmwareUpdateMode.CHECKING to R.string.device_settings_update_status_checking,
        DeviceFirmwareUpdateMode.STARTING to R.string.device_settings_update_status_preparing,
        DeviceFirmwareUpdateMode.IN_PROGRESS to R.string.device_settings_update_status_installing,
        DeviceFirmwareUpdateMode.RECOVERING to R.string.device_settings_update_status_recovering,
        DeviceFirmwareUpdateMode.RESTARTING to R.string.device_settings_update_status_restarting,
        DeviceFirmwareUpdateMode.SUCCEEDED to R.string.device_settings_update_status_succeeded,
        DeviceFirmwareUpdateMode.UP_TO_DATE to R.string.device_settings_update_status_up_to_date,
        DeviceFirmwareUpdateMode.FAILED to R.string.device_settings_update_status_failed,
        DeviceFirmwareUpdateMode.UNSUPPORTED to R.string.device_settings_update_status_unsupported
    )
}

private val DeviceFirmwareUpdateUiState.isAvailabilityCheckFailure: Boolean
    get() = mode == DeviceFirmwareUpdateMode.FAILED &&
        failure?.stage == DeviceOtaFailureStage.AVAILABILITY_CHECK
