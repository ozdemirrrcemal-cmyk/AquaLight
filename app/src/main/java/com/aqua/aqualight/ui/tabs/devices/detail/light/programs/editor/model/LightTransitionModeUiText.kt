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
            LightCurveTransitionMode.LINEAR -> "Normal mode · four time anchors only"
            LightCurveTransitionMode.SMOOTH -> "Soft start and soft end ramp"
            LightCurveTransitionMode.NATURAL -> "Sunrise-like natural ramp"
        }
    }
}
