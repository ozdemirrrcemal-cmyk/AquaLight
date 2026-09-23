package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R

private const val LIGHT_PRODUCT_COUNT = 964
private const val LIGHT_BRAND_COUNT = 52

internal enum class LightHardwareType(
    val keywordRes: List<Int>
) {
    LIGHT(
        listOf(
            R.string.catalog_keyword_lamp,
            R.string.catalog_light_keyword_fixture
        )
    ),
    REPLACEMENT_LAMP(
        listOf(
            R.string.catalog_keyword_lamp,
            R.string.catalog_light_keyword_replacement_lamp,
            R.string.catalog_light_keyword_replacement_module,
            R.string.catalog_light_keyword_tube
        )
    ),
    SHADE(
        listOf(
            R.string.catalog_keyword_shade,
            R.string.catalog_light_keyword_light_shield,
            R.string.catalog_light_keyword_visor,
            R.string.catalog_light_keyword_mirror
        )
    ),
    DIFFUSER(
        listOf(
            R.string.catalog_light_keyword_diffuser,
            R.string.catalog_keyword_shade,
            R.string.catalog_light_keyword_light_shield
        )
    ),
    MOUNTING(
        listOf(
            R.string.catalog_light_keyword_mount,
            R.string.catalog_light_keyword_mounting,
            R.string.catalog_light_keyword_stand,
            R.string.catalog_light_keyword_hanging,
            R.string.catalog_light_keyword_hanging_kit,
            R.string.catalog_light_keyword_bracket,
            R.string.catalog_light_keyword_arm,
            R.string.catalog_light_keyword_holder,
            R.string.catalog_light_keyword_clip,
            R.string.catalog_light_keyword_leg,
            R.string.catalog_light_keyword_feet,
            R.string.catalog_light_keyword_suspension,
            R.string.catalog_light_keyword_rope,
            R.string.catalog_light_keyword_cable,
            R.string.catalog_light_keyword_gooseneck,
            R.string.catalog_light_keyword_adapter,
            R.string.catalog_light_keyword_retrofit
        )
    ),
    OPTICAL_ACCESSORY(
        listOf(
            R.string.catalog_light_keyword_optics,
            R.string.catalog_light_keyword_optical_accessory,
            R.string.catalog_light_keyword_lens
        )
    )
}

/*
 * The commercial lighting catalog is generated data, not executable business logic.
 *
 * Product rows are intentionally sharded across LightCatalogDataXX files. Keeping hundreds
 * of declarative rows inside brand functions caused Detekt LongMethod/TooManyFunctions
 * findings, while keeping all rows in one property initializer would concentrate generated
 * bytecode in a single JVM <clinit>. Sharding keeps each generated data class initializer
 * bounded without suppressing static-analysis rules.
 */
object LightCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        buildList(LIGHT_PRODUCT_COUNT) {
        addAll(lightCatalogShard01)
        addAll(lightCatalogShard02)
        addAll(lightCatalogShard03)
        addAll(lightCatalogShard04)
        addAll(lightCatalogShard05)
        addAll(lightCatalogShard06)
        addAll(lightCatalogShard07)
        addAll(lightCatalogShard08)
        addAll(lightCatalogShard09)
        addAll(lightCatalogShard10)
        addAll(lightCatalogShard11)
        addAll(lightCatalogShard12)
        addAll(lightCatalogShard13)
        addAll(lightCatalogShard14)
        addAll(lightCatalogShard15)
        addAll(lightCatalogShard16)
        addAll(lightCatalogShard17)
        }

    init {
        check(definitions.size == LIGHT_PRODUCT_COUNT)
        check(definitions.map(AquariumMaterialDefinition::id).distinct().size == definitions.size)
        check(definitions.map(AquariumMaterialDefinition::nameRes).distinct().size == definitions.size)
        check(definitions.map(AquariumMaterialDefinition::brandRes).distinct().size == LIGHT_BRAND_COUNT)
        check(definitions.all { definition -> definition.id.startsWith("light_") })
    }
}

internal fun lightDefinition(
    id: String,
    brandRes: Int,
    nameRes: Int,
    hardwareType: LightHardwareType,
    vararg extraKeywordRes: Int
): AquariumMaterialDefinition = AquariumMaterialDefinition(
    id = id,
    brandRes = brandRes,
    nameRes = nameRes,
    categoryKey = MaterialCategoryKey.LIGHT,
    categoryTitleRes = R.string.catalog_material_category_light_title,
    keywordRes = (
        listOf(
            R.string.catalog_keyword_light,
            R.string.catalog_light_keyword_lighting,
            R.string.catalog_light_keyword_aquarium_light
        ) +
            extraKeywordRes.toList() +
            hardwareType.keywordRes
        ).distinct()
)
