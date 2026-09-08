package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class DeviceTimerManualControlBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_device_timer_manual_control
) {

    private var resultSent = false
    private var selectedRegime = DeviceTimerChannelRegime.ON
    private var selectedDurationMinutes = DEFAULT_DURATION_MINUTES

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        selectedRegime = savedInstanceState?.getString(STATE_REGIME)
            ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
            ?: args.getString(ARG_INITIAL_REGIME)
                ?.let { runCatching { DeviceTimerChannelRegime.valueOf(it) }.getOrNull() }
                ?.takeUnless { it == DeviceTimerChannelRegime.AUTO }
            ?: DeviceTimerChannelRegime.ON
        selectedDurationMinutes = savedInstanceState?.getInt(
            STATE_DURATION,
            DEFAULT_DURATION_MINUTES
        ) ?: DEFAULT_DURATION_MINUTES

        view.findViewById<TextView>(R.id.tvTimerManualTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvTimerManualMessage).text =
            args.getString(ARG_MESSAGE).orEmpty()
        view.findViewById<TextView>(R.id.tvTimerManualDurationLabel).text =
            args.getString(ARG_DURATION_LABEL).orEmpty()

        val onButton = view.findViewById<TextView>(R.id.btnTimerManualOn).apply {
            text = args.getString(ARG_ON_TEXT).orEmpty()
            setOnClickListener {
                selectedRegime = DeviceTimerChannelRegime.ON
                renderSelection(view)
            }
        }
        val offButton = view.findViewById<TextView>(R.id.btnTimerManualOff).apply {
            text = args.getString(ARG_OFF_TEXT).orEmpty()
            setOnClickListener {
                selectedRegime = DeviceTimerChannelRegime.OFF
                renderSelection(view)
            }
        }
        listOf(
            R.id.btnTimerDuration15 to PRESET_15_MINUTES,
            R.id.btnTimerDuration30 to PRESET_30_MINUTES,
            R.id.btnTimerDuration60 to PRESET_60_MINUTES
        ).forEach { (id, minutes) ->
            view.findViewById<TextView>(id).apply {
                text = args.getString(durationLabelArgument(minutes)).orEmpty()
                setOnClickListener {
                    selectedDurationMinutes = minutes
                    renderSelection(view)
                }
            }
        }
        view.findViewById<TextView>(R.id.btnTimerDurationCustom).apply {
            text = args.getString(ARG_CUSTOM_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CUSTOM_DURATION)
                dismiss()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnTimerManualStart).apply {
            text = args.getString(ARG_START_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_START)
                dismiss()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnTimerManualResume).apply {
            text = args.getString(ARG_RESUME_TEXT).orEmpty()
            isVisible = args.getBoolean(ARG_RESUME_VISIBLE)
            setOnClickListener {
                publish(RESULT_RESUME)
                dismiss()
            }
        }
        onButton.isFocusable = true
        offButton.isFocusable = true
        renderSelection(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_REGIME, selectedRegime.name)
        outState.putInt(STATE_DURATION, selectedDurationMinutes)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED)
        super.onCancel(dialog)
    }

    private fun renderSelection(view: View) {
        view.findViewById<TextView>(R.id.btnTimerManualOn).isSelected =
            selectedRegime == DeviceTimerChannelRegime.ON
        view.findViewById<TextView>(R.id.btnTimerManualOff).isSelected =
            selectedRegime == DeviceTimerChannelRegime.OFF
        view.findViewById<TextView>(R.id.btnTimerDuration15).isSelected =
            selectedDurationMinutes == PRESET_15_MINUTES
        view.findViewById<TextView>(R.id.btnTimerDuration30).isSelected =
            selectedDurationMinutes == PRESET_30_MINUTES
        view.findViewById<TextView>(R.id.btnTimerDuration60).isSelected =
            selectedDurationMinutes == PRESET_60_MINUTES
    }

    private fun publish(result: String) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_REGIME to selectedRegime.name,
                RESULT_DURATION_MINUTES to selectedDurationMinutes
            )
        )
    }

    private fun durationLabelArgument(minutes: Int): String = when (minutes) {
        PRESET_15_MINUTES -> ARG_15_TEXT
        PRESET_30_MINUTES -> ARG_30_TEXT
        else -> ARG_60_TEXT
    }

    companion object {
        const val RESULT_KEY = "timer_manual_control_result"
        const val RESULT_REGIME = "timer_manual_control_regime"
        const val RESULT_DURATION_MINUTES = "timer_manual_control_duration_minutes"
        const val RESULT_START = "start"
        const val RESULT_CUSTOM_DURATION = "custom_duration"
        const val RESULT_RESUME = "resume"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_DURATION_LABEL = "arg_duration_label"
        private const val ARG_ON_TEXT = "arg_on_text"
        private const val ARG_OFF_TEXT = "arg_off_text"
        private const val ARG_15_TEXT = "arg_15_text"
        private const val ARG_30_TEXT = "arg_30_text"
        private const val ARG_60_TEXT = "arg_60_text"
        private const val ARG_CUSTOM_TEXT = "arg_custom_text"
        private const val ARG_START_TEXT = "arg_start_text"
        private const val ARG_RESUME_TEXT = "arg_resume_text"
        private const val ARG_RESUME_VISIBLE = "arg_resume_visible"
        private const val ARG_INITIAL_REGIME = "arg_initial_regime"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val STATE_REGIME = "state_regime"
        private const val STATE_DURATION = "state_duration"
        private const val TAG_PREFIX = "DeviceTimerManualControlBottomSheet:"
        private const val PRESET_15_MINUTES = 15
        private const val PRESET_30_MINUTES = 30
        private const val PRESET_60_MINUTES = 60
        private const val DEFAULT_DURATION_MINUTES = PRESET_30_MINUTES

        @Suppress("LongParameterList")
        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String,
            durationLabel: String,
            onText: String,
            offText: String,
            duration15Text: String,
            duration30Text: String,
            duration60Text: String,
            customText: String,
            startText: String,
            resumeText: String,
            resumeVisible: Boolean,
            initialRegime: DeviceTimerChannelRegime,
            requestKey: String
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) {
                return
            }
            DeviceTimerManualControlBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_DURATION_LABEL to durationLabel,
                    ARG_ON_TEXT to onText,
                    ARG_OFF_TEXT to offText,
                    ARG_15_TEXT to duration15Text,
                    ARG_30_TEXT to duration30Text,
                    ARG_60_TEXT to duration60Text,
                    ARG_CUSTOM_TEXT to customText,
                    ARG_START_TEXT to startText,
                    ARG_RESUME_TEXT to resumeText,
                    ARG_RESUME_VISIBLE to resumeVisible,
                    ARG_INITIAL_REGIME to initialRegime.name,
                    ARG_REQUEST_KEY to requestKey
                )
            }.show(fragmentManager, tag)
        }
    }
}
