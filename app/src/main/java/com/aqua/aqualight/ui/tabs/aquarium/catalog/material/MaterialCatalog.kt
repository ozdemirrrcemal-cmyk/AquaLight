package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic

object MaterialCatalog {

    private const val POPULAR_KEYWORD_LIMIT = 4
    const val EXPECTED_SUBSTRATE_PRODUCT_COUNT = 12

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

    private val definitionsById = definitions.associateBy(AquariumMaterialDefinition::id)
    private val substrateCategoryKeys = setOf(
        MaterialCategoryKey.SUBSTRATE,
        MaterialCategoryKey.GRAVEL
    )

    init {
        check(definitionsById.size == definitions.size)

        val substrateProducts = definitions.filter { definition ->
            definition.categoryKey in substrateCategoryKeys
        }
        check(substrateProducts.size == EXPECTED_SUBSTRATE_PRODUCT_COUNT)
        check(substrateProducts.all { definition -> definition.substrateMetadata != null })
        check(
            definitions
                .filterNot { definition -> definition.categoryKey in substrateCategoryKeys }
                .none { definition -> definition.substrateMetadata != null }
        )
    }

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
            .take(POPULAR_KEYWORD_LIMIT)
    }

    /** Returns metadata only when both the stable product identity and category match. */
    fun substrateMetadata(
        productId: String,
        categoryKey: String
    ): AquariumSubstrateProductMetadata? = definitionsById[productId]
        ?.takeIf { definition -> definition.categoryKey == categoryKey }
        ?.substrateMetadata

    /** Custom and unverified substrate products fail closed as UNKNOWN. */
    fun resolveSubstrateSemantic(
        productId: String,
        categoryKey: String
    ): AquariumSubstrateSemantic {
        if (categoryKey !in substrateCategoryKeys) {
            return AquariumSubstrateSemantic.NOT_APPLICABLE
        }

        return substrateMetadata(productId, categoryKey)?.semantic
            ?: AquariumSubstrateSemantic.UNKNOWN
    }
}
