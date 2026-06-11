package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object HeaterCatalog {

    private const val CATEGORY_TITLE = "Heater"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "heater_eheim_thermocontrol_50w",
            brand = "Eheim",
            name = "Eheim Thermocontrol 50W",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "thermo", "temperature", "eheim")
        ),
        AquariumMaterial(
            id = "heater_eheim_thermocontrol_100w",
            brand = "Eheim",
            name = "Eheim Thermocontrol 100W",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "thermo", "temperature", "eheim")
        ),
        AquariumMaterial(
            id = "heater_eheim_thermocontrol_150w",
            brand = "Eheim",
            name = "Eheim Thermocontrol 150W",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "thermo", "temperature", "eheim")
        ),
        AquariumMaterial(
            id = "heater_jbl_protemp_s_50",
            brand = "JBL",
            name = "JBL ProTemp S 50",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "temperature", "jbl", "protemp")
        ),
        AquariumMaterial(
            id = "heater_jbl_protemp_s_100",
            brand = "JBL",
            name = "JBL ProTemp S 100",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "temperature", "jbl", "protemp")
        ),
        AquariumMaterial(
            id = "heater_aquael_ultra_heater_50w",
            brand = "Aquael",
            name = "Aquael Ultra Heater 50W",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "temperature", "aquael")
        ),
        AquariumMaterial(
            id = "heater_aquael_ultra_heater_100w",
            brand = "Aquael",
            name = "Aquael Ultra Heater 100W",
            categoryKey = MaterialCategoryKey.HEATER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("heater", "temperature", "aquael")
        )
    )
}