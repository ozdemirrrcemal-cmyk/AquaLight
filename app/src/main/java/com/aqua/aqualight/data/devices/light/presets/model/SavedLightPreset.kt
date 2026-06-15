package com.aqua.aqualight.data.devices.light.presets.model

data class SavedLightPreset(
    val id: String,
    val ownerUid: String = "",
    val deviceId: Long = 0L,
    val deviceUid: String = "",
    val productId: String = "",
    val name: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val createdAt: Long,
    val updatedAt: Long
)
