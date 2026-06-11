package com.aqua.aqualight.ui.tabs.aquarium.create.materials

data class AquariumMaterial(
    val id: String,
    val name: String,
    val brand: String,
    val categoryKey: String,
    val categoryTitle: String,
    val keywords: List<String> = emptyList()
)