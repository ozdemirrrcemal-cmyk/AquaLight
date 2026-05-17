package com.aqua.aqualight.ui.tabs.aquarium.create.materials

data class TankMaterialSelection(
    val id: Long = System.nanoTime(),
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String = "",
    val note: String = ""
)