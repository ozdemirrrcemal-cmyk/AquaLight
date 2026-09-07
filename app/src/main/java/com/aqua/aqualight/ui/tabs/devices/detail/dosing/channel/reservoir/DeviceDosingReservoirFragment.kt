package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.dialog.UnsavedChangesExitGuard
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Render/input host for the authoritative reservoir editor. */
class DeviceDosingReservoirFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingReservoirFragmentArgs by navArgs()
    private val viewModel: DeviceDosingReservoirViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private lateinit var unsavedChangesExitGuard: UnsavedChangesExitGuard
    private val notifications by lazy {
        DeviceDosingReservoirNotificationController(this, viewModel) {
            showReservoirMessage(
                R.string.notification_feature_access_check_failed,
                BaseActivity.SnackType.ERROR
            )
        }
    }
    private val capacityEditor by lazy { DeviceDosingReservoirCapacityEditor(this, viewModel) }

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_reservoir_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bind(deviceUidText = args.deviceUid, slotIdText = args.slotId)
        setupUnsavedChangesExitGuard()
        capacityEditor.registerResult(viewLifecycleOwner)
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        observeReservoirEvents()
        observeOperationLoading()
        setupContent(view)
    }

    override fun onBackRequested() = unsavedChangesExitGuard.requestExit()

    override fun onResume() {
        super.onResume()
        notifications.refresh()
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val editorState by viewModel.editorState.collectAsStateWithLifecycle()
                val draft = editorState.draft
                DeviceDosingReservoirScreen(
                    state = DeviceDosingReservoirUiState(
                        trackingEnabled = draft.trackingEnabled,
                        capacityValue = capacityEditor.formatCapacity(
                            draft.reservoirCapacityMicroliters
                        ),
                        remainingValue = editorState.remainingMicroliters
                            ?.takeIf {
                                draft.trackingEnabled && editorState.remainingAccountingCertain
                            }
                            ?.let(capacityEditor::formatRemainingVolume)
                            ?: getString(R.string.device_dosing_detail_value_unavailable),
                        capacityRejection = editorState.capacityRejection,
                        lowLevelAlertEnabled = draft.lowLevelAlertEnabled,
                        reservoirNeedsAttention = editorState.reservoirNeedsAttention,
                        editorEnabled = editorState.editable && !editorState.operationInProgress,
                        canSave = editorState.canSave,
                        canRefill = editorState.canRefill,
                        lowLevelAlertNotificationAvailability = editorState.notificationAvailability
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = notifications::setTrackingEnabled,
                        onCapacityClick = capacityEditor::show,
                        onRefillClick = viewModel::refill,
                        onLowLevelAlertEnabledChange = notifications::setLowLevelAlertEnabled,
                        onRepairLowLevelAlertNotifications = notifications::repair,
                        onSaveClick = viewModel::save
                    )
                )
            }
        }
    }

    private fun setupUnsavedChangesExitGuard() {
        unsavedChangesExitGuard = UnsavedChangesExitGuard.attach(
            fragment = this,
            configuration = UnsavedChangesExitGuard.Configuration(
                requestKey = UNSAVED_CHANGES_REQUEST_KEY,
                actionId = ACTION_EXIT_WITHOUT_SAVING,
                hasUnsavedChanges = { viewModel.currentEditorState().dirty },
                isExitBlocked = { viewModel.currentEditorState().operationInProgress },
                exit = {
                    val navController = findNavController()
                    if (
                        navController.currentDestination?.id == R.id.deviceDosingReservoirFragment
                    ) {
                        navController.navigateUp()
                    }
                }
            )
        )
    }

    private fun observeReservoirEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DeviceDosingReservoirEvent.Saved -> {
                            showReservoirMessage(R.string.device_dosing_reservoir_saved)
                            findNavController().navigateUp()
                        }
                        is DeviceDosingReservoirEvent.SaveRejected -> showReservoirMessage(
                            event.reason.messageRes,
                            BaseActivity.SnackType.ERROR
                        )
                        DeviceDosingReservoirEvent.Refilled ->
                            showReservoirMessage(R.string.device_dosing_reservoir_refilled)
                        DeviceDosingReservoirEvent.SaveFailed,
                        DeviceDosingReservoirEvent.RefillFailed -> showReservoirMessage(
                            R.string.device_dosing_detail_operation_failed,
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun observeOperationLoading() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editorState
                    .map { state -> state.operationInProgress }
                    .distinctUntilChanged()
                    .collect { loading -> setFragmentGlobalLoading(loading) }
            }
        }
    }

    private fun showReservoirMessage(
        messageRes: Int,
        type: BaseActivity.SnackType = BaseActivity.SnackType.SUCCESS
    ) {
        (activity as? BaseActivity)?.showSnackBar(getString(messageRes), type)
    }

}

private val DeviceDosingChannelRejection.messageRes: Int
    get() = when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT -> R.string.device_dosing_detail_error_invalid_input
        DeviceDosingChannelRejection.NOT_EDITABLE -> R.string.device_dosing_detail_error_not_editable
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            R.string.device_dosing_detail_error_calibration_required
        DeviceDosingChannelRejection.BUSY -> R.string.device_dosing_detail_error_busy
        DeviceDosingChannelRejection.CONFLICT -> R.string.device_dosing_detail_error_state_changed
        DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED ->
            R.string.device_dosing_error_output_stop_unconfirmed
        DeviceDosingChannelRejection.UNSAFE -> R.string.device_dosing_detail_error_safety_blocked
        DeviceDosingChannelRejection.UNKNOWN -> R.string.device_dosing_detail_operation_failed
    }

private const val UNSAVED_CHANGES_REQUEST_KEY = "dosing_reservoir_unsaved_changes"
private const val ACTION_EXIT_WITHOUT_SAVING = "exit_dosing_reservoir_without_saving"
