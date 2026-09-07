package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.annotation.StringRes

data class AquariumPlantDefinition(
    @StringRes val nameRes: Int,
    @StringRes val categoryRes: Int
) {
    fun resolve(context: Context): AquariumPlant {
        return AquariumPlant(
            name = context.getString(nameRes),
            category = context.getString(categoryRes)
        )
    }
}

data class AquariumPlant(
    val name: String,
    val category: String
)
