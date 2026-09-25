package com.aqua.aqualight.data.aquarium.model

import com.aqua.aqualight.application.aquarium.AquariumIdGenerator

data class TankPlantTag(
    val id: Long = AquariumIdGenerator.newLong(),
    val catalogId: String,
    val plantName: String,
    val category: String,
    val markerX: Float = 0.5f,
    val markerY: Float = 0.5f,
    val photoUri: String? = null
)
