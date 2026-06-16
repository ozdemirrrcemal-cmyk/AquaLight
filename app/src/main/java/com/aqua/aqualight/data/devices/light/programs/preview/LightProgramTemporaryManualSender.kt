package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideStore
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeReadProfile
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSession

/**
 * Sends one preview frame to the current ESP32 firmware using temporary
 * VManual values. This does not write LP program points and therefore does
 * not change the saved program.
 */
class LightProgramTemporaryManualSender(
    private val runtimeSession: LightRuntimeSession,
    private val timeoutMillis: Long = DEFAULT_PREVIEW_MANUAL_TIMEOUT_MILLIS
) {

    private var restoreTarget: LightProgramPreviewRestoreTarget? = null

    suspend fun begin(): ApiResult<Unit> {
        if (restoreTarget == null) {
            restoreTarget = captureRestoreTarget()
        }

        return ApiResult.success(Unit)
    }

    suspend fun send(
        frame: LightProgramPreviewFrame
    ): ApiResult<Unit> {
        return runtimeSession.setTemporaryManualOutput(
            channelValues = frame.outputValues.toApiChannelValues(),
            timeoutMillis = timeoutMillis
        )
    }

    suspend fun stop(): ApiResult<Unit> {
        val target = restoreTarget ?: LightProgramPreviewRestoreTarget.Unknown
        restoreTarget = null

        return when (target) {
            LightProgramPreviewRestoreTarget.ControllerManaged -> {
                runtimeSession.resumeAuto()
            }

            is LightProgramPreviewRestoreTarget.Manual -> {
                runtimeSession.setManualOutput(
                    channelValues = target.channels.normalized()
                )
            }

            is LightProgramPreviewRestoreTarget.Scene -> {
                runtimeSession.setSceneOutput(
                    channelValues = target.channels.normalized(),
                    sceneName = target.sceneName,
                    sceneSource = target.sceneSource
                )
            }

            LightProgramPreviewRestoreTarget.Unknown -> {
                ApiResult.success(Unit)
            }
        }
    }

    private suspend fun captureRestoreTarget(): LightProgramPreviewRestoreTarget {
        runtimeSession.state.value.snapshot?.let { snapshot ->
            return LightProgramPreviewRestoreTarget.fromSnapshot(snapshot)
        }

        return when (val result = runtimeSession.refreshNow(
            readProfile = LightRuntimeReadProfile.LIVE
        )) {
            is ApiResult.Success -> LightProgramPreviewRestoreTarget.fromSnapshot(result.value)
            is ApiResult.Error -> {
                LightLocalOverrideStore.current(
                    deviceId = runtimeSession.deviceId
                )?.let { localOverride ->
                    LightProgramPreviewRestoreTarget.fromLocalOverride(localOverride)
                } ?: LightProgramPreviewRestoreTarget.Unknown
            }
        }
    }

    private fun com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues.toApiChannelValues(): LightChannelValues {
        val normalized = normalized()
        return LightChannelValues(
            red = normalized.red,
            green = normalized.green,
            blue = normalized.blue,
            white = normalized.white
        ).normalized()
    }

    companion object {
        /**
         * Preview frames are refreshed regularly. A short timeout prevents the
         * controller from staying in manual override if the app disconnects.
         */
        const val DEFAULT_PREVIEW_MANUAL_TIMEOUT_MILLIS = 2_000L
    }
}
