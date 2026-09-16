package com.aqua.aqualight.data.aquarium.model

import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

data class TankPlantTag(
    val id: Long = AquariumIdGenerator.newLong(),
    val catalogId: String,
    val plantName: String,
    val category: String,
    val lightDemand: AquariumPlantLightDemand,
    val plantedAtEpochDay: Long? = null,
    val markerX: Float = 0.5f,
    val markerY: Float = 0.5f
)
