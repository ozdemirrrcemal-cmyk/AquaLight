package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object LedBackgroundCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "led_background_chihiros_vivid_background",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_led_background_chihiros_vivid_background_name,
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitleRes = R.string.catalog_material_category_led_background_title,
            keywordRes = listOf(
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_background,
                R.string.catalog_keyword_light,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "led_background_chihiros_shades",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_led_background_chihiros_shades_name,
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitleRes = R.string.catalog_material_category_led_background_title,
            keywordRes = listOf(
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_background,
                R.string.catalog_keyword_shade,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "led_background_twinstar_light_screen",
            brandRes = R.string.catalog_brand_twinstar,
            nameRes = R.string.catalog_material_led_background_twinstar_light_screen_name,
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitleRes = R.string.catalog_material_category_led_background_title,
            keywordRes = listOf(
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_background,
                R.string.catalog_keyword_screen,
                R.string.catalog_keyword_twinstar
            )
        ),
        AquariumMaterialDefinition(
            id = "led_background_week_aqua_led_screen",
            brandRes = R.string.catalog_brand_week_aqua,
            nameRes = R.string.catalog_material_led_background_week_aqua_led_screen_name,
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitleRes = R.string.catalog_material_category_led_background_title,
            keywordRes = listOf(
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_background,
                R.string.catalog_keyword_screen,
                R.string.catalog_keyword_week_aqua
            )
        )
    )
}
