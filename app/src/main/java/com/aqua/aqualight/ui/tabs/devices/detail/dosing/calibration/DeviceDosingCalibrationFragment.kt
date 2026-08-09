package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceDosingCalibrationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceDosingCalibrationFragment : Fragment(R.layout.fragment_device_dosing_calibration) {

    private val args: DeviceDosingCalibrationFragmentArgs by navArgs()
    private val viewModel: DeviceDosingCalibrationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceDosingCalibrationBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceDosingCalibrationBinding.bind(view)

        setupHeader()
        setupBackHandling()
        setupContent()
        observeEvents()

        viewModel.bind(
            deviceUid = args.deviceUid,
            channelKey = args.channelKey
        )
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_dosing_calibration_title),
                onBackClick = { viewModel.dispatch(DosingCalibrationAction.Exit) }
            )
        )
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            viewModel.dispatch(DosingCalibrationAction.Exit)
        }
    }

    private fun setupContent() {
        binding.calibrationCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceDosingCalibrationScreen(
                    state = state,
                    onAction = viewModel::dispatch
                )
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DosingCalibrationEvent.Exit,
                        DosingCalibrationEvent.Completed -> navigateUpOnce()
                    }
                }
            }
        }
    }

    private fun navigateUpOnce() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceDosingCalibrationFragment) {
            navController.navigateUp()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
