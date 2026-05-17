package com.aqua.aqualight.ui.tabs.aquarium.create

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.TankMaterialSelection
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag

data class TankDraft(
    val name: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val plants: List<TankPlantTag> = emptyList(),

    val materials: List<TankMaterialSelection> = emptyList(),

    val info: String = "",

    val setupDateMillis: Long? = null,
    val widthCm: Int = 10,
    val lengthCm: Int = 10,
    val heightCm: Int = 10,
    val volumeUnit: String = "L",
    val tankType: String = "Fish",
    val tankStyle: String = ""
)