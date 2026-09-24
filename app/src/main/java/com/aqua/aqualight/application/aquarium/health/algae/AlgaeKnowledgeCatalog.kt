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
                AlgaeFactorId.IMMATURE_TANK to 5
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
                AlgaeFactorId.LIGHT_INTENSITY to 4,
                AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT to 4,
                AlgaeFactorId.NUTRIENT_IMBALANCE to 2
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
                AlgaeFactorId.CO2_STABILITY to 5,
                AlgaeFactorId.ORGANIC_LOAD to 4,
                AlgaeFactorId.FILTER_MAINTENANCE to 3,
                AlgaeFactorId.PLANT_STRESS to 3,
                AlgaeFactorId.FLOW_OR_OXYGENATION to 2
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
                AlgaeFactorId.LIGHT_DURATION to 4,
                AlgaeFactorId.LIGHT_INTENSITY to 3,
                AlgaeFactorId.NUTRIENT_IMBALANCE to 3,
                AlgaeFactorId.PLANT_STRESS to 3,
                AlgaeFactorId.NITROGEN_WASTE to 3,
                AlgaeFactorId.WARM_WATER to 2
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
                AlgaeFactorId.PLANT_STRESS to 5,
                AlgaeFactorId.CO2_STABILITY to 4,
                AlgaeFactorId.ORGANIC_LOAD to 2
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
                AlgaeFactorId.LIGHT_INTENSITY to 4,
                AlgaeFactorId.NITROGEN_WASTE to 4,
                AlgaeFactorId.IMMATURE_TANK to 3,
                AlgaeFactorId.FLOW_OR_OXYGENATION to 2,
                AlgaeFactorId.WARM_WATER to 2
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
                AlgaeFactorId.FLOW_OR_OXYGENATION to 5,
                AlgaeFactorId.ORGANIC_LOAD to 4,
                AlgaeFactorId.LOW_NITRATE_CONTEXT to 3
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
                AlgaeFactorId.LIGHT_INTENSITY to 5,
                AlgaeFactorId.LIGHT_DURATION to 4,
                AlgaeFactorId.NITROGEN_WASTE to 5,
                AlgaeFactorId.IMMATURE_TANK to 3
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
                AlgaeFactorId.FLOW_OR_OXYGENATION to 4,
                AlgaeFactorId.LIGHT_INTENSITY to 3,
                AlgaeFactorId.PLANT_STRESS to 2
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
