package com.aqua.aqualight.data.aquarium.model

import com.aqua.aqualight.data.aquarium.util.AquariumIdGenerator

data class TankMaterialSelection(
    val id: Long = AquariumIdGenerator.newLong(),
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String = "",
    val note: String = ""
)
