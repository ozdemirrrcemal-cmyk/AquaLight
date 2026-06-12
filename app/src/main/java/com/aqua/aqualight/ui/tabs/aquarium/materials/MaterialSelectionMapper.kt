package com.aqua.aqualight.ui.tabs.aquarium.materials

import com.aqua.aqualight.data.aquarium.catalog.material.AquariumMaterial
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCatalog
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.util.AquariumIdGenerator

object MaterialSelectionMapper {
    fun productsForCategory(
        categoryKey: String,
        currentSelections: List<TankMaterialSelection>
    ): List<AquariumMaterial> {
        val catalogProducts = MaterialCatalog.getByCategory(categoryKey)
        val catalogIds = catalogProducts.map { product -> product.id }.toSet()

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
        currentSelections: List<TankMaterialSelection>
    ): List<TankMaterialSelection> {
        return products
            .filter { product -> selectedProductIds.contains(product.id) }
            .map { product ->
                val existingSelection = currentSelections.firstOrNull { selection ->
                    selection.productId == product.id
                }

                TankMaterialSelection(
                    id = existingSelection?.id ?: AquariumIdGenerator.newLong(),
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
            id = AquariumIdGenerator.newCustomProductId(
                categoryKey = categoryKey,
                materialName = materialName
            ),
            name = materialName,
            brand = "",
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            keywords = listOf(
                categoryTitle,
                materialName,
                "custom"
            )
        )
    }
}
