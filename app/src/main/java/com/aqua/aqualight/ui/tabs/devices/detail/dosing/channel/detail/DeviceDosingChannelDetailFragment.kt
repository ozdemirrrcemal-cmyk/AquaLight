package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.utils.DialogType
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/** Navigation/render host for one centrally identified, firmware-authoritative Dosing channel. */
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()
    private val viewModel: DeviceDosingChannelDetailViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationTitle: String
        get() = args.channelTitle.ifBlank { getString(R.string.device_family_dosing) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bind(
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            routeCalibrationEpochSeconds = args.lastCalibratedAtEpochSeconds
        )
        if (!viewModel.currentDraft().routeValid) {
            findNavController().navigateUp()
            return
        }
        setupManualDoseResult()
        setupResetConfirmationResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                DeviceDosingChannelDetailScreen(
                    state = DeviceDosingChannelDetailUiState(
                        lastCalibrationDate = formatLastCalibrationDate(
                            draft.lastCalibratedAtEpochSeconds
                        ),
                        missedDoseRecoveryEnabled = draft.missedDoseRecoveryEnabled,
                        missedDoseRecoveryAvailable = draft.missedDoseRecoveryAvailable,
                        manualDoseAvailable = draft.manualDoseAvailable,
                        channelResetAvailable = draft.channelResetAvailable,
                        interactionBusy = draft.interactionBusy
                    ),
                    actions = DeviceDosingChannelDetailActions(
                        onMenuItemClick = ::openMenuItem,
                        onRecalibrateClick = ::openRecalibration,
                        onMissedDoseRecoveryChange = viewModel::setMissedDoseRecoveryEnabled,
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
            if (result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) != args.slotId) {
                return@setFragmentResultListener
            }
            if (result.getString(TextInputBottomSheet.RESULT_KEY) != TextInputBottomSheet.RESULT_SAVED) {
                return@setFragmentResultListener
            }
            val microliters = DeviceDosingScheduleAmountContract.parseMicroliters(
                result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            ) ?: return@setFragmentResultListener
            val amountMl = DeviceDosingScheduleAmountContract.milliliters(microliters)
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.dispenseManualDose(amountMl)
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
            if (result.getString(ConfirmDialogFragment.RESULT_KEY) != ConfirmDialogFragment.RESULT_CONFIRM) {
                return@setFragmentResultListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                if (viewModel.resetChannel()) findNavController().navigateUp()
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
        val maxManualDose = viewModel.currentDraft().maxManualDoseMl
        if (!viewModel.currentDraft().manualDoseAvailable || maxManualDose <= 0.0) return
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
            payloadId = args.slotId,
            maxLength = MANUAL_DOSE_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            minimumNumericValueExclusive = MANUAL_DOSE_MINIMUM_EXCLUSIVE,
            requestFocus = true
        )
    }

    private fun showResetChannelConfirmation() {
        if (!viewModel.currentDraft().channelResetAvailable) return
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
        const val MANUAL_DOSE_REQUEST_KEY = "dosing_manual_dose_input"
        const val MANUAL_DOSE_MAX_LENGTH = 7
        const val MANUAL_DOSE_MINIMUM_EXCLUSIVE = 0.0
        const val RESET_CONFIRM_REQUEST_KEY = "dosing_channel_reset_confirm"
        const val ACTION_RESET_CHANNEL = "reset_dosing_channel"
    }
}
