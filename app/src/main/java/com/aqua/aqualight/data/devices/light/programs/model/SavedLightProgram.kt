package com.aqua.aqualight.data.devices.light.programs.model

import com.aqua.aqualight.data.devices.light.model.LightRgbwChannels

data class SavedLightProgram(
    val id: String,
    val ownerUid: String = "",
    val deviceId: Long = 0L,
    val deviceUid: String = "",
    val productId: String = "",
    val name: String,
    val isActive: Boolean = false,
    val startMinute: Int,
    val peakStartMinute: Int,
    val peakEndMinute: Int,
    val endMinute: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val repeatMode: LightProgramRepeatMode = LightProgramRepeatMode.EVERY,
    val selectedDays: Set<Int> = ALL_DAYS,
    val transitionMode: LightProgramTransitionMode = LightProgramTransitionMode.NATURAL,
    val syncState: LightProgramSyncState = LightProgramSyncState.LOCAL_ONLY,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastLoadedAtMillis: Long = 0L,
    val lastLoadedHash: String = ""
) {

    val channels: LightRgbwChannels
        get() = LightRgbwChannels(
            red = red,
            green = green,
            blue = blue,
            white = white
        )

    val peakPercent: Int
        get() = maxOf(
            red,
            green,
            blue,
            white
        ).coerceIn(0, 100)

    companion object {
        val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
