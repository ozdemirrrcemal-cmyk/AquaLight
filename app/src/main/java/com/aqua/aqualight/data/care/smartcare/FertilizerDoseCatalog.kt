package com.aqua.aqualight.data.care.smartcare

object FertilizerDoseCatalog {

  val rules: List<FertilizerDoseRule> = listOf(

    FertilizerDoseRule(
      id = "tropica_specialised_nutrition_weekly",
      brand = FertilizerBrand.TROPICA,
      productName = "Specialised Nutrition",
      baseDoseMl = 6.0,
      baseVolumeL = 50.0,
      frequency = FertilizerFrequency.WEEKLY,
      doseType = FertilizerDoseType.COMPLETE_MACRO_MICRO,
      startupGuidance = FertilizerStartupGuidance.WITHHOLD_OR_LIMIT_FIRST_28_DAYS,
      catalogProductIds = setOf("fertilizer_tropica_specialised_nutrition"),
      evidenceSourceIds = SmartCareEvidenceCatalog.tags(
        SmartCareEvidenceId.PLANT_STARTUP_METHOD,
        SmartCareEvidenceId.PLANT_NUTRITION_MACRO_MICRO_GUIDE
      ),
      algaeResponse = FertilizerAlgaeResponse.HALVE_DOSE_AND_INCREASE_WATER_CHANGES,
      noteTr = "Makro ve mikro besin içerir. Yosun artışı varsa doz dikkatli azaltılmalıdır.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "tropica_premium_nutrition_weekly",
      brand = FertilizerBrand.TROPICA,
      productName = "Premium Nutrition",
      baseDoseMl = 6.0,
      baseVolumeL = 50.0,
      frequency = FertilizerFrequency.WEEKLY,
      doseType = FertilizerDoseType.MICRO_TRACE,
      startupGuidance = FertilizerStartupGuidance.WITHHOLD_OR_LIMIT_FIRST_28_DAYS,
      catalogProductIds = setOf("fertilizer_tropica_premium_nutrition"),
      evidenceSourceIds = SmartCareEvidenceCatalog.tags(
        SmartCareEvidenceId.PLANT_STARTUP_METHOD,
        SmartCareEvidenceId.PLANT_NUTRITION_MICRO_GUIDE
      ),
      noteTr = "Azot ve fosfor içermez. Daha çok balıklı veya düşük/orta bitkili tanklar için uygundur.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "dennerle_plant_care_npk_weekly",
      brand = FertilizerBrand.DENNERLE,
      productName = "Plant Care NPK",
      baseDoseMl = 10.0,
      baseVolumeL = 100.0,
      frequency = FertilizerFrequency.WEEKLY,
      doseType = FertilizerDoseType.MACRO_NPK,
      requiresWaterTest = true,
      noteTr = "Nitrat, fosfat ve potasyum içerir. Ölçüm yaparak kullanmak daha güvenlidir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "dennerle_plant_care_pro_weekly",
      brand = FertilizerBrand.DENNERLE,
      productName = "Plant Care Pro",
      baseDoseMl = 10.0,
      baseVolumeL = 100.0,
      frequency = FertilizerFrequency.WEEKLY,
      doseType = FertilizerDoseType.MICRO_TRACE,
      noteTr = "Haftalık genel bitki bakım gübresidir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "dennerle_plant_care_pro_daily",
      brand = FertilizerBrand.DENNERLE,
      productName = "Plant Care Pro Daily",
      baseDoseMl = 0.1,
      baseVolumeL = 10.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.MICRO_TRACE,
      noteTr = "Nano akvaryumlar ve hassas günlük dozlama için uygundur.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "two_hr_apt_1_daily",
      brand = FertilizerBrand.TWO_HR_AQUARIST,
      productName = "APT 1",
      baseDoseMl = 3.0,
      baseVolumeL = 100.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.COMPLETE,
      noteTr = "Günlük dozlama için tasarlanmıştır. Bitki yoğunluğuna göre ayarlanmalıdır.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "two_hr_apt_3_daily",
      brand = FertilizerBrand.TWO_HR_AQUARIST,
      productName = "APT 3",
      baseDoseMl = 3.0,
      baseVolumeL = 100.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.COMPLETE,
      noteTr = "CO₂ destekli ve yoğun bitkili tanklarda günlük dozlama için uygundur.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "two_hr_apt_e_daily",
      brand = FertilizerBrand.TWO_HR_AQUARIST,
      productName = "APT e",
      baseDoseMl = 2.0,
      baseVolumeL = 100.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.COMPLETE,
      noteTr = "Daha düşük günlük dozla kullanılan APT serisi gübredir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "ada_green_brighty_nitrogen_daily",
      brand = FertilizerBrand.ADA,
      productName = "Green Brighty Nitrogen",
      baseDoseMl = 1.0,
      baseVolumeL = 20.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.NITROGEN,
      startupGuidance = FertilizerStartupGuidance.APPLY_ONLY_WHEN_NEEDED,
      evidenceSourceIds = SmartCareEvidenceCatalog.tags(
        SmartCareEvidenceId.AQUASCAPE_FERTILIZATION_GUIDE
      ),
      requiresWaterTest = true,
      noteTr = "Azot desteği içindir. Özellikle nitrat ihtiyacı gözlemlenmelidir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "ada_green_brighty_neutral_k_daily",
      brand = FertilizerBrand.ADA,
      productName = "Green Brighty Neutral K",
      baseDoseMl = 1.0,
      baseVolumeL = 20.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.POTASSIUM,
      evidenceSourceIds = SmartCareEvidenceCatalog.tags(
        SmartCareEvidenceId.AQUASCAPE_FERTILIZATION_GUIDE
      ),
      noteTr = "Potasyum desteği içindir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "ada_green_brighty_iron_daily",
      brand = FertilizerBrand.ADA,
      productName = "Green Brighty Iron",
      baseDoseMl = 1.0,
      baseVolumeL = 20.0,
      frequency = FertilizerFrequency.DAILY,
      doseType = FertilizerDoseType.IRON,
      startupGuidance = FertilizerStartupGuidance.DEFER_UNTIL_DAY_61,
      evidenceSourceIds = SmartCareEvidenceCatalog.tags(
        SmartCareEvidenceId.AQUASCAPE_FERTILIZATION_GUIDE
      ),
      noteTr = "Demir desteği içindir. Bitki yoğunluğuna göre dikkatli ayarlanmalıdır.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "seachem_flourish_once_or_twice_weekly",
      brand = FertilizerBrand.SEACHEM,
      productName = "Flourish",
      baseDoseMl = 5.0,
      baseVolumeL = 250.0,
      frequency = FertilizerFrequency.ONCE_OR_TWICE_WEEKLY,
      doseType = FertilizerDoseType.MICRO_TRACE,
      noteTr = "Genel iz element desteğidir. Haftada 1-2 kez kullanılabilir.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "seachem_flourish_nitrogen_twice_weekly",
      brand = FertilizerBrand.SEACHEM,
      productName = "Flourish Nitrogen",
      baseDoseMl = 2.5,
      baseVolumeL = 160.0,
      frequency = FertilizerFrequency.TWICE_WEEKLY,
      doseType = FertilizerDoseType.NITROGEN,
      noteTr = "Azot eksikliği belirtilerine göre kullanılmalıdır.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "seachem_flourish_potassium_two_to_three_weekly",
      brand = FertilizerBrand.SEACHEM,
      productName = "Flourish Potassium",
      baseDoseMl = 5.0,
      baseVolumeL = 125.0,
      frequency = FertilizerFrequency.TWO_TO_THREE_TIMES_WEEKLY,
      doseType = FertilizerDoseType.POTASSIUM,
      noteTr = "Potasyum eksikliği belirtilerine göre kullanılmalıdır.",
      sourceTags = emptyList()
    ),

    FertilizerDoseRule(
      id = "seachem_flourish_phosphorus_once_or_twice_weekly",
      brand = FertilizerBrand.SEACHEM,
      productName = "Flourish Phosphorus",
      baseDoseMl = 2.5,
      baseVolumeL = 80.0,
      frequency = FertilizerFrequency.ONCE_OR_TWICE_WEEKLY,
      doseType = FertilizerDoseType.PHOSPHORUS,
      noteTr = "Fosfor eksikliği belirtilerine göre kullanılmalıdır.",
      sourceTags = emptyList()
    )
  )

  fun findById(
    id: String
  ): FertilizerDoseRule? {
    return rules.firstOrNull { rule ->
      rule.id == id
    }
  }
}
