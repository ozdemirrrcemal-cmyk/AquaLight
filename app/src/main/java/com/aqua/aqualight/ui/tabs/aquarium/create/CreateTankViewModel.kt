package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankDraft

class CreateTankViewModel : ViewModel() {

  var tankDraft = AquariumTankDraft()
    private set

  fun updateTankName(name: String) {
    tankDraft = tankDraft.copy(name = name)
  }

  fun updateTankDescription(description: String) {
    tankDraft = tankDraft.copy(description = description)
  }

  fun updateTankPhoto(photoUri: String?) {
    tankDraft = tankDraft.copy(photoUri = photoUri)
  }

  fun updateTankPlants(plants: List<AquariumPlantTag>) {
    tankDraft = tankDraft.copy(plants = plants.toList())
  }

  fun updateTankMaterials(materials: List<AquariumMaterialSelection>) {
    tankDraft = tankDraft.copy(materials = materials.toList())
  }

  fun updateTankMaterialsForCategory(
    categoryKey: String,
    materials: List<AquariumMaterialSelection>
  ) {
    val otherMaterials = tankDraft.materials.filterNot {
      it.categoryKey == categoryKey
    }

    tankDraft = tankDraft.copy(
      materials = otherMaterials + materials
    )
  }

  fun getMaterialsByCategory(
    categoryKey: String
  ): List<AquariumMaterialSelection> {
    return tankDraft.materials.filter {
      it.categoryKey == categoryKey
    }
  }

  fun updateTankInfo(info: String) {
    tankDraft = tankDraft.copy(info = info)
  }

  fun updateSetupDate(setupDateMillis: Long?) {
    tankDraft = tankDraft.copy(setupDateMillis = setupDateMillis)
  }

  fun updateTankSize(
    widthCm: Int,
    lengthCm: Int,
    heightCm: Int,
    sizeUnit: String = tankDraft.sizeUnit
  ) {
    tankDraft = tankDraft.copy(
      widthCm = widthCm,
      lengthCm = lengthCm,
      heightCm = heightCm,
      sizeUnit = sizeUnit.ifBlank { "cm" }
    )
  }

  fun updateSizeUnit(sizeUnit: String) {
    tankDraft = tankDraft.copy(
      sizeUnit = sizeUnit.ifBlank { "cm" }
    )
  }

  fun updateVolumeUnit(volumeUnit: String) {
    tankDraft = tankDraft.copy(volumeUnit = volumeUnit)
  }

  fun updateTankType(tankType: String) {
    tankDraft = tankDraft.copy(tankType = tankType)
  }

  fun updateTankStyle(tankStyle: String) {
    tankDraft = tankDraft.copy(tankStyle = tankStyle)
  }

  fun completeTank() = Unit
}
