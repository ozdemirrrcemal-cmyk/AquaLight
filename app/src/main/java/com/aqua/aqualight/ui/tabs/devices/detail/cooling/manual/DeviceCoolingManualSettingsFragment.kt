package com.aqua.aqualight.ui.tabs.devices.detail.cooling.manual

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.DeviceCoolingModeSettingsFragment

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
}
