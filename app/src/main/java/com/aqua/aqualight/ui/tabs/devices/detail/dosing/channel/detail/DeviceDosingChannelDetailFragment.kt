package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
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
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

/** Navigation/render host for one centrally identified Dosing channel. */
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()
    private val viewModel: DeviceDosingChannelDetailViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val dialogs by lazy { DeviceDosingChannelDetailDialogs(this, viewModel) }

    override val destinationTitle: String
        get() = getString(R.string.device_family_dosing)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialogs.registerResultListeners(viewLifecycleOwner)
        observeOperationEvents()
        observeOperationLoading()
        observeAuthoritativeRoute()
        observeChannelTitle()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
        viewModel.bind(
            deviceUidText = args.deviceUid,
            slotIdText = args.slotId
        )
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshAuthoritative()
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                DeviceDosingChannelDetailScreen(
                    state = DeviceDosingChannelDetailUiState(
                        lastCalibrationDate = if (draft.authoritativeStateAvailable) {
                            formatLastCalibrationDate(draft.lastCalibratedAtEpochSeconds)
                        } else {
                            getString(R.string.device_dosing_detail_value_unavailable)
                        },
                        missedDoseRecoveryEnabled = draft.missedDoseRecoveryEnabled,
                        missedDoseRecoveryEditable = draft.missedDoseRecoveryEditable,
                        manualDoseActive = draft.manualDoseActive,
                        manualDoseEnabled = draft.manualDoseEnabled,
                        resetEnabled = draft.resetEnabled,
                        operationInProgress = draft.operationInProgress,
                        missedDoseRecoverySyncing = draft.missedDoseRecoverySyncing
                    ),
                    actions = DeviceDosingChannelDetailActions(
                        onMenuItemClick = ::openMenuItem,
                        onRecalibrateClick = ::openRecalibration,
                        onMissedDoseRecoveryChange = viewModel::setMissedDoseRecoveryEnabled,
                        onManualDoseClick = dialogs::handleManualDoseClick,
                        onResetChannelClick = dialogs::showResetChannelConfirmation
                    )
                )
            }
        }
    }

    private fun observeAuthoritativeRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.draft
                    .map { draft -> draft.authoritativeStateAvailable && !draft.routeValid }
                    .distinctUntilChanged()
                    .filter { routeRejected -> routeRejected }
                    .collect {
                        navigateUpFromDetailIfCurrent()
                    }
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

    private fun observeOperationLoading() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.draft
                    .map { draft -> draft.operationInProgress || draft.missedDoseRecoverySyncing }
                    .distinctUntilChanged()
                    .collect { loading -> setFragmentGlobalLoading(loading) }
            }
        }
    }

    private fun observeOperationEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved ->
                            dialogs.showOperationMessage(R.string.device_dosing_detail_settings_saved)
                        DeviceDosingChannelDetailEvent.ManualDoseStarted ->
                            dialogs.showOperationMessage(R.string.device_dosing_detail_manual_started)
                        DeviceDosingChannelDetailEvent.ManualDoseStopped ->
                            dialogs.showOperationMessage(R.string.device_dosing_detail_manual_stopped)
                        DeviceDosingChannelDetailEvent.ChannelReset ->
                            dialogs.showOperationMessage(R.string.device_dosing_detail_channel_reset_done)
                        is DeviceDosingChannelDetailEvent.OperationFailed ->
                            dialogs.showOperationFailure(event.failure)
                    }
                }
            }
        }
    }

    private fun navigateUpFromDetailIfCurrent() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceDosingChannelDetailFragment) {
            navController.navigateUp()
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

}
