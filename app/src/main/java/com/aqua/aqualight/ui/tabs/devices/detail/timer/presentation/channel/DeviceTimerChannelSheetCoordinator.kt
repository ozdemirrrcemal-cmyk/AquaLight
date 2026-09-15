package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.channel

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOperatingState
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.effectiveName

/** Owns Timer channel sheet presentation and result routing outside the Fragment lifecycle shell. */
internal class DeviceTimerChannelSheetCoordinator(
    private val fragment: Fragment,
    private val state: () -> DeviceTimerChannelDetailUiState
) {
    fun showTimedControl() {
        val current = state()
        val channel = current.channel ?: return
        if (!current.temporaryOverrideWriteEnabled || current.mutationPending) return
        DeviceTimerManualControlBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            request = manualControlRequest(
                initialRegime = if (channel.operatingState == DeviceTimerOperatingState.ON) {
                    DeviceTimerChannelRegime.OFF
                } else {
                    DeviceTimerChannelRegime.ON
                },
                resumeVisible = channel.temporaryOverrideActive
            )
        )
    }

    fun showWorkMode() {
        val current = state()
        val channel = current.channel ?: return
        if (!current.channelStateWriteEnabled || current.mutationPending) return
        SingleChoiceBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            request = SingleChoiceBottomSheet.Request(
                title = fragment.getString(R.string.device_timer_work_mode_title),
                options = listOf(
                    DeviceTimerWorkMode.MANUAL.name to
                        fragment.getString(R.string.device_timer_work_mode_manual),
                    DeviceTimerWorkMode.PROGRAM.name to
                        fragment.getString(R.string.device_timer_work_mode_program)
                ),
                selectedId = if (channel.regime == DeviceTimerChannelRegime.AUTO) {
                    DeviceTimerWorkMode.PROGRAM.name
                } else {
                    DeviceTimerWorkMode.MANUAL.name
                },
                columns = SINGLE_CHOICE_COLUMN_COUNT,
                resultTarget = SingleChoiceBottomSheet.ResultTarget(REQUEST_WORK_MODE)
            )
        )
    }

    fun showChannelName() {
        val current = state()
        val channel = current.channel ?: return
        if (!current.displayNameWriteEnabled || current.mutationPending) return
        TextInputBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            title = fragment.getString(R.string.device_timer_channel_name_title),
            label = fragment.getString(R.string.device_timer_channel_name_label),
            hint = channel.defaultName,
            initialValue = channel.effectiveName,
            supportingText = fragment.getString(R.string.device_timer_channel_name_helper),
            saveText = fragment.getString(R.string.device_timer_apply),
            cancelText = fragment.getString(R.string.device_timer_cancel),
            required = true,
            requiredMessage = fragment.getString(R.string.device_timer_channel_name_required),
            requestKey = REQUEST_CHANNEL_NAME,
            maxLength = MAX_NAME_CHARACTERS,
            disableSaveWhenUnchanged = true,
            requestFocus = true,
            presetActionText = fragment.getString(R.string.device_timer_channel_name_reset),
            presetDisplayValue = channel.defaultName,
            presetResultValue = ""
        )
    }

    fun registerResults(
        owner: LifecycleOwner,
        viewModel: DeviceTimerChannelViewModel
    ) {
        registerTimedControlResult(owner, viewModel)
        registerCustomDurationResult(owner, viewModel)
        registerWorkModeResult(owner, viewModel)
        registerChannelNameResult(owner, viewModel)
    }

    private fun showCustomDuration(regime: DeviceTimerChannelRegime) {
        IntegerStepperBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            title = fragment.getString(R.string.device_timer_manual_custom_duration_title),
            helperText = fragment.getString(R.string.device_timer_timed_duration_helper),
            valueFormat = fragment.getString(R.string.device_timer_duration_value_format),
            initialValue = DEFAULT_CUSTOM_DURATION_MINUTES,
            minValue = MIN_TEMPORARY_DURATION_MINUTES,
            maxValue = MAX_TEMPORARY_DURATION_MINUTES,
            step = TEMPORARY_DURATION_STEP_MINUTES,
            saveText = fragment.getString(R.string.device_timer_manual_start),
            cancelText = fragment.getString(R.string.device_timer_cancel),
            decreaseContentDescription = fragment.getString(
                R.string.device_timer_duration_decrease
            ),
            increaseContentDescription = fragment.getString(
                R.string.device_timer_duration_increase
            ),
            requestKey = REQUEST_CUSTOM_DURATION,
            payloadId = regime.name
        )
    }

    private fun registerTimedControlResult(
        owner: LifecycleOwner,
        viewModel: DeviceTimerChannelViewModel
    ) {
        fragment.parentFragmentManager.setFragmentResultListener(
            REQUEST_MANUAL_CONTROL,
            owner
        ) { _, result ->
            val regime = result.getString(DeviceTimerManualControlBottomSheet.RESULT_REGIME)
                ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            when (result.getString(DeviceTimerManualControlBottomSheet.RESULT_KEY)) {
                DeviceTimerManualControlBottomSheet.RESULT_START ->
                    viewModel.startTemporaryOverride(
                        regime,
                        result.getInt(
                            DeviceTimerManualControlBottomSheet.RESULT_DURATION_MINUTES
                        )
                    )
                DeviceTimerManualControlBottomSheet.RESULT_CUSTOM_DURATION ->
                    showCustomDuration(regime)
                DeviceTimerManualControlBottomSheet.RESULT_RESUME ->
                    viewModel.resumePersistentMode()
            }
        }
    }

    private fun registerCustomDurationResult(
        owner: LifecycleOwner,
        viewModel: DeviceTimerChannelViewModel
    ) {
        fragment.parentFragmentManager.setFragmentResultListener(
            REQUEST_CUSTOM_DURATION,
            owner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) !=
                IntegerStepperBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val regime = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID)
                ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            viewModel.startTemporaryOverride(
                regime,
                result.getInt(IntegerStepperBottomSheet.RESULT_VALUE)
            )
        }
    }

    private fun registerWorkModeResult(
        owner: LifecycleOwner,
        viewModel: DeviceTimerChannelViewModel
    ) {
        fragment.parentFragmentManager.setFragmentResultListener(
            REQUEST_WORK_MODE,
            owner
        ) { _, result ->
            if (result.getString(SingleChoiceBottomSheet.RESULT_KEY) !=
                SingleChoiceBottomSheet.RESULT_SELECTED
            ) return@setFragmentResultListener
            val workMode = result.getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID)
                ?.let { runCatching { DeviceTimerWorkMode.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            viewModel.setWorkMode(workMode)
        }
    }

    private fun registerChannelNameResult(
        owner: LifecycleOwner,
        viewModel: DeviceTimerChannelViewModel
    ) {
        fragment.parentFragmentManager.setFragmentResultListener(
            REQUEST_CHANNEL_NAME,
            owner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            viewModel.updateDisplayName(
                result.getString(TextInputBottomSheet.RESULT_VALUE)
                    .orEmpty()
                    .takeIf(String::isNotBlank)
            )
        }
    }

    private fun manualControlRequest(
        initialRegime: DeviceTimerChannelRegime,
        resumeVisible: Boolean
    ) = DeviceTimerManualControlRequest(
        header = DeviceTimerManualControlHeader(
            title = fragment.getString(R.string.device_timer_manual_control_title),
            message = fragment.getString(R.string.device_timer_manual_control_message),
            durationLabel = fragment.getString(R.string.device_timer_manual_duration_label)
        ),
        options = DeviceTimerManualControlOptions(
            onText = fragment.getString(R.string.device_timer_timed_on),
            offText = fragment.getString(R.string.device_timer_timed_off),
            duration15Text = fragment.getString(R.string.device_timer_duration_15_minutes),
            duration30Text = fragment.getString(R.string.device_timer_duration_30_minutes),
            duration60Text = fragment.getString(R.string.device_timer_duration_1_hour),
            customText = fragment.getString(R.string.device_timer_duration_custom)
        ),
        actions = DeviceTimerManualControlActions(
            startText = fragment.getString(R.string.device_timer_manual_start),
            resumeText = fragment.getString(R.string.device_timer_resume_program)
        ),
        resumeVisible = resumeVisible,
        initialRegime = initialRegime,
        requestKey = REQUEST_MANUAL_CONTROL
    )

    private companion object {
        const val REQUEST_MANUAL_CONTROL = "timer_manual_control"
        const val REQUEST_CUSTOM_DURATION = "timer_custom_duration"
        const val REQUEST_WORK_MODE = "timer_work_mode"
        const val REQUEST_CHANNEL_NAME = "timer_channel_name"
        const val MIN_TEMPORARY_DURATION_MINUTES = 1
        const val MAX_TEMPORARY_DURATION_MINUTES = 1_440
        const val DEFAULT_CUSTOM_DURATION_MINUTES = 30
        const val TEMPORARY_DURATION_STEP_MINUTES = 1
        const val MAX_NAME_CHARACTERS = 48
        const val SINGLE_CHOICE_COLUMN_COUNT = 1
    }
}
