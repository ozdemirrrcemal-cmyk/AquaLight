package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy

data class LivestockHealthSymptomDefinition(
    val key: String,
    val applicableCategories: Set<String>
)

object LivestockHealthSymptomCatalog {

    const val ABNORMAL_BEHAVIOR = "livestock_abnormal_behavior"
    const val FISH_SURFACE_GASPING = "fish_surface_gasping"
    const val FISH_RAPID_BREATHING = "fish_rapid_breathing"
    const val FISH_LOSS_OF_APPETITE = "fish_loss_of_appetite"
    const val FISH_HIDING = "fish_hiding"
    const val FISH_COLOR_LOSS = "fish_color_loss"
    const val FISH_ABNORMAL_SWIMMING = "fish_abnormal_swimming"
    const val FISH_VISIBLE_SPOTS = "fish_visible_spots"
    const val FISH_FIN_DAMAGE = "fish_fin_damage"

    private val allCategories = AquariumLivestockTaxonomy.categoryCodes

    val definitions: List<LivestockHealthSymptomDefinition> = listOf(
        LivestockHealthSymptomDefinition(
            key = ABNORMAL_BEHAVIOR,
            applicableCategories = allCategories
        ),
        fishSymptom(FISH_SURFACE_GASPING),
        fishSymptom(FISH_RAPID_BREATHING),
        fishSymptom(FISH_LOSS_OF_APPETITE),
        fishSymptom(FISH_HIDING),
        fishSymptom(FISH_COLOR_LOSS),
        fishSymptom(FISH_ABNORMAL_SWIMMING),
        fishSymptom(FISH_VISIBLE_SPOTS),
        fishSymptom(FISH_FIN_DAMAGE)
    )

    private val definitionsByKey =
        definitions.associateBy(LivestockHealthSymptomDefinition::key)

    init {
        check(definitionsByKey.size == definitions.size) {
            "Livestock health symptom keys must be unique."
        }
        check(definitions.all { definition ->
            definition.key.isNotBlank() &&
                definition.key == definition.key.trim() &&
                definition.applicableCategories.isNotEmpty() &&
                definition.applicableCategories.all(
                    AquariumLivestockTaxonomy.categoryCodes::contains
                )
        }) {
            "Livestock health symptom catalog contains an invalid definition."
        }
    }

    fun requireApplicable(
        symptomKey: String,
        categoryKey: String
    ): LivestockHealthSymptomDefinition {
        require(categoryKey in AquariumLivestockTaxonomy.categoryCodes) {
            "Unsupported livestock health category."
        }
        val definition = definitionsByKey[symptomKey]
            ?: throw IllegalArgumentException("Unsupported livestock health symptom.")
        require(categoryKey in definition.applicableCategories) {
            "Livestock health symptom is not applicable to the selected category."
        }
        return definition
    }

    private fun fishSymptom(key: String) =
        LivestockHealthSymptomDefinition(
            key = key,
            applicableCategories = setOf(AquariumLivestockTaxonomy.FISH)
        )
}
