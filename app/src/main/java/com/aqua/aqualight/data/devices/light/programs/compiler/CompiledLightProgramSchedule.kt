package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode

data class CompiledLightProgramSchedule(
    val programId: String,
    val programName: String,
    val transitionMode: LightProgramTransitionMode,
    val points: List<CompiledLightProgramPoint>,
    val hash: String
)

data class CompiledLightProgramPoint(
    val minuteOfDay: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    val timeText: String
        get() {
            val safeMinute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            val hour = safeMinute / 60
            val minute = safeMinute % 60
            return "%02d:%02d:00".format(hour, minute)
        }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
