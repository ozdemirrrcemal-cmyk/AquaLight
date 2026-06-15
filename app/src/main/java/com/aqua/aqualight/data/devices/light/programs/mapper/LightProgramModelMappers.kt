package com.aqua.aqualight.data.devices.light.programs.mapper

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramRepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode

fun String.toLightProgramRepeatMode(): LightProgramRepeatMode {
    return LightProgramRepeatMode.fromStorage(this)
}

fun String.toLightProgramTransitionMode(): LightProgramTransitionMode {
    return LightProgramTransitionMode.fromStorage(this)
}
