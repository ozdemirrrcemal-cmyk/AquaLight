package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode

object LightTransitionModeUiText {

    fun title(
        mode: LightCurveTransitionMode
    ): String {
        return when (mode) {
            LightCurveTransitionMode.LINEAR -> "Linear Transition"
            LightCurveTransitionMode.SMOOTH -> "Smooth Transition"
            LightCurveTransitionMode.NATURAL -> "Natural Transition"
        }
    }

    fun subtitle(
        mode: LightCurveTransitionMode
    ): String {
        return when (mode) {
            LightCurveTransitionMode.LINEAR -> "Direct ramp between selected times"
            LightCurveTransitionMode.SMOOTH -> "App generates soft device points"
            LightCurveTransitionMode.NATURAL -> "App generates sunrise/sunset device points"
        }
    }
}
