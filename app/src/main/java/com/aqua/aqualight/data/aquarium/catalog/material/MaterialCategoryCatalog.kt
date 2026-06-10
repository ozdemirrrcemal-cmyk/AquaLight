package com.aqua.aqualight.data.aquarium.catalog.material

object MaterialCategoryCatalog {

    val bioCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.FERTILIZER,
            title = "Fertilizer",
            shortCode = "Fe"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DECORATION,
            title = "Decoration",
            shortCode = "De"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.GRAVEL,
            title = "Gravel",
            shortCode = "Gr"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.SUBSTRATE,
            title = "Substrate",
            shortCode = "Su"
        )
    )

    val hardwareCategories = listOf(
        MaterialCategory(
            key = MaterialCategoryKey.AQUARIUM,
            title = "Aquarium",
            shortCode = "Aq"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.CO2,
            title = "CO2",
            shortCode = "C"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LIGHT,
            title = "Light",
            shortCode = "Li"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.FILTER,
            title = "Filter",
            shortCode = "Fi"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.HEATER,
            title = "Heater",
            shortCode = "He"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.COOLER,
            title = "Cooler",
            shortCode = "Co"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.DOSING,
            title = "Dosing",
            shortCode = "Do"
        ),
        MaterialCategory(
            key = MaterialCategoryKey.LED_BACKGROUND,
            title = "LED Background",
            shortCode = "LED"
        )
    )

    val allCategories: List<MaterialCategory> =
        bioCategories + hardwareCategories
}
