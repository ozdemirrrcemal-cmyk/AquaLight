package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

import android.os.Bundle
import android.text.InputType
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract

internal data class DeviceDosingTimerScheduleEditorHost(
    val fragment: Fragment,
    val slotId: String,
    val maxEventsPerChannel: Int,
    val doses: () -> List<DeviceDosingTimerDose>,
    val updateDoses: (List<DeviceDosingTimerDose>) -> Unit,
    val updateValidation: (Int?) -> Unit
)

/** Coordinates Timer time-and-amount entry independently from screen rendering. */
internal class DeviceDosingTimerScheduleEditor(
    private val host: DeviceDosingTimerScheduleEditorHost,
    savedInstanceState: Bundle?
) {
    private var pendingIndex = savedInstanceState?.getInt(STATE_PENDING_INDEX, NO_PENDING_INDEX)
        ?: NO_PENDING_INDEX
    private var pendingStartTimeMs = savedInstanceState?.getLong(
        STATE_PENDING_START_TIME_MS,
        DEFAULT_START_TIME_MS
    ) ?: DEFAULT_START_TIME_MS

    fun bindResults(lifecycleOwner: LifecycleOwner) {
        setupTimeResult(lifecycleOwner)
        setupAmountResult(lifecycleOwner)
    }

    fun saveState(outState: Bundle) {
        outState.putInt(STATE_PENDING_INDEX, pendingIndex)
        outState.putLong(STATE_PENDING_START_TIME_MS, pendingStartTimeMs)
    }

    fun beginAdd() {
        val doses = host.doses()
        if (doses.size >= host.maxEventsPerChannel) {
            host.updateValidation(R.string.device_dosing_timer_error_too_many)
            return
        }
        host.updateValidation(null)
        pendingIndex = NEW_DOSE_INDEX
        pendingStartTimeMs = nextDefaultTimerStartTimeMs(doses)
        showTimePicker()
    }

    fun beginEdit(index: Int) {
        val dose = host.doses().getOrNull(index) ?: return
        host.updateValidation(null)
        pendingIndex = index
        pendingStartTimeMs = dose.startTimeMs
        showTimePicker()
    }

    private fun setupTimeResult(lifecycleOwner: LifecycleOwner) {
        host.fragment.childFragmentManager.setFragmentResultListener(
            TIME_PICKER_REQUEST_KEY,
            lifecycleOwner
        ) { _, result ->
            selectedTimerMinutesOfDay(result, host.slotId)?.let { minutesOfDay ->
                pendingStartTimeMs = DeviceDosingTimerScheduleContract.startTimeMs(minutesOfDay)
                host.fragment.view?.post(::showAmountEditor)
            }
        }
    }

    private fun setupAmountResult(lifecycleOwner: LifecycleOwner) {
        host.fragment.childFragmentManager.setFragmentResultListener(
            AMOUNT_REQUEST_KEY,
            lifecycleOwner
        ) { _, result ->
            val isExpectedResult =
                result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) == host.slotId &&
                    result.getString(TextInputBottomSheet.RESULT_KEY) ==
                    TextInputBottomSheet.RESULT_SAVED
            if (isExpectedResult) {
                val amountMicroliters = DeviceDosingScheduleAmountContract.parseMicroliters(
                    result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                )
                if (amountMicroliters == null) {
                    host.updateValidation(R.string.device_dosing_timer_error_invalid_amount)
                    clearPendingDose()
                } else {
                    commitPendingDose(amountMicroliters)
                }
            }
        }
    }

    private fun showTimePicker() {
        val fragment = host.fragment
        val minutesOfDay = DeviceDosingTimerScheduleContract.minutesOfDay(pendingStartTimeMs)
        AquaTimePickerBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = fragment.getString(R.string.device_dosing_timer_time_picker_title),
                message = fragment.getString(R.string.device_dosing_timer_time_picker_message),
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                confirmText = fragment.getString(R.string.device_dosing_timer_time_picker_confirm),
                cancelText = fragment.getString(R.string.common_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = TIME_PICKER_REQUEST_KEY,
                    payloadId = host.slotId
                )
            )
        )
    }

    private fun showAmountEditor() {
        val fragment = host.fragment
        val doses = host.doses()
        val existingAmount = doses.getOrNull(pendingIndex)?.amountMicroliters
        TextInputBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_dosing_timer_amount_editor_title),
            label = fragment.getString(R.string.device_dosing_timer_amount_editor_label),
            hint = fragment.getString(R.string.device_dosing_timer_amount_editor_hint),
            initialValue = existingAmount?.let { amount ->
                DeviceDosingScheduleAmountContract.formatInput(
                    amount,
                    fragment.resources.configuration.locales[0]
                )
            }.orEmpty(),
            supportingText = fragment.getString(
                R.string.device_dosing_timer_amount_editor_description
            ),
            suffixText = fragment.getString(R.string.device_dosing_detail_ml_unit),
            saveText = fragment.getString(
                if (pendingIndex in doses.indices) {
                    R.string.device_dosing_timer_amount_editor_update
                } else {
                    R.string.device_dosing_timer_amount_editor_add
                }
            ),
            cancelText = fragment.getString(R.string.common_cancel),
            required = true,
            requiredMessage = fragment.getString(
                R.string.device_dosing_timer_error_invalid_amount
            ),
            requestKey = AMOUNT_REQUEST_KEY,
            payloadId = host.slotId,
            maxLength = AMOUNT_INPUT_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            minimumNumericValueExclusive = 0.0,
            requestFocus = true
        )
    }

    private fun commitPendingDose(amountMicroliters: Long) {
        val candidate = DeviceDosingTimerDose(
            startTimeMs = pendingStartTimeMs,
            amountMicroliters = amountMicroliters
        )
        val updated = host.doses().toMutableList().apply {
            if (pendingIndex in indices) this[pendingIndex] = candidate else add(candidate)
        }
        val error = DeviceDosingTimerScheduleContract.validate(
            doses = updated,
            maxEventsPerChannel = host.maxEventsPerChannel
        )
        if (error == null) {
            host.updateDoses(
                DeviceDosingTimerScheduleContract.normalize(
                    doses = updated,
                    maxEventsPerChannel = host.maxEventsPerChannel
                )
            )
            host.updateValidation(null)
        } else {
            host.updateValidation(timerValidationMessage(error))
        }
        clearPendingDose()
    }

    private fun clearPendingDose() {
        pendingIndex = NO_PENDING_INDEX
        pendingStartTimeMs = DEFAULT_START_TIME_MS
    }
}

private fun selectedTimerMinutesOfDay(result: Bundle, slotId: String): Int? {
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
        isExpectedResult && minutes in 0 until DeviceDosingTimerScheduleContract.MINUTES_PER_DAY
    }
}

private fun nextDefaultTimerStartTimeMs(doses: List<DeviceDosingTimerDose>): Long {
    val usedTimes = doses.mapTo(mutableSetOf(), DeviceDosingTimerDose::startTimeMs)
    val preferred = doses.lastOrNull()?.startTimeMs
        ?.plus(DEFAULT_DOSE_GAP_MS)
        ?.takeIf { timeMs -> timeMs <= DeviceDosingTimerScheduleContract.LAST_MINUTE_START_MS }
        ?.takeUnless(usedTimes::contains)
    return preferred ?: (0 until DeviceDosingTimerScheduleContract.MINUTES_PER_DAY)
        .asSequence()
        .map(DeviceDosingTimerScheduleContract::startTimeMs)
        .first { timeMs -> timeMs !in usedTimes }
}

@StringRes
private fun timerValidationMessage(
    error: DeviceDosingTimerScheduleContract.ValidationError
): Int = when (error) {
    DeviceDosingTimerScheduleContract.ValidationError.INVALID_DOSE ->
        R.string.device_dosing_timer_error_invalid_amount
    DeviceDosingTimerScheduleContract.ValidationError.TOO_MANY_DOSES ->
        R.string.device_dosing_timer_error_too_many
    DeviceDosingTimerScheduleContract.ValidationError.DUPLICATE_TIME ->
        R.string.device_dosing_timer_error_duplicate_time
    DeviceDosingTimerScheduleContract.ValidationError.TOTAL_OVERFLOW ->
        R.string.device_dosing_timer_error_total
}

private const val STATE_PENDING_INDEX = "timer_schedule_pending_index"
private const val STATE_PENDING_START_TIME_MS = "timer_schedule_pending_start_time_ms"
private const val TIME_PICKER_REQUEST_KEY = "timer_schedule_time_picker"
private const val AMOUNT_REQUEST_KEY = "timer_schedule_amount_input"
private const val NEW_DOSE_INDEX = -1
private const val NO_PENDING_INDEX = -2
private const val INVALID_MINUTES_OF_DAY = -1
private const val MINUTES_PER_HOUR = 60
private const val AMOUNT_INPUT_MAX_LENGTH = 12
private const val DEFAULT_START_TIME_MS = 10 * 60 * 60 * 1_000L
private const val DEFAULT_DOSE_GAP_MS = 60 * 60 * 1_000L
