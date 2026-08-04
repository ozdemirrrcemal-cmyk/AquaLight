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
import java.math.BigDecimal

/** Re-creatable bounded value selector for settings that must not accept free-form input. */
class ValueStepperBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_value_stepper
) {

    private var resultSent = false
    private var stepperState: BoundedStepperState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val initialValue = args.getDouble(ARG_INITIAL_VALUE)
        val minimumValue = args.getDouble(ARG_MINIMUM_VALUE)
        val maximumValue = args.getDouble(ARG_MAXIMUM_VALUE)
        val step = args.getDouble(ARG_STEP)
        val restoredValue = savedInstanceState?.getDouble(STATE_CURRENT_VALUE) ?: initialValue
        val state = BoundedStepperState(
            initialValue = restoredValue,
            minimumValue = minimumValue,
            maximumValue = maximumValue,
            step = step
        ).also { stepperState = it }

        val title = view.findViewById<TextView>(R.id.tvValueStepperTitle)
        val value = view.findViewById<TextView>(R.id.tvValueStepperValue)
        val increment = view.findViewById<MaterialButton>(R.id.btnValueStepperIncrement)
        val decrement = view.findViewById<MaterialButton>(R.id.btnValueStepperDecrement)
        val cancel = view.findViewById<MaterialButton>(R.id.btnValueStepperCancel)
        val save = view.findViewById<MaterialButton>(R.id.btnValueStepperSave)
        val unitSuffix = args.getString(ARG_UNIT_SUFFIX).orEmpty()

        title.text = args.getString(ARG_TITLE).orEmpty()
        increment.contentDescription = args.getString(ARG_INCREMENT_DESCRIPTION).orEmpty()
        decrement.contentDescription = args.getString(ARG_DECREMENT_DESCRIPTION).orEmpty()
        cancel.text = args.getString(ARG_CANCEL_TEXT).orEmpty()
        save.text = args.getString(ARG_SAVE_TEXT).orEmpty()

        fun render() {
            value.text = formatValue(state.value, unitSuffix)
            increment.isEnabled = state.canIncrement
            decrement.isEnabled = state.canDecrement
            save.isEnabled = state.value != initialValue
        }

        increment.setOnClickListener {
            state.increment()
            render()
        }
        decrement.setOnClickListener {
            state.decrement()
            render()
        }
        cancel.setOnClickListener {
            publish(RESULT_CANCELLED, state.value)
            dismiss()
        }
        save.setOnClickListener {
            publish(RESULT_SAVED, state.value)
            dismiss()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        stepperState?.let { state -> outState.putDouble(STATE_CURRENT_VALUE, state.value) }
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED, stepperState?.value ?: 0.0)
        super.onCancel(dialog)
    }

    private fun publish(result: String, value: Double) {
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
            initialValue: Double,
            minimumValue: Double,
            maximumValue: Double,
            step: Double,
            unitSuffix: String,
            saveText: String,
            cancelText: String,
            incrementDescription: String,
            decrementDescription: String,
            requestKey: String,
            payloadId: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
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

        private fun formatValue(value: Double, unitSuffix: String): String =
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + unitSuffix
    }
}

internal class BoundedStepperState(
    initialValue: Double,
    private val minimumValue: Double,
    private val maximumValue: Double,
    private val step: Double
) {
    init {
        require(initialValue.isFinite()) { "initialValue must be finite." }
        require(minimumValue.isFinite()) { "minimumValue must be finite." }
        require(maximumValue.isFinite()) { "maximumValue must be finite." }
        require(step.isFinite() && step > 0.0) { "step must be finite and positive." }
        require(minimumValue <= maximumValue) { "minimumValue must not exceed maximumValue." }
    }

    var value: Double = initialValue.coerceIn(minimumValue, maximumValue)
        private set

    val canIncrement: Boolean
        get() = value < maximumValue

    val canDecrement: Boolean
        get() = value > minimumValue

    fun increment() {
        value = (value + step).coerceAtMost(maximumValue)
    }

    fun decrement() {
        value = (value - step).coerceAtLeast(minimumValue)
    }
}
