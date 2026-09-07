package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R

object MaterialCategoryCatalog {

    val bioCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.FERTILIZER,
            titleRes = R.string.catalog_material_category_fertilizer_title,
            shortCodeRes = R.string.catalog_material_category_fertilizer_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DECORATION,
            titleRes = R.string.catalog_material_category_decoration_title,
            shortCodeRes = R.string.catalog_material_category_decoration_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.GRAVEL,
            titleRes = R.string.catalog_material_category_gravel_title,
            shortCodeRes = R.string.catalog_material_category_gravel_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.SUBSTRATE,
            titleRes = R.string.catalog_material_category_substrate_title,
            shortCodeRes = R.string.catalog_material_category_substrate_short_code
        )
    )

    val hardwareCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.AQUARIUM,
            titleRes = R.string.catalog_material_category_aquarium_title,
            shortCodeRes = R.string.catalog_material_category_aquarium_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.CO2,
            titleRes = R.string.catalog_material_category_co2_title,
            shortCodeRes = R.string.catalog_material_category_co2_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LIGHT,
            titleRes = R.string.catalog_material_category_light_title,
            shortCodeRes = R.string.catalog_material_category_light_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.FILTER,
            titleRes = R.string.catalog_material_category_filter_title,
            shortCodeRes = R.string.catalog_material_category_filter_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.HEATER,
            titleRes = R.string.catalog_material_category_heater_title,
            shortCodeRes = R.string.catalog_material_category_heater_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.COOLER,
            titleRes = R.string.catalog_material_category_cooler_title,
            shortCodeRes = R.string.catalog_material_category_cooler_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DOSING,
            titleRes = R.string.catalog_material_category_dosing_title,
            shortCodeRes = R.string.catalog_material_category_dosing_short_code
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LED_BACKGROUND,
            titleRes = R.string.catalog_material_category_led_background_title,
            shortCodeRes = R.string.catalog_material_category_led_background_short_code
        )
    )

    val allCategories: List<MaterialCategory> =
        bioCategories + hardwareCategories
}
