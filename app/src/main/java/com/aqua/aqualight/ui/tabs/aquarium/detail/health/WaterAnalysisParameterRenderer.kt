package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.LinearLayout
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
    private val state: WaterAnalysisParameterState
) {
    private var currentTankProfile: String? = null
    private val grid = WaterAnalysisParameterGrid(fragment)
    private val tileBinder = WaterAnalysisParameterTileBinder(
        fragment = fragment,
        state = state,
        onChanged = { render(currentTankProfile) }
    )

    fun render(tankProfile: String?) {
        currentTankProfile = tankProfile
        if (tankProfile == null) {
            renderMissingProfile()
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
        renderParameterContainer(
            binding.recommendedParametersContainer,
            recommendedModels(tankProfile)
        )
        renderAdditionalParameterContainer(
            tankProfile = tankProfile,
            models = additionalModels(tankProfile),
            availableAdditional = availableAdditional(tankProfile)
        )
    }

    fun clearActiveMeasurementParameter(parameterId: WaterTestParameterId): Boolean {
        val matches = state.activeMeasurementParameterId == parameterId
        if (matches) {
            state.activeMeasurementParameterId = null
            render(currentTankProfile)
        }
        return matches
    }

    private fun renderMissingProfile() {
        binding.profileContextCard.isVisible = false
        binding.recommendedParametersContainer.removeAllViews()
        binding.additionalTestsCard.isVisible = false
        binding.additionalParametersContainer.isVisible = false
        binding.additionalParametersContainer.removeAllViews()
    }

    private fun recommendedModels(tankProfile: String): List<WaterTestParameterUiModel> =
        WaterTestProfileUiCatalog.recommendedIds(tankProfile).map { id ->
            WaterTestProfileUiCatalog.model(
                tankProfile = tankProfile,
                id = id,
                importance = WaterTestImportance.RECOMMENDED,
                value = state.parameterValues[id].orEmpty()
            )
        }

    private fun additionalModels(tankProfile: String): List<WaterTestParameterUiModel> =
        WaterTestProfileUiCatalog.additionalIds(tankProfile)
            .filter(state.additionalParameters::contains)
            .map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = tankProfile,
                    id = id,
                    importance = WaterTestImportance.ADDITIONAL,
                    value = state.parameterValues[id].orEmpty()
                )
            }

    private fun availableAdditional(tankProfile: String): List<WaterTestParameterId> =
        WaterTestProfileUiCatalog.additionalIds(tankProfile)
            .filterNot(state.additionalParameters::contains)

    private fun renderParameterContainer(
        container: LinearLayout,
        models: List<WaterTestParameterUiModel>
    ) {
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)

        models.chunked(PARAMETERS_PER_ROW).forEachIndexed { rowIndex, rowModels ->
            val row = grid.createRow()
            rowModels.forEachIndexed { column, model ->
                addParameterCard(row, model, column, spacing)
            }
            if (rowModels.size < PARAMETERS_PER_ROW) {
                grid.addSpacer(row, spacing)
            }
            container.addView(row, grid.rowLayoutParams(rowIndex, rowSpacing))
        }
    }

    private fun renderAdditionalParameterContainer(
        tankProfile: String,
        models: List<WaterTestParameterUiModel>,
        availableAdditional: List<WaterTestParameterId>
    ) {
        val hasAdditionalContent =
            models.isNotEmpty() || availableAdditional.isNotEmpty()
        binding.additionalTestsCard.isVisible = hasAdditionalContent
        binding.additionalParametersContainer.isVisible = hasAdditionalContent

        val container = binding.additionalParametersContainer
        container.removeAllViews()
        val spacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_8)
        val itemCount = models.size + if (availableAdditional.isNotEmpty()) 1 else 0

        (0 until itemCount).toList().chunked(PARAMETERS_PER_ROW)
            .forEachIndexed { rowIndex, indexes ->
                val row = grid.createRow()
                indexes.forEachIndexed { column, itemIndex ->
                    if (itemIndex < models.size) {
                        addParameterCard(row, models[itemIndex], column, spacing)
                    } else {
                        val layoutParams = tileLayoutParams(
                            itemCountInRow = indexes.size,
                            column = column,
                            spacing = spacing
                        )
                        addTestTile(row, tankProfile, availableAdditional, layoutParams)
                    }
                }
                if (indexes.size < PARAMETERS_PER_ROW) {
                    grid.addSpacer(row, spacing)
                }
                container.addView(row, grid.rowLayoutParams(rowIndex, rowSpacing))
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
        tileBinder.bind(itemBinding, model)
        row.addView(itemBinding.root, grid.cellLayoutParams(column, spacing))
    }

    private fun tileLayoutParams(
        itemCountInRow: Int,
        column: Int,
        spacing: Int
    ): LinearLayout.LayoutParams =
        if (itemCountInRow == 1) {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        } else {
            grid.cellLayoutParams(column, spacing)
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

    private companion object {
        const val PARAMETERS_PER_ROW = 2
        const val ADD_TEST_EXAMPLE_LIMIT = 3
    }
}
