package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisAddTestBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisParameterInputBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisWaterParametersBinding
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText

internal class WaterAnalysisParameterRenderer(
    private val fragment: Fragment,
    private val binding: ItemTankHealthAnalysisWaterParametersBinding,
    private val parameterValues: MutableMap<WaterTestParameterId, String>,
    private val additionalParameters: MutableSet<WaterTestParameterId>,
    private val measurementSelections:
        MutableMap<WaterTestParameterId, WaterMeasurementSelectionUi>,
    activeMeasurementParameterId: WaterTestParameterId?,
    private val onActiveMeasurementParameterChanged: (WaterTestParameterId?) -> Unit
) {
    private var currentTankProfile: String? = null
    private var activeMeasurementParameterId = activeMeasurementParameterId

    fun render(tankProfile: String?) {
        currentTankProfile = tankProfile
        if (tankProfile == null) {
            binding.profileContextCard.isVisible = false
            binding.recommendedParametersContainer.removeAllViews()
            binding.additionalTestsCard.isVisible = false
            binding.additionalParametersContainer.isVisible = false
            binding.additionalParametersContainer.removeAllViews()
            return
        }

        binding.profileContextCard.isVisible = true
        binding.ivProfileIcon.setImageResource(
            WaterTestProfileUiCatalog.profileIconRes(tankProfile)
        )
        binding.tvProfileContext.text = fragment.getString(
            R.string.tank_health_analysis_profile_context,
            AquariumTankTaxonomyText.tankTypeLabel(fragment.requireContext(), tankProfile)
        )

        val recommendedModels = WaterTestProfileUiCatalog.recommendedIds(tankProfile).map { id ->
            WaterTestProfileUiCatalog.model(
                tankProfile = tankProfile,
                id = id,
                importance = WaterTestImportance.RECOMMENDED,
                value = parameterValues[id].orEmpty()
            )
        }
        renderParameterContainer(binding.recommendedParametersContainer, recommendedModels)

        val additionalOrder = WaterTestProfileUiCatalog.additionalIds(tankProfile)
        val additionalModels = additionalOrder
            .filter(additionalParameters::contains)
            .map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = tankProfile,
                    id = id,
                    importance = WaterTestImportance.ADDITIONAL,
                    value = parameterValues[id].orEmpty()
                )
            }
        val availableAdditional = additionalOrder.filterNot(additionalParameters::contains)
        val hasAdditionalContent =
            additionalModels.isNotEmpty() || availableAdditional.isNotEmpty()

        binding.additionalTestsCard.isVisible = hasAdditionalContent
        binding.additionalParametersContainer.isVisible = hasAdditionalContent
        renderAdditionalParameterContainer(
            tankProfile = tankProfile,
            models = additionalModels,
            availableAdditional = availableAdditional
        )
    }

    fun clearActiveMeasurementParameter(parameterId: WaterTestParameterId): Boolean {
        if (activeMeasurementParameterId != parameterId) return false
        setActiveMeasurementParameter(null)
        return true
    }

    private fun setActiveMeasurementParameter(parameterId: WaterTestParameterId?) {
        if (activeMeasurementParameterId == parameterId) return
        activeMeasurementParameterId = parameterId
        onActiveMeasurementParameterChanged(parameterId)
        render(currentTankProfile)
    }

    private fun renderParameterContainer(
        container: LinearLayout,
        models: List<WaterTestParameterUiModel>
    ) {
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)

        models.chunked(PARAMETERS_PER_ROW).forEachIndexed { rowIndex, rowModels ->
            val row = createParameterRow()
            rowModels.forEachIndexed { column, model ->
                addParameterCard(row, model, column, spacing)
            }
            if (rowModels.size < PARAMETERS_PER_ROW) {
                addGridSpacer(row, spacing)
            }
            container.addView(row, parameterRowLayoutParams(rowIndex, rowSpacing))
        }
    }

    private fun renderAdditionalParameterContainer(
        tankProfile: String,
        models: List<WaterTestParameterUiModel>,
        availableAdditional: List<WaterTestParameterId>
    ) {
        val container = binding.additionalParametersContainer
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)
        val itemCount = models.size + if (availableAdditional.isNotEmpty()) 1 else 0

        (0 until itemCount).toList().chunked(PARAMETERS_PER_ROW)
            .forEachIndexed { rowIndex, indexes ->
                val row = createParameterRow()
                indexes.forEachIndexed { column, itemIndex ->
                    if (itemIndex < models.size) {
                        addParameterCard(row, models[itemIndex], column, spacing)
                    } else {
                        val tileLayoutParams = if (indexes.size == 1) {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        } else {
                            gridCellLayoutParams(column, spacing)
                        }
                        addTestTile(
                            row = row,
                            tankProfile = tankProfile,
                            availableAdditional = availableAdditional,
                            layoutParams = tileLayoutParams
                        )
                    }
                }
                if (indexes.size < PARAMETERS_PER_ROW) {
                    addGridSpacer(row, spacing)
                }
                container.addView(row, parameterRowLayoutParams(rowIndex, rowSpacing))
            }
    }

    private fun createParameterRow(): LinearLayout =
        LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }

    private fun parameterRowLayoutParams(
        rowIndex: Int,
        rowSpacing: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (rowIndex > 0) topMargin = rowSpacing
        }

    private fun gridCellLayoutParams(
        column: Int,
        spacing: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = if (column == 0) spacing else 0
            marginStart = if (column == 0) 0 else spacing
        }

    private fun addParameterCard(
        row: LinearLayout,
        model: WaterTestParameterUiModel,
        column: Int,
        spacing: Int
    ) {
        val itemBinding = ItemTankHealthAnalysisParameterInputBinding.inflate(
            fragment.layoutInflater,
            row,
            false
        )
        bindParameterInput(itemBinding, model)
        row.addView(itemBinding.root, gridCellLayoutParams(column, spacing))
    }

    private fun addTestTile(
        row: LinearLayout,
        tankProfile: String,
        availableAdditional: List<WaterTestParameterId>,
        layoutParams: LinearLayout.LayoutParams
    ) {
        val addBinding = ItemTankHealthAnalysisAddTestBinding.inflate(
            fragment.layoutInflater,
            row,
            false
        )
        val examples = availableAdditional
            .take(ADD_TEST_EXAMPLE_LIMIT)
            .map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = tankProfile,
                    id = id,
                    importance = WaterTestImportance.ADDITIONAL,
                    value = ""
                )
            }
            .joinToString(", ") { model -> fragment.getString(model.nameRes) }

        addBinding.tvAddTestExample.text = fragment.getString(
            R.string.tank_health_analysis_add_test_example_format,
            examples
        )
        addBinding.root.setOnClickListener {
            WaterTestPickerBottomSheet.show(
                fragmentManager = fragment.childFragmentManager,
                tankProfile = tankProfile,
                parameterIds = availableAdditional
            )
        }
        row.addView(addBinding.root, layoutParams)
    }

    private fun addGridSpacer(row: LinearLayout, spacing: Int) {
        row.addView(
            Space(fragment.requireContext()),
            LinearLayout.LayoutParams(0, 1, 1f).apply {
                marginStart = spacing
            }
        )
    }

    private fun bindParameterInput(
        itemBinding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        itemBinding.root.id = View.generateViewId()
        itemBinding.inputContainer.id = View.generateViewId()
        itemBinding.inputValue.id = View.generateViewId()
        itemBinding.inputValue.isSaveEnabled = false

        itemBinding.ivParameterIcon.setImageResource(model.iconRes)
        itemBinding.tvParameterName.setText(model.nameRes)

        val defaultMeasurementSelection = WaterMeasurementUiCatalog.defaultSelection(model.id)
        val storedMeasurementSelection = measurementSelections[model.id]
        val measurementSelection = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = model.id,
            selection = storedMeasurementSelection ?: defaultMeasurementSelection
        )
        if (
            storedMeasurementSelection != null &&
            storedMeasurementSelection != measurementSelection
        ) {
            measurementSelections[model.id] = measurementSelection
        }

        val basisOptions = WaterMeasurementUiCatalog.basisOptions(model.id)
        val displayedSymbolRes = if (basisOptions.size > 1) {
            WaterMeasurementUiCatalog.optionLabelRes(
                basisOptions,
                measurementSelection.basisId
            )
        } else {
            model.symbolRes
        }
        itemBinding.tvParameterSymbol.isVisible = displayedSymbolRes != null
        displayedSymbolRes?.let(itemBinding.tvParameterSymbol::setText)

        val unitOptions = WaterMeasurementUiCatalog.unitOptions(model.id)
        val hasUnit = unitOptions.isNotEmpty()
        val measurementActive = activeMeasurementParameterId == model.id
        val context = fragment.requireContext()
        val activeColor = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val defaultCardOutline = ContextCompat.getColor(
            context,
            R.color.aqua_card_outline_subtle
        )

        itemBinding.root.strokeColor = if (measurementActive) {
            activeColor
        } else {
            defaultCardOutline
        }
        itemBinding.root.strokeWidth = fragment.resources.getDimensionPixelSize(
            if (measurementActive) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )

        val openMeasurementConfiguration = View.OnClickListener {
            val shown = WaterMeasurementMethodBottomSheet.show(
                fragmentManager = fragment.childFragmentManager,
                parameterId = model.id,
                selection = measurementSelection
            )
            if (shown) {
                setActiveMeasurementParameter(model.id)
            }
        }

        itemBinding.unitDivider.isVisible = hasUnit
        itemBinding.unitSelector.isVisible = hasUnit
        itemBinding.unitSelector.isClickable = hasUnit
        itemBinding.unitSelector.isFocusable = hasUnit
        itemBinding.unitSelector.contentDescription = if (hasUnit) {
            buildString {
                append(fragment.getString(model.nameRes))
                append(". ")
                append(fragment.getString(R.string.water_measurement_open_configuration))
            }
        } else {
            null
        }
        if (hasUnit) {
            itemBinding.tvParameterUnit.setText(
                WaterMeasurementUiCatalog.optionLabelRes(
                    unitOptions,
                    measurementSelection.unitId
                )
            )
            itemBinding.unitSelector.setOnClickListener(openMeasurementConfiguration)
        } else {
            itemBinding.unitSelector.setOnClickListener(null)
        }

        itemBinding.parameterHeader.isClickable = !hasUnit
        itemBinding.parameterHeader.isFocusable = !hasUnit
        itemBinding.parameterHeader.contentDescription = if (!hasUnit) {
            buildString {
                append(fragment.getString(model.nameRes))
                append(". ")
                append(fragment.getString(R.string.water_measurement_open_configuration))
            }
        } else {
            null
        }
        itemBinding.parameterHeader.setOnClickListener(
            if (hasUnit) null else openMeasurementConfiguration
        )

        itemBinding.inputValue.setText(model.value)
        val renderInputStroke: (Boolean) -> Unit = { hasFocus ->
            val emphasized = measurementActive || hasFocus
            itemBinding.inputContainer.strokeColor = if (emphasized) {
                activeColor
            } else {
                ContextCompat.getColor(context, R.color.aqua_input_stroke_unfocused)
            }
            itemBinding.inputContainer.strokeWidth = fragment.resources.getDimensionPixelSize(
                if (emphasized) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
            )
        }
        renderInputStroke(itemBinding.inputValue.hasFocus())
        itemBinding.inputValue.setOnFocusChangeListener { _, hasFocus ->
            renderInputStroke(hasFocus)
        }

        itemBinding.inputValue.contentDescription = buildString {
            append(fragment.getString(model.nameRes))
            displayedSymbolRes?.let { symbolRes ->
                append(", ")
                append(fragment.getString(symbolRes))
            }
        }

        val removable = model.importance == WaterTestImportance.ADDITIONAL
        itemBinding.btnRemoveParameter.isVisible = removable
        itemBinding.btnRemoveParameter.setOnClickListener(
            if (removable) {
                View.OnClickListener {
                    additionalParameters.remove(model.id)
                    parameterValues.remove(model.id)
                    measurementSelections.remove(model.id)
                    render(currentTankProfile)
                }
            } else {
                null
            }
        )

        itemBinding.inputValue.addTextChangedListener(
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
                    parameterValues[model.id] = s?.toString().orEmpty()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private companion object {
        const val PARAMETERS_PER_ROW = 2
        const val ADD_TEST_EXAMPLE_LIMIT = 3
    }
}
