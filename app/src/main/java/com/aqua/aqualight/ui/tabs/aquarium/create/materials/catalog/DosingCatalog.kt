package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object DosingCatalog {

    private const val CATEGORY_TITLE = "Dosing"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "dosing_chihiros_dosing_pump",
            brand = "Chihiros",
            name = "Chihiros Dosing Pump",
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("dosing", "pump", "fertilizer", "chihiros")
        ),
        AquariumMaterial(
            id = "dosing_chihiros_dosing_system",
            brand = "Chihiros",
            name = "Chihiros Dosing System",
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("dosing", "pump", "system", "fertilizer", "chihiros")
        ),
        AquariumMaterial(
            id = "dosing_jebao_dp_4",
            brand = "Jebao",
            name = "Jebao DP-4 Dosing Pump",
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("dosing", "pump", "jebao")
        ),
        AquariumMaterial(
            id = "dosing_kamoer_x1",
            brand = "Kamoer",
            name = "Kamoer X1 Dosing Pump",
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("dosing", "pump", "kamoer")
        ),
        AquariumMaterial(
            id = "dosing_kamoer_f4",
            brand = "Kamoer",
            name = "Kamoer F4 Dosing Pump",
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("dosing", "pump", "kamoer")
        )
    )
}