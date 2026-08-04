package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.util.Locale

/** Process-safe integer editor for bounded values that must not accept free-form text. */
class IntegerStepperBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_integer_stepper
) {

    private var resultSent = false
    private var selectedValue = 0

    @Suppress("LongMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val minValue = minOf(args.getInt(ARG_MIN_VALUE), args.getInt(ARG_MAX_VALUE))
        val maxValue = maxOf(args.getInt(ARG_MIN_VALUE), args.getInt(ARG_MAX_VALUE))
        val step = args.getInt(ARG_STEP).coerceAtLeast(1)
        val initialValue = args.getInt(ARG_INITIAL_VALUE).coerceIn(minValue, maxValue)
        val savedValue = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_SELECTED_VALUE) }
            ?.getInt(STATE_SELECTED_VALUE)
        selectedValue = restoreIntegerStepperSelection(
            savedValue = savedValue,
            initialValue = initialValue,
            minValue = minValue,
            maxValue = maxValue
        )

        view.findViewById<TextView>(R.id.tvIntegerStepperTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvIntegerStepperHelper).apply {
            text = args.getString(ARG_HELPER_TEXT).orEmpty()
            isVisible = text.isNotBlank()
        }

        val valueText = view.findViewById<TextView>(R.id.tvIntegerStepperValue)
        val decreaseButton = view.findViewById<MaterialButton>(R.id.btnIntegerStepperDecrease)
        val increaseButton = view.findViewById<MaterialButton>(R.id.btnIntegerStepperIncrease)
        val initialSelection = initialValue
        val disableSaveWhenUnchanged = args.getBoolean(ARG_DISABLE_SAVE_WHEN_UNCHANGED)

        decreaseButton.contentDescription =
            args.getString(ARG_DECREASE_CONTENT_DESCRIPTION).orEmpty()
        increaseButton.contentDescription =
            args.getString(ARG_INCREASE_CONTENT_DESCRIPTION).orEmpty()

        view.findViewById<MaterialButton>(R.id.btnIntegerStepperCancel).apply {
            text = args.getString(ARG_CANCEL_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CANCELLED, selectedValue)
                dismiss()
            }
        }

        val saveButton = view.findViewById<MaterialButton>(R.id.btnIntegerStepperSave).apply {
            text = args.getString(ARG_SAVE_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_SAVED, selectedValue)
                dismiss()
            }
        }

        fun renderSelection() {
            valueText.text = formatValue(
                format = args.getString(ARG_VALUE_FORMAT).orEmpty(),
                value = selectedValue
            )
            valueText.contentDescription = valueText.text
            decreaseButton.isEnabled = selectedValue > minValue
            increaseButton.isEnabled = selectedValue < maxValue
            saveButton.isEnabled =
                !disableSaveWhenUnchanged || selectedValue != initialSelection
        }

        decreaseButton.setOnClickListener {
            selectedValue = (selectedValue - step).coerceAtLeast(minValue)
            renderSelection()
        }
        increaseButton.setOnClickListener {
            selectedValue = (selectedValue + step).coerceAtMost(maxValue)
            renderSelection()
        }
        renderSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_VALUE, selectedValue)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED, selectedValue)
        super.onCancel(dialog)
    }

    private fun formatValue(format: String, value: Int): String {
        if (format.isBlank()) return value.toString()
        return runCatching {
            String.format(Locale.getDefault(), format, value)
        }.getOrDefault(value.toString())
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
        const val RESULT_KEY = "integer_stepper_result"
        const val RESULT_VALUE = "integer_stepper_value"
        const val RESULT_PAYLOAD_ID = "integer_stepper_payload_id"
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_HELPER_TEXT = "arg_helper_text"
        private const val ARG_VALUE_FORMAT = "arg_value_format"
        private const val ARG_INITIAL_VALUE = "arg_initial_value"
        private const val ARG_MIN_VALUE = "arg_min_value"
        private const val ARG_MAX_VALUE = "arg_max_value"
        private const val ARG_STEP = "arg_step"
        private const val ARG_SAVE_TEXT = "arg_save_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_DECREASE_CONTENT_DESCRIPTION =
            "arg_decrease_content_description"
        private const val ARG_INCREASE_CONTENT_DESCRIPTION =
            "arg_increase_content_description"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val ARG_DISABLE_SAVE_WHEN_UNCHANGED =
            "arg_disable_save_when_unchanged"
        private const val STATE_SELECTED_VALUE = "state_selected_value"
        private const val TAG_PREFIX = "IntegerStepperBottomSheet:"

        @Suppress("LongParameterList")
        fun show(
            fragmentManager: FragmentManager,
            title: String,
            helperText: String,
            valueFormat: String,
            initialValue: Int,
            minValue: Int,
            maxValue: Int,
            step: Int,
            saveText: String,
            cancelText: String,
            decreaseContentDescription: String,
            increaseContentDescription: String,
            requestKey: String,
            payloadId: String = "",
            disableSaveWhenUnchanged: Boolean = false
        ) {
            require(minValue <= maxValue) { "Minimum value must not exceed maximum value." }
            require(step > 0) { "Step must be positive." }
            val tag = TAG_PREFIX + requestKey
            if (
                fragmentManager.findFragmentByTag(tag) != null ||
                fragmentManager.isStateSaved
            ) {
                return
            }
            IntegerStepperBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_HELPER_TEXT to helperText,
                    ARG_VALUE_FORMAT to valueFormat,
                    ARG_INITIAL_VALUE to initialValue.coerceIn(minValue, maxValue),
                    ARG_MIN_VALUE to minValue,
                    ARG_MAX_VALUE to maxValue,
                    ARG_STEP to step,
                    ARG_SAVE_TEXT to saveText,
                    ARG_CANCEL_TEXT to cancelText,
                    ARG_DECREASE_CONTENT_DESCRIPTION to decreaseContentDescription,
                    ARG_INCREASE_CONTENT_DESCRIPTION to increaseContentDescription,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId,
                    ARG_DISABLE_SAVE_WHEN_UNCHANGED to disableSaveWhenUnchanged
                )
            }.show(fragmentManager, tag)
        }
    }
}

internal fun restoreIntegerStepperSelection(
    savedValue: Int?,
    initialValue: Int,
    minValue: Int,
    maxValue: Int
): Int {
    require(minValue <= maxValue) { "Minimum value must not exceed maximum value." }
    return (savedValue ?: initialValue).coerceIn(minValue, maxValue)
}
