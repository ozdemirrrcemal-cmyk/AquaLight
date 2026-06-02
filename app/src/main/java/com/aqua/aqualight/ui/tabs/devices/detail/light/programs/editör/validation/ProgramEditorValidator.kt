package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorDraft

object ProgramEditorValidator {

    fun validate(
        draft: ProgramEditorDraft
    ): ProgramEditorValidationResult {
        if (draft.repeatDays.isEmpty()) {
            return invalid(
                messageRes = R.string.light_editor_error_repeat_day_required
            )
        }

        if (draft.curvePoints.isEmpty()) {
            return invalid(
                messageRes = R.string.light_editor_error_curve_points_required
            )
        }

        val startPoint =
            draft.curvePoints.firstOrNull { point ->
                point.kind == ProgramEditorCurvePointKind.START
            }

        val peakStartPoint =
            draft.curvePoints.firstOrNull { point ->
                point.kind == ProgramEditorCurvePointKind.PEAK_START
            }

        val peakEndPoint =
            draft.curvePoints.firstOrNull { point ->
                point.kind == ProgramEditorCurvePointKind.PEAK_END
            }

        val endPoint =
            draft.curvePoints.firstOrNull { point ->
                point.kind == ProgramEditorCurvePointKind.END
            }

        if (
            startPoint == null ||
            peakStartPoint == null ||
            peakEndPoint == null ||
            endPoint == null
        ) {
            return invalid(
                messageRes = R.string.light_editor_error_default_points_required
            )
        }

        if (
            !(
                startPoint.minuteOfDay <
                    peakStartPoint.minuteOfDay &&
                    peakStartPoint.minuteOfDay <
                    peakEndPoint.minuteOfDay &&
                    peakEndPoint.minuteOfDay <
                    endPoint.minuteOfDay
                )
        ) {
            return invalid(
                messageRes = R.string.light_editor_error_point_order_invalid
            )
        }

        if (startPoint.masterPercent != MIN_INTENSITY_PERCENT) {
            return invalid(
                messageRes = R.string.light_editor_error_start_must_be_zero
            )
        }

        if (endPoint.masterPercent != MIN_INTENSITY_PERCENT) {
            return invalid(
                messageRes = R.string.light_editor_error_end_must_be_zero
            )
        }

        if (
            peakStartPoint.masterPercent <= MIN_INTENSITY_PERCENT ||
            peakEndPoint.masterPercent <= MIN_INTENSITY_PERCENT
        ) {
            return invalid(
                messageRes = R.string.light_editor_error_peak_must_be_above_zero
            )
        }

        val hasDuplicateMinutes =
            draft.curvePoints
                .groupBy { point ->
                    point.minuteOfDay
                }
                .any { entry ->
                    entry.value.size > 1
                }

        if (hasDuplicateMinutes) {
            return invalid(
                messageRes = R.string.light_editor_error_duplicate_point_time
            )
        }

        val photoperiodMinutes =
            endPoint.minuteOfDay - startPoint.minuteOfDay

        if (photoperiodMinutes < MIN_PHOTOPERIOD_MINUTES) {
            return invalid(
                messageRes = R.string.light_editor_error_photoperiod_too_short
            )
        }

        if (photoperiodMinutes > MAX_PHOTOPERIOD_MINUTES) {
            return invalid(
                messageRes = R.string.light_editor_error_photoperiod_too_long
            )
        }

        val hasInvalidCustomPoint =
            draft.curvePoints.any { point ->
                point.kind == ProgramEditorCurvePointKind.CUSTOM &&
                    (
                        point.minuteOfDay <= startPoint.minuteOfDay ||
                            point.minuteOfDay >= endPoint.minuteOfDay
                        )
            }

        if (hasInvalidCustomPoint) {
            return invalid(
                messageRes = R.string.light_editor_error_custom_point_out_of_range
            )
        }

        return ProgramEditorValidationResult.Valid
    }

    private fun invalid(
        messageRes: Int
    ): ProgramEditorValidationResult.Invalid {
        return ProgramEditorValidationResult.Invalid(
            messageRes = messageRes
        )
    }

    private const val MIN_INTENSITY_PERCENT = 0

    private const val MINUTES_IN_HOUR = 60
    private const val MIN_PHOTOPERIOD_MINUTES = 1 * MINUTES_IN_HOUR
    private const val MAX_PHOTOPERIOD_MINUTES = 18 * MINUTES_IN_HOUR
}