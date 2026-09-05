package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingModeSettingsFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DeviceCoolingManualSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_manual_settings_title
) {

    private val args: DeviceCoolingManualSettingsFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingManualSettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationDeviceUid: String
        get() = args.deviceUid

    override fun onModeSettingsViewCreated(savedInstanceState: Bundle?) {
        super.onModeSettingsViewCreated(savedInstanceState)
        viewModel.bind(destinationDeviceUid)
        observeMutationLoading()
        modeSettingsBinding.coolingModeSettingsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingManualSettingsScreen(
                    state = state,
                    onTargetPercentChanged = viewModel::updateTargetPercent,
                    onRetry = viewModel::refresh
                )
            }
        }
    }

    private fun observeMutationLoading() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state -> state.operationInProgress }
                    .distinctUntilChanged()
                    .collect { loading -> setFragmentGlobalLoading(loading) }
            }
        }
    }
}
