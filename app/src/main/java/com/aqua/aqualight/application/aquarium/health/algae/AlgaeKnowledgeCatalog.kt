package com.aqua.aqualight.application.aquarium.health.algae

data class AlgaeKnowledgeProfile(
    val id: AlgaeTypeId,
    val commonLocations: Set<AlgaeObservationLocation>,
    val factorWeights: Map<AlgaeFactorId, Int>,
    val baselineActions: List<AlgaeActionId>,
    val evidence: Set<AlgaeEvidenceId>
)

object AlgaeKnowledgeCatalog {

    const val CATALOG_REVISION: Int = 2
    const val EXPECTED_RECORD_COUNT: Int = 13

    private const val WEIGHT_CONTEXTUAL = 2
    private const val WEIGHT_MODERATE = 3
    private const val WEIGHT_STRONG = 4
    private const val WEIGHT_PRIMARY = 5

    val records: List<AlgaeKnowledgeProfile> = listOf(
        profile(
            id = AlgaeTypeId.BROWN_DIATOM,
            locations = FILM_LOCATIONS + AlgaeObservationLocation.SUBSTRATE,
            factors = mapOf(
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_PRIMARY
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.SIPHON_SUBSTRATE,
                AlgaeActionId.ALLOW_TANK_TO_MATURE,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.TROPICA_TYPES_OF_ALGAE,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW,
                AlgaeEvidenceId.TROPICA_GROWING_IN
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_SPOT,
            locations = FILM_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_STRONG,
                AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT to WEIGHT_STRONG,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.TRIM_AFFECTED_LEAVES,
                AlgaeActionId.REVIEW_LIGHT_INTENSITY,
                AlgaeActionId.REVIEW_NO3_PO4_BALANCE,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_DUST,
            locations = GLASS_AND_HARDSCAPE_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_STRONG,
                AlgaeFactorId.NITROGEN_WASTE to WEIGHT_STRONG,
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE,
                AlgaeFactorId.WARM_WATER to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.REVIEW_LIGHT_INTENSITY,
                AlgaeActionId.PERFORM_WATER_CHANGE,
                AlgaeActionId.ALLOW_TANK_TO_MATURE,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.TWO_HOUR_GREEN_DUST,
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_COAT,
            locations = GLASS_AND_HARDSCAPE_LOCATIONS +
                AlgaeObservationLocation.EQUIPMENT,
            factors = mapOf(
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_MODERATE,
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_CONTEXTUAL,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.REVIEW_LIGHT_INTENSITY,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.AQUASABI_GREEN_COATS,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        ),
        profile(
            id = AlgaeTypeId.BLACK_BEARD,
            locations = ATTACHED_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.CO2_STABILITY to WEIGHT_PRIMARY,
                AlgaeFactorId.ORGANIC_LOAD to WEIGHT_STRONG,
                AlgaeFactorId.FILTER_MAINTENANCE to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_MODERATE,
                AlgaeFactorId.FLOW_OR_OXYGENATION to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.TRIM_AFFECTED_LEAVES,
                AlgaeActionId.CLEAN_HARDSCAPE,
                AlgaeActionId.VERIFY_CO2_STABILITY,
                AlgaeActionId.SERVICE_FILTER,
                AlgaeActionId.SIPHON_SUBSTRATE,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_BLACK_BEARD,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        ),
        profile(
            id = AlgaeTypeId.STAGHORN,
            locations = ATTACHED_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.PLANT_STRESS to WEIGHT_PRIMARY,
                AlgaeFactorId.CO2_STABILITY to WEIGHT_STRONG,
                AlgaeFactorId.ORGANIC_LOAD to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.TRIM_AFFECTED_LEAVES,
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.VERIFY_CO2_STABILITY,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW,
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.FUZZ,
            locations = FILAMENTOUS_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_STRONG,
                AlgaeFactorId.CO2_STABILITY to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_MODERATE
            ),
            actions = FILAMENTOUS_BASE_ACTIONS,
            evidence = setOf(
                AlgaeEvidenceId.AQUASABI_FUZZ_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
            )
        ),
        profile(
            id = AlgaeTypeId.HAIR,
            locations = FILAMENTOUS_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.LIGHT_DURATION to WEIGHT_MODERATE,
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_MODERATE,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_STRONG,
                AlgaeFactorId.NITROGEN_WASTE to WEIGHT_MODERATE,
                AlgaeFactorId.CO2_STABILITY to WEIGHT_MODERATE
            ),
            actions = FILAMENTOUS_BASE_ACTIONS,
            evidence = setOf(
                AlgaeEvidenceId.AQUASABI_HAIR_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE,
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.THREAD,
            locations = FILAMENTOUS_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.LIGHT_DURATION to WEIGHT_STRONG,
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_MODERATE,
                AlgaeFactorId.CO2_STABILITY to WEIGHT_MODERATE,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_MODERATE,
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE
            ),
            actions = FILAMENTOUS_BASE_ACTIONS,
            evidence = setOf(
                AlgaeEvidenceId.AQUASABI_THREAD_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE,
                AlgaeEvidenceId.TROPICA_TYPES_OF_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.FLUFF,
            locations = FILAMENTOUS_LOCATIONS,
            factors = mapOf(
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_MODERATE,
                AlgaeFactorId.CO2_STABILITY to WEIGHT_CONTEXTUAL
            ),
            actions = FILAMENTOUS_BASE_ACTIONS,
            evidence = setOf(
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW,
                AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.CLADOPHORA,
            locations = setOf(
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROOT_WOOD,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.SUBSTRATE
            ),
            factors = mapOf(
                AlgaeFactorId.FLOW_OR_OXYGENATION to WEIGHT_STRONG,
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.TRIM_AFFECTED_LEAVES,
                AlgaeActionId.CLEAN_HARDSCAPE,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.TWO_HOUR_CLADOPHORA,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW,
                AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.CYANOBACTERIA,
            locations = setOf(
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.SUBSTRATE,
                AlgaeObservationLocation.EQUIPMENT
            ),
            factors = mapOf(
                AlgaeFactorId.FLOW_OR_OXYGENATION to WEIGHT_PRIMARY,
                AlgaeFactorId.ORGANIC_LOAD to WEIGHT_STRONG,
                AlgaeFactorId.LOW_NITRATE_CONTEXT to WEIGHT_MODERATE
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.SIPHON_SUBSTRATE,
                AlgaeActionId.PERFORM_WATER_CHANGE,
                AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION,
                AlgaeActionId.TEMPORARY_BLACKOUT,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_WATER,
            locations = setOf(AlgaeObservationLocation.WATER_COLUMN),
            factors = mapOf(
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_PRIMARY,
                AlgaeFactorId.LIGHT_DURATION to WEIGHT_STRONG,
                AlgaeFactorId.NITROGEN_WASTE to WEIGHT_PRIMARY,
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE
            ),
            actions = listOf(
                AlgaeActionId.REVIEW_LIGHT_DURATION,
                AlgaeActionId.REVIEW_LIGHT_INTENSITY,
                AlgaeActionId.UV_FOR_GREEN_WATER,
                AlgaeActionId.TEMPORARY_BLACKOUT,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.TROPICA_TYPES_OF_ALGAE,
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_GREEN_WATER,
                AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW
            )
        )
    )

    private val byId = records.associateBy(AlgaeKnowledgeProfile::id)

    init {
        require(records.size == EXPECTED_RECORD_COUNT)
        require(records.map(AlgaeKnowledgeProfile::id).toSet() == AlgaeTypeId.entries.toSet())
        require(byId.size == records.size)
        require(records.all { record -> record.factorWeights.isNotEmpty() })
        require(records.all { record -> record.baselineActions.isNotEmpty() })
        require(records.all { record -> record.evidence.isNotEmpty() })
        require(
            records.flatMap { record -> record.evidence }
                .all { evidenceId ->
                    runCatching {
                        AlgaeEvidenceCatalog.requireRecord(evidenceId)
                    }.isSuccess
                }
        )
    }

    fun requireProfile(id: AlgaeTypeId): AlgaeKnowledgeProfile =
        requireNotNull(byId[id]) {
            "Missing algae knowledge profile for $id"
        }

    private fun profile(
        id: AlgaeTypeId,
        locations: Set<AlgaeObservationLocation>,
        factors: Map<AlgaeFactorId, Int>,
        actions: List<AlgaeActionId>,
        evidence: Set<AlgaeEvidenceId>
    ) = AlgaeKnowledgeProfile(
        id = id,
        commonLocations = locations,
        factorWeights = factors,
        baselineActions = actions,
        evidence = evidence
    )

    private val FILM_LOCATIONS = setOf(
        AlgaeObservationLocation.FRONT_GLASS,
        AlgaeObservationLocation.BACK_GLASS,
        AlgaeObservationLocation.SIDE_GLASS,
        AlgaeObservationLocation.PLANTS,
        AlgaeObservationLocation.ROCKS
    )

    private val GLASS_AND_HARDSCAPE_LOCATIONS = setOf(
        AlgaeObservationLocation.FRONT_GLASS,
        AlgaeObservationLocation.BACK_GLASS,
        AlgaeObservationLocation.SIDE_GLASS,
        AlgaeObservationLocation.ROCKS
    )

    private val ATTACHED_LOCATIONS = setOf(
        AlgaeObservationLocation.PLANTS,
        AlgaeObservationLocation.ROOT_WOOD,
        AlgaeObservationLocation.ROCKS,
        AlgaeObservationLocation.EQUIPMENT
    )

    private val FILAMENTOUS_LOCATIONS = setOf(
        AlgaeObservationLocation.PLANTS,
        AlgaeObservationLocation.ROOT_WOOD,
        AlgaeObservationLocation.ROCKS,
        AlgaeObservationLocation.EQUIPMENT
    )

    private val FILAMENTOUS_BASE_ACTIONS = listOf(
        AlgaeActionId.MANUAL_REMOVAL,
        AlgaeActionId.TRIM_AFFECTED_LEAVES,
        AlgaeActionId.REVIEW_LIGHT_DURATION,
        AlgaeActionId.REVIEW_LIGHT_INTENSITY,
        AlgaeActionId.REVIEW_FERTILIZER_PLAN,
        AlgaeActionId.VERIFY_CO2_STABILITY,
        AlgaeActionId.RECHECK_IN_FEW_DAYS
    )
}
