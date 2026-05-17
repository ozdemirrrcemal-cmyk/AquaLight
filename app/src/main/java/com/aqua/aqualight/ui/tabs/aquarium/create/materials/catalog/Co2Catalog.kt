package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object Co2Catalog {

    private const val CATEGORY_TITLE = "CO2"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "co2_chihiros_co2_regulator",
            brand = "Chihiros",
            name = "Chihiros CO2 Regulator",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "regulator", "chihiros")
        ),
        AquariumMaterial(
            id = "co2_chihiros_co2_generator",
            brand = "Chihiros",
            name = "Chihiros CO2 Generator System",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "generator", "system", "chihiros")
        ),
        AquariumMaterial(
            id = "co2_ista_professional_regulator",
            brand = "ISTA",
            name = "ISTA Professional CO2 Regulator",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "regulator", "ista")
        ),
        AquariumMaterial(
            id = "co2_ista_aluminium_cylinder",
            brand = "ISTA",
            name = "ISTA Aluminium CO2 Cylinder",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "cylinder", "bottle", "ista")
        ),
        AquariumMaterial(
            id = "co2_jbl_proflora_u504",
            brand = "JBL",
            name = "JBL ProFlora u504 CO2 Set",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "jbl", "proflora", "set")
        ),
        AquariumMaterial(
            id = "co2_dennerle_quantum",
            brand = "Dennerle",
            name = "Dennerle Quantum CO2 Regulator",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "regulator", "dennerle")
        ),
        AquariumMaterial(
            id = "co2_aquario_neo_diffuser",
            brand = "Aquario",
            name = "Aquario Neo CO2 Diffuser",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "diffuser", "neo", "aquario")
        ),
        AquariumMaterial(
            id = "co2_do_aqua_music_glass",
            brand = "Do!aqua",
            name = "Do!aqua CO2 Music Glass Diffuser",
            categoryKey = MaterialCategoryKey.CO2,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("co2", "diffuser", "glass", "doaqua")
        )
    )
}