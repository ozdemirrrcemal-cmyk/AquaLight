package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramRepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.RepeatMode

fun RepeatMode.toDataRepeatMode(): LightProgramRepeatMode {
    return when (this) {
        RepeatMode.EVERY -> LightProgramRepeatMode.EVERY
        RepeatMode.WEEK -> LightProgramRepeatMode.WEEK
        RepeatMode.WEEKEND -> LightProgramRepeatMode.WEEKEND
        RepeatMode.CUSTOM -> LightProgramRepeatMode.CUSTOM
    }
}

fun LightProgramRepeatMode.toUiRepeatMode(): RepeatMode {
    return when (this) {
        LightProgramRepeatMode.EVERY -> RepeatMode.EVERY
        LightProgramRepeatMode.WEEK -> RepeatMode.WEEK
        LightProgramRepeatMode.WEEKEND -> RepeatMode.WEEKEND
        LightProgramRepeatMode.CUSTOM -> RepeatMode.CUSTOM
    }
}

fun LightCurveTransitionMode.toDataTransitionMode(): LightProgramTransitionMode {
    return when (this) {
        LightCurveTransitionMode.LINEAR -> LightProgramTransitionMode.LINEAR
        LightCurveTransitionMode.SMOOTH -> LightProgramTransitionMode.SMOOTH
        LightCurveTransitionMode.NATURAL -> LightProgramTransitionMode.NATURAL
    }
}

fun LightProgramTransitionMode.toUiTransitionMode(): LightCurveTransitionMode {
    return when (this) {
        LightProgramTransitionMode.LINEAR -> LightCurveTransitionMode.LINEAR
        LightProgramTransitionMode.SMOOTH -> LightCurveTransitionMode.SMOOTH
        LightProgramTransitionMode.NATURAL -> LightCurveTransitionMode.NATURAL
    }
}
