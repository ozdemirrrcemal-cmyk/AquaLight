package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag

class CreateTankViewModel : ViewModel() {

    var tankDraft = TankDraft()
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

    fun updateTankPlants(plants: List<TankPlantTag>) {
        tankDraft = tankDraft.copy(plants = plants.toList())
    }

    fun updateTankMaterial(material: String) {
        tankDraft = tankDraft.copy(material = material)
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
        heightCm: Int
    ) {
        tankDraft = tankDraft.copy(
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm
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

    fun completeTank() {
        /*
          Kalıcı kayıt yok şimdilik.
          Hazır bilgiler:
          tankDraft.name
          tankDraft.description
          tankDraft.photoUri
          tankDraft.plants
          tankDraft.material

          Step 5:
          tankDraft.setupDateMillis
          tankDraft.widthCm
          tankDraft.lengthCm
          tankDraft.heightCm
          tankDraft.volumeUnit
          tankDraft.tankType
          tankDraft.tankStyle
        */
    }
}