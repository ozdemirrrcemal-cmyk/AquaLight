package com.aqua.aqualight.data.aquarium.model

import com.aqua.aqualight.application.aquarium.AquariumIdGenerator

data class SavedAquariumTank(
    val id: Long,
    val ownerUid: String = "",
    val name: String,
    val description: String,
    val photoUri: String?,
    val setupDateEpochDay: Long?,
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val sizeUnit: String = "cm",
    val volumeUnit: String,
    val tankType: String,
    val tankStyle: String,
    val createdAtMillis: Long,
    val smartCareEnabled: Boolean = true,
    val careRemindersEnabled: Boolean = true,
    val plants: List<SavedAquariumPlant>,
    val materials: List<SavedAquariumMaterial>,
    val livestock: List<SavedAquariumLivestock> = emptyList()
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

data class SavedAquariumLivestock(
    val id: Long = AquariumIdGenerator.newLong(),
    val name: String = "",
    val category: String = "",
    val quantity: Int = 1,
    val addedDateEpochDay: Long? = null,
    val note: String = ""
)
