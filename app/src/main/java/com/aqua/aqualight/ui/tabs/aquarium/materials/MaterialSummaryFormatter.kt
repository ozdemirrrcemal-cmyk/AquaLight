package com.aqua.aqualight.ui.tabs.aquarium.materials

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection

object MaterialSummaryFormatter {
    fun summaryForSelections(
        context: Context,
        selections: List<AquariumMaterialSelection>
    ): String {
        return summary(
            context = context,
            names = selections.map(AquariumMaterialSelection::name)
        )
    }

    fun summaryForSavedMaterials(
        context: Context,
        materials: List<AquariumMaterialSelection>
    ): String {
        return summary(
            context = context,
            names = materials.map(AquariumMaterialSelection::name)
        )
    }

    private fun summary(
        context: Context,
        names: List<String>
    ): String {
        val cleanNames = names.filter(String::isNotBlank)
        return when (cleanNames.size) {
            0 -> context.getString(R.string.material_picker_not_selected)
            1 -> cleanNames.first()
            else -> context.getString(
                R.string.material_picker_more_count,
                cleanNames.first(),
                cleanNames.size - 1
            )
        }
    }
}
