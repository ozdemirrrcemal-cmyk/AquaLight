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
        evaluatePlantStress(profile, context, factorScores)
        evaluateFlow(observation, profile, context, factorScores)
        evaluateTemperature(profile, context, factorScores, missingData)

        val factors = factorScores
            .map { (factor, score) ->
                AlgaeFactorAssessment(
                    factor = factor,
                    strength = algaeFactorStrength(score),
                    score = score
                )
            }
            .sortedByDescending(AlgaeFactorAssessment::score)

        return AlgaeAnalysisResult(
            algaeType = observation.algaeType,
            priority = algaeAnalysisPriority(observation, factors),
            factors = factors,
            actions = algaeActionPlan(
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
        profile.factorWeights[AlgaeFactorId.LIGHT_DURATION]?.let { weight ->
            when (context.lightDurationState) {
                AlgaeLightDurationState.UNKNOWN ->
                    missingData += AlgaeMissingData.LIGHT_PROFILE
                AlgaeLightDurationState.EXTENDED ->
                    factorScores[AlgaeFactorId.LIGHT_DURATION] = weight
                AlgaeLightDurationState.WITHIN_RANGE -> Unit
            }
        }

        profile.factorWeights[AlgaeFactorId.LIGHT_INTENSITY]?.let { weight ->
            when (context.lightExposureState) {
                AlgaeLightExposureState.UNKNOWN ->
                    missingData += AlgaeMissingData.LIGHT_PROFILE
                AlgaeLightExposureState.HIGH ->
                    factorScores[AlgaeFactorId.LIGHT_INTENSITY] = weight
                AlgaeLightExposureState.WITHIN_RANGE -> Unit
            }
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
        }

        if (context.co2Stability == AlgaeCo2Stability.UNSTABLE) {
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
            factor in MAINTENANCE_FACTORS
        }

        if (
            needsMaintenanceData &&
            context.waterChangeOverdue == null &&
            context.filterMaintenanceOverdue == null
        ) {
            missingData += AlgaeMissingData.MAINTENANCE_HISTORY
        }

        if (context.waterChangeOverdue == true) {
            addFactor(profile, factorScores, AlgaeFactorId.WATER_CHANGE_INTERVAL)
            addFactor(profile, factorScores, AlgaeFactorId.ORGANIC_LOAD)
        }

        if (context.filterMaintenanceOverdue == true) {
            addFactor(profile, factorScores, AlgaeFactorId.FILTER_MAINTENANCE)
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

        if (states.all { state -> state == WaterParameterState.UNKNOWN }) {
            missingData += AlgaeMissingData.WATER_ANALYSIS
        }

        if (
            water.ammoniaState.isElevatedOrHigh() ||
            water.nitriteState.isElevatedOrHigh()
        ) {
            addFactor(profile, factorScores, AlgaeFactorId.NITROGEN_WASTE)
        }

        if (
            water.phosphateState != WaterParameterState.UNKNOWN &&
            water.phosphateState != WaterParameterState.NORMAL
        ) {
            addFactor(
                profile,
                factorScores,
                AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT
            )
        }

        if (water.nitrateState == WaterParameterState.LOW) {
            addFactor(profile, factorScores, AlgaeFactorId.LOW_NITRATE_CONTEXT)
        }

        if (
            listOf(water.nitrateState, water.phosphateState)
                .any(WaterParameterState::isOutsideNormal)
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

    private fun evaluatePlantStress(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.plantStressObserved == true) {
            addFactor(profile, factorScores, AlgaeFactorId.PLANT_STRESS)
        }
    }

    private fun evaluateFlow(
        observation: AlgaeObservationInput,
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        val weight = profile.factorWeights[AlgaeFactorId.FLOW_OR_OXYGENATION] ?: return
        val shouldFlag = when (observation.algaeType) {
            AlgaeTypeId.BLACK_BEARD ->
                context.flowState == AlgaeFlowState.TURBULENT
            AlgaeTypeId.CYANOBACTERIA,
            AlgaeTypeId.GREEN_DUST,
            AlgaeTypeId.CLADOPHORA ->
                context.flowState == AlgaeFlowState.LOW
            else -> false
        }

        if (shouldFlag) {
            factorScores[AlgaeFactorId.FLOW_OR_OXYGENATION] = weight
        }
    }

    private fun evaluateTemperature(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>,
        missingData: MutableSet<AlgaeMissingData>
    ) {
        profile.factorWeights[AlgaeFactorId.WARM_WATER]?.let { weight ->
            when (context.temperatureState) {
                AlgaeTemperatureState.UNKNOWN ->
                    missingData += AlgaeMissingData.TEMPERATURE_CONTEXT
                AlgaeTemperatureState.WARM ->
                    factorScores[AlgaeFactorId.WARM_WATER] = weight
                AlgaeTemperatureState.WITHIN_RANGE -> Unit
            }
        }
    }
}

private const val HIGH_FACTOR_SCORE = 5
private const val MEDIUM_FACTOR_SCORE = 3

private val MAINTENANCE_FACTORS = setOf(
    AlgaeFactorId.ORGANIC_LOAD,
    AlgaeFactorId.FILTER_MAINTENANCE,
    AlgaeFactorId.WATER_CHANGE_INTERVAL
)

private val FACTOR_ACTIONS = mapOf(
    AlgaeFactorId.LIGHT_DURATION to AlgaeActionId.REVIEW_LIGHT_DURATION,
    AlgaeFactorId.LIGHT_INTENSITY to AlgaeActionId.REVIEW_LIGHT_INTENSITY,
    AlgaeFactorId.CO2_STABILITY to AlgaeActionId.VERIFY_CO2_STABILITY,
    AlgaeFactorId.ORGANIC_LOAD to AlgaeActionId.PERFORM_WATER_CHANGE,
    AlgaeFactorId.FILTER_MAINTENANCE to AlgaeActionId.SERVICE_FILTER,
    AlgaeFactorId.WATER_CHANGE_INTERVAL to AlgaeActionId.PERFORM_WATER_CHANGE,
    AlgaeFactorId.NITROGEN_WASTE to AlgaeActionId.PERFORM_WATER_CHANGE,
    AlgaeFactorId.NUTRIENT_IMBALANCE to AlgaeActionId.REVIEW_FERTILIZER_PLAN,
    AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT to AlgaeActionId.REVIEW_NO3_PO4_BALANCE,
    AlgaeFactorId.LOW_NITRATE_CONTEXT to AlgaeActionId.REVIEW_NO3_PO4_BALANCE,
    AlgaeFactorId.IMMATURE_TANK to AlgaeActionId.ALLOW_TANK_TO_MATURE,
    AlgaeFactorId.PLANT_STRESS to AlgaeActionId.TRIM_AFFECTED_LEAVES,
    AlgaeFactorId.FLOW_OR_OXYGENATION to AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION
)

private fun addFactor(
    profile: AlgaeKnowledgeProfile,
    factorScores: MutableMap<AlgaeFactorId, Int>,
    factor: AlgaeFactorId
) {
    profile.factorWeights[factor]?.let { weight ->
        factorScores[factor] = maxOf(factorScores[factor] ?: 0, weight)
    }
}

private fun algaeActionPlan(
    observation: AlgaeObservationInput,
    profile: AlgaeKnowledgeProfile,
    factors: List<AlgaeFactorAssessment>,
    missingData: Set<AlgaeMissingData>
): List<AlgaeActionRecommendation> {
    val ordered = linkedSetOf<AlgaeActionId>()
    ordered += profile.baselineActions
    factors.mapNotNull { factor ->
        FACTOR_ACTIONS[factor.factor]
    }.forEach(ordered::add)

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

private fun algaeAnalysisPriority(
    observation: AlgaeObservationInput,
    factors: List<AlgaeFactorAssessment>
): AlgaeAnalysisPriority =
    when {
        observation.density == AlgaeDensity.HIGH ||
            observation.trend == AlgaeTrend.INCREASING ||
            factors.any { factor -> factor.strength == AlgaeFactorStrength.HIGH } ->
            AlgaeAnalysisPriority.ACTION_RECOMMENDED
        observation.density == AlgaeDensity.MEDIUM || factors.isNotEmpty() ->
            AlgaeAnalysisPriority.REVIEW
        else -> AlgaeAnalysisPriority.MONITOR
    }

private fun algaeFactorStrength(score: Int): AlgaeFactorStrength =
    when {
        score >= HIGH_FACTOR_SCORE -> AlgaeFactorStrength.HIGH
        score >= MEDIUM_FACTOR_SCORE -> AlgaeFactorStrength.MEDIUM
        else -> AlgaeFactorStrength.LOW
    }

private fun WaterParameterState.isElevatedOrHigh(): Boolean =
    this == WaterParameterState.ELEVATED ||
        this == WaterParameterState.HIGH

private fun WaterParameterState.isOutsideNormal(): Boolean =
    this != WaterParameterState.UNKNOWN &&
        this != WaterParameterState.NORMAL
