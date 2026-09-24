package com.aqua.aqualight.application.aquarium.health.algae

object AlgaeAnalysisEngine {

    private const val LONG_LIGHT_MINUTES = 9 * 60
    private const val HIGH_LIGHT_PERCENT = 80
    private const val STARTUP_TANK_DAYS = 60
    private const val WATER_CHANGE_REVIEW_DAYS = 10
    private const val FILTER_REVIEW_DAYS = 28
    private const val LOW_PLANT_COUNT = 5
    private const val WARM_WATER_C = 26.0

    fun analyze(
        observation: AlgaeObservationInput,
        context: AlgaeTankContext
    ): AlgaeAnalysisResult {
        val profile = AlgaeKnowledgeCatalog.requireProfile(observation.algaeType)
        val factorScores = mutableMapOf<AlgaeFactorId, Int>()
        val missingData = linkedSetOf<AlgaeMissingData>()

        evaluateLighting(profile, context, factorScores, missingData)
        evaluateCo2(profile, context, factorScores, missingData)
        evaluateMaintenance(profile, context, factorScores, missingData)
        evaluateWater(profile, context, factorScores, missingData)
        evaluateTankMaturity(profile, context, factorScores)
        evaluatePlantMass(profile, context, factorScores)
        evaluateTemperature(profile, context, factorScores)
        evaluateLocation(observation, profile, factorScores)

        val factors = factorScores
            .map { (factor, score) ->
                AlgaeFactorAssessment(
                    factor = factor,
                    strength = strengthFor(score),
                    score = score
                )
            }
            .sortedByDescending(AlgaeFactorAssessment::score)

        val actions = buildActions(
            observation = observation,
            profile = profile,
            factors = factors,
            missingData = missingData
        )

        return AlgaeAnalysisResult(
            algaeType = observation.algaeType,
            priority = priorityFor(observation, factors),
            factors = factors,
            actions = actions,
            missingData = missingData
        )
    }

    private fun evaluateLighting(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val durationWeight = profile.factorWeights[AlgaeFactorId.LIGHT_DURATION]
        val intensityWeight = profile.factorWeights[AlgaeFactorId.LIGHT_INTENSITY]

        if (durationWeight != null) {
            val duration = context.lightDurationMinutes
            if (duration == null) {
                missingData += AlgaeMissingData.LIGHT_SCHEDULE
            } else if (duration >= LONG_LIGHT_MINUTES) {
                factorScores[AlgaeFactorId.LIGHT_DURATION] = durationWeight
            }
        }

        if (
            intensityWeight != null &&
            context.lightIntensityPercent != null &&
            context.lightIntensityPercent >= HIGH_LIGHT_PERCENT
        ) {
            factorScores[AlgaeFactorId.LIGHT_INTENSITY] = intensityWeight
        }
    }

    private fun evaluateCo2(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val weight = profile.factorWeights[AlgaeFactorId.CO2_STABILITY] ?: return
        if (context.hasCo2 != true) {
            return
        }
        if (!context.co2ScheduleKnown) {
            missingData += AlgaeMissingData.CO2_SCHEDULE
            return
        }

        val leadMinutes = context.co2LeadMinutesBeforeLight
        if (leadMinutes != null && leadMinutes < 60) {
            factorScores[AlgaeFactorId.CO2_STABILITY] = weight
        }
    }

    private fun evaluateMaintenance(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val needsMaintenanceData = profile.factorWeights.keys.any { factor ->
            factor == AlgaeFactorId.ORGANIC_LOAD ||
                factor == AlgaeFactorId.FILTER_MAINTENANCE ||
                factor == AlgaeFactorId.WATER_CHANGE_INTERVAL
        }

        if (
            needsMaintenanceData &&
            context.daysSinceWaterChange == null &&
            context.daysSinceFilterMaintenance == null
        ) {
            missingData += AlgaeMissingData.MAINTENANCE_HISTORY
        }

        val daysSinceWaterChange = context.daysSinceWaterChange
        if (daysSinceWaterChange != null && daysSinceWaterChange > WATER_CHANGE_REVIEW_DAYS) {
            profile.factorWeights[AlgaeFactorId.WATER_CHANGE_INTERVAL]?.let { weight ->
                factorScores[AlgaeFactorId.WATER_CHANGE_INTERVAL] = weight
            }
            profile.factorWeights[AlgaeFactorId.ORGANIC_LOAD]?.let { weight ->
                factorScores[AlgaeFactorId.ORGANIC_LOAD] = maxOf(
                    factorScores[AlgaeFactorId.ORGANIC_LOAD] ?: 0,
                    weight
                )
            }
        }

        val daysSinceFilterMaintenance = context.daysSinceFilterMaintenance
        if (daysSinceFilterMaintenance != null && daysSinceFilterMaintenance > FILTER_REVIEW_DAYS) {
            profile.factorWeights[AlgaeFactorId.FILTER_MAINTENANCE]?.let { weight ->
                factorScores[AlgaeFactorId.FILTER_MAINTENANCE] = weight
            }
            profile.factorWeights[AlgaeFactorId.ORGANIC_LOAD]?.let { weight ->
                factorScores[AlgaeFactorId.ORGANIC_LOAD] = maxOf(
                    factorScores[AlgaeFactorId.ORGANIC_LOAD] ?: 0,
                    weight
                )
            }
        }
    }

    private fun evaluateWater(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val water = context.waterQuality
        val hasAnyWaterData = listOf(
            water.nitrateState,
            water.phosphateState,
            water.nitriteState,
            water.ammoniaState
        ).any { state -> state != WaterParameterState.UNKNOWN }

        if (!hasAnyWaterData) {
            missingData += AlgaeMissingData.WATER_ANALYSIS
        }

        if (
            water.ammoniaState == WaterParameterState.ELEVATED ||
            water.ammoniaState == WaterParameterState.HIGH ||
            water.nitriteState == WaterParameterState.ELEVATED ||
            water.nitriteState == WaterParameterState.HIGH
        ) {
            profile.factorWeights[AlgaeFactorId.NITROGEN_WASTE]?.let { weight ->
                factorScores[AlgaeFactorId.NITROGEN_WASTE] = weight
            }
        }

        if (water.phosphateState == WaterParameterState.LOW) {
            profile.factorWeights[AlgaeFactorId.LOW_PHOSPHATE_CONTEXT]?.let { weight ->
                factorScores[AlgaeFactorId.LOW_PHOSPHATE_CONTEXT] = weight
            }
        }

        if (water.nitrateState == WaterParameterState.LOW) {
            profile.factorWeights[AlgaeFactorId.LOW_NITRATE_CONTEXT]?.let { weight ->
                factorScores[AlgaeFactorId.LOW_NITRATE_CONTEXT] = weight
            }
        }

        val nutrientStates = listOf(water.nitrateState, water.phosphateState)
        if (
            nutrientStates.any { state ->
                state == WaterParameterState.LOW ||
                    state == WaterParameterState.ELEVATED ||
                    state == WaterParameterState.HIGH
            }
        ) {
            profile.factorWeights[AlgaeFactorId.NUTRIENT_IMBALANCE]?.let { weight ->
                factorScores[AlgaeFactorId.NUTRIENT_IMBALANCE] = weight
            }
        }
    }

    private fun evaluateTankMaturity(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (
            context.tankAgeDays != null &&
            context.tankAgeDays <= STARTUP_TANK_DAYS
        ) {
            profile.factorWeights[AlgaeFactorId.IMMATURE_TANK]?.let { weight ->
                factorScores[AlgaeFactorId.IMMATURE_TANK] = weight
            }
        }
    }

    private fun evaluatePlantMass(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.plantCount != null && context.plantCount < LOW_PLANT_COUNT) {
            profile.factorWeights[AlgaeFactorId.LOW_PLANT_MASS]?.let { weight ->
                factorScores[AlgaeFactorId.LOW_PLANT_MASS] = weight
            }
        }
    }

    private fun evaluateTemperature(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.temperatureC != null && context.temperatureC >= WARM_WATER_C) {
            profile.factorWeights[AlgaeFactorId.WARM_WATER]?.let { weight ->
                factorScores[AlgaeFactorId.WARM_WATER] = weight
            }
        }
    }

    private fun evaluateLocation(
        observation: AlgaeObservationInput,
        profile: AlgaeKnowledgeProfile,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (
            observation.algaeType == AlgaeTypeId.CYANOBACTERIA &&
            AlgaeObservationLocation.SUBSTRATE in observation.locations
        ) {
            profile.factorWeights[AlgaeFactorId.FLOW_OR_OXYGENATION]?.let { weight ->
                factorScores[AlgaeFactorId.FLOW_OR_OXYGENATION] = weight
            }
        }

        if (
            observation.algaeType == AlgaeTypeId.BLACK_BEARD &&
            AlgaeObservationLocation.EQUIPMENT in observation.locations
        ) {
            profile.factorWeights[AlgaeFactorId.ORGANIC_LOAD]?.let { weight ->
                factorScores[AlgaeFactorId.ORGANIC_LOAD] = maxOf(
                    factorScores[AlgaeFactorId.ORGANIC_LOAD] ?: 0,
                    weight
                )
            }
        }
    }

    private fun buildActions(
        observation: AlgaeObservationInput,
        profile: AlgaeKnowledgeProfile,
        factors: List<AlgaeFactorAssessment>,
        missingData: Set<AlgaeMissingData>
    ): List<AlgaeActionRecommendation> {
        val ordered = linkedSetOf<AlgaeActionId>()
        ordered += profile.baselineActions

        factors.forEach { factor ->
            when (factor.factor) {
                AlgaeFactorId.LIGHT_DURATION -> ordered += AlgaeActionId.REVIEW_LIGHT_DURATION
                AlgaeFactorId.LIGHT_INTENSITY -> ordered += AlgaeActionId.REVIEW_LIGHT_INTENSITY
                AlgaeFactorId.CO2_STABILITY -> ordered += AlgaeActionId.VERIFY_CO2_STABILITY
                AlgaeFactorId.FILTER_MAINTENANCE -> ordered += AlgaeActionId.SERVICE_FILTER
                AlgaeFactorId.WATER_CHANGE_INTERVAL,
                AlgaeFactorId.ORGANIC_LOAD,
                AlgaeFactorId.NITROGEN_WASTE -> ordered += AlgaeActionId.PERFORM_WATER_CHANGE
                AlgaeFactorId.NUTRIENT_IMBALANCE -> ordered += AlgaeActionId.REVIEW_FERTILIZER_PLAN
                AlgaeFactorId.LOW_PHOSPHATE_CONTEXT,
                AlgaeFactorId.LOW_NITRATE_CONTEXT -> ordered += AlgaeActionId.REVIEW_NO3_PO4_BALANCE
                AlgaeFactorId.FLOW_OR_OXYGENATION -> {
                    ordered += AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION
                }
                AlgaeFactorId.IMMATURE_TANK -> ordered += AlgaeActionId.ALLOW_TANK_TO_MATURE
                AlgaeFactorId.PLANT_STRESS -> ordered += AlgaeActionId.TRIM_AFFECTED_LEAVES
                AlgaeFactorId.LOW_PLANT_MASS -> Unit
                AlgaeFactorId.WARM_WATER -> Unit
            }
        }

        if (AlgaeMissingData.CO2_SCHEDULE in missingData) {
            ordered += AlgaeActionId.ADD_CO2_SCHEDULE_DATA
        }

        if (observation.trend == AlgaeTrend.INCREASING) {
            ordered += AlgaeActionId.RECHECK_IN_FEW_DAYS
        }

        return ordered.mapIndexed { index, action ->
            AlgaeActionRecommendation(
                action = action,
                priority = index + 1
            )
        }
    }

    private fun priorityFor(
        observation: AlgaeObservationInput,
        factors: List<AlgaeFactorAssessment>
    ): AlgaeAnalysisPriority {
        if (
            observation.density == AlgaeDensity.HIGH ||
            observation.trend == AlgaeTrend.INCREASING ||
            factors.any { factor -> factor.strength == AlgaeFactorStrength.HIGH }
        ) {
            return AlgaeAnalysisPriority.ACTION_RECOMMENDED
        }

        if (
            observation.density == AlgaeDensity.MEDIUM ||
            factors.isNotEmpty()
        ) {
            return AlgaeAnalysisPriority.REVIEW
        }

        return AlgaeAnalysisPriority.MONITOR
    }

    private fun strengthFor(score: Int): AlgaeFactorStrength =
        when {
            score >= 5 -> AlgaeFactorStrength.HIGH
            score >= 3 -> AlgaeFactorStrength.MEDIUM
            else -> AlgaeFactorStrength.LOW
        }
}
