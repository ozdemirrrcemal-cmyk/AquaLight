package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene

internal fun debugLightAutomaticFixturePrograms(
    channels: List<DeviceLightAutomaticChannel>
): List<DeviceLightAutomaticProgram> = listOf(
    DeviceLightAutomaticProgram(
        programId = "ap-00000001",
        enabled = true,
        weekdaysMask = EVERY_DAY_MASK,
        startTimeMs = fixtureHours(FIRST_PROGRAM_START_HOUR),
        endTimeMs = fixtureHours(FIRST_PROGRAM_END_HOUR),
        rampDurationMs = fixtureMinutes(FIRST_PROGRAM_RAMP_MINUTES),
        scene = fixtureScene(channels, red = 70, green = 60, blue = 50, white = 80)
    ),
    DeviceLightAutomaticProgram(
        programId = "ap-00000002",
        enabled = false,
        weekdaysMask = MONDAY_WEDNESDAY_FRIDAY_MASK,
        startTimeMs = fixtureHours(SECOND_PROGRAM_START_HOUR),
        endTimeMs = fixtureHours(SECOND_PROGRAM_END_HOUR),
        rampDurationMs = fixtureMinutes(SECOND_PROGRAM_RAMP_MINUTES),
        scene = fixtureScene(channels, red = 100, green = 40, blue = 30, white = 40)
    ),
    DeviceLightAutomaticProgram(
        programId = "ap-00000003",
        enabled = false,
        weekdaysMask = TUESDAY_THURSDAY_SATURDAY_MASK,
        startTimeMs = fixtureHours(THIRD_PROGRAM_START_HOUR),
        endTimeMs = fixtureHours(THIRD_PROGRAM_END_HOUR),
        rampDurationMs = fixtureMinutes(THIRD_PROGRAM_RAMP_MINUTES),
        scene = fixtureScene(channels, red = 50, green = 60, blue = 70, white = 70)
    )
)

internal fun String.toDebugAutomaticChannel(): DeviceLightAutomaticChannel = checkNotNull(
    DeviceLightAutomaticChannel.entries.singleOrNull { channel -> channel.wireKey == this }
) { "Unsupported Debug Light fixture channel: $this" }

internal val debugLightAutomaticRampDurationsMs = listOf(
    fixtureMinutes(NO_RAMP_MINUTES),
    fixtureMinutes(FIRST_PROGRAM_RAMP_MINUTES),
    fixtureMinutes(SECOND_PROGRAM_RAMP_MINUTES),
    fixtureMinutes(THIRD_PROGRAM_RAMP_MINUTES),
    fixtureMinutes(FOURTH_RAMP_MINUTES),
    fixtureMinutes(FIFTH_RAMP_MINUTES)
)

private fun fixtureScene(
    channels: List<DeviceLightAutomaticChannel>,
    red: Int,
    green: Int,
    blue: Int,
    white: Int
): DeviceLightAutomaticScene = DeviceLightAutomaticScene(
    channels.associateWith { channel ->
        when (channel) {
            DeviceLightAutomaticChannel.RED -> red
            DeviceLightAutomaticChannel.GREEN -> green
            DeviceLightAutomaticChannel.BLUE -> blue
            DeviceLightAutomaticChannel.WHITE -> white
        }
    }
)

private fun fixtureHours(value: Int): Long = value * MINUTES_PER_HOUR * MILLIS_PER_MINUTE

private fun fixtureMinutes(value: Int): Long = value * MILLIS_PER_MINUTE

private const val FIRST_PROGRAM_START_HOUR = 6
private const val FIRST_PROGRAM_END_HOUR = 22
private const val FIRST_PROGRAM_RAMP_MINUTES = 30
private const val SECOND_PROGRAM_START_HOUR = 8
private const val SECOND_PROGRAM_END_HOUR = 20
private const val SECOND_PROGRAM_RAMP_MINUTES = 60
private const val THIRD_PROGRAM_START_HOUR = 7
private const val THIRD_PROGRAM_END_HOUR = 21
private const val THIRD_PROGRAM_RAMP_MINUTES = 90
private const val EVERY_DAY_MASK = 0x7f
private const val MONDAY_WEDNESDAY_FRIDAY_MASK = 0x54
private const val TUESDAY_THURSDAY_SATURDAY_MASK = 0x2a
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
private const val NO_RAMP_MINUTES = 0
private const val FOURTH_RAMP_MINUTES = 120
private const val FIFTH_RAMP_MINUTES = 150
