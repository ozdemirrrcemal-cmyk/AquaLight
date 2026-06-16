package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.model.ApiResult
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

    suspend fun send(
        frame: LightProgramPreviewFrame
    ): ApiResult<Unit> {
        return runtimeSession.setTemporaryManualOutput(
            channelValues = frame.outputValues.toApiChannelValues(),
            timeoutMillis = timeoutMillis
        )
    }

    suspend fun stop(): ApiResult<Unit> {
        return runtimeSession.resumeAuto()
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
