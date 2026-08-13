package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import kotlinx.coroutines.launch

/** Calibration destination for one centrally identified Dosing channel. */
class DeviceDosingChannelCalibrationFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_calibration) {

    private val args: DeviceDosingChannelCalibrationFragmentArgs by navArgs()
    private val viewModel: DeviceDosingChannelCalibrationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationTitle: String
        get() = getString(R.string.device_menu_calibration_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContent(view)
        setupBackHandling()
        observeEvents()
        viewModel.bind(
            route = DeviceDosingCalibrationRoute(
                deviceUid = args.deviceUid,
                slotId = args.slotId,
                pumpCount = args.pumpCount,
                channelNumber = args.channelNumber,
                channelTitle = args.channelTitle,
                recalibration = args.recalibration
            )
        )
    }

    override fun onBackRequested() = viewModel.requestExit()

    override fun onStop() {
        viewModel.onHostStopped()
        super.onStop()
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.calibrationCompose).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceDosingCalibrationScreen(
                    state = state,
                    onAction = viewModel::onAction
                )
            }
        }
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.requestExit()
            }
        )
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: DeviceDosingCalibrationEvent) {
        when (event) {
            DeviceDosingCalibrationEvent.Exit -> findNavController().navigateUp()
            is DeviceDosingCalibrationEvent.Completed -> openCompletedChannel(event)
        }
    }

    private fun openCompletedChannel(event: DeviceDosingCalibrationEvent.Completed) {
        val navController = findNavController()
        if (navController.popBackStack(R.id.deviceDosingRootFragment, false)) {
            AppRouteNavigator.openDosingChannel(
                navController = navController,
                target = event.target
            )
        }
    }
}
