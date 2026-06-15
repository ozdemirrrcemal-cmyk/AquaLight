package com.aqua.aqualight.data.devices.light.programs.model

/**
 * Data/domain representation of a light program draft.
 *
 * Keep this class UI-free on purpose. Screens may use graph-specific models,
 * but repository/store/compiler code must speak this primitive contract so a
 * firmware/API switch only changes the data/runtime layer.
 */
data class LightProgramDraft(
    val startMinute: Int,
    val peakStartMinute: Int,
    val peakEndMinute: Int,
    val endMinute: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val repeatMode: LightProgramRepeatMode = LightProgramRepeatMode.EVERY,
    val selectedDays: Set<Int> = SavedLightProgram.ALL_DAYS,
    val transitionMode: LightProgramTransitionMode = LightProgramTransitionMode.NATURAL
) {

    fun normalizedForCurrentFirmware(): LightProgramDraft {
        return copy(
            startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY - 1),
            peakStartMinute = peakStartMinute.coerceIn(0, MINUTES_PER_DAY - 1),
            peakEndMinute = peakEndMinute.coerceIn(0, MINUTES_PER_DAY - 1),
            endMinute = endMinute.coerceIn(1, MINUTES_PER_DAY),
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100),
            // Firmware does not support repeat days yet. Keep the model ready,
            // but compile/store current editor saves as all days.
            repeatMode = LightProgramRepeatMode.EVERY,
            selectedDays = SavedLightProgram.ALL_DAYS
        )
    }

    val maxChannelPercent: Int
        get() = maxOf(red, green, blue, white).coerceIn(0, 100)

    companion object {
        const val MINUTES_PER_DAY: Int = 24 * 60

        fun default(): LightProgramDraft {
            return LightProgramDraft(
                startMinute = 8 * 60,
                peakStartMinute = 10 * 60,
                peakEndMinute = 16 * 60,
                endMinute = 18 * 60,
                red = 0,
                green = 0,
                blue = 0,
                white = 0
            )
        }
    }
}
