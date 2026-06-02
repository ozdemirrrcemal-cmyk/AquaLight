package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveSeries

object SavedLightProgramListMapper {

    fun map(
        context: Context,
        programs: List<SavedLightProgram>
    ): LightProgramListUiState {
        val items =
            programs.map { program ->
                program.toListItem(
                    context = context
                )
            }

        val activeProgramId =
            programs.firstOrNull { program ->
                program.isActive && program.isEnabled
            }?.id

        return LightProgramListUiState(
            activeProgram =
                items.firstOrNull { item ->
                    item.id == activeProgramId
                },
            programs = items
        )
    }

    private fun SavedLightProgram.toListItem(
        context: Context
    ): LightProgramListItem {
        val startMinute =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.START
            }?.minuteOfDay ?: DEFAULT_START_MINUTES

        val endMinute =
            curvePoints.firstOrNull { point ->
                point.kind == SavedLightProgramCurvePointKind.END
            }?.minuteOfDay ?: DEFAULT_END_MINUTES

        val startTime = startMinute.toTimeLabel()
        val endTime = endMinute.toTimeLabel()
        val repeat = repeatDays.toRepeatLabel()
        val peak = context.getString(
            R.string.common_percent_value,
            peakIntensityPercent
        )

        return LightProgramListItem(
            id = id,
            title = title,
            subtitle = "$startTime → $endTime · $repeat",
            scheduleSummary = "$startTime → $endTime · $repeat",
            startTimeLabel = startTime,
            rampLabel = "$rampMinutes min",
            endTimeLabel = endTime,
            repeatLabel = repeat,
            peakLabel = peak,
            redLabel = "R${balance.red}",
            greenLabel = "G${balance.green}",
            blueLabel = "B${balance.blue}",
            whiteLabel = "W${balance.white}",
            photoperiodLabel = photoperiodLabel(
                startMinute = startMinute,
                endMinute = endMinute
            ),
            isEnabled = isActive && isEnabled,
            curveData = toCurveData()
        )
    }

    private fun SavedLightProgram.toCurveData(): LightCurveChartData {
        return LightCurveChartData(
            series =
                listOf(
                    LightCurveSeries(
                        channel = LightCurveChannel.MASTER,
                        isActive = true,
                        points =
                            curvePoints
                                .sortedBy { point ->
                                    point.minuteOfDay
                                }
                                .map { point ->
                                    LightCurvePoint(
                                        minuteOfDay = point.minuteOfDay,
                                        intensityPercent = point.masterPercent,
                                        isMajor = point.kind != SavedLightProgramCurvePointKind.CUSTOM
                                    )
                                }
                    )
                ),
            currentTimeMinutes = null
        )
    }

    private fun Set<Int>.toRepeatLabel(): String {
        return when (this) {
            ALL_DAYS -> "Every day"
            WEEKDAYS -> "Weekdays"
            WEEKEND -> "Weekend"
            else -> "$size days"
        }
    }

    private fun photoperiodLabel(
        startMinute: Int,
        endMinute: Int
    ): String {
        val durationMinutes =
            if (endMinute >= startMinute) {
                endMinute - startMinute
            } else {
                MINUTES_IN_DAY - startMinute + endMinute
            }

        val hours = durationMinutes / MINUTES_IN_HOUR
        val minutes = durationMinutes % MINUTES_IN_HOUR

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun Int.toTimeLabel(): String {
        val safeMinutes =
            ((this % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

        val hour = safeMinutes / MINUTES_IN_HOUR
        val minute = safeMinutes % MINUTES_IN_HOUR

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

    private const val DEFAULT_START_MINUTES = 9 * MINUTES_IN_HOUR
    private const val DEFAULT_END_MINUTES = (19 * MINUTES_IN_HOUR) + 15

    private val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
    private val WEEKEND = setOf(6, 7)
}