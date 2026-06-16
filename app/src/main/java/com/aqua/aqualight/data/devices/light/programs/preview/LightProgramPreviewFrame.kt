package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint

/**
 * One editor preview frame calculated from the same compiled point schedule
 * that will later be uploaded to the controller.
 */
data class LightProgramPreviewFrame(
    val progressPercent: Int,
    val simulatedMinuteOfDay: Int,
    val simulatedTime: LightCurvePoint,
    val outputValues: LightCurveChannelValues
)
