package com.aqua.aqualight.data.aquarium.catalog.material

data class AquariumMaterial(
    val id: String,
    val name: String,
    val brand: String,
    val categoryKey: String,
    val categoryTitle: String,
    val keywords: List<String> = emptyList()
)
