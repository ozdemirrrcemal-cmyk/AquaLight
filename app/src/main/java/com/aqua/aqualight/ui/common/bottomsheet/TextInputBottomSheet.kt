package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val inputLayout = view.findViewById<TextInputLayout>(R.id.textInputLayout)
        val input = view.findViewById<TextInputEditText>(R.id.etTextInputValue)
        val secondaryLabel = view.findViewById<android.widget.TextView>(
            R.id.tvTextInputSecondaryLabel
        )
        val secondaryValue = view.findViewById<android.widget.TextView>(
            R.id.tvTextInputSecondaryValue
        )
        val initialValue = args.getString(ARG_INITIAL_VALUE).orEmpty()
        val required = args.getBoolean(ARG_REQUIRED)
        val disableSaveWhenUnchanged = args.getBoolean(ARG_DISABLE_SAVE_WHEN_UNCHANGED)

        view.findViewById<android.widget.TextView>(R.id.tvTextInputTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<android.widget.TextView>(R.id.tvTextInputLabel).text =
            args.getString(ARG_LABEL).orEmpty()
        inputLayout.hint = args.getString(ARG_HINT).orEmpty()
        input.setText(initialValue)
        input.setSelection(input.text?.length ?: 0)
        args.getInt(ARG_MAX_LENGTH)
            .takeIf { maxLength -> maxLength > 0 }
            ?.let { maxLength ->
                input.filters = input.filters + InputFilter.LengthFilter(maxLength)
            }

        val secondaryText = args.getString(ARG_SECONDARY_VALUE).orEmpty()
        val showSecondary = secondaryText.isNotBlank()
        secondaryLabel.isVisible = showSecondary
        secondaryValue.isVisible = showSecondary
        secondaryLabel.text = args.getString(ARG_SECONDARY_LABEL).orEmpty()
        secondaryValue.text = secondaryText

        view.findViewById<MaterialButton>(R.id.btnTextInputCancel).apply {
            text = args.getString(ARG_CANCEL_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CANCELLED, "")
                dismiss()
            }
        }

        val saveButton = view.findViewById<MaterialButton>(R.id.btnTextInputSave).apply {
            text = args.getString(ARG_SAVE_TEXT).orEmpty()
            setOnClickListener {
                val value = input.text?.toString()?.trim().orEmpty()
                if (required && value.isBlank()) {
                    inputLayout.error = args.getString(ARG_REQUIRED_MESSAGE).orEmpty()
                    return@setOnClickListener
                }
                inputLayout.error = null
                publish(RESULT_SAVED, value)
                dismiss()
            }
        }

        fun updateSaveEnabled() {
            val value = input.text?.toString()?.trim().orEmpty()
            val hasRequiredValue = !required || value.isNotBlank()
            val hasChanged = !disableSaveWhenUnchanged || value != initialValue.trim()
            saveButton.isEnabled = hasRequiredValue && hasChanged
        }

        input.doAfterTextChanged {
            inputLayout.error = null
            updateSaveEnabled()
        }
        updateSaveEnabled()

        if (args.getBoolean(ARG_REQUEST_FOCUS)) {
            input.post {
                if (!isAdded) return@post
                input.requestFocus()
                dialog?.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                )
            }
        }
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

    companion object {
        const val RESULT_KEY = "text_input_result"
        const val RESULT_VALUE = "text_input_value"
        const val RESULT_PAYLOAD_ID = "text_input_payload_id"
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_LABEL = "arg_label"
        private const val ARG_HINT = "arg_hint"
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
        private const val ARG_DISABLE_SAVE_WHEN_UNCHANGED =
            "arg_disable_save_when_unchanged"
        private const val ARG_REQUEST_FOCUS = "arg_request_focus"
        private const val TAG_PREFIX = "TextInputBottomSheet:"

        @Suppress("LongParameterList")
        fun show(
            fragmentManager: FragmentManager,
            title: String,
            label: String,
            hint: String,
            initialValue: String,
            secondaryLabel: String = "",
            secondaryValue: String = "",
            saveText: String,
            cancelText: String,
            required: Boolean,
            requiredMessage: String,
            requestKey: String,
            payloadId: String = "",
            maxLength: Int = 0,
            disableSaveWhenUnchanged: Boolean = false,
            requestFocus: Boolean = false
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
            TextInputBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_LABEL to label,
                    ARG_HINT to hint,
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
                    ARG_DISABLE_SAVE_WHEN_UNCHANGED to disableSaveWhenUnchanged,
                    ARG_REQUEST_FOCUS to requestFocus
                )
            }.show(fragmentManager, tag)
        }
    }
}
