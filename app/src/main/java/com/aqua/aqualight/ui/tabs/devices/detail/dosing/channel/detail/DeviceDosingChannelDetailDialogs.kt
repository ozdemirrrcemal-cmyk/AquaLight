package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.text.InputType
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.utils.DialogType

/** Owns modal input and feedback for the channel-detail navigation host. */
internal class DeviceDosingChannelDetailDialogs(
    private val fragment: DeviceDosingChannelDetailFragment,
    private val viewModel: DeviceDosingChannelDetailViewModel
) {
    fun registerResultListeners(owner: LifecycleOwner) {
        registerManualDoseResult(owner)
        registerResetResult(owner)
    }

    fun handleManualDoseClick() {
        if (viewModel.currentDraft().manualDoseActive) {
            viewModel.stopManualDose()
        } else {
            showManualDoseEditor()
        }
    }

    fun showOperationFailure(failure: DeviceDosingChannelDetailFailure) {
        showOperationMessage(failure.messageResource(), BaseActivity.SnackType.ERROR)
    }

    fun showOperationMessage(
        messageRes: Int,
        type: BaseActivity.SnackType = BaseActivity.SnackType.SUCCESS
    ) {
        (fragment.activity as? BaseActivity)?.showSnackBar(fragment.getString(messageRes), type)
    }

    fun showResetChannelConfirmation() {
        ConfirmDialogFragment.show(
            fragmentManager = fragment.childFragmentManager,
            request = ConfirmDialogFragment.Request(
                title = fragment.getString(R.string.device_dosing_detail_reset_warning_title),
                message = fragment.getString(R.string.device_dosing_detail_reset_warning_description),
                confirmText = fragment.getString(R.string.device_dosing_detail_reset_action),
                cancelText = fragment.getString(R.string.cancel),
                presentation = ConfirmDialogFragment.Presentation(
                    type = DialogType.WARNING,
                    destructive = true
                ),
                resultTarget = ConfirmDialogFragment.ResultTarget(
                    requestKey = RESET_CONFIRM_REQUEST_KEY,
                    actionId = ACTION_RESET_CHANNEL
                )
            )
        )
    }

    private fun registerManualDoseResult(owner: LifecycleOwner) {
        fragment.childFragmentManager.setFragmentResultListener(
            MANUAL_DOSE_REQUEST_KEY,
            owner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) != MANUAL_DOSE_PAYLOAD_ID) {
                return@setFragmentResultListener
            }
            if (result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED) {
                viewModel.startManualDose(
                    result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                )
            }
        }
    }

    private fun registerResetResult(owner: LifecycleOwner) {
        fragment.childFragmentManager.setFragmentResultListener(
            RESET_CONFIRM_REQUEST_KEY,
            owner
        ) { _, result ->
            val confirmed = result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) ==
                ACTION_RESET_CHANNEL && result.getString(ConfirmDialogFragment.RESULT_KEY) ==
                ConfirmDialogFragment.RESULT_CONFIRM
            if (confirmed) viewModel.resetChannel()
        }
    }

    private fun showManualDoseEditor() {
        if (!viewModel.currentDraft().manualDoseEnabled) return
        TextInputBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_dosing_detail_manual_title),
            label = fragment.getString(R.string.device_dosing_detail_manual_amount),
            hint = fragment.getString(R.string.device_dosing_detail_manual_amount_hint),
            initialValue = "",
            supportingText = fragment.getString(R.string.device_dosing_detail_manual_amount_description),
            suffixText = fragment.getString(R.string.device_dosing_detail_ml_unit),
            saveText = fragment.getString(R.string.device_dosing_detail_dispense_dose),
            cancelText = fragment.getString(R.string.common_cancel),
            required = true,
            requiredMessage = fragment.getString(R.string.device_dosing_detail_manual_amount_required),
            requestKey = MANUAL_DOSE_REQUEST_KEY,
            payloadId = MANUAL_DOSE_PAYLOAD_ID,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            requestFocus = true
        )
    }

    private companion object {
        const val MANUAL_DOSE_REQUEST_KEY = "dosing_manual_dose_input"
        const val MANUAL_DOSE_PAYLOAD_ID = "manual_dose"
        const val RESET_CONFIRM_REQUEST_KEY = "dosing_channel_reset_confirm"
        const val ACTION_RESET_CHANNEL = "reset_dosing_channel"
    }
}

private fun DeviceDosingChannelDetailFailure.messageResource(): Int = when (this) {
    DeviceDosingChannelDetailFailure.INVALID_INPUT -> R.string.device_dosing_detail_error_invalid_input
    DeviceDosingChannelDetailFailure.NOT_EDITABLE -> R.string.device_dosing_detail_error_not_editable
    DeviceDosingChannelDetailFailure.CALIBRATION_REQUIRED ->
        R.string.device_dosing_detail_error_calibration_required
    DeviceDosingChannelDetailFailure.BUSY -> R.string.device_dosing_detail_error_busy
    DeviceDosingChannelDetailFailure.STATE_CHANGED -> R.string.device_dosing_detail_error_state_changed
    DeviceDosingChannelDetailFailure.OUTPUT_STOP_UNCONFIRMED ->
        R.string.device_dosing_error_output_stop_unconfirmed
    DeviceDosingChannelDetailFailure.SAFETY_BLOCKED -> R.string.device_dosing_detail_error_safety_blocked
    DeviceDosingChannelDetailFailure.UNAVAILABLE -> R.string.device_dosing_detail_error_unavailable
    DeviceDosingChannelDetailFailure.TRY_AGAIN -> R.string.device_dosing_detail_operation_failed
}
