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
            factor == AlgaeFactorId.ORGANIC_LOAD ||
                factor == AlgaeFactorId.FILTER_MAINTENANCE ||
                factor == AlgaeFactorId.WATER_CHANGE_INTERVAL
        }

        if (
            needsMaintenanceData &&
            context.waterChangeOverdue == null &&
            context.filterMaintenanceOverdue == null
        ) {
            missingData += AlgaeMissingData.MAINTENANCE_HISTORY
        }

        if (context.waterChangeOverdue == true) {
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

        if (context.filterMaintenanceOverdue == true) {
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
            profile.factorWeights[AlgaeFactorId.NITROGEN_WASTE]?.let { weight ->
                factorScores[AlgaeFactorId.NITROGEN_WASTE] = weight
            }
        }

        if (
            water.phosphateState != WaterParameterState.UNKNOWN &&
            water.phosphateState != WaterParameterState.NORMAL
        ) {
            profile.factorWeights[AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT]
                ?.let { weight ->
                    factorScores[AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT] = weight
                }
        }

        if (water.nitrateState == WaterParameterState.LOW) {
            profile.factorWeights[AlgaeFactorId.LOW_NITRATE_CONTEXT]?.let { weight ->
                factorScores[AlgaeFactorId.LOW_NITRATE_CONTEXT] = weight
            }
        }

        if (
            listOf(water.nitrateState, water.phosphateState)
                .any { state ->
                    state != WaterParameterState.UNKNOWN &&
                        state != WaterParameterState.NORMAL
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
        if (context.startupPeriod == true) {
            profile.factorWeights[AlgaeFactorId.IMMATURE_TANK]?.let { weight ->
                factorScores[AlgaeFactorId.IMMATURE_TANK] = weight
            }
        }
    }

    private fun evaluatePlantStress(
        profile: AlgaeKnowledgeProfile,
        context: AlgaeTankContext,
        factorScores: MutableMap<AlgaeFactorId, Int>
    ) {
        if (context.plantStressObserved == true) {
            profile.factorWeights[AlgaeFactorId.PLANT_STRESS]?.let { weight ->
                factorScores[AlgaeFactorId.PLANT_STRESS] = weight
            }
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
                AlgaeFactorId.LIGHT_DURATION ->
                    ordered += AlgaeActionId.REVIEW_LIGHT_DURATION
                AlgaeFactorId.LIGHT_INTENSITY ->
                    ordered += AlgaeActionId.REVIEW_LIGHT_INTENSITY
                AlgaeFactorId.CO2_STABILITY ->
                    ordered += AlgaeActionId.VERIFY_CO2_STABILITY
                AlgaeFactorId.FILTER_MAINTENANCE ->
                    ordered += AlgaeActionId.SERVICE_FILTER
                AlgaeFactorId.WATER_CHANGE_INTERVAL,
                AlgaeFactorId.ORGANIC_LOAD,
                AlgaeFactorId.NITROGEN_WASTE ->
                    ordered += AlgaeActionId.PERFORM_WATER_CHANGE
                AlgaeFactorId.NUTRIENT_IMBALANCE ->
                    ordered += AlgaeActionId.REVIEW_FERTILIZER_PLAN
                AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT,
                AlgaeFactorId.LOW_NITRATE_CONTEXT ->
                    ordered += AlgaeActionId.REVIEW_NO3_PO4_BALANCE
                AlgaeFactorId.FLOW_OR_OXYGENATION ->
                    ordered += AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION
                AlgaeFactorId.IMMATURE_TANK ->
                    ordered += AlgaeActionId.ALLOW_TANK_TO_MATURE
                AlgaeFactorId.PLANT_STRESS ->
                    ordered += AlgaeActionId.TRIM_AFFECTED_LEAVES
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

    private fun WaterParameterState.isElevatedOrHigh(): Boolean =
        this == WaterParameterState.ELEVATED ||
            this == WaterParameterState.HIGH
}
