package com.aqua.aqualight.application.care

enum class SmartCareRecommendationConfidence {
    MODERATE,
    HIGH
}

enum class SmartCareLightingPhase {
    STARTUP,
    ESTABLISHING,
    MATURE
}

enum class SmartCareLightingAdjustment {
    HOLD,
    INCREASE_GRADUALLY
}

/**
 * Application-safe lighting advice shared by maintenance notifications and the future Quick Setup.
 * Channel intensity is deliberately absent until fixture output or PAR is known.
 */
data class SmartCareLightingRecommendation(
    val phase: SmartCareLightingPhase,
    val photoperiodMinutes: Int,
    val maximumPhotoperiodMinutes: Int,
    val adjustment: SmartCareLightingAdjustment,
    val confidence: SmartCareRecommendationConfidence,
    val requiresIntensityCalibration: Boolean,
    val sourceIds: Set<String>
)
