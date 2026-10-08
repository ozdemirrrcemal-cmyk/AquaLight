package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.round

data class SmartCareTankProfile(
  val tankId: Long,
  val tankName: String,
  val setupDay: Int?,
  val setupWeek: Int?,
  val grossVolumeL: Double,
  val estimatedWaterVolumeL: Double,
  val isFreshwater: Boolean,
  val isMarine: Boolean,
  val isNatureAquarium: Boolean,
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

private data class SmartCareProfileInputs(
  val tank: SavedAquariumTank,
  val setupDay: Int?,
  val setupWeek: Int?,
  val grossVolumeL: Double,
  val characteristics: SmartCareTankCharacteristics,
  val isStartupPeriod: Boolean,
  val isMatureTank: Boolean
)

object SmartCareProfileBuilder {

  fun build(
    tank: SavedAquariumTank,
    nowMillis: Long = System.currentTimeMillis()
  ): SmartCareTankProfile {
    val setupDay = calculateSetupDay(tank.setupDateEpochDay, nowMillis)
    val inputs = SmartCareProfileInputs(
      tank = tank,
      setupDay = setupDay,
      setupWeek = setupDay?.let { day -> ((day - 1) / 7) + 1 },
      grossVolumeL = calculateGrossVolumeL(tank),
      characteristics = SmartCareTankClassifier.classify(tank),
      isStartupPeriod = setupDay != null && setupDay in 1..90,
      isMatureTank = setupDay != null && setupDay > 90
    )
    return createProfile(inputs)
  }

  private fun createProfile(inputs: SmartCareProfileInputs): SmartCareTankProfile {
    val tank = inputs.tank
    val traits = inputs.characteristics
    return SmartCareTankProfile(
      tankId = tank.id,
      tankName = tank.name.ifBlank { "Aquarium" },
      setupDay = inputs.setupDay,
      setupWeek = inputs.setupWeek,
      grossVolumeL = inputs.grossVolumeL,
      estimatedWaterVolumeL = SmartFertilizerDoseCalculator.estimateWaterVolumeL(
        inputs.grossVolumeL
      ),
      isFreshwater = traits.isFreshwater,
      isMarine = traits.isMarine,
      isNatureAquarium = traits.isNatureAquarium,
      hasPlants = traits.hasPlants,
      plantCount = traits.plantCount,
      hasLivestock = traits.hasLivestock,
      hasFish = traits.hasFish,
      hasShrimp = traits.hasShrimp,
      hasCo2 = traits.hasCo2,
      hasFertilizer = traits.hasFertilizer,
      hasActiveSoil = traits.hasActiveSoil,
      hasFilter = traits.hasFilter,
      hasLight = traits.hasLight,
      isStartupPeriod = inputs.isStartupPeriod,
      isMatureTank = inputs.isMatureTank,
      isHighTech = traits.isHighTech,
      isLowTech = traits.isLowTech,
      conditions = buildConditions(
        traits,
        inputs.isStartupPeriod,
        inputs.isMatureTank
      )
    )
  }

  private fun calculateSetupDay(
    setupDateEpochDay: Long?,
    nowMillis: Long
  ): Int? {
    if (setupDateEpochDay == null) return null
    val setupDate = LocalDate.ofEpochDay(setupDateEpochDay)
    val nowDate = Instant.ofEpochMilli(nowMillis)
      .atZone(ZoneId.systemDefault())
      .toLocalDate()
    return ChronoUnit.DAYS.between(setupDate, nowDate)
      .coerceAtLeast(0L)
      .toInt() + 1
  }

  private fun calculateGrossVolumeL(tank: SavedAquariumTank): Double {
    val volume = AquariumVolumeCalculator.grossLiters(
      widthCm = tank.widthCm,
      lengthCm = tank.lengthCm,
      heightCm = tank.heightCm
    )
    return round(volume * 10.0) / 10.0
  }

  private fun buildConditions(
    traits: SmartCareTankCharacteristics,
    isStartupPeriod: Boolean,
    isMatureTank: Boolean
  ): Set<SmartCareCondition> {
    val conditions = mutableSetOf<SmartCareCondition>()
    addTankTypeConditions(conditions, traits)
    addEquipmentAndLivestockConditions(conditions, traits)
    addLifecycleConditions(conditions, traits, isStartupPeriod, isMatureTank)
    return conditions
  }

  private fun addTankTypeConditions(
    conditions: MutableSet<SmartCareCondition>,
    traits: SmartCareTankCharacteristics
  ) {
    if (traits.isFreshwater) conditions.add(SmartCareCondition.FRESHWATER)
    if (traits.isMarine) conditions.add(SmartCareCondition.MARINE)
    if (traits.isNatureAquarium) conditions.add(SmartCareCondition.NATURE_AQUARIUM)
  }

  private fun addEquipmentAndLivestockConditions(
    conditions: MutableSet<SmartCareCondition>,
    traits: SmartCareTankCharacteristics
  ) {
    conditions.add(if (traits.hasPlants) SmartCareCondition.PLANTED else SmartCareCondition.NO_PLANTS)
    conditions.add(if (traits.hasCo2) SmartCareCondition.HAS_CO2 else SmartCareCondition.NO_CO2)
    conditions.add(
      if (traits.hasActiveSoil) SmartCareCondition.HAS_ACTIVE_SOIL else SmartCareCondition.NO_ACTIVE_SOIL
    )
    conditions.add(
      if (traits.hasFertilizer) SmartCareCondition.HAS_FERTILIZER else SmartCareCondition.FERTILIZER_UNKNOWN
    )
    conditions.add(
      if (traits.hasLivestock) SmartCareCondition.HAS_LIVESTOCK else SmartCareCondition.NO_LIVESTOCK
    )
    if (traits.hasShrimp) conditions.add(SmartCareCondition.HAS_SHRIMP)
    if (traits.hasFish) conditions.add(SmartCareCondition.HAS_FISH)
    if (traits.hasLight) conditions.add(SmartCareCondition.HAS_LIGHT)
    if (traits.hasFilter) conditions.add(SmartCareCondition.HAS_FILTER)
  }

  private fun addLifecycleConditions(
    conditions: MutableSet<SmartCareCondition>,
    traits: SmartCareTankCharacteristics,
    isStartupPeriod: Boolean,
    isMatureTank: Boolean
  ) {
    if (traits.isHighTech) conditions.add(SmartCareCondition.HIGH_TECH)
    if (traits.isLowTech) conditions.add(SmartCareCondition.LOW_TECH)
    if (isStartupPeriod) conditions.add(SmartCareCondition.STARTUP_PERIOD)
    if (isMatureTank) conditions.add(SmartCareCondition.MATURE_TANK)
  }
}
