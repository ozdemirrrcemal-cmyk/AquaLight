package com.aqua.aqualight.data.care.smartcare

import kotlin.math.round

object SmartFertilizerDoseCalculator {

  fun calculate(
    rule: FertilizerDoseRule,
    grossVolumeL: Double,
    setupDay: Int?,
    useEstimatedWaterVolume: Boolean = true
  ): FertilizerDoseRecommendation {
    val estimatedWaterVolumeL = if (useEstimatedWaterVolume) {
      estimateWaterVolumeL(grossVolumeL)
    } else {
      grossVolumeL
    }

    val normalDoseMl = roundDose(
      estimatedWaterVolumeL / rule.baseVolumeL * rule.baseDoseMl
    )

    val decision = decision(rule, setupDay)

    return FertilizerDoseRecommendation(
      rule = rule,
      grossVolumeL = roundDose(grossVolumeL),
      estimatedWaterVolumeL = roundDose(estimatedWaterVolumeL),
      normalDoseMl = normalDoseMl,
      advisedDoseMl = if (decision == FertilizerDoseDecision.LABEL_DOSE) {
        normalDoseMl
      } else {
        null
      },
      decision = decision,
      usesEstimatedWaterVolume = useEstimatedWaterVolume
    )
  }

  fun estimateWaterVolumeL(
    grossVolumeL: Double
  ): Double {
    return grossVolumeL * 0.85
  }

  fun decision(
    rule: FertilizerDoseRule,
    setupDay: Int?
  ): FertilizerDoseDecision {
    return getDoseDecision(rule.startupGuidance, setupDay)
  }

  private fun getDoseDecision(
    guidance: FertilizerStartupGuidance,
    setupDay: Int?
  ): FertilizerDoseDecision {
    return when (guidance) {
      FertilizerStartupGuidance.FOLLOW_LABEL -> {
        FertilizerDoseDecision.LABEL_DOSE
      }

      FertilizerStartupGuidance.WITHHOLD_OR_LIMIT_FIRST_28_DAYS -> {
        if (setupDay != null && setupDay <= 28) {
          FertilizerDoseDecision.WITHHOLD_OR_LIMIT
        } else {
          FertilizerDoseDecision.LABEL_DOSE
        }
      }

      FertilizerStartupGuidance.DEFER_UNTIL_DAY_61 -> {
        if (setupDay != null && setupDay < 61) {
          FertilizerDoseDecision.DEFER
        } else {
          FertilizerDoseDecision.LABEL_DOSE
        }
      }

      FertilizerStartupGuidance.APPLY_ONLY_WHEN_NEEDED -> {
        FertilizerDoseDecision.OBSERVATION_REQUIRED
      }
    }
  }

  private fun roundDose(
    value: Double
  ): Double {
    return if (value < 1.0) {
      round(value * 10.0) / 10.0
    } else {
      round(value * 2.0) / 2.0
    }
  }
}
