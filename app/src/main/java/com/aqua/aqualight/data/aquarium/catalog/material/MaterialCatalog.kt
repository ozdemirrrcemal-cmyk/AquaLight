package com.aqua.aqualight.data.aquarium.catalog.material


object MaterialCatalog {

    val products: List<AquariumMaterial> =
        FertilizerCatalog.products +
            DecorationCatalog.products +
            GravelCatalog.products +
            SubstrateCatalog.products +
            AquariumCatalog.products +
            Co2Catalog.products +
            LightCatalog.products +
            FilterCatalog.products +
            HeaterCatalog.products +
            CoolerCatalog.products +
            DosingCatalog.products +
            LedBackgroundCatalog.products

    fun getByCategory(
        categoryKey: String
    ): List<AquariumMaterial> {
        return products.filter {
            it.categoryKey == categoryKey
        }
    }

    fun search(
        categoryKey: String,
        query: String
    ): List<AquariumMaterial> {
        val categoryProducts = getByCategory(categoryKey)

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
        categoryKey: String
    ): List<String> {
        val products = getByCategory(categoryKey)

        return products
            .flatMap { it.keywords }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(4)
    }
}
