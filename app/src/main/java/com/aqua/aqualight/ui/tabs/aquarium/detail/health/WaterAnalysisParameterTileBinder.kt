package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisParameterInputBinding

internal class WaterAnalysisParameterTileBinder(
    private val fragment: Fragment,
    private val state: WaterAnalysisParameterState,
    private val onChanged: () -> Unit
) {
    private val valueBinder = WaterAnalysisParameterValueBinder(
        fragment = fragment,
        state = state,
        onChanged = onChanged
    )

    fun bind(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        prepareViewIds(binding)
        binding.ivParameterIcon.setImageResource(model.iconRes)
        binding.tvParameterName.setText(model.nameRes)

        val selection = resolveSelection(model.id)
        val symbolRes = resolveDisplayedSymbol(model, selection)
        bindSymbol(binding, symbolRes)
        bindMeasurementConfiguration(binding, model, selection)
        valueBinder.bindInput(binding, model, symbolRes)
        valueBinder.bindRemoveAction(binding, model)
    }

    private fun prepareViewIds(binding: ItemTankHealthAnalysisParameterInputBinding) {
        binding.root.id = View.generateViewId()
        binding.inputContainer.id = View.generateViewId()
        binding.inputValue.id = View.generateViewId()
        binding.inputValue.isSaveEnabled = false
    }

    private fun resolveSelection(
        parameterId: WaterTestParameterId
    ): WaterMeasurementSelectionUi {
        val stored = state.measurementSelections[parameterId]
        val normalized = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = parameterId,
            selection = stored ?: WaterMeasurementUiCatalog.defaultSelection(parameterId)
        )
        if (stored != null && stored != normalized) {
            state.measurementSelections[parameterId] = normalized
        }
        return normalized
    }

    private fun resolveDisplayedSymbol(
        model: WaterTestParameterUiModel,
        selection: WaterMeasurementSelectionUi
    ): Int? {
        val basisOptions = WaterMeasurementUiCatalog.basisOptions(model.id)
        return if (basisOptions.size > 1) {
            selectedOptionLabelRes(basisOptions, selection.basisId)
        } else {
            model.symbolRes
        }
    }

    private fun bindSymbol(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        symbolRes: Int?
    ) {
        binding.tvParameterSymbol.isVisible = symbolRes != null
        symbolRes?.let(binding.tvParameterSymbol::setText)
    }

    private fun bindMeasurementConfiguration(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel,
        selection: WaterMeasurementSelectionUi
    ) {
        val unitOptions = WaterMeasurementUiCatalog.unitOptions(model.id)
        val hasUnit = unitOptions.isNotEmpty()
        renderActiveState(binding, model.id)

        val openConfiguration = View.OnClickListener {
            val shown = WaterMeasurementMethodBottomSheet.show(
                fragmentManager = fragment.childFragmentManager,
                parameterId = model.id,
                selection = selection
            )
            if (shown) {
                state.activeMeasurementParameterId = model.id
                onChanged()
            }
        }

        bindUnitSelector(binding, model, selection, unitOptions, openConfiguration)
        bindUnitlessHeader(binding, model, hasUnit, openConfiguration)
    }

    private fun renderActiveState(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        parameterId: WaterTestParameterId
    ) {
        val context = fragment.requireContext()
        val active = state.activeMeasurementParameterId == parameterId
        binding.root.strokeColor = ContextCompat.getColor(
            context,
            if (active) R.color.aqua_accent_primary else R.color.aqua_card_outline_subtle
        )
        binding.root.strokeWidth = fragment.resources.getDimensionPixelSize(
            if (active) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
    }

    private fun bindUnitSelector(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel,
        selection: WaterMeasurementSelectionUi,
        unitOptions: List<WaterMeasurementOptionUi>,
        openConfiguration: View.OnClickListener
    ) {
        val hasUnit = unitOptions.isNotEmpty()
        binding.unitDivider.isVisible = hasUnit
        binding.unitSelector.isVisible = hasUnit
        binding.unitSelector.isClickable = hasUnit
        binding.unitSelector.isFocusable = hasUnit
        binding.unitSelector.contentDescription =
            configurationDescription(model).takeIf { hasUnit }

        if (hasUnit) {
            binding.unitSelector.setText(
                selectedOptionLabelRes(unitOptions, selection.unitId)
            )
            binding.unitSelector.setOnClickListener(openConfiguration)
        } else {
            binding.unitSelector.setOnClickListener(null)
        }
    }

    private fun bindUnitlessHeader(
        binding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel,
        hasUnit: Boolean,
        openConfiguration: View.OnClickListener
    ) {
        binding.parameterHeader.isClickable = !hasUnit
        binding.parameterHeader.isFocusable = !hasUnit
        binding.parameterHeader.contentDescription =
            configurationDescription(model).takeUnless { hasUnit }
        binding.parameterHeader.setOnClickListener(
            if (hasUnit) null else openConfiguration
        )
    }

    private fun configurationDescription(model: WaterTestParameterUiModel): String =
        buildString {
            append(fragment.getString(model.nameRes))
            append(". ")
            append(fragment.getString(R.string.water_measurement_open_configuration))
        }
}
