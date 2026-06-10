package com.aqua.aqualight.data.devices.light.programs

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues as UiChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.CloudFrequency as UiCloudFrequency
import com.aqua.aqualight.data.devices.light.programs.model.CloudSimulationSettings as UiCloudSimulationSettings
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.MoonlightChannel as UiMoonlightChannel
import com.aqua.aqualight.data.devices.light.programs.model.MoonlightSettings as UiMoonlightSettings
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode as UiRepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import java.util.UUID

object LightProgramProtoMapper {

    fun toProto(
        program: SavedLightProgram
    ): LightProgram {
        val safeProgram = sanitizeProgram(program)

        return LightProgram.newBuilder()
            .setId(safeProgram.id)
            .setDeviceId(safeProgram.deviceId)
            .setName(safeProgram.name)
            .setIsActive(safeProgram.isActive)
            .setTiming(toProtoTiming(safeProgram.draft))
            .setChannels(toProtoChannels(safeProgram.draft.channelValues))
            .setRepeatMode(toProtoRepeatMode(safeProgram.draft.repeatMode))
            .addAllSelectedDays(safeProgram.draft.selectedDays.sorted())
            .setMoonlight(toProtoMoonlight(safeProgram.draft.moonlightSettings))
            .setCloudSimulation(toProtoCloudSimulation(safeProgram.draft.cloudSimulationSettings))
            .setTransitionMode(toProtoTransitionMode(safeProgram.draft.transitionMode))
            .setCreatedAt(safeProgram.createdAt)
            .setUpdatedAt(safeProgram.updatedAt)
            .build()
    }

    fun fromProto(
        proto: LightProgram
    ): SavedLightProgram {
        val repeatMode = fromProtoRepeatMode(proto.repeatMode)

        val selectedDays = sanitizeSelectedDays(
            days = proto.selectedDaysList.toSet(),
            repeatMode = repeatMode
        )

        val program = SavedLightProgram(
            id = proto.id.ifBlank {
                UUID.randomUUID().toString()
            },
            deviceId = proto.deviceId.coerceAtLeast(0L),
            name = proto.name.trim().ifBlank {
                "Light Program"
            },
            isActive = proto.isActive,
            draft = LightProgramDraft(
                start = fromProtoTimePoint(proto.timing.start),
                peakStart = fromProtoTimePoint(proto.timing.peakStart),
                peakEnd = fromProtoTimePoint(proto.timing.peakEnd),
                end = fromProtoTimePoint(proto.timing.end),
                channelValues = fromProtoChannels(proto.channels),
                repeatMode = repeatMode,
                selectedDays = selectedDays,
                moonlightSettings = fromProtoMoonlight(proto.moonlight),
                cloudSimulationSettings = fromProtoCloudSimulation(proto.cloudSimulation),
                transitionMode = fromProtoTransitionMode(proto.transitionMode)
            ),
            createdAt = proto.createdAt.coerceAtLeast(0L),
            updatedAt = proto.updatedAt.coerceAtLeast(0L)
        )

        return sanitizeProgram(program)
    }

    private fun sanitizeProgram(
        program: SavedLightProgram
    ): SavedLightProgram {
        val repeatMode = program.draft.repeatMode

        val sanitizedDraft = program.draft.copy(
            start = sanitizeTimePoint(program.draft.start),
            peakStart = sanitizeTimePoint(program.draft.peakStart),
            peakEnd = sanitizeTimePoint(program.draft.peakEnd),
            end = sanitizeTimePoint(program.draft.end),
            channelValues = sanitizeChannels(program.draft.channelValues),
            selectedDays = sanitizeSelectedDays(
                days = program.draft.selectedDays,
                repeatMode = repeatMode
            ),
            moonlightSettings = sanitizeMoonlight(program.draft.moonlightSettings),
            cloudSimulationSettings = sanitizeCloudSimulation(program.draft.cloudSimulationSettings)
        )

        return program.copy(
            id = program.id.ifBlank {
                UUID.randomUUID().toString()
            },
            deviceId = program.deviceId.coerceAtLeast(0L),
            name = program.name.trim().ifBlank {
                "Light Program"
            },
            draft = sanitizedDraft,
            createdAt = program.createdAt.coerceAtLeast(0L),
            updatedAt = program.updatedAt.coerceAtLeast(0L)
        )
    }

    private fun toProtoTiming(
        draft: LightProgramDraft
    ): LightCurveTiming {
        return LightCurveTiming.newBuilder()
            .setStart(toProtoTimePoint(draft.start))
            .setPeakStart(toProtoTimePoint(draft.peakStart))
            .setPeakEnd(toProtoTimePoint(draft.peakEnd))
            .setEnd(toProtoTimePoint(draft.end))
            .build()
    }

    private fun toProtoTimePoint(
        point: LightCurvePoint
    ): TimePoint {
        val safePoint = sanitizeTimePoint(point)

        return TimePoint.newBuilder()
            .setHour(safePoint.hour)
            .setMinute(safePoint.minute)
            .build()
    }

    private fun fromProtoTimePoint(
        point: TimePoint
    ): LightCurvePoint {
        return sanitizeTimePoint(
            LightCurvePoint.of(
                hour = point.hour,
                minute = point.minute
            )
        )
    }

    private fun sanitizeTimePoint(
        point: LightCurvePoint
    ): LightCurvePoint {
        return LightCurvePoint.of(
            hour = point.hour.coerceIn(0, 23),
            minute = point.minute.coerceIn(0, 59)
        )
    }

    private fun toProtoChannels(
        channels: UiChannelValues
    ): LightChannelValues {
        val safeChannels = sanitizeChannels(channels)

        return LightChannelValues.newBuilder()
            .setRed(safeChannels.red)
            .setGreen(safeChannels.green)
            .setBlue(safeChannels.blue)
            .setWhite(safeChannels.white)
            .build()
    }

    private fun fromProtoChannels(
        channels: LightChannelValues
    ): UiChannelValues {
        return sanitizeChannels(
            UiChannelValues(
                red = channels.red,
                green = channels.green,
                blue = channels.blue,
                white = channels.white
            )
        )
    }

    private fun sanitizeChannels(
        channels: UiChannelValues
    ): UiChannelValues {
        return UiChannelValues(
            red = channels.red.coerceIn(0, 100),
            green = channels.green.coerceIn(0, 100),
            blue = channels.blue.coerceIn(0, 100),
            white = channels.white.coerceIn(0, 100)
        )
    }

    private fun toProtoMoonlight(
        settings: UiMoonlightSettings
    ): MoonlightSettings {
        val safeSettings = sanitizeMoonlight(settings)

        return MoonlightSettings.newBuilder()
            .setEnabled(safeSettings.enabled)
            .setFollowProgramEnd(safeSettings.followProgramEnd)
            .setStartTime(toProtoTimePoint(safeSettings.startTime))
            .setEndTime(toProtoTimePoint(safeSettings.endTime))
            .setChannel(toProtoMoonlightChannel(safeSettings.channel))
            .setIntensityPercent(safeSettings.intensityPercent)
            .build()
    }

    private fun fromProtoMoonlight(
        settings: MoonlightSettings
    ): UiMoonlightSettings {
        return sanitizeMoonlight(
            UiMoonlightSettings(
                enabled = settings.enabled,
                followProgramEnd = settings.followProgramEnd,
                startTime = fromProtoTimePoint(settings.startTime),
                endTime = fromProtoTimePoint(settings.endTime),
                channel = fromProtoMoonlightChannel(settings.channel),
                intensityPercent = settings.intensityPercent
            )
        )
    }

    private fun sanitizeMoonlight(
        settings: UiMoonlightSettings
    ): UiMoonlightSettings {
        return settings.copy(
            startTime = sanitizeTimePoint(settings.startTime),
            endTime = sanitizeTimePoint(settings.endTime),
            intensityPercent = settings.intensityPercent.coerceIn(1, 15)
        )
    }

    private fun toProtoCloudSimulation(
        settings: UiCloudSimulationSettings
    ): CloudSimulationSettings {
        val safeSettings = sanitizeCloudSimulation(settings)

        return CloudSimulationSettings.newBuilder()
            .setEnabled(safeSettings.enabled)
            .setCoveragePercent(safeSettings.coveragePercent)
            .setFrequency(toProtoCloudFrequency(safeSettings.frequency))
            .build()
    }

    private fun fromProtoCloudSimulation(
        settings: CloudSimulationSettings
    ): UiCloudSimulationSettings {
        return sanitizeCloudSimulation(
            UiCloudSimulationSettings(
                enabled = settings.enabled,
                coveragePercent = settings.coveragePercent,
                frequency = fromProtoCloudFrequency(settings.frequency)
            )
        )
    }

    private fun sanitizeCloudSimulation(
        settings: UiCloudSimulationSettings
    ): UiCloudSimulationSettings {
        return settings.copy(
            enabled = false,
            coveragePercent = settings.coveragePercent.coerceIn(0, 100)
        )
    }

    private fun sanitizeSelectedDays(
        days: Set<Int>,
        repeatMode: UiRepeatMode
    ): Set<Int> {
        val sanitizedDays = days
            .filter { day ->
                day in 1..7
            }
            .toSet()

        if (sanitizedDays.isNotEmpty()) {
            return sanitizedDays
        }

        return when (repeatMode) {
            UiRepeatMode.EVERY -> setOf(1, 2, 3, 4, 5, 6, 7)
            UiRepeatMode.WEEK -> setOf(1, 2, 3, 4, 5)
            UiRepeatMode.WEEKEND -> setOf(6, 7)
            UiRepeatMode.CUSTOM -> setOf(1, 2, 3, 4, 5, 6, 7)
        }
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