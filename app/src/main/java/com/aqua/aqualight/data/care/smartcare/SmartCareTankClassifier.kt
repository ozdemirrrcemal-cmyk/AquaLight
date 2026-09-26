package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank

data class SmartCareTankCharacteristics(
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
  val isHighTech: Boolean,
  val isLowTech: Boolean
)

private data class SmartCareLivestockSignals(
  val hasLivestock: Boolean,
  val hasFish: Boolean,
  val hasShrimp: Boolean
)

private data class SmartCareEquipmentSignals(
  val hasCo2: Boolean,
  val hasFertilizer: Boolean,
  val hasActiveSoil: Boolean,
  val hasFilter: Boolean,
  val hasLight: Boolean
)

object SmartCareTankClassifier {

  fun classify(tank: SavedAquariumTank): SmartCareTankCharacteristics {
    val livestock = classifyLivestock(tank)
    val equipment = classifyEquipment(tank.materials)
    val hasPlants = tank.plants.isNotEmpty()
    return SmartCareTankCharacteristics(
      isFreshwater = isFreshwaterTankType(tank.tankType),
      isMarine = isMarineTankType(tank.tankType),
      isNatureAquarium = SmartCareTextMatcher.normalize(tank.tankStyle) in setOf(
        SmartCareTextMatcher.normalize(AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM),
        "nature"
      ),
      hasPlants = hasPlants,
      plantCount = tank.plants.size,
      hasLivestock = livestock.hasLivestock,
      hasFish = livestock.hasFish,
      hasShrimp = livestock.hasShrimp,
      hasCo2 = equipment.hasCo2,
      hasFertilizer = equipment.hasFertilizer,
      hasActiveSoil = equipment.hasActiveSoil,
      hasFilter = equipment.hasFilter,
      hasLight = equipment.hasLight,
      isHighTech = hasPlants && equipment.hasCo2 && equipment.hasLight,
      isLowTech = hasPlants && !equipment.hasCo2
    )
  }

  private fun classifyLivestock(tank: SavedAquariumTank): SmartCareLivestockSignals {
    val hasLivestock = tank.livestock.isNotEmpty()
    val hasShrimp = SmartCareTextMatcher.hasLivestockKeyword(
      tank,
      arrayOf("shrimp", "karides", "neocaridina", "caridina", "amano")
    )
    val hasFish = hasLivestock && !hasShrimp || SmartCareTextMatcher.hasLivestockKeyword(
      tank,
      arrayOf(
        "fish", "balık", "tetra", "guppy", "betta", "rasbora",
        "cory", "corydoras", "danio", "molly", "platy"
      )
    )
    return SmartCareLivestockSignals(hasLivestock, hasFish, hasShrimp)
  }

  private fun classifyEquipment(
    materials: List<SavedAquariumMaterial>
  ): SmartCareEquipmentSignals {
    return SmartCareEquipmentSignals(
      hasCo2 = hasCo2(materials),
      hasFertilizer = hasFertilizer(materials),
      hasActiveSoil = hasActiveSoil(materials),
      hasFilter = hasFilter(materials),
      hasLight = hasLight(materials)
    )
  }

  private fun hasCo2(materials: List<SavedAquariumMaterial>): Boolean {
    return SmartCareTextMatcher.hasMaterialCategory(materials, MATERIAL_CATEGORY_CO2) ||
      SmartCareTextMatcher.hasMaterialKeyword(materials, arrayOf("co2", "co₂", "carbon dioxide"))
  }

  private fun hasFertilizer(materials: List<SavedAquariumMaterial>): Boolean {
    return SmartCareTextMatcher.hasMaterialCategory(materials, MATERIAL_CATEGORY_FERTILIZER) ||
      SmartCareTextMatcher.hasMaterialKeyword(
        materials,
        arrayOf(
          "fertilizer", "fertiliser", "fert", "gübre", "nutrition",
          "brighty", "apt", "flourish", "plant care"
        )
      )
  }

  private fun hasActiveSoil(materials: List<SavedAquariumMaterial>): Boolean {
    return materials.any { material ->
      AquariumSubstrateMetadataCatalog.resolveSemantic(
        productId = material.productId,
        categoryKey = material.categoryKey
      ) == AquariumSubstrateSemantic.ACTIVE_SOIL
    }
  }

  private fun hasFilter(materials: List<SavedAquariumMaterial>): Boolean {
    return SmartCareTextMatcher.hasMaterialCategory(materials, MATERIAL_CATEGORY_FILTER) ||
      SmartCareTextMatcher.hasMaterialKeyword(
        materials,
        arrayOf("filter", "filtre", "canister", "sponge filter", "hang on", "hOB", "internal filter")
      )
  }

  private fun hasLight(materials: List<SavedAquariumMaterial>): Boolean {
    return SmartCareTextMatcher.hasMaterialCategory(materials, MATERIAL_CATEGORY_LIGHT) ||
      SmartCareTextMatcher.hasMaterialKeyword(
        materials,
        arrayOf("light", "lighting", "led", "chihiros", "twinstar", "lamba", "aydınlatma")
      )
  }

  private fun isMarineTankType(tankType: String): Boolean =
    AquariumTankTaxonomy.environmentForTankType(tankType) ==
      AquariumTankTaxonomy.WATER_ENVIRONMENT_MARINE

  private fun isFreshwaterTankType(tankType: String): Boolean =
    AquariumTankTaxonomy.environmentForTankType(tankType) ==
      AquariumTankTaxonomy.WATER_ENVIRONMENT_FRESHWATER

  private const val MATERIAL_CATEGORY_CO2 = "co2"
  private const val MATERIAL_CATEGORY_FERTILIZER = "fertilizer"
  private const val MATERIAL_CATEGORY_FILTER = "filter"
  private const val MATERIAL_CATEGORY_LIGHT = "light"
}
