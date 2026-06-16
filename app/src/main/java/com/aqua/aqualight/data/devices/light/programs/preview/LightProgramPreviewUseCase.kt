package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceSchedule
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramPointExpansionOptions
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft

/**
 * Program editor preview contract.
 *
 * It keeps three things aligned:
 * 1. The editor draft selected by the user.
 * 2. The concrete LP points that will be uploaded when the program is saved.
 * 3. The temporary VManual values used for live preview.
 */
class LightProgramPreviewUseCase(
    private val temporaryManualSender: LightProgramTemporaryManualSender
) {

    fun compileSchedule(
        draft: LightProgramDraft,
        options: LightProgramPointExpansionOptions = LightProgramPointExpansionOptions()
    ): LightProgramDeviceSchedule {
        return LightProgramPreviewEngine.compileSchedule(
            draft = draft,
            options = options
        )
    }

    fun frameAt(
        schedule: LightProgramDeviceSchedule,
        elapsedMillis: Long,
        previewDurationMillis: Long
    ): LightProgramPreviewFrame {
        return LightProgramPreviewEngine.frameAt(
            schedule = schedule,
            elapsedMillis = elapsedMillis,
            previewDurationMillis = previewDurationMillis
        )
    }

    suspend fun beginLivePreview(): ApiResult<Unit> {
        return temporaryManualSender.begin()
    }

    suspend fun sendLivePreviewFrame(
        frame: LightProgramPreviewFrame
    ): ApiResult<Unit> {
        return temporaryManualSender.send(frame)
    }

    suspend fun stopLivePreview(): ApiResult<Unit> {
        return temporaryManualSender.stop()
    }
}
