package com.aqua.aqualight.ui.tabs.maintenance.smartcare

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
      startupDoseFactor = startupDoseFactor,
      titleTr = buildTitle(rule),
      messageTr = buildMessage(
        rule = rule,
        estimatedWaterVolumeL = estimatedWaterVolumeL,
        normalDoseMl = normalDoseMl,
        startupDoseMl = startupDoseMl,
        startupDoseFactor = startupDoseFactor,
        setupDay = setupDay
      )
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

  private fun buildTitle(
    rule: FertilizerDoseRule
  ): String {
    return "Gübre dozu önerisi"
  }

  private fun buildMessage(
    rule: FertilizerDoseRule,
    estimatedWaterVolumeL: Double,
    normalDoseMl: Double,
    startupDoseMl: Double,
    startupDoseFactor: Double,
    setupDay: Int?
  ): String {
    val frequencyText = getFrequencyText(rule.frequency)

    return if (setupDay != null && setupDay <= 30) {
      if (startupDoseFactor == 0.0) {
        "Tankınız yeni kurulum döneminde. ${rule.productName} için ilk hafta gübrelemeyi ertelemek daha güvenli olabilir."
      } else {
        "Tahmini ${roundDose(estimatedWaterVolumeL)} L su hacmine göre ${rule.productName} için ${frequencyText} yaklaşık ${startupDoseMl} mL ile düşük doz başlamak daha güvenli olabilir."
      }
    } else {
      "Tahmini ${roundDose(estimatedWaterVolumeL)} L su hacmine göre ${rule.productName} için ${frequencyText} yaklaşık ${normalDoseMl} mL önerilir."
    }
  }

  private fun getFrequencyText(
    frequency: FertilizerFrequency
  ): String {
    return when (frequency) {
      FertilizerFrequency.DAILY -> "günlük"
      FertilizerFrequency.WEEKLY -> "haftalık"
      FertilizerFrequency.ONCE_OR_TWICE_WEEKLY -> "haftada 1-2 kez"
      FertilizerFrequency.TWICE_WEEKLY -> "haftada 2 kez"
      FertilizerFrequency.TWO_TO_THREE_TIMES_WEEKLY -> "haftada 2-3 kez"
      FertilizerFrequency.AS_NEEDED -> "ihtiyaca göre"
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