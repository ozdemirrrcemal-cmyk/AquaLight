package com.aqua.aqualight.ui.tabs.maintenance.smartcare

import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class SmartCareTankProfile(
  val tankId: Long,
  val tankName: String,
  val setupDay: Int?,
  val setupWeek: Int?,
  val grossVolumeL: Double,
  val estimatedWaterVolumeL: Double,
  val hasPlants: Boolean,
  val plantCount: Int,
  val hasLivestock: Boolean,
  val hasFish: Boolean,
  val hasShrimp: Boolean,
  val hasCo2: Boolean,
  val hasFertilizer: Boolean,
  val hasActiveSoil: Boolean,
  val hasFilter: Boolean,
  val hasLight: Boolean,
  val isStartupPeriod: Boolean,
  val isMatureTank: Boolean,
  val isHighTech: Boolean,
  val isLowTech: Boolean,
  val conditions: Set<SmartCareCondition>
)

object SmartCareProfileBuilder {

  fun build(
    tank: SavedAquariumTank,
    nowMillis: Long = System.currentTimeMillis()
  ): SmartCareTankProfile {
    val setupDay = calculateSetupDay(
      setupDateMillis = tank.setupDateMillis,
      nowMillis = nowMillis
    )

    val setupWeek = setupDay?.let { day ->
      ((day - 1) / 7) + 1
    }

    val grossVolumeL = calculateGrossVolumeL(tank)
    val estimatedWaterVolumeL =
      SmartFertilizerDoseCalculator.estimateWaterVolumeL(grossVolumeL)

    val hasPlants = tank.plants.isNotEmpty()
    val plantCount = tank.plants.size

    val hasLivestock = tank.livestock.isNotEmpty()
    val hasShrimp = hasLivestockKeyword(
      tank = tank,
      keywords = arrayOf(
        "shrimp",
        "karides",
        "neocaridina",
        "caridina",
        "amano"
      )
    )

    val hasFish = hasLivestock && !hasShrimp || hasLivestockKeyword(
      tank = tank,
      keywords = arrayOf(
        "fish",
        "balık",
        "tetra",
        "guppy",
        "betta",
        "rasbora",
        "cory",
        "corydoras",
        "danio",
        "molly",
        "platy"
      )
    )

    val hasCo2 = hasMaterialKeyword(
      materials = tank.materials,
      keywords = arrayOf(
        "co2",
        "co₂",
        "carbon dioxide"
      )
    )

    val hasFertilizer = hasMaterialKeyword(
      materials = tank.materials,
      keywords = arrayOf(
        "fertilizer",
        "fertiliser",
        "fert",
        "gübre",
        "nutrition",
        "brighty",
        "apt",
        "flourish",
        "plant care"
      )
    )

    val hasActiveSoil = hasMaterialKeyword(
      materials = tank.materials,
      keywords = arrayOf(
        "active soil",
        "aqua soil",
        "aquasoil",
        "soil",
        "amazonia",
        "controsoil",
        "stratum",
        "plant substrate"
      )
    )

    val hasFilter = hasMaterialKeyword(
      materials = tank.materials,
      keywords = arrayOf(
        "filter",
        "filtre",
        "canister",
        "sponge filter",
        "hang on",
        "hOB",
        "internal filter"
      )
    )

    val hasLight = hasMaterialKeyword(
      materials = tank.materials,
      keywords = arrayOf(
        "light",
        "lighting",
        "led",
        "chihiros",
        "twinstar",
        "lamba",
        "aydınlatma"
      )
    )

    val isStartupPeriod = setupDay != null && setupDay in 1..90
    val isMatureTank = setupDay != null && setupDay > 90

    val isHighTech = hasPlants && hasCo2 && hasLight
    val isLowTech = hasPlants && !hasCo2

    val conditions = buildConditions(
      hasPlants = hasPlants,
      hasCo2 = hasCo2,
      hasActiveSoil = hasActiveSoil,
      hasFertilizer = hasFertilizer,
      hasLivestock = hasLivestock,
      hasShrimp = hasShrimp,
      hasFish = hasFish,
      hasLight = hasLight,
      hasFilter = hasFilter,
      isStartupPeriod = isStartupPeriod,
      isMatureTank = isMatureTank,
      isHighTech = isHighTech,
      isLowTech = isLowTech
    )

    return SmartCareTankProfile(
      tankId = tank.id,
      tankName = tank.name.ifBlank { "Aquarium" },
      setupDay = setupDay,
      setupWeek = setupWeek,
      grossVolumeL = grossVolumeL,
      estimatedWaterVolumeL = estimatedWaterVolumeL,
      hasPlants = hasPlants,
      plantCount = plantCount,
      hasLivestock = hasLivestock,
      hasFish = hasFish,
      hasShrimp = hasShrimp,
      hasCo2 = hasCo2,
      hasFertilizer = hasFertilizer,
      hasActiveSoil = hasActiveSoil,
      hasFilter = hasFilter,
      hasLight = hasLight,
      isStartupPeriod = isStartupPeriod,
      isMatureTank = isMatureTank,
      isHighTech = isHighTech,
      isLowTech = isLowTech,
      conditions = conditions
    )
  }

  private fun calculateSetupDay(
    setupDateMillis: Long?,
    nowMillis: Long
  ): Int? {
    if (setupDateMillis == null) {
      return null
    }

    val diffMillis = nowMillis - setupDateMillis

    if (diffMillis < 0) {
      return 1
    }

    return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() + 1
  }

  private fun calculateGrossVolumeL(
    tank: SavedAquariumTank
  ): Double {
    if (
      tank.widthCm <= 0 ||
      tank.lengthCm <= 0 ||
      tank.heightCm <= 0
    ) {
      return 0.0
    }

    val volume = (
      tank.widthCm *
        tank.lengthCm *
        tank.heightCm
      ) / 1000.0

    return (volume * 10.0).roundToInt() / 10.0
  }

  private fun buildConditions(
    hasPlants: Boolean,
    hasCo2: Boolean,
    hasActiveSoil: Boolean,
    hasFertilizer: Boolean,
    hasLivestock: Boolean,
    hasShrimp: Boolean,
    hasFish: Boolean,
    hasLight: Boolean,
    hasFilter: Boolean,
    isStartupPeriod: Boolean,
    isMatureTank: Boolean,
    isHighTech: Boolean,
    isLowTech: Boolean
  ): Set<SmartCareCondition> {
    val conditions = mutableSetOf<SmartCareCondition>()

    if (hasPlants) {
      conditions.add(SmartCareCondition.PLANTED)
    } else {
      conditions.add(SmartCareCondition.NO_PLANTS)
    }

    if (hasCo2) {
      conditions.add(SmartCareCondition.HAS_CO2)
    } else {
      conditions.add(SmartCareCondition.NO_CO2)
    }

    if (hasActiveSoil) {
      conditions.add(SmartCareCondition.HAS_ACTIVE_SOIL)
    } else {
      conditions.add(SmartCareCondition.NO_ACTIVE_SOIL)
    }

    if (hasFertilizer) {
      conditions.add(SmartCareCondition.HAS_FERTILIZER)
    } else {
      conditions.add(SmartCareCondition.FERTILIZER_UNKNOWN)
    }

    if (hasLivestock) {
      conditions.add(SmartCareCondition.HAS_LIVESTOCK)
    } else {
      conditions.add(SmartCareCondition.NO_LIVESTOCK)
    }

    if (hasShrimp) {
      conditions.add(SmartCareCondition.HAS_SHRIMP)
    }

    if (hasFish) {
      conditions.add(SmartCareCondition.HAS_FISH)
    }

    if (hasLight) {
      conditions.add(SmartCareCondition.HAS_LIGHT)
    }

    if (hasFilter) {
      conditions.add(SmartCareCondition.HAS_FILTER)
    }

    if (isHighTech) {
      conditions.add(SmartCareCondition.HIGH_TECH)
    }

    if (isLowTech) {
      conditions.add(SmartCareCondition.LOW_TECH)
    }

    if (isStartupPeriod) {
      conditions.add(SmartCareCondition.STARTUP_PERIOD)
    }

    if (isMatureTank) {
      conditions.add(SmartCareCondition.MATURE_TANK)
    }

    return conditions
  }

  private fun hasMaterialKeyword(
    materials: List<SavedAquariumMaterial>,
    keywords: Array<String>
  ): Boolean {
    return materials.any { material ->
      containsAnyKeyword(
        value = "${material.categoryKey} ${material.name}",
        keywords = keywords
      )
    }
  }

  private fun hasLivestockKeyword(
    tank: SavedAquariumTank,
    keywords: Array<String>
  ): Boolean {
    return tank.livestock.any { livestock ->
      containsAnyKeyword(
        value = livestock.toString(),
        keywords = keywords
      )
    }
  }

  private fun containsAnyKeyword(
    value: String,
    keywords: Array<String>
  ): Boolean {
    val normalizedValue = normalize(value)

    return keywords.any { keyword ->
      normalizedValue.contains(
        normalize(keyword)
      )
    }
  }

  private fun normalize(
    value: String
  ): String {
    return value
      .lowercase(Locale.ROOT)
      .replace("₂", "2")
      .replace("ı", "i")
  }
}