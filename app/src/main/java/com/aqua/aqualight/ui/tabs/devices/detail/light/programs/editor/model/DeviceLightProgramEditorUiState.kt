package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramRepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.toDataTransitionMode

/**
 * Editor state is intentionally kept UI-friendly while preserving future
 * firmware fields. Repeat is persisted in the model but locked to EVERY until
 * device firmware supports custom days. Transition is active in-app: the app
 * compiles the selected transition into concrete LP points sent to the device.
 */
data class DeviceLightProgramEditorUiState(
    val programId: String? = null,
    val programName: String = DEFAULT_PROGRAM_NAME,
    val isEditingExistingProgram: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingToDevice: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val syncState: LightProgramSyncState = LightProgramSyncState.LOCAL_ONLY,
    val repeatFeatureEnabled: Boolean = false,
    val transitionFeatureEnabled: Boolean = true,
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val transitionMode: LightCurveTransitionMode,
    val previewSpeed: PreviewSpeed,
    val currentDeviceTime: LightCurvePoint,
    val previewSimulationTime: LightCurvePoint? = null,
    val isPreviewRunning: Boolean = false,
    val previewProgressPercent: Int = 0
) {
    val draft: LightProgramDraft
        get() {
            val normalizedChannels = channelValues.normalized()
            return LightProgramDraft(
                startMinute = LightProgramTimeMath.startMinutes(start),
                peakStartMinute = LightProgramTimeMath.normalMinutes(peakStart),
                peakEndMinute = LightProgramTimeMath.normalMinutes(peakEnd),
                endMinute = LightProgramTimeMath.endMinutes(end),
                red = normalizedChannels.red,
                green = normalizedChannels.green,
                blue = normalizedChannels.blue,
                white = normalizedChannels.white,
                repeatMode = LightProgramRepeatMode.EVERY,
                selectedDays = ALL_DAYS,
                transitionMode = transitionMode.toDataTransitionMode()
            )
        }

    val graphState: LightCurveGraphState
        get() = LightCurveGraphState(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues.normalized(),
            currentTime = previewSimulationTime ?: currentDeviceTime,
            transitionMode = transitionMode
        )

    val isBusy: Boolean
        get() = isLoading || isSaving || isLoadingToDevice

    companion object {
        private const val DEFAULT_PROGRAM_NAME = "New Program"
        val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

        fun default(): DeviceLightProgramEditorUiState {
            return DeviceLightProgramEditorUiState(
                start = LightCurvePoint.of(8, 0),
                peakStart = LightCurvePoint.of(10, 0),
                peakEnd = LightCurvePoint.of(16, 0),
                end = LightCurvePoint.of(18, 0),
                channelValues = LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0),
                repeatMode = RepeatMode.EVERY,
                selectedDays = ALL_DAYS,
                transitionMode = LightCurveTransitionMode.NATURAL,
                previewSpeed = PreviewSpeed.ONE_MINUTE,
                currentDeviceTime = LightCurvePoint.of(0, 0)
            )
        }
    }
}
