package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisParameterInputBinding

internal class WaterAnalysisParameterValueBinder(
    private val fragment: Fragment,
    private val state: WaterAnalysisParameterState,
    private val onChanged: () -> Unit
) {

    fun bindInput(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel,
        symbolRes: Int?
    ) {
        binding.inputValue.setText(model.value)
        binding.inputValue.error = if (state.invalidParameterId == model.id) {
            fragment.getString(R.string.tank_health_analysis_invalid_parameter_value, fragment.getString(model.nameRes))
        } else null
        renderInputStroke(binding, binding.inputValue.hasFocus(), model.id)
        binding.inputValue.setOnFocusChangeListener { _, hasFocus ->
            renderInputStroke(binding, hasFocus, model.id)
        }
        binding.inputValue.contentDescription = buildString {
            append(fragment.getString(model.nameRes))
            symbolRes?.let { resource ->
                append(", ")
                append(fragment.getString(resource))
            }
        }
        binding.inputValue.addTextChangedListener(parameterValueWatcher(model.id, binding))
    }

    fun bindRemoveAction(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        val removable = model.importance == WaterTestImportance.ADDITIONAL
        binding.btnRemoveParameter.isVisible = removable
        binding.btnRemoveParameter.contentDescription = if (removable) {
            fragment.getString(
                R.string.tank_health_analysis_remove_named_test,
                fragment.getString(model.nameRes)
            )
        } else {
            null
        }
        binding.btnRemoveParameter.setOnClickListener(
            if (removable) {
                View.OnClickListener {
                    state.additionalParameters.remove(model.id)
                    state.parameterValues.remove(model.id)
                    state.measurementSelections.remove(model.id)
                    onChanged()
                }
            } else {
                null
            }
        )
    }

    private fun renderInputStroke(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        hasFocus: Boolean,
        parameterId: WaterTestParameterId
    ) {
        val context = fragment.requireContext()
        val emphasized = state.activeMeasurementParameterId == parameterId || hasFocus
        binding.inputContainer.strokeColor = ContextCompat.getColor(
            context,
            if (emphasized) R.color.aqua_accent_primary else R.color.aqua_input_stroke_unfocused
        )
        binding.inputContainer.strokeWidth = fragment.resources.getDimensionPixelSize(
            if (emphasized) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
    }

    private fun parameterValueWatcher(
        parameterId: WaterTestParameterId,
        binding: ItemTankHealthAnalysisParameterInputBinding
    ): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                state.parameterValues[parameterId] = s?.toString().orEmpty()
                if (state.invalidParameterId == parameterId) {
                    state.invalidParameterId = null
                    binding.inputValue.error = null
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
}
