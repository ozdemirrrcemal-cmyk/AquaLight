package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import android.content.Context

object MaterialCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        FertilizerCatalog.definitions +
            DecorationCatalog.definitions +
            GravelCatalog.definitions +
            SubstrateCatalog.definitions +
            AquariumCatalog.definitions +
            Co2Catalog.definitions +
            LightCatalog.definitions +
            FilterCatalog.definitions +
            HeaterCatalog.definitions +
            CoolerCatalog.definitions +
            DosingCatalog.definitions +
            LedBackgroundCatalog.definitions

    fun getByCategory(
        context: Context,
        categoryKey: String
    ): List<AquariumMaterial> {
        return definitions.filter {
            it.categoryKey == categoryKey
        }.map { definition -> definition.resolve(context) }
    }

    fun search(
        context: Context,
        categoryKey: String,
        query: String
    ): List<AquariumMaterial> {
        val categoryProducts = getByCategory(context, categoryKey)

        if (query.isBlank()) {
            return categoryProducts
        }

        return categoryProducts.filter { product ->
            product.name.contains(query, ignoreCase = true) ||
                product.brand.contains(query, ignoreCase = true) ||
                product.categoryTitle.contains(query, ignoreCase = true) ||
                product.keywords.any {
                    it.contains(query, ignoreCase = true)
                }
        }
    }

    fun getPopularKeywords(
        context: Context,
        categoryKey: String
    ): List<String> {
        val products = definitions.filter { definition ->
            definition.categoryKey == categoryKey
        }

        return products
            .flatMap { it.keywordRes }
            .map { context.getString(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(4)
    }
}
