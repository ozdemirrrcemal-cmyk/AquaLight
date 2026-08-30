package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class DeviceCoolingProgramSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_program_settings_title
) {

    private val args: DeviceCoolingProgramSettingsFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingProgramSettingsViewModel by viewModels()

    override val destinationDeviceUid: String
        get() = args.deviceUid

    override fun onModeSettingsViewCreated(savedInstanceState: Bundle?) {
        super.onModeSettingsViewCreated(savedInstanceState)
        registerPickerResults()
        modeSettingsBinding.coolingModeSettingsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingProgramSettingsScreen(
                    state = state,
                    onSlotClick = viewModel::selectSlot,
                    onAddSlot = viewModel::addTimeSlot,
                    onStartTimeClick = ::showStartTimeSheet,
                    onEndTimeClick = ::showEndTimeSheet,
                    onStartTemperatureClick = ::showStartTemperatureSheet,
                    onMaximumTemperatureClick = ::showMaximumTemperatureSheet,
                    onFanLimitClick = ::showFanLimitSheet,
                    onSave = viewModel::saveDraft
                )
            }
        }
    }

    private fun registerPickerResults() {
        registerTimeResult(REQUEST_START_TIME, viewModel::updateStartTime)
        registerTimeResult(REQUEST_END_TIME, viewModel::updateEndTime)
        registerStepperResult(REQUEST_START_TEMPERATURE) { slotId, value ->
            viewModel.updateStartTemperature(slotId, value * TEMPERATURE_DISPLAY_SCALE)
        }
        registerStepperResult(REQUEST_MAXIMUM_TEMPERATURE) { slotId, value ->
            viewModel.updateMaximumSpeedTemperature(slotId, value * TEMPERATURE_DISPLAY_SCALE)
        }
        registerStepperResult(REQUEST_FAN_LIMIT) { slotId, value ->
            viewModel.updateFanLimit(slotId, value)
        }
    }

    private fun registerTimeResult(
        requestKey: String,
        onSelected: (String, Int) -> Unit
    ) {
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val slotId = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            if (slotId.isBlank()) return@setFragmentResultListener
            onSelected(
                slotId,
                result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
            )
        }
    }

    private fun registerStepperResult(
        requestKey: String,
        onSaved: (String, Int) -> Unit
    ) {
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) !=
                IntegerStepperBottomSheet.RESULT_SAVED
            ) {
                return@setFragmentResultListener
            }
            val slotId = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            if (slotId.isBlank()) return@setFragmentResultListener
            onSaved(slotId, result.getInt(IntegerStepperBottomSheet.RESULT_VALUE))
        }
    }

    private fun showStartTimeSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        showTimeSheet(
            slotId = slotId,
            minutesOfDay = slot.startMinutes,
            requestKey = REQUEST_START_TIME,
            titleRes = R.string.device_cooling_program_start_time_sheet_title,
            messageRes = R.string.device_cooling_program_start_time_sheet_message
        )
    }

    private fun showEndTimeSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        showTimeSheet(
            slotId = slotId,
            minutesOfDay = slot.endMinutes,
            requestKey = REQUEST_END_TIME,
            titleRes = R.string.device_cooling_program_end_time_sheet_title,
            messageRes = R.string.device_cooling_program_end_time_sheet_message
        )
    }

    private fun showTimeSheet(
        slotId: String,
        minutesOfDay: Int,
        requestKey: String,
        titleRes: Int,
        messageRes: Int
    ) {
        AquaTimePickerBottomSheet.show(
            fragmentManager = parentFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(titleRes),
                message = getString(messageRes),
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                confirmText = getString(R.string.device_cooling_automatic_stepper_apply),
                cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = requestKey,
                    payloadId = slotId
                )
            )
        )
    }

    private fun showStartTemperatureSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        val minimumUnits = toTemperatureUnits(DeviceCoolingProgramPolicy.minimumTemperatureC)
        val maximumUnits = floorToTemperatureStepUnits(
            slot.maximumSpeedTemperatureC - DeviceCoolingProgramPolicy.minimumTemperatureGapC
        )
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_fan_start_temperature),
            helperText = getString(R.string.device_cooling_program_start_temperature_sheet_helper),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = toTemperatureUnits(slot.startTemperatureC)
                .coerceIn(minimumUnits, maximumUnits),
            minValue = minimumUnits,
            maxValue = maximumUnits,
            step = TEMPERATURE_STEP_UNITS,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = REQUEST_START_TEMPERATURE,
            payloadId = slotId,
            displayScale = TEMPERATURE_DISPLAY_SCALE
        )
    }

    private fun showMaximumTemperatureSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        val minimumUnits = ceilToTemperatureStepUnits(
            slot.startTemperatureC + DeviceCoolingProgramPolicy.minimumTemperatureGapC
        )
        val maximumUnits = toTemperatureUnits(DeviceCoolingProgramPolicy.maximumTemperatureC)
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_max_speed_temperature),
            helperText = getString(R.string.device_cooling_program_max_temperature_sheet_helper),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = toTemperatureUnits(slot.maximumSpeedTemperatureC)
                .coerceIn(minimumUnits, maximumUnits),
            minValue = minimumUnits,
            maxValue = maximumUnits,
            step = TEMPERATURE_STEP_UNITS,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = REQUEST_MAXIMUM_TEMPERATURE,
            payloadId = slotId,
            displayScale = TEMPERATURE_DISPLAY_SCALE
        )
    }

    private fun showFanLimitSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_program_fan_limit_sheet_title),
            helperText = getString(R.string.device_cooling_program_fan_limit_sheet_helper),
            valueFormat = getString(R.string.device_cooling_program_fan_limit_value_format),
            initialValue = slot.fanLimitPercent,
            minValue = DeviceCoolingProgramPolicy.minimumFanLimitPercent,
            maxValue = DeviceCoolingProgramPolicy.maximumFanLimitPercent,
            step = DeviceCoolingProgramPolicy.fanLimitStepPercent,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_program_fan_limit_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_program_fan_limit_increase
            ),
            requestKey = REQUEST_FAN_LIMIT,
            payloadId = slotId
        )
    }

    private fun findSlot(slotId: String): DeviceCoolingProgramSlot? =
        viewModel.uiState.value.slots.firstOrNull { slot -> slot.id == slotId }

    private fun toTemperatureUnits(value: Double): Int =
        (value / TEMPERATURE_DISPLAY_SCALE).roundToInt()

    private fun floorToTemperatureStepUnits(value: Double): Int {
        val units = floor(value / DeviceCoolingProgramPolicy.temperatureStepC).toInt()
        return toTemperatureUnits(units * DeviceCoolingProgramPolicy.temperatureStepC)
    }

    private fun ceilToTemperatureStepUnits(value: Double): Int {
        val units = ceil(value / DeviceCoolingProgramPolicy.temperatureStepC).toInt()
        return toTemperatureUnits(units * DeviceCoolingProgramPolicy.temperatureStepC)
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val TEMPERATURE_DISPLAY_SCALE = 0.1
        const val TEMPERATURE_STEP_UNITS = 5
        const val REQUEST_START_TIME = "cooling_program_start_time"
        const val REQUEST_END_TIME = "cooling_program_end_time"
        const val REQUEST_START_TEMPERATURE = "cooling_program_start_temperature"
        const val REQUEST_MAXIMUM_TEMPERATURE = "cooling_program_maximum_temperature"
        const val REQUEST_FAN_LIMIT = "cooling_program_fan_limit"
    }
}
