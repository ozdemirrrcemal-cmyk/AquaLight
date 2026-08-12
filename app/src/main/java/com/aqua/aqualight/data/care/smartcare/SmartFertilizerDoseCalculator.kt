package com.aqua.aqualight.data.care.smartcare

import kotlin.math.round

object SmartFertilizerDoseCalculator {

  fun calculate(
    rule: FertilizerDoseRule,
    grossVolumeL: Double,
    setupDay: Int?,
    hasActiveSoil: Boolean,
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

    val startupDoseFactor = getStartupDoseFactor(
      setupDay = setupDay,
      hasActiveSoil = hasActiveSoil
    )

    val startupDoseMl = roundDose(
      normalDoseMl * startupDoseFactor
    )

    return FertilizerDoseRecommendation(
      rule = rule,
      grossVolumeL = roundDose(grossVolumeL),
      estimatedWaterVolumeL = roundDose(estimatedWaterVolumeL),
      normalDoseMl = normalDoseMl,
      startupDoseMl = startupDoseMl,
      startupDoseFactor = startupDoseFactor
    )
  }

  fun estimateWaterVolumeL(
    grossVolumeL: Double
  ): Double {
    return grossVolumeL * 0.85
  }

  private fun getStartupDoseFactor(
    setupDay: Int?,
    hasActiveSoil: Boolean
  ): Double {
    if (setupDay == null || setupDay > 30) {
      return 1.0
    }

    val baseFactor = when (setupDay) {
      in 1..7 -> 0.0
      in 8..21 -> 0.5
      in 22..30 -> 0.75
      else -> 1.0
    }

    return if (hasActiveSoil && setupDay <= 30) {
      minOf(
        baseFactor,
        0.5
      )
    } else {
      baseFactor
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
