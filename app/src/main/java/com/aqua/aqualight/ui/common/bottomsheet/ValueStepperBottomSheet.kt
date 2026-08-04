package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/** Re-creatable bounded selector for settings that must not accept free-form input. */
class ValueStepperBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_value_stepper
) {

    private var resultSent = false
    private var currentValue: Int? = null

    @Suppress("LongMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val initialValue = args.getInt(ARG_INITIAL_VALUE)
        val restoredValue = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_CURRENT_VALUE) }
            ?.getInt(STATE_CURRENT_VALUE)
            ?: initialValue
        val state = BoundedIntStepperState(
            initialValue = restoredValue,
            minimumValue = args.getInt(ARG_MINIMUM_VALUE),
            maximumValue = args.getInt(ARG_MAXIMUM_VALUE),
            step = args.getInt(ARG_STEP)
        )
        currentValue = state.value

        val valueText = view.findViewById<TextView>(R.id.tvValueStepperValue)
        val incrementButton = view.findViewById<MaterialButton>(R.id.btnValueStepperIncrement)
        val decrementButton = view.findViewById<MaterialButton>(R.id.btnValueStepperDecrement)
        val saveButton = view.findViewById<MaterialButton>(R.id.btnValueStepperSave)
        val unitSuffix = args.getString(ARG_UNIT_SUFFIX).orEmpty()

        view.findViewById<TextView>(R.id.tvValueStepperTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        incrementButton.contentDescription =
            args.getString(ARG_INCREMENT_DESCRIPTION).orEmpty()
        decrementButton.contentDescription =
            args.getString(ARG_DECREMENT_DESCRIPTION).orEmpty()

        view.findViewById<MaterialButton>(R.id.btnValueStepperCancel).apply {
            text = args.getString(ARG_CANCEL_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CANCELLED, state.value)
                dismiss()
            }
        }
        saveButton.apply {
            text = args.getString(ARG_SAVE_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_SAVED, state.value)
                dismiss()
            }
        }

        fun render() {
            currentValue = state.value
            valueText.text = getString(
                R.string.value_stepper_value_format,
                state.value,
                unitSuffix
            )
            incrementButton.isEnabled = state.canIncrement
            decrementButton.isEnabled = state.canDecrement
            saveButton.isEnabled = state.value != initialValue
        }

        incrementButton.setOnClickListener {
            state.increment()
            render()
        }
        decrementButton.setOnClickListener {
            state.decrement()
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentValue?.let { value -> outState.putInt(STATE_CURRENT_VALUE, value) }
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(
            RESULT_CANCELLED,
            currentValue ?: requireArguments().getInt(ARG_INITIAL_VALUE)
        )
        super.onCancel(dialog)
    }

    private fun publish(result: String, value: Int) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_VALUE to value,
                RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
            )
        )
    }

    companion object {
        const val RESULT_KEY = "value_stepper_result"
        const val RESULT_VALUE = "value_stepper_value"
        const val RESULT_PAYLOAD_ID = "value_stepper_payload_id"
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_INITIAL_VALUE = "arg_initial_value"
        private const val ARG_MINIMUM_VALUE = "arg_minimum_value"
        private const val ARG_MAXIMUM_VALUE = "arg_maximum_value"
        private const val ARG_STEP = "arg_step"
        private const val ARG_UNIT_SUFFIX = "arg_unit_suffix"
        private const val ARG_SAVE_TEXT = "arg_save_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_INCREMENT_DESCRIPTION = "arg_increment_description"
        private const val ARG_DECREMENT_DESCRIPTION = "arg_decrement_description"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val STATE_CURRENT_VALUE = "state_current_value"
        private const val TAG_PREFIX = "ValueStepperBottomSheet:"

        @Suppress("LongParameterList")
        fun show(
            fragmentManager: FragmentManager,
            title: String,
            initialValue: Int,
            minimumValue: Int,
            maximumValue: Int,
            step: Int,
            unitSuffix: String,
            saveText: String,
            cancelText: String,
            incrementDescription: String,
            decrementDescription: String,
            requestKey: String,
            payloadId: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) {
                return
            }
            ValueStepperBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_INITIAL_VALUE to initialValue,
                    ARG_MINIMUM_VALUE to minimumValue,
                    ARG_MAXIMUM_VALUE to maximumValue,
                    ARG_STEP to step,
                    ARG_UNIT_SUFFIX to unitSuffix,
                    ARG_SAVE_TEXT to saveText,
                    ARG_CANCEL_TEXT to cancelText,
                    ARG_INCREMENT_DESCRIPTION to incrementDescription,
                    ARG_DECREMENT_DESCRIPTION to decrementDescription,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId
                )
            }.show(fragmentManager, tag)
        }
    }
}
