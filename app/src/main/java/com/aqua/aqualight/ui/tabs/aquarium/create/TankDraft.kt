package com.aqua.aqualight.ui.tabs.aquarium.create

import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag

data class TankDraft(
    val name: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val plants: List<TankPlantTag> = emptyList(),
    val material: String = "",
    val info: String = ""
)