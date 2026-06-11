package com.aqua.aqualight.data.aquarium.model

data class TankMaterialSelection(
    val id: Long = System.nanoTime(),
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String = "",
    val note: String = ""
)
