package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure

/** Customer-facing Light error copy resolved exclusively from stable application semantics. */
internal object DeviceLightCommercialErrorResolver {

    fun resolve(failure: DeviceLightAdaptationFailure): DeviceLightCommercialErrorMessage =
        when (failure) {
            DeviceLightAdaptationFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_adaptation_operation_error
            )
            DeviceLightAdaptationFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_adaptation_not_connected_error
            )
            DeviceLightAdaptationFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_adaptation_unsupported_error
            )
            DeviceLightAdaptationFailure.STALE_REVISION -> commercialError(
                R.string.device_light_error_stale_title,
                R.string.device_light_adaptation_stale_error
            )
            DeviceLightAdaptationFailure.CLOCK_NOT_READY -> commercialError(
                R.string.device_light_error_clock_title,
                R.string.device_light_adaptation_clock_error
            )
            DeviceLightAdaptationFailure.INVALID_REQUEST -> commercialError(
                R.string.device_light_error_invalid_request_title,
                R.string.device_light_adaptation_invalid_error
            )
            DeviceLightAdaptationFailure.REJECTED -> rejectedError(
                R.string.device_light_adaptation_operation_error
            )
            DeviceLightAdaptationFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_adaptation_operation_error
            )
        }

    fun resolve(failure: DeviceLightAutomaticFailure): DeviceLightCommercialErrorMessage =
        when (failure) {
            DeviceLightAutomaticFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_auto_operation_error
            )
            DeviceLightAutomaticFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_auto_editor_not_connected
            )
            DeviceLightAutomaticFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_auto_operation_error
            )
            DeviceLightAutomaticFailure.STALE_REVISION -> commercialError(
                R.string.device_light_error_stale_title,
                R.string.device_light_auto_editor_stale
            )
            DeviceLightAutomaticFailure.CAPACITY_REACHED -> commercialError(
                R.string.device_light_error_capacity_title,
                R.string.device_light_auto_editor_capacity
            )
            DeviceLightAutomaticFailure.OVERLAP -> commercialError(
                R.string.device_light_error_overlap_title,
                R.string.device_light_auto_editor_overlap
            )
            DeviceLightAutomaticFailure.NOT_FOUND -> commercialError(
                R.string.device_light_error_not_found_title,
                R.string.device_light_auto_editor_not_found
            )
            DeviceLightAutomaticFailure.REJECTED -> rejectedError(
                R.string.device_light_auto_operation_error
            )
            DeviceLightAutomaticFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_auto_operation_error
            )
        }

    fun resolve(failure: DeviceLightCustomFailure): DeviceLightCommercialErrorMessage =
        when (failure) {
            DeviceLightCustomFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_custom_operation_error
            )
            DeviceLightCustomFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_error_not_connected_message
            )
            DeviceLightCustomFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_custom_operation_error
            )
            DeviceLightCustomFailure.REJECTED -> rejectedError(
                R.string.device_light_custom_operation_error
            )
            DeviceLightCustomFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_custom_operation_error
            )
        }

    fun resolve(failure: DeviceLightLibraryFailure): DeviceLightCommercialErrorMessage =
        when (failure) {
            DeviceLightLibraryFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_library_operation_error
            )
            DeviceLightLibraryFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_error_not_connected_message
            )
            DeviceLightLibraryFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_library_operation_error
            )
            DeviceLightLibraryFailure.REJECTED -> rejectedError(
                R.string.device_light_library_operation_error
            )
            DeviceLightLibraryFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_library_operation_error
            )
            DeviceLightLibraryFailure.INVALID_NAME -> commercialError(
                R.string.device_light_error_invalid_request_title,
                R.string.device_light_library_name_invalid_error
            )
            DeviceLightLibraryFailure.DUPLICATE_NAME -> commercialError(
                R.string.device_light_error_duplicate_name_title,
                R.string.device_light_library_name_duplicate_error
            )
            DeviceLightLibraryFailure.NOT_FOUND -> commercialError(
                R.string.device_light_error_not_found_title,
                R.string.device_light_library_operation_error
            )
            DeviceLightLibraryFailure.INCOMPATIBLE -> commercialError(
                R.string.device_light_error_incompatible_title,
                R.string.device_light_library_operation_error
            )
        }

    fun resolveLibraryRead(
        failure: DeviceLightLibraryFailure
    ): DeviceLightCommercialErrorMessage = when (failure) {
        DeviceLightLibraryFailure.UNAVAILABLE,
        DeviceLightLibraryFailure.NOT_CONNECTED,
        DeviceLightLibraryFailure.UNSUPPORTED,
        DeviceLightLibraryFailure.REJECTED,
        DeviceLightLibraryFailure.INVALID_DATA,
        DeviceLightLibraryFailure.INVALID_NAME,
        DeviceLightLibraryFailure.DUPLICATE_NAME,
        DeviceLightLibraryFailure.NOT_FOUND,
        DeviceLightLibraryFailure.INCOMPATIBLE -> commercialError(
            R.string.device_light_library_error_title,
            R.string.device_light_library_error_message
        )
    }

    fun resolve(failure: DeviceLightManualFailure): DeviceLightCommercialErrorMessage =
        when (failure) {
            DeviceLightManualFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_manual_operation_error
            )
            DeviceLightManualFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_manual_not_connected_error
            )
            DeviceLightManualFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_manual_operation_error
            )
            DeviceLightManualFailure.REJECTED -> rejectedError(
                R.string.device_light_manual_operation_error
            )
            DeviceLightManualFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_manual_operation_error
            )
        }

    fun resolve(
        failure: DeviceLightSystemFailure,
        partialApplyPossible: Boolean = false
    ): DeviceLightCommercialErrorMessage {
        if (partialApplyPossible) {
            return commercialError(
                R.string.device_light_error_partial_apply_title,
                R.string.device_light_system_partial_save_error
            )
        }
        return when (failure) {
            DeviceLightSystemFailure.UNAVAILABLE -> commercialError(
                R.string.device_light_error_unavailable_title,
                R.string.device_light_system_operation_error
            )
            DeviceLightSystemFailure.NOT_CONNECTED -> commercialError(
                R.string.device_light_error_not_connected_title,
                R.string.device_light_system_not_connected_error
            )
            DeviceLightSystemFailure.UNSUPPORTED -> commercialError(
                R.string.device_light_error_unsupported_title,
                R.string.device_light_system_unsupported_error
            )
            DeviceLightSystemFailure.REJECTED -> rejectedError(
                R.string.device_light_system_operation_error
            )
            DeviceLightSystemFailure.INVALID_DATA -> invalidDataError(
                R.string.device_light_system_invalid_data_error
            )
        }
    }

    private fun rejectedError(@StringRes messageRes: Int) = commercialError(
        R.string.device_light_error_rejected_title,
        messageRes
    )

    private fun invalidDataError(@StringRes messageRes: Int) = commercialError(
        R.string.device_light_error_invalid_data_title,
        messageRes
    )

    private fun commercialError(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) = DeviceLightCommercialErrorMessage(titleRes = titleRes, messageRes = messageRes)
}

internal data class DeviceLightCommercialErrorMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

internal fun DeviceLightControlFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = when (this) {
        DeviceLightControlFailure.UNAVAILABLE -> DeviceLightCommercialErrorMessage(
            R.string.device_light_error_unavailable_title,
            R.string.device_light_mode_change_error
        )
        DeviceLightControlFailure.NOT_CONNECTED -> DeviceLightCommercialErrorMessage(
            R.string.device_light_error_not_connected_title,
            R.string.device_light_mode_change_error
        )
        DeviceLightControlFailure.UNSUPPORTED -> DeviceLightCommercialErrorMessage(
            R.string.device_light_error_unsupported_title,
            R.string.device_light_mode_change_error
        )
        DeviceLightControlFailure.REJECTED -> DeviceLightCommercialErrorMessage(
            R.string.device_light_error_rejected_title,
            R.string.device_light_mode_change_error
        )
        DeviceLightControlFailure.INVALID_DATA -> DeviceLightCommercialErrorMessage(
            R.string.device_light_error_invalid_data_title,
            R.string.device_light_mode_change_error
        )
    }

internal fun DeviceLightAdaptationFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolve(this)

internal fun DeviceLightAutomaticFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolve(this)

internal fun DeviceLightCustomFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolve(this)

internal fun DeviceLightLibraryFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolve(this)

internal fun DeviceLightLibraryFailure.toCommercialLightReadError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolveLibraryRead(this)

internal fun DeviceLightManualFailure.toCommercialLightError():
    DeviceLightCommercialErrorMessage = DeviceLightCommercialErrorResolver.resolve(this)

internal fun DeviceLightSystemFailure.toCommercialLightError(
    partialApplyPossible: Boolean = false
): DeviceLightCommercialErrorMessage =
    DeviceLightCommercialErrorResolver.resolve(this, partialApplyPossible)
