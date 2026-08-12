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

data class FertilizerDoseRule(
  val id: String,
  val brand: FertilizerBrand,
  val productName: String,
  val baseDoseMl: Double,
  val baseVolumeL: Double,
  val frequency: FertilizerFrequency,
  val doseType: FertilizerDoseType,
  val requiresWaterTest: Boolean = false,
  val noteTr: String,
  val sourceTags: List<String>
)

data class FertilizerDoseRecommendation(
  val rule: FertilizerDoseRule,
  val grossVolumeL: Double,
  val estimatedWaterVolumeL: Double,
  val normalDoseMl: Double,
  val startupDoseMl: Double,
  val startupDoseFactor: Double
)
