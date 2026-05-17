package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object FilterCatalog {

    private const val CATEGORY_TITLE = "Filter"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "filter_eheim_classic_150",
            brand = "Eheim",
            name = "Eheim Classic 150",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "eheim")
        ),
        AquariumMaterial(
            id = "filter_eheim_classic_250",
            brand = "Eheim",
            name = "Eheim Classic 250",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "eheim")
        ),
        AquariumMaterial(
            id = "filter_eheim_experience_250",
            brand = "Eheim",
            name = "Eheim Experience 250",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "eheim")
        ),
        AquariumMaterial(
            id = "filter_oase_biomaster_250",
            brand = "Oase",
            name = "Oase BioMaster 250",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "oase", "biomaster")
        ),
        AquariumMaterial(
            id = "filter_oase_biomaster_350",
            brand = "Oase",
            name = "Oase BioMaster 350",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "oase", "biomaster")
        ),
        AquariumMaterial(
            id = "filter_jbl_cristalprofi_e702",
            brand = "JBL",
            name = "JBL CristalProfi e702",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "jbl")
        ),
        AquariumMaterial(
            id = "filter_jbl_cristalprofi_e902",
            brand = "JBL",
            name = "JBL CristalProfi e902",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "jbl")
        ),
        AquariumMaterial(
            id = "filter_aquael_versamax",
            brand = "Aquael",
            name = "Aquael Versamax Hang-on Filter",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "hang on", "hob", "aquael")
        ),
        AquariumMaterial(
            id = "filter_sunsun_hw_603b",
            brand = "SunSun",
            name = "SunSun HW-603B External Filter",
            categoryKey = MaterialCategoryKey.FILTER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("filter", "external", "canister", "sunsun")
        )
    )
}