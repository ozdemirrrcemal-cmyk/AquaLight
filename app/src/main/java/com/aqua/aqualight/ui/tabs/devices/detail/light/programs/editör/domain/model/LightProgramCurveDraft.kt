package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel

data class LightProgramCurveDraft(
    val channel: LightCurveChannel,
    val points: List<LightProgramCurvePointDraft>
)