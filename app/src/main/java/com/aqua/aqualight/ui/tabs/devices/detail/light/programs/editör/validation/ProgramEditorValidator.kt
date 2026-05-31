package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.MAX_PERCENT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.POINT_ID_END
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.POINT_ID_PEAK_END
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.POINT_ID_PEAK_START
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.POINT_ID_START
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.REQUIRED_DEFAULT_POINT_COUNT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramCurvePointDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramSaveDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramValidationResult

object ProgramEditorValidator {

    fun validate(
        draft: ProgramSaveDraft
    ): ProgramValidationResult {
        if (draft.name.isBlank()) {
            return ProgramValidationResult.invalid(
                message = "Program name cannot be empty"
            )
        }

        if (draft.repeatDays.isEmpty()) {
            return ProgramValidationResult.invalid(
                message = "Select at least one repeat day"
            )
        }

        return when (draft.mode) {
            ProgramEditorMode.SIMPLE -> {
                validateCurveDraft(
                    curveName = "Simple curve",
                    points = draft.simpleCurve
                )
            }

            ProgramEditorMode.PRO -> {
                ProChannel.values().forEach { channel ->
                    val validation =
                        validateCurveDraft(
                            curveName = "${channel.label} curve",
                            points = draft.proCurves[channel].orEmpty()
                        )

                    if (!validation.isValid) {
                        return validation
                    }
                }

                ProgramValidationResult.valid()
            }
        }
    }

    private fun validateCurveDraft(
        curveName: String,
        points: List<ProgramCurvePointDraft>
    ): ProgramValidationResult {
        if (points.size < REQUIRED_DEFAULT_POINT_COUNT) {
            return ProgramValidationResult.invalid(
                message = "$curveName needs at least 4 points"
            )
        }

        val start =
            points.draftPointById(
                pointId = POINT_ID_START
            ) ?: return ProgramValidationResult.invalid(
                message = "$curveName is missing Start point"
            )

        val peakStart =
            points.draftPointById(
                pointId = POINT_ID_PEAK_START
            ) ?: return ProgramValidationResult.invalid(
                message = "$curveName is missing Peak start point"
            )

        val peakEnd =
            points.draftPointById(
                pointId = POINT_ID_PEAK_END
            ) ?: return ProgramValidationResult.invalid(
                message = "$curveName is missing Peak end point"
            )

        val end =
            points.draftPointById(
                pointId = POINT_ID_END
            ) ?: return ProgramValidationResult.invalid(
                message = "$curveName is missing End point"
            )

        val defaultOrderIsValid =
            start.minute < peakStart.minute &&
                peakStart.minute < peakEnd.minute &&
                peakEnd.minute < end.minute

        if (!defaultOrderIsValid) {
            return ProgramValidationResult.invalid(
                message = "$curveName order must be Start < Peak start < Peak end < End"
            )
        }

        val hasInvalidIntensity =
            points.any { point ->
                point.intensity !in 0..MAX_PERCENT
            }

        if (hasInvalidIntensity) {
            return ProgramValidationResult.invalid(
                message = "$curveName intensity must be between 0% and 100%"
            )
        }

        val hasDuplicateTime =
            points
                .groupBy { point ->
                    point.minute
                }
                .any { entry ->
                    entry.value.size > 1
                }

        if (hasDuplicateTime) {
            return ProgramValidationResult.invalid(
                message = "$curveName cannot have two points at the same time"
            )
        }

        val hasOutOfRangeIntermediatePoint =
            points.any { point ->
                point.kind == CurvePointKind.INTERMEDIATE &&
                    (point.minute <= start.minute || point.minute >= end.minute)
            }

        if (hasOutOfRangeIntermediatePoint) {
            return ProgramValidationResult.invalid(
                message = "$curveName extra points must stay between Start and End"
            )
        }

        return ProgramValidationResult.valid()
    }

    private fun List<ProgramCurvePointDraft>.draftPointById(
        pointId: String
    ): ProgramCurvePointDraft? {
        return firstOrNull { point ->
            point.id == pointId
        }
    }
}