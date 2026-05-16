package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.ViewModel

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
            Şimdilik burada database kaydı yok.

            Sonra buraya:
            - Room insert
            - Repository çağrısı
            - AquariumFragment listesini güncelleme
            eklenecek.
        */
    }
}