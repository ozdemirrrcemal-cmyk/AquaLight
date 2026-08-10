package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Re-creatable single text input sheet used by simple feature editors. */
class TextInputBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_text_input
) {

    private var resultSent = false
    private var presetSelected = false
    private var applyingPreset = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val views = bindContent(view, args, savedInstanceState)
        val initialValue = args.getString(ARG_INITIAL_VALUE).orEmpty()
        val required = args.getBoolean(ARG_REQUIRED)
        val disableSaveWhenUnchanged = args.getBoolean(ARG_DISABLE_SAVE_WHEN_UNCHANGED)
        val minimumNumericValueExclusive = args.getDouble(
            ARG_MINIMUM_NUMERIC_VALUE_EXCLUSIVE,
            NO_MINIMUM_NUMERIC_VALUE
        )
        val presetActionText = args.getString(ARG_PRESET_ACTION_TEXT).orEmpty()
        val presetDisplayValue = args.getString(ARG_PRESET_DISPLAY_VALUE).orEmpty()
        val presetResultValue = args.getString(ARG_PRESET_RESULT_VALUE).orEmpty()
        val showPreset = presetActionText.isNotBlank() && presetDisplayValue.isNotBlank()
        val refreshSaveEnabled = {
            updateSaveEnabled(
                views = views,
                required = required,
                disableSaveWhenUnchanged = disableSaveWhenUnchanged,
                initialValue = initialValue,
                minimumNumericValueExclusive = minimumNumericValueExclusive
            )
        }

        bindCancelAction(view, args)
        bindSaveAction(views, args, required, presetResultValue)
        bindPresetAndInputActions(
            views = views,
            showPreset = showPreset,
            presetActionText = presetActionText,
            presetDisplayValue = presetDisplayValue,
            refreshSaveEnabled = refreshSaveEnabled
        )
        refreshSaveEnabled()
        requestFocusIfNeeded(
            input = views.input,
            shouldRequestFocus = args.getBoolean(ARG_REQUEST_FOCUS)
        )
    }

    private fun bindContent(
        view: View,
        args: Bundle,
        savedInstanceState: Bundle?
    ): TextInputViews {
        val views = TextInputViews(
            inputLayout = view.findViewById(R.id.textInputLayout),
            input = view.findViewById(R.id.etTextInputValue),
            presetButton = view.findViewById(R.id.btnTextInputPreset),
            secondaryLabel = view.findViewById(R.id.tvTextInputSecondaryLabel),
            secondaryValue = view.findViewById(R.id.tvTextInputSecondaryValue),
            saveButton = view.findViewById(R.id.btnTextInputSave)
        )
        val initialValue = args.getString(ARG_INITIAL_VALUE).orEmpty()
        val presetActionText = args.getString(ARG_PRESET_ACTION_TEXT).orEmpty()
        val presetDisplayValue = args.getString(ARG_PRESET_DISPLAY_VALUE).orEmpty()
        val showPreset = presetActionText.isNotBlank() && presetDisplayValue.isNotBlank()
        presetSelected = savedInstanceState?.getBoolean(STATE_PRESET_SELECTED) == true && showPreset

        view.findViewById<TextView>(R.id.tvTextInputTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvTextInputLabel).text =
            args.getString(ARG_LABEL).orEmpty()
        views.inputLayout.hint = args.getString(ARG_HINT).orEmpty()
        views.inputLayout.helperText = args.getString(ARG_SUPPORTING_TEXT)
            ?.takeIf(String::isNotBlank)
        views.inputLayout.suffixText = args.getString(ARG_SUFFIX_TEXT)
            ?.takeIf(String::isNotBlank)
        args.getInt(ARG_INPUT_TYPE, NO_INPUT_TYPE)
            .takeUnless { inputType -> inputType == NO_INPUT_TYPE }
            ?.let { inputType -> views.input.inputType = inputType }
        views.input.setText(if (presetSelected) presetDisplayValue else initialValue)
        views.input.setSelection(views.input.text?.length ?: 0)
        args.getInt(ARG_MAX_LENGTH)
            .takeIf { maxLength -> maxLength > 0 }
            ?.let { maxLength ->
                views.input.filters = views.input.filters + InputFilter.LengthFilter(maxLength)
            }

        val secondaryText = args.getString(ARG_SECONDARY_VALUE).orEmpty()
        val showSecondary = secondaryText.isNotBlank()
        views.secondaryLabel.isVisible = showSecondary
        views.secondaryValue.isVisible = showSecondary
        views.secondaryLabel.text = args.getString(ARG_SECONDARY_LABEL).orEmpty()
        views.secondaryValue.text = secondaryText
        return views
    }

    private fun bindCancelAction(view: View, args: Bundle) {
        view.findViewById<MaterialButton>(R.id.btnTextInputCancel).apply {
            text = args.getString(ARG_CANCEL_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CANCELLED, "")
                dismiss()
            }
        }
    }

    private fun bindSaveAction(
        views: TextInputViews,
        args: Bundle,
        required: Boolean,
        presetResultValue: String
    ) {
        views.saveButton.apply {
            text = args.getString(ARG_SAVE_TEXT).orEmpty()
            setOnClickListener {
                val displayedValue = views.input.text?.toString()?.trim().orEmpty()
                if (required && displayedValue.isBlank()) {
                    views.inputLayout.error = args.getString(ARG_REQUIRED_MESSAGE).orEmpty()
                    return@setOnClickListener
                }
                views.inputLayout.error = null
                publish(
                    RESULT_SAVED,
                    resolveTextInputResultValue(
                        typedValue = displayedValue,
                        presetSelected = presetSelected,
                        presetResultValue = presetResultValue
                    )
                )
                dismiss()
            }
        }
    }

    private fun bindPresetAndInputActions(
        views: TextInputViews,
        showPreset: Boolean,
        presetActionText: String,
        presetDisplayValue: String,
        refreshSaveEnabled: () -> Unit
    ) {
        views.presetButton.apply {
            isVisible = showPreset
            text = presetActionText
            setOnClickListener {
                presetSelected = true
                applyingPreset = true
                views.input.setText(presetDisplayValue)
                views.input.setSelection(views.input.text?.length ?: 0)
                applyingPreset = false
                views.inputLayout.error = null
                refreshSaveEnabled()
            }
        }

        views.input.doAfterTextChanged { editable ->
            val displayedValue = editable?.toString()?.trim().orEmpty()
            if (!applyingPreset && presetSelected && displayedValue != presetDisplayValue) {
                presetSelected = false
            }
            views.inputLayout.error = null
            refreshSaveEnabled()
        }
    }

    private fun updateSaveEnabled(
        views: TextInputViews,
        required: Boolean,
        disableSaveWhenUnchanged: Boolean,
        initialValue: String,
        minimumNumericValueExclusive: Double
    ) {
        val displayedValue = views.input.text?.toString()?.trim().orEmpty()
        val hasValidValue = isTextInputValueValid(
            value = displayedValue,
            required = required,
            minimumNumericValueExclusive = minimumNumericValueExclusive
        )
        val hasChanged = !disableSaveWhenUnchanged ||
            presetSelected ||
            displayedValue != initialValue.trim()
        views.saveButton.isEnabled = hasValidValue && hasChanged
    }

    private fun requestFocusIfNeeded(
        input: TextInputEditText,
        shouldRequestFocus: Boolean
    ) {
        if (!shouldRequestFocus) return
        input.post {
            if (!isAdded) return@post
            input.requestFocus()
            dialog?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PRESET_SELECTED, presetSelected)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED, "")
        super.onCancel(dialog)
    }

    private fun publish(result: String, value: String) {
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

    private data class TextInputViews(
        val inputLayout: TextInputLayout,
        val input: TextInputEditText,
        val presetButton: MaterialButton,
        val secondaryLabel: TextView,
        val secondaryValue: TextView,
        val saveButton: MaterialButton
    )

    companion object {
        const val RESULT_KEY = "text_input_result"
        const val RESULT_VALUE = "text_input_value"
        const val RESULT_PAYLOAD_ID = "text_input_payload_id"
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_LABEL = "arg_label"
        private const val ARG_HINT = "arg_hint"
        private const val ARG_SUPPORTING_TEXT = "arg_supporting_text"
        private const val ARG_SUFFIX_TEXT = "arg_suffix_text"
        private const val ARG_INITIAL_VALUE = "arg_initial_value"
        private const val ARG_SECONDARY_LABEL = "arg_secondary_label"
        private const val ARG_SECONDARY_VALUE = "arg_secondary_value"
        private const val ARG_SAVE_TEXT = "arg_save_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_REQUIRED = "arg_required"
        private const val ARG_REQUIRED_MESSAGE = "arg_required_message"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val ARG_MAX_LENGTH = "arg_max_length"
        private const val ARG_INPUT_TYPE = "arg_input_type"
        private const val ARG_MINIMUM_NUMERIC_VALUE_EXCLUSIVE =
            "arg_minimum_numeric_value_exclusive"
        private const val ARG_DISABLE_SAVE_WHEN_UNCHANGED =
            "arg_disable_save_when_unchanged"
        private const val ARG_REQUEST_FOCUS = "arg_request_focus"
        private const val ARG_PRESET_ACTION_TEXT = "arg_preset_action_text"
        private const val ARG_PRESET_DISPLAY_VALUE = "arg_preset_display_value"
        private const val ARG_PRESET_RESULT_VALUE = "arg_preset_result_value"
        private const val STATE_PRESET_SELECTED = "state_preset_selected"
        private const val TAG_PREFIX = "TextInputBottomSheet:"
        private const val NO_INPUT_TYPE = -1
        private val NO_MINIMUM_NUMERIC_VALUE = Double.NaN

        @Suppress("LongParameterList")
        fun show(
            fragmentManager: FragmentManager,
            title: String,
            label: String,
            hint: String,
            initialValue: String,
            supportingText: String = "",
            suffixText: String = "",
            secondaryLabel: String = "",
            secondaryValue: String = "",
            saveText: String,
            cancelText: String,
            required: Boolean,
            requiredMessage: String,
            requestKey: String,
            payloadId: String = "",
            maxLength: Int = 0,
            inputType: Int = NO_INPUT_TYPE,
            minimumNumericValueExclusive: Double = NO_MINIMUM_NUMERIC_VALUE,
            disableSaveWhenUnchanged: Boolean = false,
            requestFocus: Boolean = false,
            presetActionText: String = "",
            presetDisplayValue: String = "",
            presetResultValue: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
            TextInputBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_LABEL to label,
                    ARG_HINT to hint,
                    ARG_SUPPORTING_TEXT to supportingText,
                    ARG_SUFFIX_TEXT to suffixText,
                    ARG_INITIAL_VALUE to initialValue,
                    ARG_SECONDARY_LABEL to secondaryLabel,
                    ARG_SECONDARY_VALUE to secondaryValue,
                    ARG_SAVE_TEXT to saveText,
                    ARG_CANCEL_TEXT to cancelText,
                    ARG_REQUIRED to required,
                    ARG_REQUIRED_MESSAGE to requiredMessage,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId,
                    ARG_MAX_LENGTH to maxLength,
                    ARG_INPUT_TYPE to inputType,
                    ARG_MINIMUM_NUMERIC_VALUE_EXCLUSIVE to minimumNumericValueExclusive,
                    ARG_DISABLE_SAVE_WHEN_UNCHANGED to disableSaveWhenUnchanged,
                    ARG_REQUEST_FOCUS to requestFocus,
                    ARG_PRESET_ACTION_TEXT to presetActionText,
                    ARG_PRESET_DISPLAY_VALUE to presetDisplayValue,
                    ARG_PRESET_RESULT_VALUE to presetResultValue
                )
            }.show(fragmentManager, tag)
        }
    }
}

internal fun resolveTextInputResultValue(
    typedValue: String,
    presetSelected: Boolean,
    presetResultValue: String
): String = if (presetSelected) presetResultValue else typedValue.trim()

internal fun isTextInputValueValid(
    value: String,
    required: Boolean,
    minimumNumericValueExclusive: Double = Double.NaN
): Boolean = when {
    required && value.isBlank() -> false
    minimumNumericValueExclusive.isNaN() -> true
    else -> value.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.let { numericValue -> numericValue > minimumNumericValueExclusive }
        ?: false
}
