package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.utils.DialogType
import java.util.Date
import java.util.concurrent.TimeUnit

/** Detail destination and navigation owner for one centrally identified Dosing channel. */
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()
    private var missedDoseRecoveryEnabled by mutableStateOf(false)

    override val destinationTitle: String
        get() = args.channelTitle.ifBlank { getString(R.string.device_family_dosing) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (args.lastCalibratedAtEpochSeconds !in 1L..MAX_EPOCH_SECONDS) {
            findNavController().navigateUp()
            return
        }
        missedDoseRecoveryEnabled = savedInstanceState?.getBoolean(
            STATE_MISSED_DOSE_RECOVERY_ENABLED,
            false
        ) ?: false
        setupManualDoseResult()
        setupResetConfirmationResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(
            view = view,
            lastCalibrationDate = formatLastCalibrationDate(args.lastCalibratedAtEpochSeconds)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_MISSED_DOSE_RECOVERY_ENABLED, missedDoseRecoveryEnabled)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View, lastCalibrationDate: String) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingChannelDetailScreen(
                    state = DeviceDosingChannelDetailUiState(
                        lastCalibrationDate = lastCalibrationDate,
                        missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
                    ),
                    actions = DeviceDosingChannelDetailActions(
                        onMenuItemClick = ::openMenuItem,
                        onRecalibrateClick = ::openRecalibration,
                        onMissedDoseRecoveryChange = { enabled ->
                            missedDoseRecoveryEnabled = enabled
                        },
                        onManualDoseClick = ::showManualDoseEditor,
                        onResetChannelClick = ::showResetChannelConfirmation
                    )
                )
            }
        }
    }

    private fun setupManualDoseResult() {
        childFragmentManager.setFragmentResultListener(
            MANUAL_DOSE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) != MANUAL_DOSE_PAYLOAD_ID) {
                return@setFragmentResultListener
            }
            when (result.getString(TextInputBottomSheet.RESULT_KEY)) {
                TextInputBottomSheet.RESULT_SAVED,
                TextInputBottomSheet.RESULT_CANCELLED -> Unit
            }
        }
    }

    private fun setupResetConfirmationResult() {
        childFragmentManager.setFragmentResultListener(
            RESET_CONFIRM_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) != ACTION_RESET_CHANNEL) {
                return@setFragmentResultListener
            }
            when (result.getString(ConfirmDialogFragment.RESULT_KEY)) {
                ConfirmDialogFragment.RESULT_CONFIRM,
                ConfirmDialogFragment.RESULT_CANCEL -> Unit
            }
        }
    }

    private fun openMenuItem(item: DosingDetailMenuItem) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingChannelDetailFragment) return

        val direction = when (item) {
            DosingDetailMenuItem.DOSING_PLAN -> DeviceDosingChannelDetailFragmentDirections
                .actionDeviceDosingChannelDetailFragmentToDeviceDosingPlanFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber
                )
            DosingDetailMenuItem.RESERVOIR -> DeviceDosingChannelDetailFragmentDirections
                .actionDeviceDosingChannelDetailFragmentToDeviceDosingReservoirFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber
                )
        }
        navController.navigate(direction)
    }

    private fun openRecalibration() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingChannelDetailFragment) return
        navController.navigate(
            DeviceDosingChannelDetailFragmentDirections
                .actionDeviceDosingChannelDetailFragmentToDeviceDosingChannelCalibrationFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    recalibration = true
                )
        )
    }

    private fun formatLastCalibrationDate(epochSeconds: Long): String =
        DateFormat.getMediumDateFormat(requireContext()).format(
            Date(TimeUnit.SECONDS.toMillis(epochSeconds))
        )

    private fun showManualDoseEditor() {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_manual_title),
            label = getString(R.string.device_dosing_detail_manual_amount),
            hint = getString(R.string.device_dosing_detail_manual_amount_hint),
            initialValue = "",
            supportingText = getString(R.string.device_dosing_detail_manual_amount_description),
            suffixText = getString(R.string.device_dosing_detail_ml_unit),
            saveText = getString(R.string.device_dosing_detail_dispense_dose),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_detail_manual_amount_required),
            requestKey = MANUAL_DOSE_REQUEST_KEY,
            payloadId = MANUAL_DOSE_PAYLOAD_ID,
            maxLength = MANUAL_DOSE_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            minimumNumericValueExclusive = MANUAL_DOSE_MINIMUM_EXCLUSIVE,
            requestFocus = true
        )
    }

    private fun showResetChannelConfirmation() {
        ConfirmDialogFragment.show(
            fragmentManager = childFragmentManager,
            request = ConfirmDialogFragment.Request(
                title = getString(R.string.device_dosing_detail_reset_warning_title),
                message = getString(R.string.device_dosing_detail_reset_warning_description),
                confirmText = getString(R.string.device_dosing_detail_reset_action),
                cancelText = getString(R.string.cancel),
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

    private companion object {
        const val STATE_MISSED_DOSE_RECOVERY_ENABLED = "dosing_missed_dose_recovery_enabled"
        const val MANUAL_DOSE_REQUEST_KEY = "dosing_manual_dose_input"
        const val MANUAL_DOSE_PAYLOAD_ID = "manual_dose"
        const val MANUAL_DOSE_MAX_LENGTH = 7
        const val MANUAL_DOSE_MINIMUM_EXCLUSIVE = 0.0
        const val RESET_CONFIRM_REQUEST_KEY = "dosing_channel_reset_confirm"
        const val ACTION_RESET_CHANNEL = "reset_dosing_channel"
        const val MAX_EPOCH_SECONDS = 0xFFFF_FFFFL
    }
}
