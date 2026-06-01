package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.LightProgramEditorRemoteResponse
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.LightProgramEditorSaveRequest
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.RemoteAcclimation
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.RemoteChannelBalance
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.RemoteCurve
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.RemoteCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramAcclimationDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramChannelBalanceDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramCurveDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramCurvePointDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramPointRole
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRepeatRuleDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel

class LightProgramEditorMapper {

    fun fromRemote(
        response: LightProgramEditorRemoteResponse
    ): LightProgramEditorDraft {
        return LightProgramEditorDraft(
            programId = response.programId,
            programName = response.programName.orEmpty(),
            mode = response.mode.toEditorMode(),
            repeatRule = LightProgramRepeatRuleDraft(
                selectedDays = response.repeatDays.orEmpty().toSet()
            ),
            rampSmoothing = response.rampSmoothing.toRampSmoothing(),
            simpleCurve = response.simpleCurve?.toDomainCurve(),
            proCurves = response.proCurves
                .orEmpty()
                .map { curve ->
                    curve.toDomainCurve()
                },
            channelBalance = response.channelBalance.toDomainBalance(),
            acclimation = response.acclimation.toDomainAcclimation()
        )
    }

    fun toSaveRequest(
        draft: LightProgramEditorDraft
    ): LightProgramEditorSaveRequest {
        return LightProgramEditorSaveRequest(
            programId = draft.programId,
            programName = draft.programName,
            mode = draft.mode.name,
            repeatDays = draft.repeatRule.selectedDays.toList().sorted(),
            rampSmoothing = draft.rampSmoothing.name,
            simpleCurve = draft.simpleCurve?.toRemoteCurve(),
            proCurves = draft.proCurves.map { curve ->
                curve.toRemoteCurve()
            },
            channelBalance = RemoteChannelBalance(
                redPercent = draft.channelBalance.redPercent,
                greenPercent = draft.channelBalance.greenPercent,
                bluePercent = draft.channelBalance.bluePercent,
                whitePercent = draft.channelBalance.whitePercent
            ),
            acclimation = RemoteAcclimation(
                enabled = draft.acclimation.enabled,
                durationDays = draft.acclimation.durationDays,
                startIntensityPercent = draft.acclimation.startIntensityPercent
            )
        )
    }

    private fun RemoteCurve.toDomainCurve(): LightProgramCurveDraft {
        return LightProgramCurveDraft(
            channel = channel.toCurveChannel(),
            points = points
                .orEmpty()
                .map { point ->
                    LightProgramCurvePointDraft(
                        id = point.id.orEmpty(),
                        role = point.role.toPointRole(),
                        label = point.label.orEmpty(),
                        minuteOfDay = point.minuteOfDay.safeMinute(),
                        intensityPercent = point.intensityPercent.safePercent(),
                        canRename = point.canRename == true,
                        canDelete = point.canDelete == true
                    )
                }
        )
    }

    private fun LightProgramCurveDraft.toRemoteCurve(): RemoteCurve {
        return RemoteCurve(
            channel = channel.name,
            points = points.map { point ->
                RemoteCurvePoint(
                    id = point.id,
                    role = point.role.name,
                    label = point.label,
                    minuteOfDay = point.minuteOfDay,
                    intensityPercent = point.intensityPercent,
                    canRename = point.canRename,
                    canDelete = point.canDelete
                )
            }
        )
    }

    private fun RemoteChannelBalance?.toDomainBalance(): LightProgramChannelBalanceDraft {
        return LightProgramChannelBalanceDraft(
            redPercent = this?.redPercent?.safePercent(),
            greenPercent = this?.greenPercent?.safePercent(),
            bluePercent = this?.bluePercent?.safePercent(),
            whitePercent = this?.whitePercent?.safePercent()
        )
    }

    private fun RemoteAcclimation?.toDomainAcclimation(): LightProgramAcclimationDraft {
        return LightProgramAcclimationDraft(
            enabled = this?.enabled == true,
            durationDays = this?.durationDays,
            startIntensityPercent = this?.startIntensityPercent?.safePercent()
        )
    }

    private fun String?.toEditorMode(): LightProgramEditorMode {
        return when (this) {
            LightProgramEditorMode.PRO.name -> LightProgramEditorMode.PRO
            else -> LightProgramEditorMode.SIMPLE
        }
    }

    private fun String?.toRampSmoothing(): LightProgramRampSmoothing {
        return when (this) {
            LightProgramRampSmoothing.SOFT.name -> LightProgramRampSmoothing.SOFT
            LightProgramRampSmoothing.NATURAL.name -> LightProgramRampSmoothing.NATURAL
            else -> LightProgramRampSmoothing.LINEAR
        }
    }

    private fun String?.toCurveChannel(): LightCurveChannel {
        return when (this) {
            LightCurveChannel.RED.name -> LightCurveChannel.RED
            LightCurveChannel.GREEN.name -> LightCurveChannel.GREEN
            LightCurveChannel.BLUE.name -> LightCurveChannel.BLUE
            LightCurveChannel.WHITE.name -> LightCurveChannel.WHITE
            else -> LightCurveChannel.MASTER
        }
    }

    private fun String?.toPointRole(): LightProgramPointRole {
        return when (this) {
            LightProgramPointRole.START.name -> LightProgramPointRole.START
            LightProgramPointRole.PEAK_START.name -> LightProgramPointRole.PEAK_START
            LightProgramPointRole.PEAK_END.name -> LightProgramPointRole.PEAK_END
            LightProgramPointRole.END.name -> LightProgramPointRole.END
            else -> LightProgramPointRole.INTERMEDIATE
        }
    }

    private fun Int?.safePercent(): Int {
        return this?.coerceIn(
            0,
            100
        ) ?: 0
    }

    private fun Int?.safeMinute(): Int {
        return this?.coerceIn(
            0,
            MINUTES_IN_DAY - 1
        ) ?: 0
    }

    private companion object {
        private const val MINUTES_IN_DAY = 24 * 60
    }
}