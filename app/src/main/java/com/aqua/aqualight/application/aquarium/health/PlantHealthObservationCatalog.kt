package com.aqua.aqualight.application.aquarium.health

data class PlantHealthSymptomDefinition(
    val key: String,
    val requiresAlgaeType: Boolean
)

object PlantHealthSymptomCatalog {

    const val GENERAL_DECLINE = "plant_general_decline"
    const val YELLOWING = "plant_yellowing"
    const val BLACKENING = "plant_blackening"
    const val MELTING = "plant_melting"
    const val LEAF_HOLES = "plant_leaf_holes"
    const val STUNTED_GROWTH = "plant_stunted_growth"
    const val PALE_NEW_GROWTH = "plant_pale_new_growth"
    const val TWISTED_GROWTH = "plant_twisted_growth"
    const val LEAF_LOSS = "plant_leaf_loss"
    const val ROOT_DAMAGE = "plant_root_damage"
    const val ALGAE_PRESENCE = "plant_algae_presence"

    val definitions: List<PlantHealthSymptomDefinition> = listOf(
        symptom(GENERAL_DECLINE),
        symptom(YELLOWING),
        symptom(BLACKENING),
        symptom(MELTING),
        symptom(LEAF_HOLES),
        symptom(STUNTED_GROWTH),
        symptom(PALE_NEW_GROWTH),
        symptom(TWISTED_GROWTH),
        symptom(LEAF_LOSS),
        symptom(ROOT_DAMAGE),
        PlantHealthSymptomDefinition(
            key = ALGAE_PRESENCE,
            requiresAlgaeType = true
        )
    )

    private val definitionsByKey =
        definitions.associateBy(PlantHealthSymptomDefinition::key)

    init {
        check(definitionsByKey.size == definitions.size) {
            "Plant health symptom keys must be unique."
        }
        check(definitions.all { definition ->
            definition.key.isNotBlank() &&
                definition.key == definition.key.trim()
        }) {
            "Plant health symptom catalog contains an invalid key."
        }
    }

    fun requireValidSelection(
        symptomKey: String,
        algaeTypeKey: String?
    ): PlantHealthSymptomDefinition {
        val definition = definitionsByKey[symptomKey]
            ?: throw IllegalArgumentException(
                "Unsupported plant health symptom."
            )

        if (definition.requiresAlgaeType) {
            AquariumAlgaeCatalog.requireKnown(algaeTypeKey)
        } else {
            require(algaeTypeKey == null) {
                "Only algae observations may persist an algae type."
            }
        }

        return definition
    }

    private fun symptom(key: String) =
        PlantHealthSymptomDefinition(
            key = key,
            requiresAlgaeType = false
        )
}

/**
 * Stable product identities for visually reportable aquarium algae/growth problems.
 *
 * This is an observation taxonomy, not a biological taxonomy. Cyanobacteria is
 * included because users encounter and report it through the same problem flow.
 */
object AquariumAlgaeCatalog {

    const val BLACK_BEARD_ALGAE = "black_beard_algae"
    const val STAGHORN_ALGAE = "staghorn_algae"
    const val HAIR_ALGAE = "hair_algae"
    const val THREAD_ALGAE = "thread_algae"
    const val FUZZ_ALGAE = "fuzz_algae"
    const val GREEN_SPOT_ALGAE = "green_spot_algae"
    const val GREEN_DUST_ALGAE = "green_dust_algae"
    const val GREEN_WATER = "green_water"
    const val DIATOMS = "diatoms"
    const val CYANOBACTERIA = "cyanobacteria"
    const val CLADOPHORA = "cladophora"
    const val RHIZOCLONIUM = "rhizoclonium"
    const val SPIROGYRA = "spirogyra"

    val keys: Set<String> = linkedSetOf(
        BLACK_BEARD_ALGAE,
        STAGHORN_ALGAE,
        HAIR_ALGAE,
        THREAD_ALGAE,
        FUZZ_ALGAE,
        GREEN_SPOT_ALGAE,
        GREEN_DUST_ALGAE,
        GREEN_WATER,
        DIATOMS,
        CYANOBACTERIA,
        CLADOPHORA,
        RHIZOCLONIUM,
        SPIROGYRA
    )

    fun requireKnown(value: String?): String {
        val key = value.orEmpty()
        require(key in keys) {
            "Unsupported aquarium algae observation type."
        }
        return key
    }
}
