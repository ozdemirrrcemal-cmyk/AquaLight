package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata

data class AquariumMaterialDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val brandRes: Int,
    val categoryKey: String,
    @StringRes val categoryTitleRes: Int,
    val keywordRes: List<Int> = emptyList(),
    val substrateMetadata: AquariumSubstrateProductMetadata? = null
) {
    fun resolve(context: Context): AquariumMaterial {
        return AquariumMaterial(
            id = id,
            name = context.getString(nameRes),
            brand = brandRes.takeIf { it != 0 }?.let { context.getString(it) }.orEmpty(),
            categoryKey = categoryKey,
            categoryTitle = context.getString(categoryTitleRes),
            keywords = keywordRes.map { context.getString(it) },
            substrateMetadata = substrateMetadata
        )
    }
}

data class AquariumMaterial(
    val id: String,
    val name: String,
    val brand: String,
    val categoryKey: String,
    val categoryTitle: String,
    val keywords: List<String> = emptyList(),
    val substrateMetadata: AquariumSubstrateProductMetadata? = null
)
