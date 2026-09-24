package com.aqua.aqualight.application.aquarium.health.algae

enum class AlgaeTypeId {
    BROWN_DIATOM,
    GREEN_SPOT,
    BLACK_BEARD,
    HAIR_THREAD,
    STAGHORN,
    GREEN_DUST,
    CYANOBACTERIA,
    GREEN_WATER,
    CLADOPHORA
}

enum class AlgaeObservationLocation {
    FRONT_GLASS,
    BACK_GLASS,
    SIDE_GLASS,
    PLANTS,
    ROOT_WOOD,
    ROCKS,
    SUBSTRATE,
    EQUIPMENT,
    WATER_COLUMN,
    OTHER
}

enum class AlgaeDensity {
    LOW,
    MEDIUM,
    HIGH
}

enum class AlgaeTrend {
    INCREASING,
    STABLE,
    DECREASING
}

enum class AlgaeFactorId {
    LIGHT_DURATION,
    LIGHT_INTENSITY,
    CO2_STABILITY,
    ORGANIC_LOAD,
    FILTER_MAINTENANCE,
    WATER_CHANGE_INTERVAL,
    NITROGEN_WASTE,
    NUTRIENT_IMBALANCE,
    LOW_PHOSPHATE_CONTEXT,
    LOW_NITRATE_CONTEXT,
    IMMATURE_TANK,
    PLANT_STRESS,
    LOW_PLANT_MASS,
    FLOW_OR_OXYGENATION,
    WARM_WATER
}

enum class AlgaeActionId {
    MANUAL_REMOVAL,
    TRIM_AFFECTED_LEAVES,
    CLEAN_HARDSCAPE,
    SIPHON_SUBSTRATE,
    REVIEW_LIGHT_DURATION,
    REVIEW_LIGHT_INTENSITY,
    VERIFY_CO2_STABILITY,
    ADD_CO2_SCHEDULE_DATA,
    SERVICE_FILTER,
    PERFORM_WATER_CHANGE,
    IMPROVE_FLOW_OR_OXYGENATION,
    REVIEW_FERTILIZER_PLAN,
    REVIEW_NO3_PO4_BALANCE,
    ALLOW_TANK_TO_MATURE,
    UV_FOR_GREEN_WATER,
    TEMPORARY_BLACKOUT,
    RECHECK_IN_FEW_DAYS
}

enum class AlgaeMissingData {
    LIGHT_SCHEDULE,
    CO2_SCHEDULE,
    WATER_ANALYSIS,
    MAINTENANCE_HISTORY
}

enum class AlgaeFactorStrength {
    LOW,
    MEDIUM,
    HIGH
}

enum class AlgaeAnalysisPriority {
    MONITOR,
    REVIEW,
    ACTION_RECOMMENDED
}

enum class WaterParameterState {
    LOW,
    NORMAL,
    ELEVATED,
    HIGH,
    UNKNOWN
}

data class AlgaeWaterQualityContext(
    val nitrateState: WaterParameterState = WaterParameterState.UNKNOWN,
    val phosphateState: WaterParameterState = WaterParameterState.UNKNOWN,
    val nitriteState: WaterParameterState = WaterParameterState.UNKNOWN,
    val ammoniaState: WaterParameterState = WaterParameterState.UNKNOWN
)

data class AlgaeTankContext(
    val tankAgeDays: Int? = null,
    val lightDurationMinutes: Int? = null,
    val lightIntensityPercent: Int? = null,
    val hasCo2: Boolean? = null,
    val co2ScheduleKnown: Boolean = false,
    val co2LeadMinutesBeforeLight: Int? = null,
    val daysSinceWaterChange: Int? = null,
    val daysSinceFilterMaintenance: Int? = null,
    val plantCount: Int? = null,
    val temperatureC: Double? = null,
    val waterQuality: AlgaeWaterQualityContext = AlgaeWaterQualityContext()
)

data class AlgaeObservationInput(
    val algaeType: AlgaeTypeId,
    val locations: Set<AlgaeObservationLocation>,
    val density: AlgaeDensity,
    val trend: AlgaeTrend
)

data class AlgaeFactorAssessment(
    val factor: AlgaeFactorId,
    val strength: AlgaeFactorStrength,
    val score: Int
)

data class AlgaeActionRecommendation(
    val action: AlgaeActionId,
    val priority: Int
)

data class AlgaeAnalysisResult(
    val algaeType: AlgaeTypeId,
    val priority: AlgaeAnalysisPriority,
    val factors: List<AlgaeFactorAssessment>,
    val actions: List<AlgaeActionRecommendation>,
    val missingData: Set<AlgaeMissingData>
)
