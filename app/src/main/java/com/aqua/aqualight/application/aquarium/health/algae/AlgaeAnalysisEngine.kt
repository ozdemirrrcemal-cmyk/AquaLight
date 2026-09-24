package com.aqua.aqualight.application.aquarium.health.algae

object AlgaeAnalysisEngine {

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
        evaluatePlantContext(profile, context, factorScores, missingData)
        evaluateFlow(profile, context, factorScores)
        evaluateTemperature(profile, context, factorScores, missingData)

        val factors = factorScores
            .map { (factor, score) ->
                AlgaeFactorAssessment(
                    factor = factor,
                    strength = strengthFor(score),
                    score = score
                )
            }
            .sortedByDescending(AlgaeFactorAssessment::score)

        return AlgaeAnalysisResult(
            algaeType = observation.algaeType,
            priority = priorityFor(observation, factors),
            factors = factors,
            actions = buildActions(
                observation = observation,
                profile = profile,
                factors = factors,
                missingData = missingData
            ),
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

        if (
            durationWeight != null &&
            context.lightDurationState == AlgaeSignalState.UNKNOWN
        ) {
            missingData += AlgaeMissingData.LIGHT_PROFILE
        } else if (
            durationWeight != null &&
            isElevated(context.lightDurationState)
        ) {
            factorScores[AlgaeFactorId.LIGHT_DURATION] = durationWeight
        }

        if (
            intensityWeight != null &&
            context.lightIntensityState == AlgaeSignalState.UNKNOWN
        ) {
            missingData += AlgaeMissingData.LIGHT_PROFILE
        } else if (
            intensityWeight != null &&
            isElevated(context.lightIntensityState)
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

        if (isElevated(context.co2TimingState)) {
            factorScores[AlgaeFactorId.CO2_STABILITY] = weight
        }
    }

    private fun evaluateMaintenance(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val maintenanceFactors = setOf(
            AlgaeFactorId.ORGANIC_LOAD,
            AlgaeFactorId.FILTER_MAINTENANCE,
            AlgaeFactorId.WATER_CHANGE_INTERVAL
        )

        if (
            profile.factorWeights.keys.any(maintenanceFactors::contains) &&
            context.waterChangeOverdue == null &&
            context.filterMaintenanceOverdue == null &&
            context.organicLoadState == AlgaeSignalState.UNKNOWN
        ) {
            missingData += AlgaeMissingData.MAINTENANCE_HISTORY
        }

        if (context.waterChangeOverdue == true) {
            addFactor(profile, factorScores, AlgaeFactorId.WATER_CHANGE_INTERVAL)
        }

        if (context.filterMaintenanceOverdue == true) {
            addFactor(profile, factorScores, AlgaeFactorId.FILTER_MAINTENANCE)
        }

        if (isElevated(context.organicLoadState)) {
            addFactor(profile, factorScores, AlgaeFactorId.ORGANIC_LOAD)
        }
    }

    private fun evaluateWater(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        val water = context.waterQuality
        val states = listOf(
            water.nitrateState,
            water.phosphateState,
            water.nitriteState,
            water.ammoniaState
        )

        if (states.all { state -> state == AlgaeSignalState.UNKNOWN }) {
            missingData += AlgaeMissingData.WATER_ANALYSIS
        }

        if (isElevated(water.ammoniaState) || isElevated(water.nitriteState)) {
            addFactor(profile, factorScores, AlgaeFactorId.NITROGEN_WASTE)
        }

        if (water.phosphateState == AlgaeSignalState.LOW) {
            addFactor(profile, factorScores, AlgaeFactorId.LOW_PHOSPHATE_CONTEXT)
        }

        if (water.nitrateState == AlgaeSignalState.LOW) {
            addFactor(profile, factorScores, AlgaeFactorId.LOW_NITRATE_CONTEXT)
        }

        if (
            water.nitrateState.isOutsideNormal() ||
            water.phosphateState.isOutsideNormal()
        ) {
            addFactor(profile, factorScores, AlgaeFactorId.NUTRIENT_IMBALANCE)
        }
    }

    private fun evaluateTankMaturity(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.startupPeriod == true) {
            addFactor(profile, factorScores, AlgaeFactorId.IMMATURE_TANK)
        }
    }

    private fun evaluatePlantContext(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        if (isElevated(context.plantStressState)) {
            addFactor(profile, factorScores, AlgaeFactorId.PLANT_STRESS)
        }

        if (
            profile.factorWeights.containsKey(AlgaeFactorId.LOW_PLANT_MASS) &&
            context.plantMassState == AlgaeSignalState.UNKNOWN
        ) {
            missingData += AlgaeMissingData.PLANT_MASS
        } else if (context.plantMassState == AlgaeSignalState.LOW) {
            addFactor(profile, factorScores, AlgaeFactorId.LOW_PLANT_MASS)
        }
    }

    private fun evaluateFlow(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.flowOrOxygenationState == AlgaeSignalState.LOW) {
            addFactor(profile, factorScores, AlgaeFactorId.FLOW_OR_OXYGENATION)
        }
    }

    private fun evaluateTemperature(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        if (
            profile.factorWeights.containsKey(AlgaeFactorId.WARM_WATER) &&
            context.temperatureState == AlgaeSignalState.UNKNOWN
        ) {
            missingData += AlgaeMissingData.TEMPERATURE_CONTEXT
        } else if (isElevated(context.temperatureState)) {
            addFactor(profile, factorScores, AlgaeFactorId.WARM_WATER)
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
                AlgaeFactorId.LOW_PLANT_MASS,
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

    private fun addFactor(
        profile: AlgaeKnowledgeProfile,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        factor: AlgaeFactorId
    ) {
        profile.factorWeights[factor]?.let { weight ->
            factorScores[factor] = weight
        }
    }
}

private fun AlgaeSignalState.isOutsideNormal(): Boolean =
    this == AlgaeSignalState.LOW ||
        this == AlgaeSignalState.ELEVATED ||
        this == AlgaeSignalState.HIGH

private fun isElevated(state: AlgaeSignalState): Boolean =
    state == AlgaeSignalState.ELEVATED ||
        state == AlgaeSignalState.HIGH

private fun strengthFor(score: Int): AlgaeFactorStrength =
    when {
        score >= 5 -> AlgaeFactorStrength.HIGH
        score >= 3 -> AlgaeFactorStrength.MEDIUM
        else -> AlgaeFactorStrength.LOW
    }
