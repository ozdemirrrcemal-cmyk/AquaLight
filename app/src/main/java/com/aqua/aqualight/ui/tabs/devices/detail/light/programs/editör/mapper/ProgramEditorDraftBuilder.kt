package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.AcclimationState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CurvePointState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.ACCLIMATION_MAX_START_PERCENT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.ACCLIMATION_MIN_START_PERCENT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.MAX_PERCENT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.MINUTES_IN_DAY
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramAcclimationDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramChannelBalanceDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramCurvePointDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramSaveDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode

object ProgramEditorDraftBuilder {

    fun build(
        name: String,
        isProMode: Boolean,
        repeatMode: RepeatMode,
        repeatDays: Set<Int>,
        rampSmoothing: RampSmoothing,
        simpleCurvePoints: List<CurvePointState>,
        proChannelCurves: Map<ProChannel, List<CurvePointState>>,
        channelBalance: ProgramChannelBalanceDraft,
        acclimationState: AcclimationState
    ): ProgramSaveDraft {
        return ProgramSaveDraft(
            name = name.trim(),
            mode =
                if (isProMode) {
                    ProgramEditorMode.PRO
                } else {
                    ProgramEditorMode.SIMPLE
                },
            repeatMode = repeatMode,
            repeatDays = repeatDays,
            rampSmoothing = rampSmoothing,
            simpleCurve = simpleCurvePoints.toProgramCurveDraftPoints(),
            proCurves =
                proChannelCurves.mapValues { entry ->
                    entry.value.toProgramCurveDraftPoints()
                },
            channelBalance = channelBalance,
            acclimation =
                buildAcclimationDraft(
                    acclimationState = acclimationState
                )
        )
    }

    private fun buildAcclimationDraft(
        acclimationState: AcclimationState
    ): ProgramAcclimationDraft {
        return ProgramAcclimationDraft(
            enabled = acclimationState.enabled,
            durationDays =
                if (acclimationState.enabled) {
                    acclimationState.durationDays
                } else {
                    0
                },
            startIntensityPercent =
                if (acclimationState.enabled) {
                    acclimationState.startIntensityPercent.coerceIn(
                        minimumValue = ACCLIMATION_MIN_START_PERCENT,
                        maximumValue = ACCLIMATION_MAX_START_PERCENT
                    )
                } else {
                    MAX_PERCENT
                }
        )
    }

    private fun List<CurvePointState>.toProgramCurveDraftPoints(): List<ProgramCurvePointDraft> {
        return sortedWith(
            compareBy<CurvePointState> { point ->
                timeToMinutes(
                    time = point.time
                )
            }.thenBy { point ->
                point.kind.sortOrder
            }
        ).map { point ->
            ProgramCurvePointDraft(
                id = point.id,
                label = point.label,
                time = point.time,
                minute =
                    timeToMinutes(
                        time = point.time
                    ),
                intensity =
                    point.intensity.coerceIn(
                        minimumValue = 0,
                        maximumValue = MAX_PERCENT
                    ),
                kind = point.kind,
                canDelete = point.canDelete
            )
        }
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute)
            .coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )
    }
}