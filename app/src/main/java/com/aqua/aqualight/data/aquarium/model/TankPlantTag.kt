package com.aqua.aqualight.data.aquarium.model

data class TankPlantTag(
    val id: Long = System.nanoTime(),
    val plantName: String,
    val category: String,
    val markerX: Float = 0.5f,
    val markerY: Float = 0.5f
)
