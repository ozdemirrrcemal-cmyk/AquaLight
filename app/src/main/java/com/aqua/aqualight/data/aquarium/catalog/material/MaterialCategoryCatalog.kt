package com.aqua.aqualight.data.aquarium.catalog.material

import com.aqua.aqualight.R

object MaterialCategoryCatalog {

    val bioCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.FERTILIZER,
            titleRes = R.string.catalog_category_fertilizer,
            shortCode = "Fe"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DECORATION,
            titleRes = R.string.catalog_category_decoration,
            shortCode = "De"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.GRAVEL,
            titleRes = R.string.catalog_category_gravel,
            shortCode = "Gr"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.SUBSTRATE,
            titleRes = R.string.catalog_category_substrate,
            shortCode = "Su"
        )
    )

    val hardwareCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.AQUARIUM,
            titleRes = R.string.catalog_category_aquarium,
            shortCode = "Aq"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.CO2,
            titleRes = R.string.catalog_category_co2,
            shortCode = "C"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LIGHT,
            titleRes = R.string.catalog_category_light,
            shortCode = "Li"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.FILTER,
            titleRes = R.string.catalog_category_filter,
            shortCode = "Fi"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.HEATER,
            titleRes = R.string.catalog_category_heater,
            shortCode = "He"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.COOLER,
            titleRes = R.string.catalog_category_cooler,
            shortCode = "Co"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DOSING,
            titleRes = R.string.catalog_category_dosing,
            shortCode = "Do"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LED_BACKGROUND,
            titleRes = R.string.catalog_category_led_background,
            shortCode = "LED"
        )
    )

    val allCategories: List<MaterialCategory> =
        bioCategories + hardwareCategories
}
