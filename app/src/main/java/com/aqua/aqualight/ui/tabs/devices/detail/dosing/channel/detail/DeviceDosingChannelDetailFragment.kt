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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

/** Navigation/render host for one centrally identified Dosing channel. */
@Suppress("TooManyFunctions") // Fragment lifecycle and user-action handlers stay deliberately local.
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()
    private val viewModel: DeviceDosingChannelDetailViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationTitle: String
        get() = getString(R.string.device_family_dosing)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bind(
            deviceUidText = args.deviceUid,
            slotIdText = args.slotId,
            lastCalibratedAtEpochSeconds = args.lastCalibratedAtEpochSeconds,
            restoredMissedDoseRecoveryEnabled = savedInstanceState?.getBoolean(
                STATE_MISSED_DOSE_RECOVERY_ENABLED,
                false
            ) ?: false
        )
        if (!viewModel.currentDraft().routeValid) {
            findNavController().navigateUp()
            return
        }
        setupManualDoseResult()
        setupResetConfirmationResult()
        observeOperationEvents()
        observeChannelTitle()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            STATE_MISSED_DOSE_RECOVERY_ENABLED,
            viewModel.currentDraft().missedDoseRecoveryEnabled
        )
        super.onSaveInstanceState(outState)
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
                        missedDoseRecoveryEditable = draft.missedDoseRecoveryEditable,
                        manualDoseActive = draft.manualDoseActive,
                        manualDoseEnabled = draft.manualDoseEnabled,
                        resetEnabled = draft.resetEnabled,
                        operationInProgress = draft.operationInProgress
                    ),
                    actions = DeviceDosingChannelDetailActions(
                        onMenuItemClick = ::openMenuItem,
                        onRecalibrateClick = ::openRecalibration,
                        onMissedDoseRecoveryChange = viewModel::setMissedDoseRecoveryEnabled,
                        onManualDoseClick = ::handleManualDoseClick,
                        onResetChannelClick = ::showResetChannelConfirmation
                    )
                )
            }
        }
    }

    private fun observeChannelTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.draft
                    .map { draft -> draft.channelTitle }
                    .filter(String::isNotBlank)
                    .distinctUntilChanged()
                    .collect(::updateDestinationTitle)
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
                TextInputBottomSheet.RESULT_SAVED -> {
                    val amount = DeviceDosingScheduleAmountContract.parseMicroliters(
                        result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                    )
                    val maximum = viewModel.currentDraft().maximumManualDoseMicroliters
                    if (amount == null || amount > maximum) {
                        showOperationMessage(
                            R.string.device_dosing_detail_manual_amount_invalid,
                            BaseActivity.SnackType.ERROR
                        )
                    } else {
                        viewModel.startManualDose(amount)
                    }
                }
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
                ConfirmDialogFragment.RESULT_CONFIRM -> viewModel.resetChannel()
                ConfirmDialogFragment.RESULT_CANCEL -> Unit
            }
        }
    }

    private fun observeOperationEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved ->
                            showOperationMessage(R.string.device_dosing_detail_settings_saved)
                        DeviceDosingChannelDetailEvent.ManualDoseStarted ->
                            showOperationMessage(R.string.device_dosing_detail_manual_started)
                        DeviceDosingChannelDetailEvent.ManualDoseStopped ->
                            showOperationMessage(R.string.device_dosing_detail_manual_stopped)
                        DeviceDosingChannelDetailEvent.ChannelReset -> {
                            showOperationMessage(R.string.device_dosing_detail_channel_reset_done)
                            findNavController().navigateUp()
                        }
                        DeviceDosingChannelDetailEvent.OperationFailed -> showOperationMessage(
                            R.string.device_dosing_detail_operation_failed,
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
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
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber
                )
            DosingDetailMenuItem.RESERVOIR -> DeviceDosingChannelDetailFragmentDirections
                .actionDeviceDosingChannelDetailFragmentToDeviceDosingReservoirFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
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

    private fun handleManualDoseClick() {
        if (viewModel.currentDraft().manualDoseActive) {
            viewModel.stopManualDose()
        } else {
            showManualDoseEditor()
        }
    }

    private fun showOperationMessage(
        messageRes: Int,
        type: BaseActivity.SnackType = BaseActivity.SnackType.SUCCESS
    ) {
        (activity as? BaseActivity)?.showSnackBar(getString(messageRes), type)
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
    }
}
