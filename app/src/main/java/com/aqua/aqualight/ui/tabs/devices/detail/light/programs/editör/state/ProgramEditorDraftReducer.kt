package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.state

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightRepeatDay
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorChannelBalance
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramRampSmoothing
import kotlin.math.roundToInt

object ProgramEditorDraftReducer {

    fun setMode(
        draft: ProgramEditorDraft,
        mode: ProgramEditorMode
    ): ProgramEditorDraft {
        return draft.copy(
            mode = mode
        )
    }

    fun setRepeatDays(
        draft: ProgramEditorDraft,
        repeatDays: Set<LightRepeatDay>
    ): ProgramEditorDraft {
        return draft.copy(
            repeatDays = repeatDays
        )
    }

    fun setRampSmoothing(
        draft: ProgramEditorDraft,
        rampSmoothing: ProgramRampSmoothing
    ): ProgramEditorDraft {
        return draft.copy(
            rampSmoothing = rampSmoothing
        )
    }

    fun updateBalance(
        draft: ProgramEditorDraft,
        balance: ProgramEditorChannelBalance
    ): ProgramEditorDraft {
        return draft.copy(
            balance = balance,
            curvePoints =
            draft.curvePoints.map {
                point ->
                point.withScaledChannels(
                    balance = balance
                )
            }
        )
    }

    fun addCustomPoint(
        draft: ProgramEditorDraft,
        pointName: String,
        timeLabel: String,
        intensityPercent: Int?,
        fallbackCustomPointName: String
    ): ProgramEditorDraft {
        val minuteOfDay =
        timeLabelToMinutes(
            timeLabel = timeLabel
        )
        ?.normalizeToTimeStep()
        ?: suggestedCustomPointMinutes(
            draft = draft
        )

        val safeIntensity =
        intensityPercent
        ?.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )
        ?: draft.peakIntensityPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        val customPoint =
        ProgramEditorCurvePoint(
            kind = ProgramEditorCurvePointKind.CUSTOM,
            minuteOfDay = minuteOfDay,
            masterPercent = safeIntensity,
            red =
            scaledChannelOutput(
                channelPercent = draft.balance.red,
                masterPercent = safeIntensity
            ),
            green =
            scaledChannelOutput(
                channelPercent = draft.balance.green,
                masterPercent = safeIntensity
            ),
            blue =
            scaledChannelOutput(
                channelPercent = draft.balance.blue,
                masterPercent = safeIntensity
            ),
            white =
            scaledChannelOutput(
                channelPercent = draft.balance.white,
                masterPercent = safeIntensity
            ),
            name =
            pointName
            .trim()
            .ifBlank {
                fallbackCustomPointName
            }
        )

        return draft.withUpdatedPoints(
            points =
            draft.curvePoints
            .plus(customPoint)
            .sortedBy {
                point ->
                point.minuteOfDay
            }
        )
    }

    fun updateDefaultPoint(
        draft: ProgramEditorDraft,
        kind: ProgramEditorCurvePointKind,
        timeLabel: String,
        intensityPercent: Int?
    ): ProgramEditorDraft {
        if (kind == ProgramEditorCurvePointKind.CUSTOM) {
            return draft
        }

        val minuteOfDay =
        timeLabelToMinutes(
            timeLabel = timeLabel
        )
        ?.normalizeToTimeStep()
        ?: return draft

        val safeIntensity =
        intensityPercent
        ?.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )
        ?: return draft

        val updatedPoints =
        draft.curvePoints
        .map {
            point ->
            if (point.kind != kind) {
                point
            } else {
                point.copy(
                    minuteOfDay = minuteOfDay,
                    masterPercent = safeIntensity
                )
                .withScaledChannels(
                    balance = draft.balance
                )
            }
        }
        .sortedBy {
            point ->
            point.minuteOfDay
        }

        return draft.withUpdatedPoints(
            points = updatedPoints
        )
    }

    fun updateCustomPoint(
        draft: ProgramEditorDraft,
        customIndex: Int,
        pointName: String,
        timeLabel: String,
        intensityPercent: Int?,
        fallbackCustomPointName: String
    ): ProgramEditorDraft {
        val targetPoint =
        draft.curvePoints
        .customPointsSorted()
        .getOrNull(
            index = customIndex
        )
        ?: return draft

        val minuteOfDay =
        timeLabelToMinutes(
            timeLabel = timeLabel
        )
        ?.normalizeToTimeStep()
        ?: return draft

        val safeIntensity =
        intensityPercent
        ?.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )
        ?: return draft

        var updated = false

        val updatedPoints =
        draft.curvePoints
        .map {
            point ->
            if (!updated && point == targetPoint) {
                updated = true

                point.copy(
                    minuteOfDay = minuteOfDay,
                    masterPercent = safeIntensity,
                    name =
                    pointName
                    .trim()
                    .ifBlank {
                        point.name.ifBlank {
                            fallbackCustomPointName
                        }
                    }
                )
                .withScaledChannels(
                    balance = draft.balance
                )
            } else {
                point
            }
        }
        .sortedBy {
            point ->
            point.minuteOfDay
        }

        return draft.withUpdatedPoints(
            points = updatedPoints
        )
    }

    fun deleteCustomPoint(
        draft: ProgramEditorDraft,
        customIndex: Int
    ): ProgramEditorDraft {
        val targetPoint =
        draft.curvePoints
        .customPointsSorted()
        .getOrNull(
            index = customIndex
        )
        ?: return draft

        var deleted = false

        val updatedPoints =
        draft.curvePoints
        .filterNot {
            point ->
            if (!deleted && point == targetPoint) {
                deleted = true
                true
            } else {
                false
            }
        }
        .sortedBy {
            point ->
            point.minuteOfDay
        }

        return draft.withUpdatedPoints(
            points = updatedPoints
        )
    }

    fun suggestedCustomPointMinutes(
        draft: ProgramEditorDraft
    ): Int {
        val peakStart =
        draft.curvePoints.firstOrNull {
            point ->
            point.kind == ProgramEditorCurvePointKind.PEAK_START
        }?.minuteOfDay

        val peakEnd =
        draft.curvePoints.firstOrNull {
            point ->
            point.kind == ProgramEditorCurvePointKind.PEAK_END
        }?.minuteOfDay

        val suggestedMinutes =
        if (
            peakStart != null &&
            peakEnd != null &&
            peakEnd > peakStart
        ) {
            peakStart + ((peakEnd - peakStart) / 2)
        } else {
            DEFAULT_CUSTOM_POINT_MINUTES
        }

        return suggestedMinutes.normalizeToTimeStep()
    }

    private fun ProgramEditorDraft.withUpdatedPoints(
        points: List<ProgramEditorCurvePoint>
    ): ProgramEditorDraft {
        return copy(
            curvePoints = points,
            peakIntensityPercent =
            points
            .maxOfOrNull {
                point ->
                point.masterPercent
            }
            ?: peakIntensityPercent
        )
    }

    private fun ProgramEditorCurvePoint.withScaledChannels(
        balance: ProgramEditorChannelBalance
    ): ProgramEditorCurvePoint {
        val safeMasterPercent =
        masterPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        return copy(
            masterPercent = safeMasterPercent,
            red =
            scaledChannelOutput(
                channelPercent = balance.red,
                masterPercent = safeMasterPercent
            ),
            green =
            scaledChannelOutput(
                channelPercent = balance.green,
                masterPercent = safeMasterPercent
            ),
            blue =
            scaledChannelOutput(
                channelPercent = balance.blue,
                masterPercent = safeMasterPercent
            ),
            white =
            scaledChannelOutput(
                channelPercent = balance.white,
                masterPercent = safeMasterPercent
            )
        )
    }

    private fun List<ProgramEditorCurvePoint>.customPointsSorted(): List<ProgramEditorCurvePoint> {
        return filter {
            point ->
            point.kind == ProgramEditorCurvePointKind.CUSTOM
        }
        .sortedBy {
            point ->
            point.minuteOfDay
        }
    }

    private fun timeLabelToMinutes(
        timeLabel: String
    ): Int? {
        val parts =
        timeLabel
        .trim()
        .split(":")

        if (parts.size != 2) {
            return null
        }

        val hour =
        parts[0].toIntOrNull()
        ?: return null

        val minute =
        parts[1].toIntOrNull()
        ?: return null

        if (
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            return null
        }

        return (hour * MINUTES_IN_HOUR) + minute
    }

    private fun Int.normalizeToTimeStep(): Int {
        val safeMinutes =
        coerceIn(
            MINUTES_IN_DAY_MIN,
            MINUTES_IN_DAY_MAX
        )

        val roundedMinutes =
        ((safeMinutes + TIME_STEP_ROUNDING_OFFSET) / TIME_STEP_MINUTES) *
            TIME_STEP_MINUTES

        return roundedMinutes
        .coerceAtLeast(
            MINUTES_IN_DAY_MIN
        )
        .coerceAtMost(
            LAST_VALID_TIME_STEP_MINUTES
        )
    }

    private fun scaledChannelOutput(
        channelPercent: Int,
        masterPercent: Int
    ): Int {
        val safeChannel =
        channelPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        val safeMaster =
        masterPercent.coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )

        return ((safeChannel * safeMaster) / PERCENT_DIVIDER)
        .roundToInt()
        .coerceIn(
            MIN_PERCENT,
            MAX_PERCENT
        )
    }

    private const val MIN_PERCENT = 0
    private const val MAX_PERCENT = 100
    private const val PERCENT_DIVIDER = 100f

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR
    private const val MINUTES_IN_DAY_MIN = 0
    private const val MINUTES_IN_DAY_MAX = MINUTES_IN_DAY - 1

    private const val TIME_STEP_MINUTES = 15
    private const val TIME_STEP_ROUNDING_OFFSET = 7
    private const val LAST_VALID_TIME_STEP_MINUTES = MINUTES_IN_DAY - TIME_STEP_MINUTES

    private const val DEFAULT_CUSTOM_POINT_MINUTES = 12 * MINUTES_IN_HOUR
}
