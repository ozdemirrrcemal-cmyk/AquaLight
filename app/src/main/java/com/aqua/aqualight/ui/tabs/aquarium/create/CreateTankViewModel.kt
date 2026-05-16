package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag

class CreateTankViewModel : ViewModel() {

    var tankDraft = TankDraft()
        private set

    fun updateTankName(name: String) {
        tankDraft = tankDraft.copy(
            name = name
        )
    }

    fun updateTankDescription(description: String) {
        tankDraft = tankDraft.copy(
            description = description
        )
    }

    fun updateTankPhoto(photoUri: String?) {
        tankDraft = tankDraft.copy(
            photoUri = photoUri
        )
    }

    fun updateTankPlants(plants: List<TankPlantTag>) {
        tankDraft = tankDraft.copy(
            plants = plants.toList()
        )
    }

    fun updateTankMaterial(material: String) {
        tankDraft = tankDraft.copy(
            material = material
        )
    }

    fun updateTankInfo(info: String) {
        tankDraft = tankDraft.copy(
            info = info
        )
    }

    fun completeTank() {
        /*
            Başlangıç aşaması:
            Şimdilik burada kalıcı kayıt yok.

            Complete aşamasında hazır olacak bilgiler:

            tankDraft.name
            tankDraft.description
            tankDraft.photoUri
            tankDraft.plants
            tankDraft.material
            tankDraft.info

            Sonra buraya:
            - UserPreferencesManager addTank
            - Proto DataStore kayıt
            - AquariumFragment liste güncelleme
            eklenecek.
        */
    }
}