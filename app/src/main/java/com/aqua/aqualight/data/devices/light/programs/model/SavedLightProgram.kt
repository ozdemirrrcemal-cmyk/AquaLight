package com.aqua.aqualight.data.devices.light.programs.model

data class SavedLightProgram(
    val id: String,
    val ownerUid: String = "",
    val deviceId: Long = 0L,
    val deviceUid: String = "",
    val productId: String = "",
    val name: String,
    val active: Boolean,
    val startMinute: Int,
    val peakStartMinute: Int,
    val peakEndMinute: Int,
    val endMinute: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val repeatMode: RepeatMode,
    val repeatDays: Set<Int>,
    val transitionMode: LightCurveTransitionMode,
    val createdAt: Long,
    val updatedAt: Long
) {
    val channelValues: LightCurveChannelValues
        get() = LightCurveChannelValues(
            red = red,
            green = green,
            blue = blue,
            white = white
        ).normalized()

    fun toDraft(): LightProgramDraft {
        return LightProgramDraft(
            start = LightCurvePoint.fromTotalMinutes(startMinute),
            peakStart = LightCurvePoint.fromTotalMinutes(peakStartMinute),
            peakEnd = LightCurvePoint.fromTotalMinutes(peakEndMinute),
            end = LightCurvePoint.fromTotalMinutes(endMinute),
            channelValues = channelValues,
            repeatMode = repeatMode,
            selectedDays = repeatDays,
            transitionMode = transitionMode
        )
    }
}
