package com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint

object LightProgramTimeMath {

    const val DAY_END_MINUTES = 24 * 60

    fun startMinutes(
        point: LightCurvePoint
    ): Int {
        return point.totalMinutes
    }

    fun normalMinutes(
        point: LightCurvePoint
    ): Int {
        return point.totalMinutes
    }

    fun endMinutes(
        point: LightCurvePoint
    ): Int {
        return if (point.hour == 0 && point.minute == 0) {
            DAY_END_MINUTES
        } else {
            point.totalMinutes
        }
    }

    fun endLabel(
        point: LightCurvePoint
    ): String {
        return if (point.hour == 0 && point.minute == 0) {
            "24:00"
        } else {
            point.label
        }
    }
}