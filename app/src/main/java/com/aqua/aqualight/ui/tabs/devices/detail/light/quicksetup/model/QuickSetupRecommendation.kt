package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft

data class QuickSetupRecommendation(
    val title: String,
    val profileLabel: String,
    val goalLabel: String,

    val setupPhaseLabel: String,
    val techLevelLabel: String,
    val durationLabel: String,
    val intensityLabel: String,
    val confidenceLabel: String,

    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,

    val channelValues: LightCurveChannelValues,

    val tankSummary: List<String>,
    val reasoningNotes: List<String>,
    val warnings: List<String>,

    val draft: LightProgramDraft
)