package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet

internal data class DeviceDosingCustomScheduleEditorHost(
    val fragment: Fragment,
    val slotId: String,
    val maxEventsPerChannel: Int,
    val maxPeriodsPerChannel: Int,
    val periods: () -> List<DeviceDosingCustomPeriod>,
    val updatePeriods: (List<DeviceDosingCustomPeriod>) -> Unit,
    val updateValidation: (Int?) -> Unit
)

/** Coordinates the three-step period editor independently from screen rendering. */
internal class DeviceDosingCustomScheduleEditor(
    private val host: DeviceDosingCustomScheduleEditorHost,
    savedInstanceState: Bundle?
) {
    private var pendingIndex = savedInstanceState?.getInt(STATE_PENDING_INDEX, NO_PENDING_INDEX)
        ?: NO_PENDING_INDEX
    private var pendingStartTimeMs = savedInstanceState?.getLong(
        STATE_PENDING_START_TIME_MS,
        DEFAULT_START_TIME_MS
    ) ?: DEFAULT_START_TIME_MS
    private var pendingEndTimeMs = savedInstanceState?.getLong(
        STATE_PENDING_END_TIME_MS,
        DEFAULT_END_TIME_MS
    ) ?: DEFAULT_END_TIME_MS

    fun bindResults(lifecycleOwner: LifecycleOwner) {
        setupStartTimeResult(lifecycleOwner)
        setupEndTimeResult(lifecycleOwner)
        host.fragment.childFragmentManager.setFragmentResultListener(
            DOSE_COUNT_REQUEST_KEY,
            lifecycleOwner
        ) { _, result ->
            val isExpectedResult =
                result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID) == host.slotId &&
                    result.getString(IntegerStepperBottomSheet.RESULT_KEY) ==
                    IntegerStepperBottomSheet.RESULT_SAVED
            if (isExpectedResult) {
                commitPendingPeriod(result.getInt(IntegerStepperBottomSheet.RESULT_VALUE))
            }
        }
    }

    fun saveState(outState: Bundle) {
        outState.putInt(STATE_PENDING_INDEX, pendingIndex)
        outState.putLong(STATE_PENDING_START_TIME_MS, pendingStartTimeMs)
        outState.putLong(STATE_PENDING_END_TIME_MS, pendingEndTimeMs)
    }

    fun beginAdd() {
        val periods = host.periods()
        val capacityAvailable = periods.size < host.maxPeriodsPerChannel &&
            remainingDoseCapacity(
                periods = periods,
                pendingIndex = pendingIndex,
                maxEventsPerChannel = host.maxEventsPerChannel
            ) > 0
        if (!capacityAvailable) {
            host.updateValidation(R.string.device_dosing_custom_error_too_many)
            return
        }
        host.updateValidation(null)
        pendingIndex = NEW_PERIOD_INDEX
        pendingStartTimeMs = nextDefaultStartTimeMs(periods)
        pendingEndTimeMs = (pendingStartTimeMs + DEFAULT_PERIOD_DURATION_MS)
            .coerceAtMost(DeviceDosingCustomScheduleContract.LAST_MINUTE_START_MS)
        showTimePicker(CustomTimePickerStage.START)
    }

    fun beginEdit(index: Int) {
        val period = host.periods().getOrNull(index) ?: return
        host.updateValidation(null)
        pendingIndex = index
        pendingStartTimeMs = period.startTimeMs
        pendingEndTimeMs = period.endTimeMs
        showTimePicker(CustomTimePickerStage.START)
    }

    private fun setupStartTimeResult(lifecycleOwner: LifecycleOwner) {
        host.fragment.childFragmentManager.setFragmentResultListener(
            START_TIME_REQUEST_KEY,
            lifecycleOwner
        ) { _, result ->
            selectedMinutesOfDay(result, host.slotId)?.let { minutesOfDay ->
                pendingStartTimeMs = DeviceDosingCustomScheduleContract.startTimeMs(minutesOfDay)
                host.fragment.view?.post { showTimePicker(CustomTimePickerStage.END) }
            }
        }
    }

    private fun setupEndTimeResult(lifecycleOwner: LifecycleOwner) {
        host.fragment.childFragmentManager.setFragmentResultListener(
            END_TIME_REQUEST_KEY,
            lifecycleOwner
        ) { _, result ->
            selectedMinutesOfDay(result, host.slotId)?.let { minutesOfDay ->
                pendingEndTimeMs = DeviceDosingCustomScheduleContract.startTimeMs(minutesOfDay)
                host.fragment.view?.post(::showDoseCountPicker)
            }
        }
    }

    private fun showTimePicker(stage: CustomTimePickerStage) {
        val fragment = host.fragment
        val timeMs = when (stage) {
            CustomTimePickerStage.START -> pendingStartTimeMs
            CustomTimePickerStage.END -> pendingEndTimeMs
        }
        val minutesOfDay = DeviceDosingCustomScheduleContract.minutesOfDay(timeMs)
        AquaTimePickerBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = fragment.getString(stage.titleRes),
                message = fragment.getString(stage.messageRes),
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                confirmText = fragment.getString(stage.confirmRes),
                cancelText = fragment.getString(R.string.common_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = stage.requestKey,
                    payloadId = host.slotId
                )
            )
        )
    }

    private fun showDoseCountPicker() {
        val periods = host.periods()
        val maximum = remainingDoseCapacity(
            periods = periods,
            pendingIndex = pendingIndex,
            maxEventsPerChannel = host.maxEventsPerChannel,
            includePendingPeriod = true
        )
        if (maximum <= 0) {
            host.updateValidation(R.string.device_dosing_custom_error_too_many)
            clearPendingPeriod()
            return
        }
        val fragment = host.fragment
        val initial = periods.getOrNull(pendingIndex)?.doseCount
            ?: DEFAULT_DOSE_COUNT.coerceAtMost(maximum)
        IntegerStepperBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_dosing_custom_count_picker_title),
            helperText = fragment.getString(R.string.device_dosing_custom_count_picker_message),
            valueFormat = fragment.getString(R.string.device_dosing_custom_count_picker_format),
            initialValue = initial,
            minValue = MIN_DOSE_COUNT,
            maxValue = maximum,
            step = MIN_DOSE_COUNT,
            saveText = fragment.getString(R.string.device_dosing_custom_count_picker_confirm),
            cancelText = fragment.getString(R.string.common_cancel),
            decreaseContentDescription = fragment.getString(
                R.string.device_dosing_custom_count_decrease_description
            ),
            increaseContentDescription = fragment.getString(
                R.string.device_dosing_custom_count_increase_description
            ),
            requestKey = DOSE_COUNT_REQUEST_KEY,
            payloadId = host.slotId
        )
    }

    private fun commitPendingPeriod(doseCount: Int) {
        val candidate = DeviceDosingCustomPeriod(
            startTimeMs = pendingStartTimeMs,
            endTimeMs = pendingEndTimeMs,
            doseCount = doseCount
        )
        val updated = host.periods().toMutableList().apply {
            if (pendingIndex in indices) this[pendingIndex] = candidate else add(candidate)
        }
        val error = DeviceDosingCustomScheduleContract.validate(
            periods = updated,
            maxEventsPerChannel = host.maxEventsPerChannel,
            maxPeriodsPerChannel = host.maxPeriodsPerChannel
        )
        if (error == null) {
            host.updatePeriods(
                DeviceDosingCustomScheduleContract.normalize(
                    periods = updated,
                    maxEventsPerChannel = host.maxEventsPerChannel,
                    maxPeriodsPerChannel = host.maxPeriodsPerChannel
                )
            )
            host.updateValidation(null)
        } else {
            host.updateValidation(customValidationMessage(error))
        }
        clearPendingPeriod()
    }

    private fun clearPendingPeriod() {
        pendingIndex = NO_PENDING_INDEX
        pendingStartTimeMs = DEFAULT_START_TIME_MS
        pendingEndTimeMs = DEFAULT_END_TIME_MS
    }
}

private enum class CustomTimePickerStage(
    val requestKey: String,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val confirmRes: Int
) {
    START(
        requestKey = START_TIME_REQUEST_KEY,
        titleRes = R.string.device_dosing_custom_start_picker_title,
        messageRes = R.string.device_dosing_custom_start_picker_message,
        confirmRes = R.string.device_dosing_custom_start_picker_confirm
    ),
    END(
        requestKey = END_TIME_REQUEST_KEY,
        titleRes = R.string.device_dosing_custom_end_picker_title,
        messageRes = R.string.device_dosing_custom_end_picker_message,
        confirmRes = R.string.device_dosing_custom_end_picker_confirm
    )
}

private fun selectedMinutesOfDay(result: Bundle, slotId: String): Int? {
    val isExpectedResult =
        result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID) == slotId &&
            result.getString(AquaTimePickerBottomSheet.RESULT_KEY) ==
            AquaTimePickerBottomSheet.RESULT_SELECTED &&
            result.getString(AquaTimePickerBottomSheet.RESULT_SELECTION_MODE) ==
            AquaTimePickerBottomSheet.SelectionMode.TIME_OF_DAY.name
    return result.getInt(
        AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY,
        INVALID_MINUTES_OF_DAY
    ).takeIf { minutes ->
        isExpectedResult && minutes in 0 until DeviceDosingCustomScheduleContract.MINUTES_PER_DAY
    }
}

private fun remainingDoseCapacity(
    periods: List<DeviceDosingCustomPeriod>,
    pendingIndex: Int,
    maxEventsPerChannel: Int,
    includePendingPeriod: Boolean = false
): Int {
    val pendingCount = periods.getOrNull(pendingIndex)?.doseCount
        ?.takeIf { includePendingPeriod }
        ?: 0
    return maxEventsPerChannel -
        DeviceDosingCustomScheduleContract.totalDoseCount(periods) + pendingCount
}

private fun nextDefaultStartTimeMs(periods: List<DeviceDosingCustomPeriod>): Long {
    val candidate = periods.lastOrNull()?.endTimeMs
        ?.plus(DEFAULT_PERIOD_GAP_MS)
        ?: DEFAULT_START_TIME_MS
    return candidate.takeIf { timeMs ->
        timeMs + DEFAULT_PERIOD_DURATION_MS <=
            DeviceDosingCustomScheduleContract.LAST_MINUTE_START_MS
    } ?: DEFAULT_START_TIME_MS
}

@StringRes
private fun customValidationMessage(
    error: DeviceDosingCustomScheduleContract.ValidationError
): Int = when (error) {
    DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD ->
        R.string.device_dosing_custom_error_invalid_period
    DeviceDosingCustomScheduleContract.ValidationError.TOO_MANY_DOSES ->
        R.string.device_dosing_custom_error_too_many
    DeviceDosingCustomScheduleContract.ValidationError.OVERLAPPING_PERIODS ->
        R.string.device_dosing_custom_error_overlap
}

private const val STATE_PENDING_INDEX = "custom_schedule_pending_index"
private const val STATE_PENDING_START_TIME_MS = "custom_schedule_pending_start_time_ms"
private const val STATE_PENDING_END_TIME_MS = "custom_schedule_pending_end_time_ms"
private const val START_TIME_REQUEST_KEY = "custom_schedule_start_time_picker"
private const val END_TIME_REQUEST_KEY = "custom_schedule_end_time_picker"
private const val DOSE_COUNT_REQUEST_KEY = "custom_schedule_dose_count_picker"
private const val NEW_PERIOD_INDEX = -1
private const val NO_PENDING_INDEX = -2
private const val INVALID_MINUTES_OF_DAY = -1
private const val MINUTES_PER_HOUR = 60
private const val MIN_DOSE_COUNT = 1
private const val DEFAULT_DOSE_COUNT = 3
private const val DEFAULT_START_TIME_MS = 10 * 60 * 60 * 1_000L
private const val DEFAULT_END_TIME_MS = 11 * 60 * 60 * 1_000L
private const val DEFAULT_PERIOD_DURATION_MS = 60 * 60 * 1_000L
private const val DEFAULT_PERIOD_GAP_MS = 60 * 60 * 1_000L
