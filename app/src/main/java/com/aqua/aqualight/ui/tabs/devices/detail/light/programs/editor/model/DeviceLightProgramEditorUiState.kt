package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveGraphChannel
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveGraphControllerChannel
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveGraphControllerPoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.capability.LightProgramFirmwareCapabilities
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceChannel
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceSchedule
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult

data class DeviceLightProgramEditorUiState(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val repeatSelectionEnabled: Boolean,
    val repeatUnavailableReason: String?,
    val transitionMode: LightCurveTransitionMode,
    val previewSpeed: PreviewSpeed,
    val currentDeviceTime: LightCurvePoint,
    val previewSimulationTime: LightCurvePoint? = null,
    val previewOutputValues: LightCurveChannelValues? = null,
    val isPreviewRunning: Boolean = false,
    val previewProgressPercent: Int = 0
) {
    val validationResult: LightProgramValidationResult
        get() = LightProgramDraftValidator.validate(draft)

    val validationMessage: String?
        get() = when (val result = validationResult) {
            LightProgramValidationResult.Valid -> null
            is LightProgramValidationResult.Invalid -> result.message
        }

    val isProgramValid: Boolean
        get() = validationResult == LightProgramValidationResult.Valid

    val canStartPreview: Boolean
        get() = isProgramValid

    val canSave: Boolean
        get() = isProgramValid

    val canLoadToDevice: Boolean
        get() = isProgramValid

    val draft: LightProgramDraft
        get() = LightProgramDraft(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues.normalized(),
            repeatMode = repeatMode,
            selectedDays = selectedDays,
            transitionMode = transitionMode
        )

    val graphState: LightCurveGraphState
        get() {
            val compiledSchedule = compiledDeviceSchedule()
            return LightCurveGraphState(
                start = start,
                peakStart = peakStart,
                peakEnd = peakEnd,
                end = end,
                channelValues = channelValues.normalized(),
                currentTime = previewSimulationTime ?: currentDeviceTime,
                transitionMode = transitionMode,
                controllerPointChannels = compiledSchedule.channels.map { channel ->
                    LightCurveGraphControllerChannel(
                        channel = when (channel.channel) {
                            LightProgramDeviceChannel.WHITE -> LightCurveGraphChannel.WHITE
                            LightProgramDeviceChannel.RED -> LightCurveGraphChannel.RED
                            LightProgramDeviceChannel.GREEN -> LightCurveGraphChannel.GREEN
                            LightProgramDeviceChannel.BLUE -> LightCurveGraphChannel.BLUE
                        },
                        points = channel.points.map { point ->
                            LightCurveGraphControllerPoint(
                                minuteOfDay = point.minuteOfDay,
                                percent = point.percent
                            )
                        }
                    )
                }
            )
        }

    private fun compiledDeviceSchedule(): LightProgramDeviceSchedule {
        return LightProgramDevicePointExpander.expand(draft)
    }

    companion object {
        fun default(
            capabilities: LightProgramFirmwareCapabilities =
                LightProgramFirmwareCapabilities.CURRENT_ESP32_LP_POINTS_ONLY
        ): DeviceLightProgramEditorUiState {
            return fromDraft(
                draft = LightProgramDraft.default(),
                capabilities = capabilities
            )
        }

        fun fromDraft(
            draft: LightProgramDraft,
            capabilities: LightProgramFirmwareCapabilities =
                LightProgramFirmwareCapabilities.CURRENT_ESP32_LP_POINTS_ONLY,
            currentDeviceTime: LightCurvePoint = LightCurvePoint.of(0, 0),
            previewSpeed: PreviewSpeed = PreviewSpeed.ONE_MINUTE
        ): DeviceLightProgramEditorUiState {
            val safeDraft = draft.copy(
                channelValues = draft.channelValues.normalized(),
                selectedDays = draft.selectedDays.ifEmpty {
                    EVERY_DAY_SELECTION
                }
            )

            return DeviceLightProgramEditorUiState(
                start = safeDraft.start,
                peakStart = safeDraft.peakStart,
                peakEnd = safeDraft.peakEnd,
                end = safeDraft.end,
                channelValues = safeDraft.channelValues,
                repeatMode = safeDraft.repeatMode,
                selectedDays = safeDraft.selectedDays,
                repeatSelectionEnabled = capabilities.supportsWeeklySchedule,
                repeatUnavailableReason = if (capabilities.supportsWeeklySchedule) {
                    null
                } else {
                    WEEKLY_SCHEDULE_FIRMWARE_UNAVAILABLE
                },
                transitionMode = safeDraft.transitionMode,
                previewSpeed = previewSpeed,
                currentDeviceTime = currentDeviceTime
            )
        }
        val EVERY_DAY_SELECTION: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

        const val WEEKLY_SCHEDULE_FIRMWARE_UNAVAILABLE =
            "Weekly scheduling will be available with a future firmware update."
    }
}
