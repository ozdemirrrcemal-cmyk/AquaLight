package com.aqua.aqualight.ui.tabs.aquarium.model

data class SavedAquariumTank(
    val id: Long,
    val name: String,
    val description: String,
    val photoUri: String?,
    val setupDateMillis: Long?,
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val volumeUnit: String,
    val tankType: String,
    val tankStyle: String,
    val createdAtMillis: Long,
    val plants: List<SavedAquariumPlant>,
    val materials: List<SavedAquariumMaterial>
)

data class SavedAquariumPlant(
    val id: Long,
    val plantName: String,
    val category: String,
    val markerX: Float,
    val markerY: Float
)

data class SavedAquariumMaterial(
    val id: Long,
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String,
    val note: String
)