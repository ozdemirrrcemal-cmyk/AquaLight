package com.aqua.aqualight.application.aquarium.health.algae

data class AlgaeKnowledgeProfile(
    val id: AlgaeTypeId,
    val commonLocations: Set<AlgaeObservationLocation>,
    val factorWeights: Map<AlgaeFactorId, Int>,
    val baselineActions: List<AlgaeActionId>,
    val evidence: Set<AlgaeEvidenceId>
)

object AlgaeKnowledgeCatalog {

    const val CATALOG_REVISION: Int = 1
    const val EXPECTED_RECORD_COUNT: Int = 9

    private const val WEIGHT_CONTEXTUAL = 2
    private const val WEIGHT_MODERATE = 3
    private const val WEIGHT_STRONG = 4
    private const val WEIGHT_PRIMARY = 5

    val records: List<AlgaeKnowledgeProfile> = listOf(
        profile(
            id = AlgaeTypeId.BROWN_DIATOM,
            locations = setOf(
                AlgaeObservationLocation.FRONT_GLASS,
                AlgaeObservationLocation.BACK_GLASS,
                AlgaeObservationLocation.SIDE_GLASS,
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.SUBSTRATE
            ),
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
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TROPICA_TYPES_OF_ALGAE,
                AlgaeEvidenceId.TROPICA_GROWING_IN
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_SPOT,
            locations = setOf(
                AlgaeObservationLocation.FRONT_GLASS,
                AlgaeObservationLocation.BACK_GLASS,
                AlgaeObservationLocation.SIDE_GLASS,
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROCKS
            ),
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
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
            )
        ),
        profile(
            id = AlgaeTypeId.BLACK_BEARD,
            locations = setOf(
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROOT_WOOD,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.EQUIPMENT
            ),
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
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
            )
        ),
        profile(
            id = AlgaeTypeId.HAIR_THREAD,
            locations = setOf(
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROOT_WOOD,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.EQUIPMENT
            ),
            factors = mapOf(
                AlgaeFactorId.LIGHT_DURATION to WEIGHT_STRONG,
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_MODERATE,
                AlgaeFactorId.NUTRIENT_IMBALANCE to WEIGHT_MODERATE,
                AlgaeFactorId.PLANT_STRESS to WEIGHT_MODERATE,
                AlgaeFactorId.NITROGEN_WASTE to WEIGHT_MODERATE,
                AlgaeFactorId.WARM_WATER to WEIGHT_CONTEXTUAL
            ),
            actions = listOf(
                AlgaeActionId.MANUAL_REMOVAL,
                AlgaeActionId.REVIEW_LIGHT_DURATION,
                AlgaeActionId.REVIEW_LIGHT_INTENSITY,
                AlgaeActionId.REVIEW_FERTILIZER_PLAN,
                AlgaeActionId.RECHECK_IN_FEW_DAYS
            ),
            evidence = setOf(
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
                AlgaeEvidenceId.TROPICA_PREVENTING_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.STAGHORN,
            locations = setOf(
                AlgaeObservationLocation.PLANTS,
                AlgaeObservationLocation.ROOT_WOOD,
                AlgaeObservationLocation.ROCKS,
                AlgaeObservationLocation.EQUIPMENT
            ),
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
                AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE
            )
        ),
        profile(
            id = AlgaeTypeId.GREEN_DUST,
            locations = setOf(
                AlgaeObservationLocation.FRONT_GLASS,
                AlgaeObservationLocation.BACK_GLASS,
                AlgaeObservationLocation.SIDE_GLASS,
                AlgaeObservationLocation.ROCKS
            ),
            factors = mapOf(
                AlgaeFactorId.LIGHT_INTENSITY to WEIGHT_STRONG,
                AlgaeFactorId.NITROGEN_WASTE to WEIGHT_STRONG,
                AlgaeFactorId.IMMATURE_TANK to WEIGHT_MODERATE,
                AlgaeFactorId.FLOW_OR_OXYGENATION to WEIGHT_CONTEXTUAL,
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
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
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
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
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
                AlgaeEvidenceId.TWO_HOUR_GREEN_WATER
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
                AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES
            )
        )
    )

    private val byId = records.associateBy(AlgaeKnowledgeProfile::id)

    init {
        require(records.size == EXPECTED_RECORD_COUNT)
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
}
