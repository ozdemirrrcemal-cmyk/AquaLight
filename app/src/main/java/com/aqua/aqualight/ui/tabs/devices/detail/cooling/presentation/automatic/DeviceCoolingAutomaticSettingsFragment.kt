package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingModeSettingsFragment

class DeviceCoolingAutomaticSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_automatic_settings_title
) {

    private val args: DeviceCoolingAutomaticSettingsFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingAutomaticSettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationDeviceUid: String
        get() = args.deviceUid

    override fun onModeSettingsViewCreated(savedInstanceState: Bundle?) {
        super.onModeSettingsViewCreated(savedInstanceState)
        registerStepperResults()
        viewModel.bind(destinationDeviceUid)
        modeSettingsBinding.coolingModeSettingsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingAutomaticStateScreen(
                    state = state,
                    actions = DeviceCoolingAutomaticSettingsActions(
                        onStartTemperatureClick = ::showStartTemperatureSheet,
                        onMaximumTemperatureClick = ::showMaximumTemperatureSheet,
                        onSilentModeChanged = viewModel::updateSilentMode,
                        onSave = viewModel::save,
                        onRetry = viewModel::refresh
                    )
                )
            }
        }
    }

    private fun registerStepperResults() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_START_TEMPERATURE,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) ==
                IntegerStepperBottomSheet.RESULT_SAVED
            ) {
                viewModel.updateStartTemperature(
                    result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) *
                        AUTOMATIC_TEMPERATURE_DISPLAY_SCALE
                )
            }
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_MAXIMUM_TEMPERATURE,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) ==
                IntegerStepperBottomSheet.RESULT_SAVED
            ) {
                viewModel.updateMaximumSpeedTemperature(
                    result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) *
                        AUTOMATIC_TEMPERATURE_DISPLAY_SCALE
                )
            }
        }
    }

    private fun showStartTemperatureSheet() {
        startTemperatureStepperBounds(viewModel.uiState.value)?.let { bounds ->
            showTemperatureSheet(
                bounds = bounds,
                titleRes = R.string.device_cooling_automatic_start_sheet_title,
                helperRes = R.string.device_cooling_automatic_start_sheet_helper,
                requestKey = REQUEST_START_TEMPERATURE
            )
        }
    }

    private fun showMaximumTemperatureSheet() {
        maximumTemperatureStepperBounds(viewModel.uiState.value)?.let { bounds ->
            showTemperatureSheet(
                bounds = bounds,
                titleRes = R.string.device_cooling_automatic_max_sheet_title,
                helperRes = R.string.device_cooling_automatic_max_sheet_helper,
                requestKey = REQUEST_MAXIMUM_TEMPERATURE
            )
        }
    }

    private fun showTemperatureSheet(
        bounds: AutomaticTemperatureStepperBounds,
        titleRes: Int,
        helperRes: Int,
        requestKey: String
    ) {
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(titleRes),
            helperText = getString(helperRes),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = bounds.initialValue,
            minValue = bounds.minimumValue,
            maxValue = bounds.maximumValue,
            step = bounds.step,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = requestKey,
            displayScale = AUTOMATIC_TEMPERATURE_DISPLAY_SCALE
        )
    }

    private companion object {
        const val REQUEST_START_TEMPERATURE = "cooling_automatic_start_temperature"
        const val REQUEST_MAXIMUM_TEMPERATURE = "cooling_automatic_maximum_temperature"
    }
}
