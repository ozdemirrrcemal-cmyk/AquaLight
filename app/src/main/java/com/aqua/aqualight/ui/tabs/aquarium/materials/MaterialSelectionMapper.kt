package com.aqua.aqualight.ui.tabs.aquarium.materials

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCatalog
import java.util.UUID

object MaterialSelectionMapper {
    fun productsForCategory(
        context: Context,
        categoryKey: String,
        currentSelections: List<AquariumMaterialSelection>
    ): List<AquariumMaterial> {
        val catalogProducts = MaterialCatalog.getByCategory(context, categoryKey)
        val catalogIds = catalogProducts.map(AquariumMaterial::id).toSet()

        val customProducts = currentSelections
            .filterNot { selection -> catalogIds.contains(selection.productId) }
            .map { selection ->
                AquariumMaterial(
                    id = selection.productId,
                    name = selection.name,
                    brand = selection.brand,
                    categoryKey = selection.categoryKey,
                    categoryTitle = selection.categoryTitle,
                    keywords = listOf(
                        selection.categoryTitle,
                        selection.name,
                        selection.brand,
                        "custom"
                    )
                )
            }

        return catalogProducts + customProducts
    }

    fun selectedMaterials(
        products: List<AquariumMaterial>,
        selectedProductIds: Set<String>,
        currentSelections: List<AquariumMaterialSelection>
    ): List<AquariumMaterialSelection> {
        return products
            .filter { product -> selectedProductIds.contains(product.id) }
            .map { product ->
                val existingSelection = currentSelections.firstOrNull { selection ->
                    selection.productId == product.id
                }

                AquariumMaterialSelection(
                    id = existingSelection?.id ?: newPositiveId(),
                    productId = product.id,
                    categoryKey = product.categoryKey,
                    categoryTitle = product.categoryTitle,
                    name = product.name,
                    brand = product.brand,
                    note = existingSelection?.note.orEmpty()
                )
            }
    }

    fun customMaterial(
        categoryKey: String,
        categoryTitle: String,
        materialName: String
    ): AquariumMaterial {
        return AquariumMaterial(
            id = newCustomProductId(categoryKey, materialName),
            name = materialName,
            brand = "",
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            keywords = listOf(categoryTitle, materialName, "custom")
        )
    }

    private fun newPositiveId(): Long {
        var candidate = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        if (candidate == 0L) candidate = 1L
        return candidate
    }

    private fun newCustomProductId(
        categoryKey: String,
        materialName: String
    ): String {
        val safeName = materialName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "custom" }
        return "custom_${categoryKey}_${safeName}_${UUID.randomUUID()}"
    }
}
