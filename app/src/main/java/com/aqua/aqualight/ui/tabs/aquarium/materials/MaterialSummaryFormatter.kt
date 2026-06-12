package com.aqua.aqualight.ui.tabs.aquarium.materials

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection

object MaterialSummaryFormatter {
    fun summaryForSelections(
        context: Context,
        selections: List<TankMaterialSelection>
    ): String {
        return summary(
            context = context,
            names = selections.map { selection -> selection.name }
        )
    }

    fun summaryForSavedMaterials(
        context: Context,
        materials: List<SavedAquariumMaterial>
    ): String {
        return summary(
            context = context,
            names = materials.map { material -> material.name }
        )
    }

    private fun summary(
        context: Context,
        names: List<String>
    ): String {
        val cleanNames = names.filter { name -> name.isNotBlank() }

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
