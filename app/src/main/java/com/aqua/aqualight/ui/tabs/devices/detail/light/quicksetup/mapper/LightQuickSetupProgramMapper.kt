package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupCurvePointKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.GeneratedQuickSetupProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.LightQuickSetupDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.WrgbChannelOutput

object LightQuickSetupProgramMapper {

    fun mapToGeneratedProgram(
        deviceId: Long,
        programName: String,
        draft: LightQuickSetupDraft
    ): GeneratedQuickSetupProgramDraft {
        val balance =
            draft.balancePreset.balance

        return GeneratedQuickSetupProgramDraft(
            deviceId = deviceId,
            programName = programName,
            repeatDays = draft.selectedDays,
            rampMinutes = draft.rampMinutes,
            peakIntensityPercent = draft.peakIntensityPercent,
            balancePreset = draft.balancePreset,
            balance = balance,
            curvePoints =
                listOf(
                    createPoint(
                        kind = GeneratedQuickSetupCurvePointKind.START,
                        timeMinutes = draft.sunriseStartMinutes,
                        masterPercent = 0,
                        draft = draft
                    ),
                    createPoint(
                        kind = GeneratedQuickSetupCurvePointKind.PEAK_START,
                        timeMinutes = draft.peakStartMinutes,
                        masterPercent = draft.peakIntensityPercent,
                        draft = draft
                    ),
                    createPoint(
                        kind = GeneratedQuickSetupCurvePointKind.PEAK_END,
                        timeMinutes = draft.peakEndMinutes,
                        masterPercent = draft.peakIntensityPercent,
                        draft = draft
                    ),
                    createPoint(
                        kind = GeneratedQuickSetupCurvePointKind.END,
                        timeMinutes = draft.sunsetEndMinutes,
                        masterPercent = 0,
                        draft = draft
                    )
                )
        )
    }

    private fun createPoint(
        kind: GeneratedQuickSetupCurvePointKind,
        timeMinutes: Int,
        masterPercent: Int,
        draft: LightQuickSetupDraft
    ): GeneratedQuickSetupCurvePoint {
        return GeneratedQuickSetupCurvePoint(
            kind = kind,
            timeMinutes = timeMinutes,
            masterPercent = masterPercent,
            channelOutput =
                if (masterPercent <= 0) {
                    WrgbChannelOutput(
                        red = 0,
                        green = 0,
                        blue = 0,
                        white = 0
                    )
                } else {
                    draft.balancePreset.balance.scaledBy(
                        masterPercent = masterPercent
                    )
                }
        )
    }
}