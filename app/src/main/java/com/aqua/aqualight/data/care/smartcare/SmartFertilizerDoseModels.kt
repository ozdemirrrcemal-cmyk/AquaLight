package com.aqua.aqualight.data.care.smartcare

enum class FertilizerBrand {
  TROPICA,
  DENNERLE,
  ADA,
  TWO_HR_AQUARIST,
  SEACHEM
}

enum class FertilizerFrequency {
  DAILY,
  WEEKLY,
  ONCE_OR_TWICE_WEEKLY,
  TWICE_WEEKLY,
  TWO_TO_THREE_TIMES_WEEKLY,
  AS_NEEDED
}

enum class FertilizerDoseType {
  COMPLETE,
  COMPLETE_MACRO_MICRO,
  MICRO_TRACE,
  MACRO_NPK,
  NITROGEN,
  PHOSPHORUS,
  POTASSIUM,
  IRON,
  MINERAL
}

enum class FertilizerStartupGuidance {
  FOLLOW_LABEL,
  WITHHOLD_OR_LIMIT_FIRST_28_DAYS,
  DEFER_UNTIL_DAY_61,
  APPLY_ONLY_WHEN_NEEDED
}

enum class FertilizerDoseDecision {
  LABEL_DOSE,
  WITHHOLD_OR_LIMIT,
  DEFER,
  OBSERVATION_REQUIRED
}

enum class FertilizerAlgaeResponse {
  FOLLOW_LABEL,
  HALVE_DOSE_AND_INCREASE_WATER_CHANGES
}

data class FertilizerDoseRule(
  val id: String,
  val brand: FertilizerBrand,
  val productName: String,
  val baseDoseMl: Double,
  val baseVolumeL: Double,
  val frequency: FertilizerFrequency,
  val doseType: FertilizerDoseType,
  val startupGuidance: FertilizerStartupGuidance = FertilizerStartupGuidance.FOLLOW_LABEL,
  val catalogProductIds: Set<String> = emptySet(),
  val evidenceSourceIds: List<String> = emptyList(),
  val algaeResponse: FertilizerAlgaeResponse = FertilizerAlgaeResponse.FOLLOW_LABEL,
  val requiresWaterTest: Boolean = false,
  val noteTr: String,
  val sourceTags: List<String>
)

data class FertilizerDoseRecommendation(
  val rule: FertilizerDoseRule,
  val grossVolumeL: Double,
  val estimatedWaterVolumeL: Double,
  val normalDoseMl: Double,
  val advisedDoseMl: Double?,
  val decision: FertilizerDoseDecision,
  val usesEstimatedWaterVolume: Boolean
)
