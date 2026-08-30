package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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
                DeviceCoolingAutomaticSettingsScreen(
                    state = state,
                    onStartTemperatureClick = ::showStartTemperatureSheet,
                    onMaximumTemperatureClick = ::showMaximumTemperatureSheet,
                    onSave = viewModel::save,
                    onRetry = viewModel::refresh
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
                    result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) * DISPLAY_SCALE
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
                    result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) * DISPLAY_SCALE
                )
            }
        }
    }

    private fun showStartTemperatureSheet() {
        val state = viewModel.uiState.value
        val policy = state.policy ?: return
        val current = state.draftStartTemperatureC ?: return
        val maximum = state.draftMaximumSpeedTemperatureC ?: return
        if (!state.editable) return

        val minimumUnits = toUnits(policy.startMinimumC)
        val maximumUnits = floorToStepUnits(
            value = minOf(policy.startMaximumC, maximum - policy.minimumGapC),
            origin = policy.startMinimumC,
            step = policy.stepC
        )
        val stepUnits = toUnits(policy.stepC).coerceAtLeast(1)
        if (maximumUnits < minimumUnits) return
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_automatic_start_sheet_title),
            helperText = getString(R.string.device_cooling_automatic_start_sheet_helper),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = snapToStepUnits(
                value = current,
                minimum = policy.startMinimumC,
                step = policy.stepC
            ).coerceIn(minimumUnits, maximumUnits),
            minValue = minimumUnits,
            maxValue = maximumUnits,
            step = stepUnits,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = REQUEST_START_TEMPERATURE,
            displayScale = DISPLAY_SCALE
        )
    }

    private fun showMaximumTemperatureSheet() {
        val state = viewModel.uiState.value
        val policy = state.policy ?: return
        val current = state.draftMaximumSpeedTemperatureC ?: return
        val start = state.draftStartTemperatureC ?: return
        if (!state.editable) return

        val minimumValue = maxOf(policy.maximumSpeedMinimumC, start + policy.minimumGapC)
        val minimumUnits = ceilToStepUnits(
            value = minimumValue,
            origin = policy.maximumSpeedMinimumC,
            step = policy.stepC
        )
        val maximumUnits = toUnits(policy.maximumSpeedMaximumC)
        val stepUnits = toUnits(policy.stepC).coerceAtLeast(1)
        if (minimumUnits > maximumUnits) return
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_automatic_max_sheet_title),
            helperText = getString(R.string.device_cooling_automatic_max_sheet_helper),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = snapToStepUnits(
                value = current,
                minimum = policy.maximumSpeedMinimumC,
                step = policy.stepC
            ).coerceIn(minimumUnits, maximumUnits),
            minValue = minimumUnits,
            maxValue = maximumUnits,
            step = stepUnits,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = REQUEST_MAXIMUM_TEMPERATURE,
            displayScale = DISPLAY_SCALE
        )
    }

    private fun toUnits(value: Double): Int = (value / DISPLAY_SCALE).roundToInt()

    private fun snapToStepUnits(value: Double, minimum: Double, step: Double): Int {
        val steps = ((value - minimum) / step).roundToInt()
        return toUnits(minimum + steps * step)
    }

    private fun floorToStepUnits(value: Double, origin: Double, step: Double): Int {
        val steps = floor((value - origin) / step).toInt()
        return toUnits(origin + steps * step)
    }

    private fun ceilToStepUnits(value: Double, origin: Double, step: Double): Int {
        val steps = ceil((value - origin) / step).toInt()
        return toUnits(origin + steps * step)
    }

    private companion object {
        const val DISPLAY_SCALE = 0.1
        const val REQUEST_START_TEMPERATURE = "cooling_automatic_start_temperature"
        const val REQUEST_MAXIMUM_TEMPERATURE = "cooling_automatic_maximum_temperature"
    }
}
