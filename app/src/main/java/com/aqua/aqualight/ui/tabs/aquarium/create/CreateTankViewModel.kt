package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.google.gson.Gson

class CreateTankViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gson = Gson()

    var tankDraft: AquariumTankDraft = restoreDraft()
        private set

    var isTankSizeConfirmed: Boolean = savedStateHandle[KEY_TANK_SIZE_CONFIRMED] ?: false
        private set

    fun updateTankName(name: String) = updateDraft { copy(name = name) }

    fun updateTankDescription(description: String) = updateDraft {
        copy(description = description)
    }

    fun updateTankPhoto(photoUri: String?) = updateDraft { copy(photoUri = photoUri) }

    fun updateTankPlants(plants: List<AquariumPlantTag>) = updateDraft {
        copy(plants = plants.toList())
    }

    fun updateTankMaterials(materials: List<AquariumMaterialSelection>) = updateDraft {
        copy(materials = materials.toList())
    }

    fun updateTankMaterialsForCategory(
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    ) {
        val otherMaterials = tankDraft.materials.filterNot {
            it.categoryKey == categoryKey
        }
        updateDraft {
            copy(materials = otherMaterials + materials)
        }
    }

    fun getMaterialsByCategory(
        categoryKey: String
    ): List<AquariumMaterialSelection> {
        return tankDraft.materials.filter {
            it.categoryKey == categoryKey
        }
    }

    fun updateTankInfo(info: String) = updateDraft { copy(info = info) }

    fun updateSetupDate(setupDateEpochDay: Long?) = updateDraft {
        copy(setupDateEpochDay = setupDateEpochDay)
    }

    fun updateTankSize(
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String = tankDraft.sizeUnit
    ) {
        updateDraft {
            copy(
                widthCm = widthCm,
                lengthCm = lengthCm,
                heightCm = heightCm,
                sizeUnit = sizeUnit.ifBlank { "cm" }
            )
        }
        isTankSizeConfirmed = true
        savedStateHandle[KEY_TANK_SIZE_CONFIRMED] = true
    }

    fun updateSizeUnit(sizeUnit: String) = updateDraft {
        copy(sizeUnit = sizeUnit.ifBlank { "cm" })
    }

    fun updateVolumeUnit(volumeUnit: String) = updateDraft {
        copy(volumeUnit = volumeUnit)
    }

    fun updateTankType(tankType: String) = updateDraft { copy(tankType = tankType) }

    fun updateTankStyle(tankStyle: String) = updateDraft { copy(tankStyle = tankStyle) }

    fun completeTank() {
        savedStateHandle.remove<String>(KEY_DRAFT_JSON)
        savedStateHandle.remove<Boolean>(KEY_TANK_SIZE_CONFIRMED)
    }

    private inline fun updateDraft(
        transform: AquariumTankDraft.() -> AquariumTankDraft
    ) {
        tankDraft = tankDraft.transform()
        savedStateHandle[KEY_DRAFT_JSON] = gson.toJson(tankDraft)
    }

    private fun restoreDraft(): AquariumTankDraft {
        val encoded = savedStateHandle.get<String>(KEY_DRAFT_JSON)
            ?.takeIf(String::isNotBlank)
            ?: run {
                savedStateHandle.remove<Boolean>(KEY_TANK_SIZE_CONFIRMED)
                return AquariumTankDraft()
            }
        return runCatching {
            gson.fromJson(encoded, AquariumTankDraft::class.java)
        }.getOrElse {
            savedStateHandle.remove<String>(KEY_DRAFT_JSON)
            savedStateHandle.remove<Boolean>(KEY_TANK_SIZE_CONFIRMED)
            AquariumTankDraft()
        }
    }

    private companion object {
        const val KEY_DRAFT_JSON = "createTank.draftJson"
        const val KEY_TANK_SIZE_CONFIRMED = "createTank.tankSizeConfirmed"
    }
}
