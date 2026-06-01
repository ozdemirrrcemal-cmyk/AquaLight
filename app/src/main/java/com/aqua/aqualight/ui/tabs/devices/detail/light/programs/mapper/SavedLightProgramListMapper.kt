package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveSeries

object SavedLightProgramListMapper {

    fun map(
        programs: List<SavedLightProgram>
    ): LightProgramListUiState {
        val mappedPrograms =
            programs.map { program ->
                program.toListItem()
            }

        val activeProgram =
            mappedPrograms.firstOrNull { item ->
                programs.firstOrNull { program ->
                    program.id == item.id
                }?.isActive == true && item.isEnabled
            }

        return LightProgramListUiState(
            activeProgram = activeProgram,
            programs = mappedPrograms
        )
    }

    private fun SavedLightProgram.toListItem(): LightProgramListItem {
        val sortedPoints =
            curvePoints.sortedBy { point ->
                point.minuteOfDay
            }

        val startPoint =
            sortedPoints.firstOrNull()

        val endPoint =
            sortedPoints.lastOrNull()

        val scheduleSummary =
            if (startPoint != null && endPoint != null) {
                "${minutesToTime(startPoint.minuteOfDay)} → ${minutesToTime(endPoint.minuteOfDay)} · ${repeatDaysLabel(repeatDays)}"
            } else {
                repeatDaysLabel(repeatDays)
            }

        val photoperiodLabel =
            if (startPoint != null && endPoint != null) {
                durationLabel(
                    startMinutes = startPoint.minuteOfDay,
                    endMinutes = endPoint.minuteOfDay
                )
            } else {
                "-"
            }

        return LightProgramListItem(
            id = id,
            title = title,
            scheduleSummary = scheduleSummary,
            peakLabel = "$peakIntensityPercent%",
            photoperiodLabel = photoperiodLabel,
            channelSummary = "R${balance.red}  G${balance.green}  B${balance.blue}  W${balance.white}",
            isEnabled = isEnabled,
            curveData = toCurveData()
        )
    }

    private fun SavedLightProgram.toCurveData(): LightCurveChartData {
        val points =
            curvePoints
                .sortedBy { point ->
                    point.minuteOfDay
                }
                .map { point ->
                    LightCurvePoint(
                        minuteOfDay = point.minuteOfDay,
                        intensityPercent = point.masterPercent,
                        isMajor = true
                    )
                }

        return LightCurveChartData(
            series =
                listOf(
                    LightCurveSeries(
                        channel = LightCurveChannel.MASTER,
                        isActive = true,
                        points = points
                    )
                ),
            currentTimeMinutes = null
        )
    }

    private fun repeatDaysLabel(
        repeatDays: Set<Int>
    ): String {
        return when (repeatDays) {
            setOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
            setOf(1, 2, 3, 4, 5) -> "Weekdays"
            setOf(6, 7) -> "Weekend"
            else -> "${repeatDays.size} days"
        }
    }

    private fun durationLabel(
        startMinutes: Int,
        endMinutes: Int
    ): String {
        val durationMinutes =
            (endMinutes - startMinutes)
                .coerceAtLeast(0)

        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60

        return if (minutes == 0) {
            "${hours}h"
        } else {
            "${hours}h ${minutes}m"
        }
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            ((minutes % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

        val hour = safeMinutes / MINUTES_IN_HOUR
        val minute = safeMinutes % MINUTES_IN_HOUR

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR
}