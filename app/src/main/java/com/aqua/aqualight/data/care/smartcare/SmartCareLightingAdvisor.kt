package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.application.care.SmartCareLightingAdjustment
import com.aqua.aqualight.application.care.SmartCareLightingPhase
import com.aqua.aqualight.application.care.SmartCareLightingRecommendation
import com.aqua.aqualight.application.care.SmartCareRecommendationConfidence

/** Source-backed photoperiod policy. It never invents RGB intensity without fixture/PAR data. */
object SmartCareLightingAdvisor {

    private const val STARTUP_END_DAY = 21
    private const val ESTABLISHING_END_DAY = 90
    private const val STARTUP_PHOTOPERIOD_MINUTES = 6 * 60
    private const val ESTABLISHED_PHOTOPERIOD_MINUTES = 8 * 60

    fun recommend(profile: SmartCareTankProfile): SmartCareLightingRecommendation? {
        val setupDay = profile.setupDay
        return if (setupDay == null || !supports(profile)) {
            null
        } else {
            val sourceIds = buildSet {
                add(SmartCareEvidenceId.TROPICA_GROWING_IN.stableId)
                add(SmartCareEvidenceId.TROPICA_QUICK_GUIDE.stableId)
                if (profile.isNatureAquarium) {
                    add(SmartCareEvidenceId.ADA_STARTING_FROM_ZERO.stableId)
                }
            }

            if (setupDay <= STARTUP_END_DAY) {
                startupRecommendation(sourceIds)
            } else {
                establishedRecommendation(setupDay, sourceIds)
            }
        }
    }

    private fun supports(profile: SmartCareTankProfile): Boolean =
        profile.isFreshwater && profile.hasPlants && profile.hasLight

    private fun startupRecommendation(
        sourceIds: Set<String>
    ): SmartCareLightingRecommendation = SmartCareLightingRecommendation(
        phase = SmartCareLightingPhase.STARTUP,
        photoperiodMinutes = STARTUP_PHOTOPERIOD_MINUTES,
        maximumPhotoperiodMinutes = STARTUP_PHOTOPERIOD_MINUTES,
        adjustment = SmartCareLightingAdjustment.HOLD,
        confidence = SmartCareRecommendationConfidence.HIGH,
        requiresIntensityCalibration = true,
        sourceIds = sourceIds
    )

    private fun establishedRecommendation(
        setupDay: Int,
        sourceIds: Set<String>
    ): SmartCareLightingRecommendation {
        val isEstablishing = setupDay <= ESTABLISHING_END_DAY
        return SmartCareLightingRecommendation(
            phase = if (isEstablishing) {
                SmartCareLightingPhase.ESTABLISHING
            } else {
                SmartCareLightingPhase.MATURE
            },
            photoperiodMinutes = if (isEstablishing) {
                STARTUP_PHOTOPERIOD_MINUTES
            } else {
                ESTABLISHED_PHOTOPERIOD_MINUTES
            },
            maximumPhotoperiodMinutes = ESTABLISHED_PHOTOPERIOD_MINUTES,
            adjustment = if (isEstablishing) {
                SmartCareLightingAdjustment.INCREASE_GRADUALLY
            } else {
                SmartCareLightingAdjustment.HOLD
            },
            confidence = SmartCareRecommendationConfidence.HIGH,
            requiresIntensityCalibration = true,
            sourceIds = sourceIds
        )
    }
}
