package com.aqua.aqualight.data.devices.light.programs

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues as UiChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudFrequency as UiCloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudSimulationSettings as UiCloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel as UiMoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightSettings as UiMoonlightSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode as UiRepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram

object LightProgramProtoMapper {

    fun toProto(
        program: SavedLightProgram
    ): LightProgram {
        return lightProgram {
            id = program.id
            name = program.name
            isActive = program.isActive
            timing = toProtoTiming(program.draft)
            channels = toProtoChannels(program.draft.channelValues)
            repeatMode = toProtoRepeatMode(program.draft.repeatMode)
            selectedDays.addAll(program.draft.selectedDays.sorted())
            moonlight = toProtoMoonlight(program.draft.moonlightSettings)
            cloudSimulation = toProtoCloudSimulation(program.draft.cloudSimulationSettings)
            transitionMode = toProtoTransitionMode(program.draft.transitionMode)
            createdAt = program.createdAt
            updatedAt = program.updatedAt
        }
    }

    fun fromProto(
        proto: LightProgram
    ): SavedLightProgram {
        return SavedLightProgram(
            id = proto.id,
            name = proto.name,
            isActive = proto.isActive,
            draft = LightProgramDraft(
                start = fromProtoTimePoint(proto.timing.start),
                peakStart = fromProtoTimePoint(proto.timing.peakStart),
                peakEnd = fromProtoTimePoint(proto.timing.peakEnd),
                end = fromProtoTimePoint(proto.timing.end),
                channelValues = fromProtoChannels(proto.channels),
                repeatMode = fromProtoRepeatMode(proto.repeatMode),
                selectedDays = proto.selectedDaysList.toSet(),
                moonlightSettings = fromProtoMoonlight(proto.moonlight),
                cloudSimulationSettings = fromProtoCloudSimulation(proto.cloudSimulation),
                transitionMode = fromProtoTransitionMode(proto.transitionMode)
            ),
            createdAt = proto.createdAt,
            updatedAt = proto.updatedAt
        )
    }

    private fun toProtoTiming(
        draft: LightProgramDraft
    ): LightCurveTiming {
        return lightCurveTiming {
            start = toProtoTimePoint(draft.start)
            peakStart = toProtoTimePoint(draft.peakStart)
            peakEnd = toProtoTimePoint(draft.peakEnd)
            end = toProtoTimePoint(draft.end)
        }
    }

    private fun toProtoTimePoint(
        point: LightCurvePoint
    ): TimePoint {
        return timePoint {
            hour = point.hour
            minute = point.minute
        }
    }

    private fun fromProtoTimePoint(
        point: TimePoint
    ): LightCurvePoint {
        return LightCurvePoint.of(
            hour = point.hour,
            minute = point.minute
        )
    }

    private fun toProtoChannels(
        channels: UiChannelValues
    ): LightChannelValues {
        return lightChannelValues {
            red = channels.red
            green = channels.green
            blue = channels.blue
            white = channels.white
        }
    }

    private fun fromProtoChannels(
        channels: LightChannelValues
    ): UiChannelValues {
        return UiChannelValues(
            red = channels.red,
            green = channels.green,
            blue = channels.blue,
            white = channels.white
        )
    }

    private fun toProtoMoonlight(
        settings: UiMoonlightSettings
    ): MoonlightSettings {
        return moonlightSettings {
            enabled = settings.enabled
            followProgramEnd = settings.followProgramEnd
            startTime = toProtoTimePoint(settings.startTime)
            endTime = toProtoTimePoint(settings.endTime)
            channel = toProtoMoonlightChannel(settings.channel)
            intensityPercent = settings.intensityPercent
        }
    }

    private fun fromProtoMoonlight(
        settings: MoonlightSettings
    ): UiMoonlightSettings {
        return UiMoonlightSettings(
            enabled = settings.enabled,
            followProgramEnd = settings.followProgramEnd,
            startTime = fromProtoTimePoint(settings.startTime),
            endTime = fromProtoTimePoint(settings.endTime),
            channel = fromProtoMoonlightChannel(settings.channel),
            intensityPercent = settings.intensityPercent
        )
    }

    private fun toProtoCloudSimulation(
        settings: UiCloudSimulationSettings
    ): CloudSimulationSettings {
        return cloudSimulationSettings {
            enabled = settings.enabled
            coveragePercent = settings.coveragePercent
            frequency = toProtoCloudFrequency(settings.frequency)
        }
    }

    private fun fromProtoCloudSimulation(
        settings: CloudSimulationSettings
    ): UiCloudSimulationSettings {
        return UiCloudSimulationSettings(
            enabled = settings.enabled,
            coveragePercent = settings.coveragePercent,
            frequency = fromProtoCloudFrequency(settings.frequency)
        )
    }

    private fun toProtoRepeatMode(
        mode: UiRepeatMode
    ): RepeatMode {
        return when (mode) {
            UiRepeatMode.EVERY -> RepeatMode.REPEAT_MODE_EVERY
            UiRepeatMode.WEEK -> RepeatMode.REPEAT_MODE_WEEK
            UiRepeatMode.WEEKEND -> RepeatMode.REPEAT_MODE_WEEKEND
            UiRepeatMode.CUSTOM -> RepeatMode.REPEAT_MODE_CUSTOM
        }
    }

    private fun fromProtoRepeatMode(
        mode: RepeatMode
    ): UiRepeatMode {
        return when (mode) {
            RepeatMode.REPEAT_MODE_EVERY,
            RepeatMode.UNRECOGNIZED -> UiRepeatMode.EVERY
            RepeatMode.REPEAT_MODE_WEEK -> UiRepeatMode.WEEK
            RepeatMode.REPEAT_MODE_WEEKEND -> UiRepeatMode.WEEKEND
            RepeatMode.REPEAT_MODE_CUSTOM -> UiRepeatMode.CUSTOM
        }
    }

    private fun toProtoMoonlightChannel(
        channel: UiMoonlightChannel
    ): MoonlightChannel {
        return when (channel) {
            UiMoonlightChannel.BLUE -> MoonlightChannel.MOONLIGHT_CHANNEL_BLUE
            UiMoonlightChannel.WHITE -> MoonlightChannel.MOONLIGHT_CHANNEL_WHITE
            UiMoonlightChannel.BLUE_WHITE -> MoonlightChannel.MOONLIGHT_CHANNEL_BLUE_WHITE
        }
    }

    private fun fromProtoMoonlightChannel(
        channel: MoonlightChannel
    ): UiMoonlightChannel {
        return when (channel) {
            MoonlightChannel.MOONLIGHT_CHANNEL_BLUE,
            MoonlightChannel.UNRECOGNIZED -> UiMoonlightChannel.BLUE
            MoonlightChannel.MOONLIGHT_CHANNEL_WHITE -> UiMoonlightChannel.WHITE
            MoonlightChannel.MOONLIGHT_CHANNEL_BLUE_WHITE -> UiMoonlightChannel.BLUE_WHITE
        }
    }

    private fun toProtoCloudFrequency(
        frequency: UiCloudFrequency
    ): CloudFrequency {
        return when (frequency) {
            UiCloudFrequency.RARE -> CloudFrequency.CLOUD_FREQUENCY_RARE
            UiCloudFrequency.NORMAL -> CloudFrequency.CLOUD_FREQUENCY_NORMAL
            UiCloudFrequency.FREQUENT -> CloudFrequency.CLOUD_FREQUENCY_FREQUENT
        }
    }

    private fun fromProtoCloudFrequency(
        frequency: CloudFrequency
    ): UiCloudFrequency {
        return when (frequency) {
            CloudFrequency.CLOUD_FREQUENCY_RARE -> UiCloudFrequency.RARE
            CloudFrequency.CLOUD_FREQUENCY_NORMAL,
            CloudFrequency.UNRECOGNIZED -> UiCloudFrequency.NORMAL
            CloudFrequency.CLOUD_FREQUENCY_FREQUENT -> UiCloudFrequency.FREQUENT
        }
    }

    private fun toProtoTransitionMode(
        mode: LightCurveTransitionMode
    ): TransitionMode {
        return when (mode) {
            LightCurveTransitionMode.LINEAR -> TransitionMode.TRANSITION_MODE_LINEAR
            LightCurveTransitionMode.SMOOTH -> TransitionMode.TRANSITION_MODE_SMOOTH
            LightCurveTransitionMode.NATURAL -> TransitionMode.TRANSITION_MODE_NATURAL
        }
    }

    private fun fromProtoTransitionMode(
        mode: TransitionMode
    ): LightCurveTransitionMode {
        return when (mode) {
            TransitionMode.TRANSITION_MODE_LINEAR,
            TransitionMode.UNRECOGNIZED -> LightCurveTransitionMode.LINEAR
            TransitionMode.TRANSITION_MODE_SMOOTH -> LightCurveTransitionMode.SMOOTH
            TransitionMode.TRANSITION_MODE_NATURAL -> LightCurveTransitionMode.NATURAL
        }
    }
}