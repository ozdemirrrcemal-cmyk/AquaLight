package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisAddTestBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisParameterInputBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisWaterParametersBinding
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText

internal class WaterAnalysisParameterController(
    private val fragment: Fragment,
    private val binding: ItemTankHealthAnalysisWaterParametersBinding,
    private val state: WaterAnalysisDraftUiState
) {
    private var tankProfile: String? = null

    fun bindPickerResultListener() {
        fragment.childFragmentManager.setFragmentResultListener(
            WaterTestPickerBottomSheet.REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val parameterId = result
                .getString(WaterTestPickerBottomSheet.RESULT_PARAMETER_ID)
                ?.let { rawId ->
                    runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
                }
                ?: return@setFragmentResultListener
            val profile = tankProfile ?: return@setFragmentResultListener

            if (parameterId in WaterTestProfileUiCatalog.additionalIds(profile)) {
                state.additionalParameters.add(parameterId)
                render(profile)
            }
        }
    }

    fun render(profile: String?) {
        tankProfile = profile

        if (profile == null) {
            binding.profileContextCard.isVisible = false
            binding.recommendedParametersContainer.removeAllViews()
            binding.additionalTestsCard.isVisible = false
            binding.additionalParametersContainer.removeAllViews()
            return
        }

        binding.profileContextCard.isVisible = true
        binding.ivProfileIcon.setImageResource(
            WaterTestProfileUiCatalog.profileIconRes(profile)
        )
        binding.tvProfileContext.text = fragment.getString(
            R.string.tank_health_analysis_profile_context,
            AquariumTankTaxonomyText.tankTypeLabel(
                fragment.requireContext(),
                profile
            )
        )

        renderParameterContainer(
            binding.recommendedParametersContainer,
            WaterTestProfileUiCatalog.recommendedIds(profile).map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = profile,
                    id = id,
                    importance = WaterTestImportance.RECOMMENDED,
                    value = state.parameterValues[id].orEmpty()
                )
            }
        )

        val additionalOrder = WaterTestProfileUiCatalog.additionalIds(profile)
        val additionalModels = additionalOrder
            .filter(state.additionalParameters::contains)
            .map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = profile,
                    id = id,
                    importance = WaterTestImportance.ADDITIONAL,
                    value = state.parameterValues[id].orEmpty()
                )
            }
        val availableAdditional = additionalOrder
            .filterNot(state.additionalParameters::contains)

        renderAdditionalParameterContainer(
            profile,
            additionalModels,
            availableAdditional
        )
    }

    private fun renderParameterContainer(
        container: LinearLayout,
        models: List<WaterTestParameterUiModel>
    ) {
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)

        models.chunked(PARAMETERS_PER_ROW).forEachIndexed { rowIndex, rowModels ->
            val row = WaterAnalysisGridLayout.createRow(fragment.requireContext())
            rowModels.forEachIndexed { column, model ->
                addParameterCard(row, model, column, spacing)
            }
            if (rowModels.size < PARAMETERS_PER_ROW) {
                WaterAnalysisGridLayout.addSpacer(row, spacing)
            }
            container.addView(
                row,
                WaterAnalysisGridLayout.rowLayoutParams(rowIndex, rowSpacing)
            )
        }
    }

    private fun renderAdditionalParameterContainer(
        profile: String,
        models: List<WaterTestParameterUiModel>,
        availableAdditional: List<WaterTestParameterId>
    ) {
        val container = binding.additionalParametersContainer
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)
        val hasAddTile = availableAdditional.isNotEmpty()
        val itemCount = models.size + if (hasAddTile) 1 else 0

        binding.additionalTestsCard.isVisible = itemCount > 0

        (0 until itemCount)
            .toList()
            .chunked(PARAMETERS_PER_ROW)
            .forEachIndexed { rowIndex, indexes ->
                val row = WaterAnalysisGridLayout.createRow(fragment.requireContext())
                indexes.forEachIndexed { column, itemIndex ->
                    if (itemIndex < models.size) {
                        addParameterCard(row, models[itemIndex], column, spacing)
                    } else {
                        addTestTile(
                            row = row,
                            profile = profile,
                            availableAdditional = availableAdditional,
                            column = column,
                            spacing = spacing
                        )
                    }
                }
                if (indexes.size < PARAMETERS_PER_ROW) {
                    WaterAnalysisGridLayout.addSpacer(row, spacing)
                }
                container.addView(
                    row,
                    WaterAnalysisGridLayout.rowLayoutParams(rowIndex, rowSpacing)
                )
            }
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
        row.addView(
            itemBinding.root,
            WaterAnalysisGridLayout.cellLayoutParams(column, spacing)
        )
    }

    private fun addTestTile(
        row: LinearLayout,
        profile: String,
        availableAdditional: List<WaterTestParameterId>,
        column: Int,
        spacing: Int
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
                    tankProfile = profile,
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
                tankProfile = profile,
                parameterIds = availableAdditional
            )
        }
        row.addView(
            addBinding.root,
            WaterAnalysisGridLayout.cellLayoutParams(column, spacing)
        )
    }

    private fun bindParameterInput(
        itemBinding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        itemBinding.root.id = View.generateViewId()
        itemBinding.inputLayout.id = View.generateViewId()
        itemBinding.inputValue.id = View.generateViewId()
        itemBinding.inputValue.isSaveEnabled = false

        itemBinding.ivParameterIcon.setImageResource(model.iconRes)
        itemBinding.tvParameterName.setText(model.nameRes)
        itemBinding.tvParameterSymbol.isVisible = model.symbolRes != null
        model.symbolRes?.let { symbolRes ->
            itemBinding.tvParameterSymbol.setText(symbolRes)
        }
        itemBinding.inputLayout.suffixText = model.unitRes?.let(fragment::getString)
        itemBinding.inputValue.setText(model.value)
        itemBinding.inputValue.contentDescription = accessibleLabel(model)

        bindRemoveAction(itemBinding, model)
        watchParameterValue(itemBinding, model)
    }

    private fun bindRemoveAction(
        itemBinding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        val removable = model.importance == WaterTestImportance.ADDITIONAL
        itemBinding.btnRemoveParameter.isVisible = removable
        itemBinding.btnRemoveParameter.setOnClickListener(
            if (removable) {
                View.OnClickListener {
                    state.additionalParameters.remove(model.id)
                    state.parameterValues.remove(model.id)
                    render(tankProfile)
                }
            } else {
                null
            }
        )
    }

    private fun watchParameterValue(
        itemBinding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
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
                    state.parameterValues[model.id] = s?.toString().orEmpty()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun accessibleLabel(model: WaterTestParameterUiModel): String =
        buildString {
            append(fragment.getString(model.nameRes))
            model.symbolRes?.let { symbolRes ->
                append(", ")
                append(fragment.getString(symbolRes))
            }
        }

    private companion object {
        const val PARAMETERS_PER_ROW = 2
        const val ADD_TEST_EXAMPLE_LIMIT = 3
    }
}

private object WaterAnalysisGridLayout {
    fun createRow(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }

    fun rowLayoutParams(
        rowIndex: Int,
        rowSpacing: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (rowIndex > 0) topMargin = rowSpacing
        }

    fun cellLayoutParams(
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

    fun addSpacer(row: LinearLayout, spacing: Int) {
        row.addView(
            Space(row.context),
            LinearLayout.LayoutParams(
                0,
                1,
                1f
            ).apply {
                marginStart = spacing
            }
        )
    }
}
