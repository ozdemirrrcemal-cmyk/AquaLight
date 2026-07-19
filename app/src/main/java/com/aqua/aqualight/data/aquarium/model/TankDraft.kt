package com.aqua.aqualight.data.aquarium.model

data class TankDraft(
    val name: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val plants: List<TankPlantTag> = emptyList(),
    val materials: List<TankMaterialSelection> = emptyList(),
    val info: String = "",
    val setupDateEpochDay: Long? = null,
    val widthCm: Int = 10,
    val lengthCm: Int = 10,
    val heightCm: Int = 10,
    val sizeUnit: String = "cm",
    val volumeUnit: String = "L",
    val tankType: String = "",
    val tankStyle: String = ""
)
