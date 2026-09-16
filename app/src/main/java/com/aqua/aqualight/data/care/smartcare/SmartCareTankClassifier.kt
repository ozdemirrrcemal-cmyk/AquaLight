package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.application.aquarium.AquariumLivestockCategory
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategory
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
    // The category is selected from the app's fixed catalog. Free-text names and notes are not
    // authoritative enough to drive lighting safety decisions.
    val hasShrimp = tank.livestock.any { item ->
      item.category == AquariumLivestockCategory.SHRIMP
    }
    val hasFish = tank.livestock.any { item ->
      item.category == AquariumLivestockCategory.FISH
    }
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
    return materials.any { material ->
      material.categoryKey == AquariumMaterialCategory.CO2
    }
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
      material.substrateSemantic == AquariumSubstrateSemantic.ACTIVE_SOIL
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

  private fun isMarineTankType(tankType: String): Boolean = tankType in setOf(
    AquariumTankTaxonomy.TYPE_MARINE,
    AquariumTankTaxonomy.TYPE_SOFTIES,
    AquariumTankTaxonomy.TYPE_MIXED_REEF,
    AquariumTankTaxonomy.TYPE_SPS,
    AquariumTankTaxonomy.TYPE_CORAL
  )

  private fun isFreshwaterTankType(tankType: String): Boolean {
    val knownFreshwater = tankType in setOf(
      AquariumTankTaxonomy.TYPE_FISH,
      AquariumTankTaxonomy.TYPE_SHRIMP,
      AquariumTankTaxonomy.TYPE_PLANTED
    )
    return knownFreshwater || SmartCareTextMatcher.normalize(tankType) in setOf(
      "freshwater",
      "tatli su"
    )
  }

  private const val MATERIAL_CATEGORY_FERTILIZER = AquariumMaterialCategory.FERTILIZER
  private const val MATERIAL_CATEGORY_FILTER = AquariumMaterialCategory.FILTER
  private const val MATERIAL_CATEGORY_LIGHT = AquariumMaterialCategory.LIGHT
}
