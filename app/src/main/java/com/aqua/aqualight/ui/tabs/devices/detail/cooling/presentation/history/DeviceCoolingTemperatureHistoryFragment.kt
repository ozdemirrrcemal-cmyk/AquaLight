package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceCoolingTemperatureHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceCoolingTemperatureHistoryFragment :
    Fragment(R.layout.fragment_device_cooling_temperature_history) {

    private val args: DeviceCoolingTemperatureHistoryFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingTemperatureHistoryViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceCoolingTemperatureHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceCoolingTemperatureHistoryBinding.bind(view)
        viewModel.bind(args.deviceUid)
        setupHeader()
        setupContent()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_cooling_history_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupContent() {
        binding.coolingHistoryCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingTemperatureHistoryScreen(
                    state = state,
                    onRangeSelected = viewModel::selectRange,
                    onRetry = viewModel::retry
                )
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
