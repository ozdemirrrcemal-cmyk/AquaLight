package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgramMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupProgramDraft
import kotlin.math.roundToInt

object ProgramEditorDraftFactory {

    fun createNewProgramDraft(
        deviceId: Long,
        title: String
    ): ProgramEditorDraft {
        val balance =
            ProgramEditorChannelBalance(
                red = DEFAULT_RED,
                green = DEFAULT_GREEN,
                blue = DEFAULT_BLUE,
                white = DEFAULT_WHITE
            )

        return ProgramEditorDraft(
            id = null,
            deviceId = deviceId,
            title = title,
            mode = ProgramEditorMode.SIMPLE,
            repeatDays = LightRepeatDay.everyDay(),
            rampSmoothing = ProgramRampSmoothing.LINEAR,
            balance = balance,
            rampMinutes = DEFAULT_RAMP_MINUTES,
            peakIntensityPercent = MAX_PERCENT,
            curvePoints =
                listOf(
                    createCurvePoint(
                        kind = ProgramEditorCurvePointKind.START,
                        minuteOfDay = DEFAULT_START_MINUTES,
                        masterPercent = MIN_PERCENT,
                        balance = balance
                    ),
                    createCurvePoint(
                        kind = ProgramEditorCurvePointKind.PEAK_START,
                        minuteOfDay = DEFAULT_PEAK_START_MINUTES,
                        masterPercent = MAX_PERCENT,
                        balance = balance
                    ),
                    createCurvePoint(
                        kind = ProgramEditorCurvePointKind.PEAK_END,
                        minuteOfDay = DEFAULT_PEAK_END_MINUTES,
                        masterPercent = MAX_PERCENT,
                        balance = balance
                    ),
                    createCurvePoint(
                        kind = ProgramEditorCurvePointKind.END,
                        minuteOfDay = DEFAULT_END_MINUTES,
                        masterPercent = MIN_PERCENT,
                        balance = balance
                    )
                )
        )
    }

    fun fromQuickSetupDraft(
        generatedDraft: GeneratedQuickSetupProgramDraft
    ): ProgramEditorDraft {
        val balance =
            ProgramEditorChannelBalance(
                red = generatedDraft.balance.red,
                green = generatedDraft.balance.green,
                blue = generatedDraft.balance.blue,
                white = generatedDraft.balance.white
            )

        return ProgramEditorDraft(
            id = null,
            deviceId = generatedDraft.deviceId,
            title = generatedDraft.programName,
            mode = ProgramEditorMode.SIMPLE,
            repeatDays = generatedDraft.repeatDays.toEditorRepeatDays(),
            rampSmoothing = ProgramRampSmoothing.LINEAR,
            balance = balance,
            rampMinutes = generatedDraft.rampMinutes,
            peakIntensityPercent = generatedDraft.peakIntensityPercent,
            curvePoints =
                generatedDraft.curvePoints.map { point ->
                    ProgramEditorCurvePoint(
                        kind = point.kind.toEditorCurvePointKind(),
                        minuteOfDay = point.timeMinutes,
                        masterPercent = point.masterPercent,
                        red = point.channelOutput.red,
                        green = point.channelOutput.green,
                        blue = point.channelOutput.blue,
                        white = point.channelOutput.white
                    )
                }
        )
    }

    fun fromSavedProgram(
        savedProgram: SavedLightProgram
    ): ProgramEditorDraft {
        return ProgramEditorDraft(
            id = savedProgram.id,
            deviceId = savedProgram.deviceId,
            title = savedProgram.title,
            mode =
                when (savedProgram.mode) {
                    SavedLightProgramMode.SIMPLE -> ProgramEditorMode.SIMPLE
                    SavedLightProgramMode.PRO -> ProgramEditorMode.PRO
                },
            repeatDays = savedProgram.repeatDays.toEditorRepeatDays(),
            rampSmoothing = ProgramRampSmoothing.LINEAR,
            balance =
                ProgramEditorChannelBalance(
                    red = savedProgram.balance.red,
                    green = savedProgram.balance.green,
                    blue = savedProgram.balance.blue,
                    white = savedProgram.balance.white
                ),
            rampMinutes = savedProgram.rampMinutes,
            peakIntensityPercent = savedProgram.peakIntensityPercent,
            curvePoints =
                savedProgram.curvePoints.map { point ->
                    ProgramEditorCurvePoint(
                        kind = point.kind.toEditorCurvePointKind(),
                        minuteOfDay = point.minuteOfDay,
                        masterPercent = point.masterPercent,
                        red = point.red,
                        green = point.green,
                        blue = point.blue,
                        white = point.white
                    )
                }
        )
    }

    private fun createCurvePoint(
        kind: ProgramEditorCurvePointKind,
        minuteOfDay: Int,
        masterPercent: Int,
        balance: ProgramEditorChannelBalance
    ): ProgramEditorCurvePoint {
        val safeMasterPercent =
            masterPercent.coerceIn(
                MIN_PERCENT,
                MAX_PERCENT
            )

        return ProgramEditorCurvePoint(
            kind = kind,
            minuteOfDay =
                minuteOfDay.coerceIn(
                    MINUTES_IN_DAY_MIN,
                    MINUTES_IN_DAY_MAX
                ),
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

    private fun GeneratedQuickSetupCurvePointKind.toEditorCurvePointKind(): ProgramEditorCurvePointKind {
        return when (this) {
            GeneratedQuickSetupCurvePointKind.START -> ProgramEditorCurvePointKind.START
            GeneratedQuickSetupCurvePointKind.PEAK_START -> ProgramEditorCurvePointKind.PEAK_START
            GeneratedQuickSetupCurvePointKind.PEAK_END -> ProgramEditorCurvePointKind.PEAK_END
            GeneratedQuickSetupCurvePointKind.END -> ProgramEditorCurvePointKind.END
        }
    }

    private fun SavedLightProgramCurvePointKind.toEditorCurvePointKind(): ProgramEditorCurvePointKind {
        return when (this) {
            SavedLightProgramCurvePointKind.START -> ProgramEditorCurvePointKind.START
            SavedLightProgramCurvePointKind.PEAK_START -> ProgramEditorCurvePointKind.PEAK_START
            SavedLightProgramCurvePointKind.PEAK_END -> ProgramEditorCurvePointKind.PEAK_END
            SavedLightProgramCurvePointKind.END -> ProgramEditorCurvePointKind.END
            SavedLightProgramCurvePointKind.CUSTOM -> ProgramEditorCurvePointKind.CUSTOM
        }
    }

    private fun Set<Int>.toEditorRepeatDays(): Set<LightRepeatDay> {
        return mapNotNull { day ->
            when (day) {
                DAY_MON -> LightRepeatDay.MONDAY
                DAY_TUE -> LightRepeatDay.TUESDAY
                DAY_WED -> LightRepeatDay.WEDNESDAY
                DAY_THU -> LightRepeatDay.THURSDAY
                DAY_FRI -> LightRepeatDay.FRIDAY
                DAY_SAT -> LightRepeatDay.SATURDAY
                DAY_SUN -> LightRepeatDay.SUNDAY
                else -> null
            }
        }.toSet()
            .ifEmpty {
                LightRepeatDay.everyDay()
            }
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

        return ((safeChannel * safeMaster) / 100f)
            .roundToInt()
            .coerceIn(
                MIN_PERCENT,
                MAX_PERCENT
            )
    }

    private const val MIN_PERCENT = 0
    private const val MAX_PERCENT = 100

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

    private const val MINUTES_IN_DAY_MIN = 0
    private const val MINUTES_IN_DAY_MAX = MINUTES_IN_DAY - 1

    private const val DEFAULT_RAMP_MINUTES = 60

    private const val DEFAULT_START_MINUTES = 9 * MINUTES_IN_HOUR
    private const val DEFAULT_PEAK_START_MINUTES = 10 * MINUTES_IN_HOUR
    private const val DEFAULT_PEAK_END_MINUTES = (18 * MINUTES_IN_HOUR) + 15
    private const val DEFAULT_END_MINUTES = (19 * MINUTES_IN_HOUR) + 15

    private const val DEFAULT_RED = 80
    private const val DEFAULT_GREEN = 84
    private const val DEFAULT_BLUE = 79
    private const val DEFAULT_WHITE = 65

    private const val DAY_MON = 1
    private const val DAY_TUE = 2
    private const val DAY_WED = 3
    private const val DAY_THU = 4
    private const val DAY_FRI = 5
    private const val DAY_SAT = 6
    private const val DAY_SUN = 7
}